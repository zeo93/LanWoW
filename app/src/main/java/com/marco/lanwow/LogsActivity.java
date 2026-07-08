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
    private String selectedMetric;

    private static final String[] METRIC_LABELS = {
            "Predefinita", "DPS", "HPS", "Boss DPS", "Punteggio M+"};
    private static final String[] METRIC_VALUES = {
            null, "dps", "hps", "bossdps", "playerscore"};

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
            selectedMetric = METRIC_VALUES[pos];
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

    private void selectZone(int index) {
        JSONObject z = zones.get(index);
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
        final String metric = selectedMetric;

        new Thread(() -> {
            JSONObject rankings = null;
            String error = null;
            try {
                rankings = WarcraftLogs.fetchRankings(this, region, realmSlug, name,
                        zoneId, difficulty, metric);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final JSONObject fRankings = rankings;
            final String fError = error;
            main.post(() -> {
                // scarta le risposte di selezioni ormai superate
                boolean sameMetric = metric == null
                        ? selectedMetric == null : metric.equals(selectedMetric);
                if (zoneId != selectedZoneId || difficulty != selectedDifficulty
                        || !sameMetric) {
                    return;
                }
                progress.setVisibility(View.GONE);
                results.removeAllViews();
                showRankings(fRankings, fError);
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
}
