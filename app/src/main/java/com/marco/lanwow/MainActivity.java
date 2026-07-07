package com.marco.lanwow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "search";
    private static final String[] REGIONS = {"eu", "us", "kr", "tw"};

    private final Handler main = new Handler(Looper.getMainLooper());

    private AutoCompleteTextView regionInput;
    private TextInputEditText realmInput;
    private TextInputEditText nameInput;
    private ProgressBar progress;
    private TextView errorText;
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        regionInput = findViewById(R.id.input_region);
        realmInput = findViewById(R.id.input_realm);
        nameInput = findViewById(R.id.input_name);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.error_text);
        results = findViewById(R.id.results);

        regionInput.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, REGIONS));

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        regionInput.setText(p.getString("region", "eu"), false);
        realmInput.setText(p.getString("realm", ""));
        nameInput.setText(p.getString("name", ""));

        MaterialButton search = findViewById(R.id.btn_search);
        search.setOnClickListener(v -> doSearch());

        UpdateChecker.checkOnStartup(this);

        // Se c'era già una ricerca salvata, ricaricala subito
        if (!p.getString("name", "").isEmpty() && !p.getString("realm", "").isEmpty()) {
            doSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doSearch() {
        String region = regionInput.getText().toString().trim().toLowerCase();
        String realm = realmInput.getText() != null ? realmInput.getText().toString().trim() : "";
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        if (region.isEmpty() || realm.isEmpty() || name.isEmpty()) {
            errorText.setText(R.string.compila_tutti_i_campi);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("region", region)
                .putString("realm", realm)
                .putString("name", name)
                .apply();

        errorText.setVisibility(View.GONE);
        results.removeAllViews();
        progress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            JSONObject profile = null;
            String profileError = null;
            try {
                profile = RaiderIo.fetchProfile(region, realm, name);
            } catch (Exception e) {
                profileError = e.getMessage();
            }

            JSONObject rankings = null;
            String wclError = null;
            boolean wclConfigured = WarcraftLogs.hasCredentials(this);
            if (wclConfigured && profile != null) {
                try {
                    rankings = WarcraftLogs.fetchZoneRankings(
                            this, region, RaiderIo.realmSlug(realm), name);
                } catch (Exception e) {
                    wclError = e.getMessage();
                }
            }

            final JSONObject fProfile = profile;
            final String fProfileError = profileError;
            final JSONObject fRankings = rankings;
            final String fWclError = wclError;
            final boolean fWclConfigured = wclConfigured;
            main.post(() -> {
                progress.setVisibility(View.GONE);
                if (fProfile == null) {
                    errorText.setText(getString(R.string.errore_ricerca, fProfileError));
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                showProfile(fProfile);
                showMythicPlus(fProfile);
                showRaids(fProfile);
                showLogs(fRankings, fWclError, fWclConfigured);
            });
        }).start();
    }

    // ------------------------------------------------------------------ UI

    private MaterialCardView newCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = dp(8);
        lp.setMargins(0, m, 0, m);
        card.setLayoutParams(lp);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setContentPadding(dp(16), dp(16), dp(16), dp(16));
        results.addView(card);
        return card;
    }

    private LinearLayout newCardColumn(MaterialCardView card) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private TextView addText(LinearLayout parent, String text, float sizeSp, int color,
                             boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        if (color != 0) {
            tv.setTextColor(color);
        }
        if (bold) {
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        tv.setPadding(0, dp(2), 0, dp(2));
        parent.addView(tv);
        return tv;
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        addText(parent, title, 18, getColor(R.color.gold), true);
    }

    /** Riga "etichetta ..... valore" con valore eventualmente colorato. */
    private void addRow(LinearLayout parent, String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(15);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        if (valueColor != 0) {
            v.setTextColor(valueColor);
        }
        row.addView(l);
        row.addView(v);
        parent.addView(row);
    }

    private void showProfile(JSONObject o) {
        MaterialCardView card = newCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView avatar = new ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(72), dp(72));
        alp.setMargins(0, 0, dp(16), 0);
        avatar.setLayoutParams(alp);
        row.addView(avatar);
        loadImage(o.optString("thumbnail_url"), avatar);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String cls = o.optString("class");
        addText(col, o.optString("name"), 22, classColor(cls), true);

        JSONObject guild = o.optJSONObject("guild");
        if (guild != null) {
            addText(col, "<" + guild.optString("name") + ">", 14,
                    getColor(R.color.gold), false);
        }
        addText(col, o.optString("race") + " " + cls
                + " — " + o.optString("active_spec_name"), 14, 0, false);
        addText(col, o.optString("realm") + " (" + o.optString("region").toUpperCase() + ")"
                + " · " + factionLabel(o.optString("faction")), 14, 0, false);

        JSONObject gear = o.optJSONObject("gear");
        if (gear != null) {
            addText(col, getString(R.string.item_level,
                    gear.optDouble("item_level_equipped", 0)), 14, 0, false);
        }
    }

    private void showMythicPlus(JSONObject o) {
        MaterialCardView card = newCard();
        LinearLayout col = newCardColumn(card);
        addSectionTitle(col, getString(R.string.mythic_plus));

        double score = 0;
        int scoreColor = 0;
        JSONArray seasons = o.optJSONArray("mythic_plus_scores_by_season");
        if (seasons != null && seasons.length() > 0) {
            JSONObject all = seasons.optJSONObject(0).optJSONObject("segments") != null
                    ? seasons.optJSONObject(0).optJSONObject("segments").optJSONObject("all")
                    : null;
            if (all != null) {
                score = all.optDouble("score", 0);
                try {
                    scoreColor = Color.parseColor(all.optString("color", "#ffffff"));
                } catch (Exception ignored) {
                }
            } else {
                JSONObject scores = seasons.optJSONObject(0).optJSONObject("scores");
                if (scores != null) {
                    score = scores.optDouble("all", 0);
                }
            }
        }
        addRow(col, getString(R.string.punteggio_stagione),
                String.format(Locale.ITALY, "%.0f", score), scoreColor);

        JSONArray best = o.optJSONArray("mythic_plus_best_runs");
        if (best != null && best.length() > 0) {
            addText(col, getString(R.string.migliori_run), 15, getColor(R.color.gold), true);
            for (int i = 0; i < best.length() && i < 8; i++) {
                JSONObject run = best.optJSONObject(i);
                String stars = "";
                for (int k = 0; k < run.optInt("num_keystone_upgrades"); k++) {
                    stars += "+";
                }
                addRow(col, run.optString("dungeon"),
                        stars + run.optInt("mythic_level")
                                + "  (" + String.format(Locale.ITALY, "%.0f",
                                run.optDouble("score", 0)) + ")", 0);
            }
        } else if (score == 0) {
            addText(col, getString(R.string.nessuna_run), 14, 0, false);
        }
    }

    private void showRaids(JSONObject o) {
        JSONObject raids = o.optJSONObject("raid_progression");
        if (raids == null || raids.length() == 0) {
            return;
        }
        MaterialCardView card = newCard();
        LinearLayout col = newCardColumn(card);
        addSectionTitle(col, getString(R.string.progressione_raid));
        Iterator<String> keys = raids.keys();
        while (keys.hasNext()) {
            String slug = keys.next();
            JSONObject raid = raids.optJSONObject(slug);
            if (raid != null) {
                String summary = raid.optString("summary").trim();
                addRow(col, prettify(slug), summary.isEmpty() ? "—" : summary, 0);
            }
        }
    }

    private void showLogs(JSONObject rankings, String error, boolean configured) {
        MaterialCardView card = newCard();
        LinearLayout col = newCardColumn(card);
        addSectionTitle(col, getString(R.string.logs_warcraftlogs));

        if (!configured) {
            addText(col, getString(R.string.wcl_non_configurato), 14, 0, false);
            MaterialButton btn = new MaterialButton(this);
            btn.setText(R.string.apri_impostazioni);
            btn.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
            col.addView(btn);
            return;
        }
        if (rankings == null) {
            addText(col, getString(R.string.errore_ricerca, error), 14, 0, false);
            return;
        }

        double bestAvg = rankings.optDouble("bestPerformanceAverage", Double.NaN);
        double medianAvg = rankings.optDouble("medianPerformanceAverage", Double.NaN);
        if (!Double.isNaN(bestAvg)) {
            addRow(col, getString(R.string.media_best),
                    String.format(Locale.ITALY, "%.1f", bestAvg), parseColor(bestAvg));
        }
        if (!Double.isNaN(medianAvg)) {
            addRow(col, getString(R.string.media_mediana),
                    String.format(Locale.ITALY, "%.1f", medianAvg), parseColor(medianAvg));
        }

        JSONArray list = rankings.optJSONArray("rankings");
        if (list == null || list.length() == 0) {
            addText(col, getString(R.string.wcl_nessun_log), 14, 0, false);
            return;
        }
        addText(col, getString(R.string.parse_per_boss), 15, getColor(R.color.gold), true);
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            JSONObject enc = r.optJSONObject("encounter");
            String boss = enc != null ? enc.optString("name") : "?";
            int kills = r.optInt("totalKills", 0);
            if (kills == 0 && r.isNull("rankPercent")) {
                addRow(col, boss, "—", 0);
                continue;
            }
            double pct = r.optDouble("rankPercent", 0);
            addRow(col, boss + "  (" + kills + " kill)",
                    String.format(Locale.ITALY, "%.0f%%", Math.floor(pct)), parseColor(pct));
        }
    }

    // -------------------------------------------------------------- helpers

    private void loadImage(String url, ImageView target) {
        if (url == null || url.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                byte[] data = Http.getBytes(url);
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                main.post(() -> target.setImageBitmap(bmp));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private String factionLabel(String faction) {
        if ("alliance".equalsIgnoreCase(faction)) {
            return getString(R.string.alleanza);
        }
        if ("horde".equalsIgnoreCase(faction)) {
            return getString(R.string.orda);
        }
        return faction;
    }

    private static String prettify(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String w : slug.split("-")) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Colori dei parse in stile WarcraftLogs. */
    private static int parseColor(double pct) {
        if (pct >= 100) return Color.parseColor("#E5CC80");
        if (pct >= 99) return Color.parseColor("#E268A8");
        if (pct >= 95) return Color.parseColor("#FF8000");
        if (pct >= 75) return Color.parseColor("#A335EE");
        if (pct >= 50) return Color.parseColor("#0070DD");
        if (pct >= 25) return Color.parseColor("#1EFF00");
        return Color.parseColor("#9D9D9D");
    }

    /** Colori ufficiali delle classi di WoW (nomi inglesi restituiti da raider.io). */
    private static int classColor(String cls) {
        switch (cls == null ? "" : cls.toLowerCase()) {
            case "warrior": return Color.parseColor("#C69B6D");
            case "paladin": return Color.parseColor("#F48CBA");
            case "hunter": return Color.parseColor("#AAD372");
            case "rogue": return Color.parseColor("#FFF468");
            case "priest": return Color.parseColor("#FFFFFF");
            case "death knight": return Color.parseColor("#C41E3A");
            case "shaman": return Color.parseColor("#0070DD");
            case "mage": return Color.parseColor("#3FC7EB");
            case "warlock": return Color.parseColor("#8788EE");
            case "monk": return Color.parseColor("#00FF98");
            case "druid": return Color.parseColor("#FF7C0A");
            case "demon hunter": return Color.parseColor("#A330C9");
            case "evoker": return Color.parseColor("#33937F");
            default: return Color.parseColor("#FFFFFF");
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
