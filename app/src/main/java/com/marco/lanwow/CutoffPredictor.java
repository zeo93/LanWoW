package com.marco.lanwow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Previsione del cutoff di fine stagione.
 *
 * Ogni consultazione salva uno snapshot locale del cutoff; con almeno 3 rilevazioni
 * su 6+ giorni la previsione usa una regressione lineare sull'andamento osservato
 * (smorzata, perché la crescita rallenta a fine stagione). Senza storico sufficiente
 * usa una stima sulla fase della stagione: a inizio stagione il cutoff è già circa
 * il 70% di quello finale e cresce in modo quasi lineare.
 */
public final class CutoffPredictor {

    private static final long TWELVE_HOURS = 12L * 3600 * 1000;
    private static final long SIX_DAYS = 6L * 24 * 3600 * 1000;
    private static final double DAY_MS = 24.0 * 3600 * 1000;
    /** La crescita osservata viene proiettata al 80%: verso fine stagione rallenta. */
    private static final double DAMPING = 0.8;
    /**
     * Durata competitiva effettiva di una stagione M+ (~22 settimane): la data di fine
     * "ufficiale" nelle API arriva fino alla stagione successiva ed è molto più lunga.
     */
    public static final long EFFECTIVE_SEASON_MS = 22L * 7 * 24 * 3600 * 1000;

    /** Data di fine nota per stagioni specifiche in formato ISO (null = non nota). */
    public static String knownEndIso(String seasonSlug) {
        if ("season-mn-1".equals(seasonSlug)) {
            // chiusura prevista: 11 agosto 2026
            return "2026-08-11";
        }
        return null;
    }

    /** Fine stagione nota o prevista per stagioni specifiche (0 = non nota). */
    private static long knownEnd(String seasonSlug) {
        String iso = knownEndIso(seasonSlug);
        return iso == null ? 0
                : java.time.Instant.parse(iso + "T00:00:00Z").toEpochMilli();
    }

    /** Fine effettiva della stagione ai fini della previsione. */
    public static long effectiveEnd(String seasonSlug, long seasonStart, long seasonEnd) {
        long known = knownEnd(seasonSlug);
        if (known > 0) {
            return Math.min(seasonEnd, known);
        }
        return Math.min(seasonEnd, seasonStart + EFFECTIVE_SEASON_MS);
    }

    /** Risultato: valore previsto e metodo usato. */
    public static class Prediction {
        public double value;
        public boolean fromTrend;
    }

    private CutoffPredictor() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cutoff_history", Context.MODE_PRIVATE);
    }

    private static String key(String region, String season) {
        return region + "_" + season;
    }

    /** Registra i valori correnti (al massimo uno snapshot ogni 12 ore). */
    public static void addSnapshot(Context c, String region, String season,
                                   JSONObject values) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray arr = new JSONArray(p.getString(key(region, season), "[]"));
            long now = System.currentTimeMillis();
            if (arr.length() > 0) {
                long last = arr.getJSONObject(arr.length() - 1).optLong("t");
                if (now - last < TWELVE_HOURS) {
                    return;
                }
            }
            arr.put(new JSONObject().put("t", now).put("v", values));
            p.edit().putString(key(region, season), arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Previsione di fine stagione per la serie indicata (es. "p999_all").
     *
     * @param current valore attuale del cutoff
     */
    public static Prediction predict(Context c, String region, String season, String series,
                                     double current, long seasonStart, long seasonEnd) {
        Prediction out = new Prediction();
        long now = System.currentTimeMillis();
        seasonEnd = effectiveEnd(season, seasonStart, seasonEnd);

        // 1) regressione lineare sugli snapshot locali, se bastano
        List<long[]> raw = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(key(region, season), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                JSONObject v = s.optJSONObject("v");
                if (v != null && v.has(series)) {
                    raw.add(new long[]{s.optLong("t")});
                    vals.add(v.optDouble(series));
                }
            }
        } catch (Exception ignored) {
        }
        if (vals.size() >= 3
                && raw.get(raw.size() - 1)[0] - raw.get(0)[0] >= SIX_DAYS) {
            // regressione y = a + b*x con x in giorni
            int n = vals.size();
            double sumX = 0;
            double sumY = 0;
            double sumXY = 0;
            double sumXX = 0;
            long t0 = raw.get(0)[0];
            for (int i = 0; i < n; i++) {
                double x = (raw.get(i)[0] - t0) / DAY_MS;
                double y = vals.get(i);
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumXX += x * x;
            }
            double denom = n * sumXX - sumX * sumX;
            if (denom != 0) {
                double slopePerDay = (n * sumXY - sumX * sumY) / denom;
                double daysRemaining = Math.max(0, (seasonEnd - now) / DAY_MS);
                if (slopePerDay > 0) {
                    out.value = current + slopePerDay * daysRemaining * DAMPING;
                    out.fromTrend = true;
                    return out;
                }
            }
        }

        // 2) stima sulla fase della stagione
        double t = (double) (now - seasonStart) / (seasonEnd - seasonStart);
        t = Math.max(0.05, Math.min(1.0, t));
        out.value = Math.max(current, current / (0.70 + 0.30 * t));
        out.fromTrend = false;
        return out;
    }
}
