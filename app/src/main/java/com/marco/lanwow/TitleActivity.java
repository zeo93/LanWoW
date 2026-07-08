package com.marco.lanwow;

import android.graphics.Color;
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

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Previsione del cutoff dei titoli M+ (top 0,1% e top 1%) a fine stagione. */
public class TitleActivity extends AppCompatActivity {

    private static final String[] REGIONS = {"eu", "us", "kr", "tw"};

    private final Handler main = new Handler(Looper.getMainLooper());

    private AutoCompleteTextView regionInput;
    private ProgressBar progress;
    private LinearLayout results;
    private String region;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_title);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        results = findViewById(R.id.results);
        regionInput = findViewById(R.id.input_region);

        region = getSharedPreferences("search", MODE_PRIVATE).getString("region", "eu");
        regionInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, REGIONS));
        regionInput.setText(region, false);
        regionInput.setOnItemClickListener((p, v, pos, id) -> {
            region = REGIONS[pos];
            load();
        });

        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final String reg = region;

        new Thread(() -> {
            RaiderIo.Season season = null;
            JSONObject cutoffs = null;
            String error = null;
            try {
                season = RaiderIo.fetchCurrentSeason(reg);
                cutoffs = RaiderIo.fetchSeasonCutoffs(reg, season.slug);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final RaiderIo.Season fSeason = season;
            final JSONObject fCutoffs = cutoffs;
            final String fError = error;
            main.post(() -> {
                if (!reg.equals(region)) {
                    return;
                }
                progress.setVisibility(View.GONE);
                results.removeAllViews();
                if (fCutoffs == null) {
                    LinearLayout col = Ui.newCard(this, results);
                    Ui.addText(this, col, getString(R.string.errore_ricerca, fError),
                            14, 0, false);
                    return;
                }
                saveSnapshot(reg, fSeason, fCutoffs);
                // in alto i dati attuali di raider.io, in basso la previsione
                showCurrent(fCutoffs, "p999", getString(R.string.top_01));
                showCurrent(fCutoffs, "p990", getString(R.string.top_1));
                showSeason(fSeason);
                showPrediction(reg, fSeason, fCutoffs);
            });
        }).start();
    }

    /** Salva lo storico locale dei sei valori (0,1% e 1% per orda/alleanza/tutti). */
    private void saveSnapshot(String reg, RaiderIo.Season season, JSONObject cutoffs) {
        try {
            JSONObject values = new JSONObject();
            for (String pct : new String[]{"p999", "p990"}) {
                JSONObject block = cutoffs.optJSONObject(pct);
                if (block == null) {
                    continue;
                }
                for (String fac : new String[]{"horde", "alliance", "all"}) {
                    JSONObject f = block.optJSONObject(fac);
                    if (f != null) {
                        values.put(pct + "_" + fac, f.optDouble("quantileMinValue", 0));
                    }
                }
            }
            CutoffPredictor.addSnapshot(this, reg, season.slug, values);
        } catch (Exception ignored) {
        }
    }

    private String[][] factions(JSONObject block) {
        return new String[][]{
                {"horde", getString(R.string.orda), block.optString("hordeColor", "")},
                {"alliance", getString(R.string.alleanza), block.optString("allianceColor", "")},
                {"all", getString(R.string.tutti), block.optString("allColor", "")},
        };
    }

    private static int safeColor(String hex) {
        try {
            return hex.isEmpty() ? 0 : Color.parseColor(hex);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Card con i cutoff attuali presi da raider.io. */
    private void showCurrent(JSONObject cutoffs, String pct, String title) {
        JSONObject block = cutoffs.optJSONObject(pct);
        if (block == null) {
            return;
        }
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, title);
        for (String[] f : factions(block)) {
            JSONObject data = block.optJSONObject(f[0]);
            if (data == null) {
                continue;
            }
            double current = data.optDouble("quantileMinValue", 0);
            if (current <= 0) {
                continue;
            }
            Ui.addRow(this, col, f[1],
                    String.format(Locale.ITALY, "%.0f", current), safeColor(f[2]));
        }
    }

    private void showSeason(RaiderIo.Season season) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.stagione) + ": " + season.name);
        long effEnd = CutoffPredictor.effectiveEnd(season.startMs, season.endMs);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);
        Ui.addRow(this, col, getString(R.string.periodo),
                fmt.format(new Date(season.startMs)) + " – ~" + fmt.format(new Date(effEnd)), 0);
        long now = System.currentTimeMillis();
        int week = (int) ((now - season.startMs) / (7L * 24 * 3600 * 1000)) + 1;
        int totalWeeks = (int) Math.round((effEnd - season.startMs)
                / (7.0 * 24 * 3600 * 1000));
        Ui.addRow(this, col, getString(R.string.avanzamento),
                getString(R.string.settimana_di, Math.min(week, totalWeeks), totalWeeks),
                getColor(R.color.gold));
    }

    /** Card finale con la previsione di fine stagione. */
    private void showPrediction(String reg, RaiderIo.Season season, JSONObject cutoffs) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.previsione_fine));

        boolean anyTrend = false;
        for (String[] pctTitle : new String[][]{
                {"p999", getString(R.string.top_01)},
                {"p990", getString(R.string.top_1)}}) {
            JSONObject block = cutoffs.optJSONObject(pctTitle[0]);
            if (block == null) {
                continue;
            }
            Ui.addText(this, col, pctTitle[1], 15, getColor(R.color.gold), true);
            for (String[] f : factions(block)) {
                JSONObject data = block.optJSONObject(f[0]);
                if (data == null) {
                    continue;
                }
                double current = data.optDouble("quantileMinValue", 0);
                if (current <= 0) {
                    continue;
                }
                CutoffPredictor.Prediction pred = CutoffPredictor.predict(this, reg,
                        season.slug, pctTitle[0] + "_" + f[0], current,
                        season.startMs, season.endMs);
                anyTrend |= pred.fromTrend;
                Ui.addRow(this, col, f[1],
                        String.format(Locale.ITALY, "~%.0f", pred.value), safeColor(f[2]));
            }
        }
        Ui.addText(this, col, getString(anyTrend
                ? R.string.metodo_trend : R.string.metodo_fase), 12, 0, false);
        Ui.addText(this, col, getString(R.string.cutoff_note), 12, 0, false);
    }
}
