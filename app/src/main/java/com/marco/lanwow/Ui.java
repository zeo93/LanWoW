package com.marco.lanwow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

/** Helper condivisi per costruire le card dei risultati a runtime. */
public final class Ui {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Ui() {
    }

    public static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Card aggiunta in fondo a parent, restituisce la colonna interna. */
    public static LinearLayout newCard(Activity a, LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = dp(a, 8);
        lp.setMargins(0, m, 0, m);
        card.setLayoutParams(lp);
        card.setRadius(dp(a, 16));
        card.setCardElevation(dp(a, 2));
        card.setContentPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16));
        parent.addView(card);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    public static TextView addText(Activity a, LinearLayout parent, String text,
                                   float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        if (color != 0) {
            tv.setTextColor(color);
        }
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setPadding(0, dp(a, 2), 0, dp(a, 2));
        parent.addView(tv);
        return tv;
    }

    public static void addSectionTitle(Activity a, LinearLayout parent, String title) {
        addText(a, parent, title, 18, a.getColor(R.color.gold), true);
    }

    /** Riga "etichetta ..... valore" con valore eventualmente colorato. */
    public static void addRow(Activity a, LinearLayout parent, String label, String value,
                              int valueColor) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(a, 3), 0, dp(a, 3));
        TextView l = new TextView(a);
        l.setText(label);
        l.setTextSize(15);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(15);
        v.setTypeface(null, Typeface.BOLD);
        if (valueColor != 0) {
            v.setTextColor(valueColor);
        }
        row.addView(l);
        row.addView(v);
        parent.addView(row);
    }

    public static void loadImage(Activity a, String url, ImageView target) {
        if (url == null || url.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                byte[] data = Http.getBytes(url);
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                MAIN.post(() -> target.setImageBitmap(bmp));
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** Colori dei parse in stile WarcraftLogs. */
    public static int parseColor(double pct) {
        if (pct >= 100) return Color.parseColor("#E5CC80");
        if (pct >= 99) return Color.parseColor("#E268A8");
        if (pct >= 95) return Color.parseColor("#FF8000");
        if (pct >= 75) return Color.parseColor("#A335EE");
        if (pct >= 50) return Color.parseColor("#0070DD");
        if (pct >= 25) return Color.parseColor("#1EFF00");
        return Color.parseColor("#9D9D9D");
    }

    /** Colori ufficiali delle classi di WoW (nomi inglesi restituiti da raider.io). */
    public static int classColor(String cls) {
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
}
