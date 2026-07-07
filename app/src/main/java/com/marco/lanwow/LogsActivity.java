package com.marco.lanwow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Log WarcraftLogs del personaggio: raid corrente e Mythic+ della stagione. */
public class LogsActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());

    private String region;
    private String realmSlug;
    private String name;

    private ProgressBar progress;
    private LinearLayout results;

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

        if (!WarcraftLogs.hasCredentials(this)) {
            LinearLayout col = Ui.newCard(this, results);
            Ui.addSectionTitle(this, col, getString(R.string.logs_warcraftlogs));
            Ui.addText(this, col, getString(R.string.wcl_non_configurato), 14, 0, false);
            return;
        }
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            JSONObject raid = null;
            String raidError = null;
            try {
                raid = WarcraftLogs.fetchRaidRankings(this, region, realmSlug, name);
            } catch (Exception e) {
                raidError = e.getMessage();
            }
            JSONObject mplus = null;
            String mplusError = null;
            try {
                mplus = WarcraftLogs.fetchMythicPlusRankings(this, region, realmSlug, name);
            } catch (Exception e) {
                mplusError = e.getMessage();
            }
            final JSONObject fRaid = raid;
            final String fRaidError = raidError;
            final JSONObject fMplus = mplus;
            final String fMplusError = mplusError;
            main.post(() -> {
                progress.setVisibility(View.GONE);
                showSection(getString(R.string.sezione_raid), fRaid, fRaidError);
                showSection(getString(R.string.sezione_mplus), fMplus, fMplusError);
            });
        }).start();
    }

    private void showSection(String title, JSONObject rankings, String error) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, title);

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
