package com.marco.lanwow;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

/** Elenco dei personaggi preferiti. */
public class FavoritesActivity extends AppCompatActivity {

    private LinearLayout results;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        results = findViewById(R.id.results);
        emptyText = findViewById(R.id.empty_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        results.removeAllViews();
        List<Favorites.Entry> all = Favorites.list(this);
        emptyText.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);

        for (Favorites.Entry e : all) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = Ui.dp(this, 8);
            lp.setMargins(0, m, 0, m);
            card.setLayoutParams(lp);
            card.setRadius(Ui.dp(this, 16));
            card.setContentPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            results.addView(card);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(row, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Ui.addText(this, col, e.name, 18, Ui.classColor(e.cls), true);
            Ui.addText(this, col, prettify(e.realm) + " (" + e.region.toUpperCase() + ")"
                    + (e.cls.isEmpty() ? "" : " · " + e.cls), 13, 0, false);

            ImageButton remove = new ImageButton(this);
            remove.setImageResource(android.R.drawable.btn_star_big_on);
            remove.setBackground(null);
            remove.setContentDescription(getString(R.string.rimosso_preferiti));
            remove.setOnClickListener(v -> {
                Favorites.remove(this, e);
                refresh();
            });
            row.addView(remove);

            card.setOnClickListener(v ->
                    CharacterActivity.open(this, e.region, e.realm, e.name, e.cls));
        }
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
