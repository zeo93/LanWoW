package com.marco.lanwow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/** Scheda completa del personaggio (dati raider.io) con stella preferiti e pulsante log. */
public class CharacterActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());

    private String region;
    private String realm;
    private String name;
    private String cls;

    private ProgressBar progress;
    private TextView errorText;
    private LinearLayout results;
    private MaterialButton logsButton;
    private MenuItem starItem;

    public static void open(Activity from, String region, String realm, String name, String cls) {
        Intent i = new Intent(from, CharacterActivity.class)
                .putExtra("region", region)
                .putExtra("realm", realm)
                .putExtra("name", name)
                .putExtra("cls", cls);
        from.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_character);

        region = getIntent().getStringExtra("region");
        realm = getIntent().getStringExtra("realm");
        name = getIntent().getStringExtra("name");
        cls = getIntent().getStringExtra("cls");
        if (cls == null) {
            cls = "";
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(name);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.error_text);
        results = findViewById(R.id.results);
        logsButton = findViewById(R.id.btn_logs);
        logsButton.setOnClickListener(v ->
                LogsActivity.open(this, region, RaiderIo.realmSlug(realm), name));

        load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_character, menu);
        starItem = menu.findItem(R.id.action_star);
        refreshStar();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_star) {
            boolean added = Favorites.toggle(this, entry());
            refreshStar();
            Toast.makeText(this, added
                    ? R.string.aggiunto_preferiti : R.string.rimosso_preferiti,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Favorites.Entry entry() {
        return new Favorites.Entry(region, RaiderIo.realmSlug(realm), name, cls);
    }

    private void refreshStar() {
        if (starItem != null) {
            starItem.setIcon(Favorites.isFavorite(this, entry())
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
        }
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        results.removeAllViews();
        logsButton.setVisibility(View.GONE);

        new Thread(() -> {
            JSONObject profile = null;
            String error = null;
            try {
                profile = RaiderIo.fetchProfile(region, realm, name);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final JSONObject fProfile = profile;
            final String fError = error;
            main.post(() -> {
                progress.setVisibility(View.GONE);
                if (fProfile == null) {
                    errorText.setText(getString(R.string.errore_ricerca, fError));
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                // nome e classe "veri" restituiti dall'API (per preferiti e titolo)
                name = fProfile.optString("name", name);
                cls = fProfile.optString("class", cls);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(name);
                }
                refreshStar();
                showProfile(fProfile);
                showMythicPlus(fProfile);
                showRanks(fProfile);
                showRaids(fProfile);
                logsButton.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void showProfile(JSONObject o) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(this, 8);
        lp.setMargins(0, m, 0, m);
        card.setLayoutParams(lp);
        card.setRadius(Ui.dp(this, 16));
        card.setContentPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        results.addView(card);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView avatar = new ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72));
        alp.setMargins(0, 0, Ui.dp(this, 16), 0);
        avatar.setLayoutParams(alp);
        row.addView(avatar);
        Ui.loadImage(this, o.optString("thumbnail_url"), avatar);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Ui.addText(this, col, o.optString("name"), 22, Ui.classColor(o.optString("class")), true);
        JSONObject guild = o.optJSONObject("guild");
        if (guild != null) {
            Ui.addText(this, col, "<" + guild.optString("name") + ">", 14,
                    getColor(R.color.gold), false);
        }
        Ui.addText(this, col, o.optString("race") + " " + o.optString("class")
                + " — " + o.optString("active_spec_name"), 14, 0, false);
        Ui.addText(this, col, o.optString("realm") + " (" + o.optString("region").toUpperCase()
                + ") · " + factionLabel(o.optString("faction")), 14, 0, false);
        JSONObject gear = o.optJSONObject("gear");
        if (gear != null) {
            Ui.addText(this, col, getString(R.string.item_level,
                    gear.optDouble("item_level_equipped", 0)), 14, 0, false);
        }
    }

    private void showMythicPlus(JSONObject o) {
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.mythic_plus));

        RaiderIo.Score s = RaiderIo.parseSeasonScore(o);
        double score = s.value;
        int scoreColor = 0;
        try {
            scoreColor = android.graphics.Color.parseColor(s.color);
        } catch (Exception ignored) {
        }
        Ui.addRow(this, col, getString(R.string.punteggio_stagione),
                String.format(Locale.ITALY, "%.0f", score), scoreColor);

        JSONArray best = o.optJSONArray("mythic_plus_best_runs");
        if (best != null && best.length() > 0) {
            Ui.addText(this, col, getString(R.string.migliori_run), 15,
                    getColor(R.color.gold), true);
            for (int i = 0; i < best.length() && i < 8; i++) {
                JSONObject run = best.optJSONObject(i);
                StringBuilder stars = new StringBuilder();
                for (int k = 0; k < run.optInt("num_keystone_upgrades"); k++) {
                    stars.append('+');
                }
                Ui.addRow(this, col, run.optString("dungeon"),
                        stars + String.valueOf(run.optInt("mythic_level"))
                                + "  (" + String.format(Locale.ITALY, "%.0f",
                                run.optDouble("score", 0)) + ")", 0);
            }
        } else if (score == 0) {
            Ui.addText(this, col, getString(R.string.nessuna_run), 14, 0, false);
        }
    }

    /** Classifica del punteggio M+ (mondo/regione/reame) per classe e ruolo. */
    private void showRanks(JSONObject o) {
        JSONObject ranks = o.optJSONObject("mythic_plus_ranks");
        if (ranks == null) {
            return;
        }
        String cls = o.optString("class");
        String[][] rows = {
                {"class", getString(R.string.classifica_tutti, classPlural(cls))},
                {"class_tank", classSingular(cls) + " " + getString(R.string.ruolo_difensori)},
                {"class_healer", classSingular(cls) + " " + getString(R.string.ruolo_guaritori)},
                {"class_dps", classSingular(cls) + " DPS"},
        };

        LinearLayout col = null;
        for (String[] r : rows) {
            JSONObject rank = ranks.optJSONObject(r[0]);
            if (rank == null || rank.optInt("world", 0) <= 0) {
                continue;
            }
            if (col == null) {
                col = Ui.newCard(this, results);
                Ui.addSectionTitle(this, col, getString(R.string.classifica));
                addRankRow(col, "", getString(R.string.colonna_mondo),
                        getString(R.string.colonna_regione), getString(R.string.colonna_reame),
                        true, 0, 0, 0);
            }
            addRankRow(col, r[1],
                    String.format(Locale.ITALY, "%,d", rank.optInt("world")),
                    String.format(Locale.ITALY, "%,d", rank.optInt("region")),
                    String.format(Locale.ITALY, "%,d", rank.optInt("realm")),
                    false,
                    rankColor(rank.optInt("world")),
                    rankColor(rank.optInt("region")),
                    rankColor(rank.optInt("realm")));
        }
    }

    private void addRankRow(LinearLayout parent, String label, String v1, String v2, String v3,
                            boolean header, int c1, int c2, int c3) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

        android.widget.TextView l = new android.widget.TextView(this);
        l.setText(label);
        l.setTextSize(header ? 12 : 14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        String[] values = {v1, v2, v3};
        int[] colors = {c1, c2, c3};
        for (int i = 0; i < 3; i++) {
            android.widget.TextView v = new android.widget.TextView(this);
            v.setText(values[i]);
            v.setTextSize(header ? 12 : 14);
            v.setMinWidth(Ui.dp(this, i == 0 ? 88 : 68));
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

    /** Colore del rank per fascia, come su raider.io. */
    private static int rankColor(int rank) {
        if (rank <= 0) return 0;
        if (rank <= 10) return 0xFFE5CC80;
        if (rank <= 100) return 0xFFA335EE;
        if (rank <= 5000) return 0xFF0070DD;
        return 0xFF1EFF00;
    }

    private static String classSingular(String cls) {
        switch (cls == null ? "" : cls.toLowerCase()) {
            case "warrior": return "Guerriero";
            case "paladin": return "Paladino";
            case "hunter": return "Cacciatore";
            case "rogue": return "Ladro";
            case "priest": return "Sacerdote";
            case "death knight": return "Cavaliere della Morte";
            case "shaman": return "Sciamano";
            case "mage": return "Mago";
            case "warlock": return "Stregone";
            case "monk": return "Monaco";
            case "druid": return "Druido";
            case "demon hunter": return "Cacciatore di Demoni";
            case "evoker": return "Evocatore";
            default: return cls;
        }
    }

    private static String classPlural(String cls) {
        switch (cls == null ? "" : cls.toLowerCase()) {
            case "warrior": return "Guerrieri";
            case "paladin": return "Paladini";
            case "hunter": return "Cacciatori";
            case "rogue": return "Ladri";
            case "priest": return "Sacerdoti";
            case "death knight": return "Cavalieri della Morte";
            case "shaman": return "Sciamani";
            case "mage": return "Maghi";
            case "warlock": return "Stregoni";
            case "monk": return "Monaci";
            case "druid": return "Druidi";
            case "demon hunter": return "Cacciatori di Demoni";
            case "evoker": return "Evocatori";
            default: return cls;
        }
    }

    private void showRaids(JSONObject o) {
        JSONObject raids = o.optJSONObject("raid_progression");
        if (raids == null || raids.length() == 0) {
            return;
        }
        LinearLayout col = Ui.newCard(this, results);
        Ui.addSectionTitle(this, col, getString(R.string.progressione_raid));
        Iterator<String> keys = raids.keys();
        while (keys.hasNext()) {
            String slug = keys.next();
            JSONObject raid = raids.optJSONObject(slug);
            if (raid != null) {
                String summary = raid.optString("summary").trim();
                Ui.addRow(this, col, prettify(slug), summary.isEmpty() ? "—" : summary, 0);
            }
        }
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
}
