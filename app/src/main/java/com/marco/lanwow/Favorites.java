package com.marco.lanwow;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Personaggi preferiti, salvati come JSON nelle SharedPreferences. */
public final class Favorites {

    /** Un preferito: regione, realm (slug), nome e classe (per il colore). */
    public static class Entry {
        public String region;
        public String realm;
        public String name;
        public String cls;

        public Entry(String region, String realm, String name, String cls) {
            this.region = region;
            this.realm = realm;
            this.name = name;
            this.cls = cls;
        }

        String key() {
            return (region + "|" + realm + "|" + name).toLowerCase();
        }
    }

    private Favorites() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("favorites", Context.MODE_PRIVATE);
    }

    public static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optString("region"), o.optString("realm"),
                        o.optString("name"), o.optString("cls")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isFavorite(Context c, Entry e) {
        for (Entry f : list(c)) {
            if (f.key().equals(e.key())) {
                return true;
            }
        }
        return false;
    }

    /** Aggiunge o rimuove; restituisce true se ora è nei preferiti. */
    public static boolean toggle(Context c, Entry e) {
        List<Entry> all = list(c);
        boolean removed = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).key().equals(e.key())) {
                all.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            all.add(e);
        }
        save(c, all);
        return !removed;
    }

    public static void remove(Context c, Entry e) {
        List<Entry> all = list(c);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).key().equals(e.key())) {
                all.remove(i);
                break;
            }
        }
        save(c, all);
    }

    private static void save(Context c, List<Entry> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : all) {
                arr.put(new JSONObject()
                        .put("region", e.region)
                        .put("realm", e.realm)
                        .put("name", e.name)
                        .put("cls", e.cls));
            }
            prefs(c).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
