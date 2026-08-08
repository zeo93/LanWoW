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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cutoff dei titoli M+ (top 0,1% e top 1%): stagione in corso con previsione di
 * fine stagione, oppure una stagione passata con i suoi cutoff definitivi.
 */
public class TitleActivity extends AppCompatActivity {

    private static final String[] REGIONS = {"eu", "us", "kr", "tw"};
    /** Elenco stagioni per regione, riusato finché l'app resta aperta. */
    private static final Map<String, List<RaiderIo.Season>> SEASON_CACHE = new HashMap<>();

    private final Handler main = new Handler(Looper.getMainLooper());

    private AutoCompleteTextView regionInput;
    private AutoCompleteTextView seasonInput;
    private ProgressBar progress;
    private LinearLayout results;

    private String region;
    private List<RaiderIo.Season> seasons = new ArrayList<>();
    private int selectedSeason;

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
        seasonInput = findViewById(R.id.input_season);

        region = getSharedPreferences("search", MODE_PRIVATE).getString("region", "eu");
        regionInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, REGIONS));
        regionInput.setText(region, false);
        regionInput.setOnItemClickListener((p, v, pos, id) -> {
            region = REGIONS[pos];
            loadSeasons();
        });

        loadSeasons();
    }

    /** Scarica l'elenco stagioni e popola la tendina (la prima è quella in corso). */
    private void loadSeasons() {
        progress.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final String reg = region;

        List<RaiderIo.Season> cached = SEASON_CACHE.get(reg);
        if (cached != null) {
            applySeasons(cached);
            return;
        }
        new Thread(() -> {
            List<RaiderIo.Season> list = null;
            String error = null;
            try {
                list = RaiderIo.fetchSeasons(reg);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<RaiderIo.Season> fList = list;
            final String fError = error;
            main.post(() -> {
                if (!reg.equals(region)) {
                    return;
                }
                if (fList == null) {
                    progress.setVisibility(View.GONE);
                    LinearLayout col = Ui.newCard(this, results);
                    Ui.addText(this, col, getString(R.string.errore_ricerca, fError),
                            14, 0, false);
                    return;
                }
                SEASON_CACHE.put(reg, fList);
                applySeasons(fList);
            });
        }).start();
    }

    private void applySeasons(List<RaiderIo.Season> list) {
        seasons = list;
        selectedSeason = 0;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            RaiderIo.Season s = list.get(i);
            labels.add(i == 0 ? getString(R.string.stagione_in_corso, s.name) : s.name);
        }
        seasonInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, labels));
        seasonInput.setText(labels.get(0), false);
        seasonInput.setOnItemClickListener((p, v, pos, id) -> {
            selectedSeason = pos;
            loadCutoffs();
        });
        loadCutoffs();
    }

    private void loadCutoffs() {
        progress.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final String reg = region;
        final int idx = selectedSeason;
        final RaiderIo.Season season = seasons.get(idx);
        final boolean concluded = CutoffPredictor.isConcluded(season);
        // la previsione ha senso solo per la stagione in corso non ancora conclusa
        final boolean wantForecast = idx == 0 && !concluded;

        new Thread(() -> {
            JSONObject cutoffs = null;
            String error = null;
            try {
                cutoffs = RaiderIo.fetchSeasonCutoffs(reg, season.slug);
            } catch (Exception e) {
                error = e.getMessage();
            }
            MplusTitle.Forecast forecast = null;
            if (wantForecast) {
                try {
                    forecast = MplusTitle.fetch(reg, CutoffPredictor.knownEndIso(season.slug));
                } catch (Exception ignored) {
                    // il modello interno fa da riserva
                }
            }
            final JSONObject fCutoffs = cutoffs;
            final String fError = error;
            final MplusTitle.Forecast fForecast = forecast;
            main.post(() -> {
                if (!reg.equals(region) || idx != selectedSeason) {
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
                if (!hasValues(fCutoffs)) {
                    LinearLayout col = Ui.newCard(this, results);
                    Ui.addSectionTitle(this, col, season.name);
                    Ui.addText(this, col, getString(R.string.cutoff_non_disponibili),
                            14, 0, false);
                    return;
                }
                if (idx == 0 && !concluded) {
                    saveSnapshot(reg, season, fCutoffs);
                }
                showCutoffs(fCutoffs, "p999", getString(R.string.top_01), concluded);
                showCutoffs(fCutoffs, "p990", getString(R.string.top_1), concluded);
                showSeason(season, concluded);
                if (!concluded && idx == 0) {
                    showPrediction(reg, season, fCutoffs, fForecast);
                }
            });
        }).start();
    }

    private static boolean hasValues(JSONObject cutoffs) {
        for (String pct : new String[]{"p999", "p990"}) {
            JSONObject block = cutoffs.optJSONObject(pct);
            if (block == null) {
                continue;
            }
            for (String fac : new String[]{"horde", "alliance", "all"}) {
                JSONObject f = block.optJSONObject(fac);
                if (f != null && f.optDouble("quantileMinValue", 0) > 0) {
                    return true;
                }
            }
        }
        return false;
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

    /** Card con i cutoff (attuali se la stagione è in corso, definitivi se conclusa). */
    private void showCutoffs(JSONObject cutoffs, String pct, String title, boolean concluded) {
        JSONObject block = cutoffs.optJSONObject(pct);
        if (block == null) {
            return;
        }
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, title);
        Ui.addText(this, col, getString(concluded
                ? R.string.cutoff_definitivi : R.string.cutoff_attuali), 12, 0, false);
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

    private void showSeason(RaiderIo.Season season, boolean concluded) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.stagione) + ": " + season.name);
        long effEnd = CutoffPredictor.effectiveEnd(season);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);
        Ui.addRow(this, col, getString(R.string.periodo),
                fmt.format(new Date(season.startMs)) + " – " + fmt.format(new Date(effEnd)), 0);
        if (concluded) {
            Ui.addRow(this, col, getString(R.string.stato),
                    getString(R.string.stagione_conclusa), getColor(R.color.gold));
            return;
        }
        long now = System.currentTimeMillis();
        int week = (int) ((now - season.startMs) / (7L * 24 * 3600 * 1000)) + 1;
        int totalWeeks = (int) Math.round((effEnd - season.startMs) / (7.0 * 24 * 3600 * 1000));
        Ui.addRow(this, col, getString(R.string.avanzamento),
                getString(R.string.settimana_di, Math.min(week, totalWeeks), totalWeeks),
                getColor(R.color.gold));
    }

    /** Card finale con la previsione di fine stagione. */
    private void showPrediction(String reg, RaiderIo.Season season, JSONObject cutoffs,
                                MplusTitle.Forecast forecast) {
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
                double predicted;
                if (forecast != null) {
                    // fattore di crescita del sito applicato a ogni cutoff
                    predicted = current * forecast.factor();
                } else {
                    CutoffPredictor.Prediction pred = CutoffPredictor.predict(this, reg,
                            season.slug, pctTitle[0] + "_" + f[0], current,
                            season.startMs, season.endMs);
                    anyTrend |= pred.fromTrend;
                    predicted = pred.value;
                }
                Ui.addRow(this, col, f[1],
                        String.format(Locale.ITALY, "~%.0f", predicted), safeColor(f[2]));
            }
        }
        if (forecast != null) {
            Ui.addText(this, col, getString(R.string.metodo_sito), 12, 0, false);
        } else {
            Ui.addText(this, col, getString(anyTrend
                    ? R.string.metodo_trend : R.string.metodo_fase), 12, 0, false);
        }
        Ui.addText(this, col, getString(R.string.cutoff_note), 12, 0, false);
    }
}
