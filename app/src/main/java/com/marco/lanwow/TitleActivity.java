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
                showSeason(fSeason);
                showPercentile(reg, fSeason, fCutoffs, "p999",
                        getString(R.string.top_01));
                showPercentile(reg, fSeason, fCutoffs, "p990",
                        getString(R.string.top_1));
                showNotes(fSeason);
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

    private void showSeason(RaiderIo.Season season) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.stagione) + ": " + season.name);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);
        Ui.addRow(this, col, getString(R.string.periodo),
                fmt.format(new Date(season.startMs)) + " – " + fmt.format(new Date(season.endMs)), 0);
        double t = (double) (System.currentTimeMillis() - season.startMs)
                / (season.endMs - season.startMs);
        Ui.addRow(this, col, getString(R.string.avanzamento),
                String.format(Locale.ITALY, "%.0f%%", t * 100), getColor(R.color.gold));
    }

    private void showPercentile(String reg, RaiderIo.Season season, JSONObject cutoffs,
                                String pct, String title) {
        JSONObject block = cutoffs.optJSONObject(pct);
        if (block == null) {
            return;
        }
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, title);

        boolean anyTrend = false;
        String[][] factions = {
                {"horde", getString(R.string.orda), block.optString("hordeColor", "")},
                {"alliance", getString(R.string.alleanza), block.optString("allianceColor", "")},
                {"all", getString(R.string.tutti), block.optString("allColor", "")},
        };
        for (String[] f : factions) {
            JSONObject data = block.optJSONObject(f[0]);
            if (data == null) {
                continue;
            }
            double current = data.optDouble("quantileMinValue", 0);
            if (current <= 0) {
                continue;
            }
            CutoffPredictor.Prediction pred = CutoffPredictor.predict(this, reg, season.slug,
                    pct + "_" + f[0], current, season.startMs, season.endMs);
            anyTrend |= pred.fromTrend;
            int color = 0;
            try {
                if (!f[2].isEmpty()) {
                    color = Color.parseColor(f[2]);
                }
            } catch (Exception ignored) {
            }
            Ui.addRow(this, col, f[1],
                    String.format(Locale.ITALY, "%.0f  →  ~%.0f", current, pred.value), color);
        }
        Ui.addText(this, col, getString(anyTrend
                ? R.string.metodo_trend : R.string.metodo_fase), 12, 0, false);
    }

    private void showNotes(RaiderIo.Season season) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addText(this, col, getString(R.string.cutoff_note), 13, 0, false);
    }
}
