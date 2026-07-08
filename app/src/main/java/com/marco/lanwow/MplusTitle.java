package com.marco.lanwow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Previsione del cutoff titolo calcolata da mplus-title.vercel.app
 * (progetto open source di ljosberinn: fit logaritmico + tasso settimanale pesato
 * + Holt smorzato, sullo storico completo dei cutoff raccolto dal sito).
 */
public final class MplusTitle {

    private static final Pattern RESPONSE = Pattern.compile(
            "Current:\\s*([\\d.]+).*Estimation\\s*\\(([^)]*)\\):\\s*([\\d.]+)");

    /** Risposta del sito: cutoff attuale e previsione (top 0,1% cross-faction). */
    public static class Forecast {
        public double current;
        public double estimation;
        public String label;

        /** Fattore di crescita previsto, applicabile anche agli altri cutoff. */
        public double factor() {
            return current > 0 ? estimation / current : 1;
        }
    }

    private MplusTitle() {
    }

    /**
     * @param endDateIso data di fine stagione nota (es. "2026-08-11"), o null:
     *                   senza data il sito estrapola a +2 settimane
     */
    public static Forecast fetch(String region, String endDateIso) throws Exception {
        String url = "https://mplus-title.vercel.app/api/cutoff/" + region.toUpperCase();
        if (endDateIso != null && !endDateIso.isEmpty()) {
            url += "?extrapolationEndDate=" + endDateIso;
        }
        String body = Http.get(url);
        Matcher m = RESPONSE.matcher(body);
        if (!m.find()) {
            throw new Exception("risposta inattesa da mplus-title");
        }
        Forecast f = new Forecast();
        f.current = Double.parseDouble(m.group(1));
        f.label = m.group(2);
        f.estimation = Double.parseDouble(m.group(3));
        if (f.estimation < f.current) {
            f.estimation = f.current;
        }
        return f;
    }
}
