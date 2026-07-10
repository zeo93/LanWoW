package com.marco.lanwow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Log WarcraftLogs con filtri come sul sito:
 * espansione → zona (raid o stagione M+) → difficoltà e metrica.
 */
public class LogsActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());

    private String region;
    private String realmSlug;
    private String name;

    private ProgressBar progress;
    private LinearLayout results;
    private View filtersCard;
    private AutoCompleteTextView expansionInput;
    private AutoCompleteTextView zoneInput;
    private AutoCompleteTextView difficultyInput;
    private AutoCompleteTextView metricInput;

    private JSONArray expansions;
    /** Zone dell'espansione selezionata (senza PTR/Beta). */
    private final List<JSONObject> zones = new ArrayList<>();
    private final List<Integer> difficultyIds = new ArrayList<>();

    private int selectedZoneId;
    private String selectedZoneName = "";
    private int selectedDifficulty;
    private int selectedMetricIdx;

    private static final String[] METRIC_LABELS = {
            "Predefinita", "DPS", "HPS", "Boss DPS", "Punteggio M+",
            "DPS per livello chiave", "HPS per livello chiave"};
    private static final String[] METRIC_VALUES = {
            null, "dps", "hps", "bossdps", "playerscore", "dps", "hps"};
    /** true dove il parse va calcolato per livello di chiave (byBracket). */
    private static final boolean[] METRIC_BRACKET = {
            false, false, false, false, false, true, true};

    public static void open(Activity from, String region, String realmSlug, String name) {
        Intent i = new Intent(from, LogsActivity.class)
                .putExtra("region", region)
                .putExtra("realm", realmSlug)
                .putExtra("name", name);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        region = getIntent().getStringExtra("region");
        realmSlug = getIntent().getStringExtra("realm");
        name = getIntent().getStringExtra("name");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.log_di, name));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        results = findViewById(R.id.results);
        filtersCard = findViewById(R.id.filters_card);
        expansionInput = findViewById(R.id.input_expansion);
        zoneInput = findViewById(R.id.input_zone);
        difficultyInput = findViewById(R.id.input_difficulty);
        metricInput = findViewById(R.id.input_metric);

        if (!WarcraftLogs.hasCredentials(this)) {
            LinearLayout col = Ui.newCard(this, results);
            Ui.addSectionTitle(this, col, getString(R.string.logs_warcraftlogs));
            Ui.addText(this, col, getString(R.string.wcl_non_configurato), 14, 0, false);
            return;
        }

        metricInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, METRIC_LABELS));
        metricInput.setText(METRIC_LABELS[0], false);
        metricInput.setOnItemClickListener((p, v, pos, id) -> {
            selectedMetricIdx = pos;
            reload();
        });

        loadExpansions();
    }

    private void loadExpansions() {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            JSONArray exps = null;
            String error = null;
            try {
                exps = WarcraftLogs.fetchExpansions(this);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final JSONArray fExps = exps;
            final String fError = error;
            main.post(() -> {
                if (fExps == null) {
                    progress.setVisibility(View.GONE);
                    LinearLayout col = Ui.newCard(this, results);
                    Ui.addText(this, col, getString(R.string.errore_ricerca, fError),
                            14, 0, false);
                    return;
                }
                expansions = fExps;
                filtersCard.setVisibility(View.VISIBLE);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < expansions.length(); i++) {
                    names.add(expansions.optJSONObject(i).optString("name"));
                }
                expansionInput.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, names));
                expansionInput.setOnItemClickListener((p, v, pos, id) -> selectExpansion(pos));
                // predefinita: l'espansione più recente
                expansionInput.setText(names.get(0), false);
                selectExpansion(0);
            });
        }).start();
    }

    private void selectExpansion(int index) {
        zones.clear();
        JSONObject exp = expansions.optJSONObject(index);
        JSONArray all = exp != null ? exp.optJSONArray("zones") : null;
        List<String> names = new ArrayList<>();
        if (all != null) {
            for (int i = 0; i < all.length(); i++) {
                JSONObject z = all.optJSONObject(i);
                String zn = z != null ? z.optString("name") : "";
                // fuori le zone di test
                if (zn.contains("PTR") || zn.contains("Beta")) {
                    continue;
                }
                zones.add(z);
                names.add(zn);
            }
        }
        zoneInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names));
        zoneInput.setOnItemClickListener((p, v, pos, id) -> selectZone(pos));
        if (!zones.isEmpty()) {
            zoneInput.setText(names.get(0), false);
            selectZone(0);
        } else {
            results.removeAllViews();
        }
    }

    private JSONObject selectedZone;

    private void selectZone(int index) {
        JSONObject z = zones.get(index);
        selectedZone = z;
        selectedZoneId = z.optInt("id");
        selectedZoneName = z.optString("name");

        // difficoltà disponibili per la zona scelta
        difficultyIds.clear();
        List<String> names = new ArrayList<>();
        difficultyIds.add(0);
        names.add(getString(R.string.predefinita));
        JSONArray diffs = z.optJSONArray("difficulties");
        if (diffs != null) {
            for (int i = 0; i < diffs.length(); i++) {
                JSONObject d = diffs.optJSONObject(i);
                if (d != null) {
                    difficultyIds.add(d.optInt("id"));
                    names.add(d.optString("name"));
                }
            }
        }
        difficultyInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names));
        difficultyInput.setText(names.get(0), false);
        selectedDifficulty = 0;
        difficultyInput.setOnItemClickListener((p, v, pos, id) -> {
            selectedDifficulty = difficultyIds.get(pos);
            reload();
        });

        reload();
    }

    private void reload() {
        progress.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final int zoneId = selectedZoneId;
        final int difficulty = selectedDifficulty;
        final int metricIdx = selectedMetricIdx;

        if (METRIC_BRACKET[metricIdx]) {
            reloadByLevel(zoneId, difficulty, metricIdx);
            return;
        }
        new Thread(() -> {
            JSONObject rankings = null;
            String error = null;
            try {
                rankings = WarcraftLogs.fetchRankings(this, region, realmSlug, name,
                        zoneId, difficulty, METRIC_VALUES[metricIdx], false);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final JSONObject fRankings = rankings;
            final String fError = error;
            main.post(() -> {
                // scarta le risposte di selezioni ormai superate
                if (zoneId != selectedZoneId || difficulty != selectedDifficulty
                        || metricIdx != selectedMetricIdx) {
                    return;
                }
                progress.setVisibility(View.GONE);
                results.removeAllViews();
                showRankings(fRankings, fError);
            });
        }).start();
    }

    /** Vista "by level": run singole per encounter, poi chiave più alta completata. */
    private void reloadByLevel(int zoneId, int difficulty, int metricIdx) {
        final JSONObject zone = selectedZone;
        final java.util.List<int[]> ids = new ArrayList<>();
        final java.util.LinkedHashMap<Integer, String> names = new java.util.LinkedHashMap<>();
        JSONArray encs = zone != null ? zone.optJSONArray("encounters") : null;
        if (encs != null) {
            for (int i = 0; i < encs.length(); i++) {
                JSONObject e = encs.optJSONObject(i);
                if (e != null && e.optInt("id", 0) > 0) {
                    ids.add(new int[]{e.optInt("id")});
                    names.put(e.optInt("id"), e.optString("name"));
                }
            }
        }
        new Thread(() -> {
            JSONObject data = null;
            String error = null;
            try {
                if (ids.isEmpty()) {
                    throw new Exception(getString(R.string.wcl_nessun_log));
                }
                data = WarcraftLogs.fetchByLevel(this, region, realmSlug, name,
                        ids, METRIC_VALUES[metricIdx]);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final JSONObject fData = data;
            final String fError = error;
            main.post(() -> {
                if (zoneId != selectedZoneId || difficulty != selectedDifficulty
                        || metricIdx != selectedMetricIdx) {
                    return;
                }
                progress.setVisibility(View.GONE);
                results.removeAllViews();
                showByLevel(fData, names, fError);
            });
        }).start();
    }

    private void showRankings(JSONObject rankings, String error) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, selectedZoneName);

        if (rankings == null) {
            Ui.addText(this, col, getString(R.string.errore_ricerca, error), 14, 0, false);
            return;
        }
        double bestAvg = rankings.optDouble("bestPerformanceAverage", Double.NaN);
        double medianAvg = rankings.optDouble("medianPerformanceAverage", Double.NaN);
        if (!Double.isNaN(bestAvg)) {
            Ui.addRow(this, col, getString(R.string.media_best),
                    String.format(Locale.ITALY, "%.1f", bestAvg), Ui.parseColor(bestAvg));
        }
        if (!Double.isNaN(medianAvg)) {
            Ui.addRow(this, col, getString(R.string.media_mediana),
                    String.format(Locale.ITALY, "%.1f", medianAvg), Ui.parseColor(medianAvg));
        }
        JSONArray list = rankings.optJSONArray("rankings");
        if (list == null || list.length() == 0) {
            Ui.addText(this, col, getString(R.string.wcl_nessun_log), 14, 0, false);
            return;
        }
        boolean any = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            JSONObject enc = r.optJSONObject("encounter");
            String boss = enc != null ? enc.optString("name") : "?";
            int kills = r.optInt("totalKills", 0);
            if (kills == 0 && r.isNull("rankPercent")) {
                Ui.addRow(this, col, boss, "—", 0);
                continue;
            }
            any = true;
            double pct = r.optDouble("rankPercent", 0);
            Ui.addRow(this, col, boss + "  (" + kills + " kill)",
                    String.format(Locale.ITALY, "%.0f%%", Math.floor(pct)),
                    Ui.parseColor(pct));
        }
        if (!any && Double.isNaN(bestAvg)) {
            Ui.addText(this, col, getString(R.string.wcl_nessun_log), 14, 0, false);
        }
    }

    /**
     * Tabella "by level" come sul sito: per ogni dungeon la run che vale più
     * punteggio M+ determina il livello chiave; best % e median % sono calcolati
     * sulle run di quel livello (metrica per livello chiave).
     */
    private void showByLevel(JSONObject data,
                             java.util.LinkedHashMap<Integer, String> names, String error) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, selectedZoneName);
        if (data == null) {
            Ui.addText(this, col, getString(R.string.errore_ricerca, error), 14, 0, false);
            return;
        }

        java.util.List<Object[]> rows = new ArrayList<>();
        double sumBest = 0;
        double sumMed = 0;
        int count = 0;
        for (java.util.Map.Entry<Integer, String> e : names.entrySet()) {
            JSONObject score = data.optJSONObject("p" + e.getKey());
            JSONObject dps = data.optJSONObject("d" + e.getKey());
            JSONArray scoreRanks = score != null ? score.optJSONArray("ranks") : null;
            JSONArray dpsRanks = dps != null ? dps.optJSONArray("ranks") : null;

            // livello della run che vale più punteggio (quella "contata" dal gioco)
            int level = 0;
            double bestPoints = -1;
            if (scoreRanks != null) {
                for (int i = 0; i < scoreRanks.length(); i++) {
                    JSONObject run = scoreRanks.optJSONObject(i);
                    if (run != null && run.optDouble("amount", 0) > bestPoints) {
                        bestPoints = run.optDouble("amount", 0);
                        level = run.optInt("bracketData", 0);
                    }
                }
            }
            if (level <= 0) {
                rows.add(new Object[]{e.getValue(), null});
                continue;
            }
            // parse delle run a quel livello
            java.util.List<Double> pcts = new ArrayList<>();
            double best = 0;
            double bestAmount = 0;
            if (dpsRanks != null) {
                for (int i = 0; i < dpsRanks.length(); i++) {
                    JSONObject run = dpsRanks.optJSONObject(i);
                    if (run == null || run.optInt("bracketData", 0) != level) {
                        continue;
                    }
                    double pct = run.optDouble("rankPercent", 0);
                    pcts.add(pct);
                    if (pct >= best) {
                        best = pct;
                        bestAmount = run.optDouble("amount", 0);
                    }
                }
            }
            if (pcts.isEmpty()) {
                rows.add(new Object[]{e.getValue(), null});
                continue;
            }
            double med = median(pcts);
            sumBest += best;
            sumMed += med;
            count++;
            rows.add(new Object[]{e.getValue(), level, bestAmount, best, med});
        }

        if (count > 0) {
            Ui.addRow(this, col, getString(R.string.media_best),
                    String.format(Locale.ITALY, "%.1f", sumBest / count),
                    Ui.parseColor(sumBest / count));
            Ui.addRow(this, col, getString(R.string.media_mediana),
                    String.format(Locale.ITALY, "%.1f", sumMed / count),
                    Ui.parseColor(sumMed / count));
        } else {
            Ui.addText(this, col, getString(R.string.wcl_nessun_log), 14, 0, false);
            return;
        }

        addLevelRow(col, "", getString(R.string.colonna_chiave), getString(R.string.colonna_best),
                getString(R.string.colonna_best_pct), getString(R.string.colonna_median_pct),
                true, 0, 0);
        for (Object[] r : rows) {
            if (r[1] == null) {
                addLevelRow(col, (String) r[0], "—", "—", "—", "—", false, 0, 0);
                continue;
            }
            double best = (double) r[3];
            double med = (double) r[4];
            addLevelRow(col, (String) r[0],
                    "+" + r[1],
                    formatAmount((double) r[2]),
                    String.format(Locale.ITALY, "%.0f", Math.floor(best)),
                    String.format(Locale.ITALY, "%.0f", Math.floor(med)),
                    false, Ui.parseColor(best), Ui.parseColor(med));
        }
    }

    private static double median(java.util.List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        java.util.Collections.sort(values);
        int n = values.size();
        return n % 2 == 1 ? values.get(n / 2)
                : (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
    }

    private void addLevelRow(LinearLayout parent, String label, String v1, String v2,
                             String v3, String v4, boolean header, int c3, int c4) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

        android.widget.TextView l = new android.widget.TextView(this);
        l.setText(label);
        l.setTextSize(header ? 11 : 13);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        String[] values = {v1, v2, v3, v4};
        int[] widths = {40, 62, 44, 44};
        int[] colors = {0, 0, c3, c4};
        for (int i = 0; i < 4; i++) {
            android.widget.TextView v = new android.widget.TextView(this);
            v.setText(values[i]);
            v.setTextSize(header ? 11 : 13);
            v.setMinWidth(Ui.dp(this, widths[i]));
            v.setGravity(android.view.Gravity.END);
            if (header) {
                v.setTextColor(0xFF9AA3B8);
            } else {
                v.setTypeface(null, android.graphics.Typeface.BOLD);
                if (colors[i] != 0) {
                    v.setTextColor(colors[i]);
                }
            }
            row.addView(v);
        }
        parent.addView(row);
    }

    /** 164732 → "164,7K"; 2500000 → "2,5M". */
    private static String formatAmount(double amount) {
        if (amount >= 1000000) {
            return String.format(Locale.ITALY, "%.1fM", amount / 1000000);
        }
        if (amount >= 1000) {
            return String.format(Locale.ITALY, "%.1fK", amount / 1000);
        }
        return String.format(Locale.ITALY, "%.0f", amount);
    }
}
