package com.marco.lanwow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Client per l'API pubblica di raider.io. */
public final class RaiderIo {

    private static final String BASE = "https://raider.io/api/v1/characters/profile";
    private static final String FIELDS = "gear,guild,mythic_plus_scores_by_season:current,"
            + "raid_progression,mythic_plus_best_runs,mythic_plus_recent_runs,mythic_plus_ranks";

    private RaiderIo() {
    }

    /** Slug del realm: minuscolo, senza accenti né apostrofi, spazi come trattini. */
    public static String realmSlug(String realm) {
        String s = Normalizer.normalize(realm.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("['’]", "")
                .toLowerCase();
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Risultato della ricerca per nome. */
    public static class SearchResult {
        public String name;
        public String realmName;
        public String realmSlug;
        public String cls;
        public String faction;
    }

    /** Cerca personaggi per nome nella regione indicata (massimo 10 risultati). */
    public static List<SearchResult> search(String region, String term) throws Exception {
        String url = "https://raider.io/api/search?term=" + URLEncoder.encode(term.trim(), "UTF-8")
                + "&region=" + URLEncoder.encode(region, "UTF-8");
        JSONObject o = new JSONObject(Http.get(url));
        JSONArray matches = o.optJSONArray("matches");
        List<SearchResult> out = new ArrayList<>();
        if (matches == null) {
            return out;
        }
        for (int i = 0; i < matches.length() && out.size() < 10; i++) {
            JSONObject m = matches.optJSONObject(i);
            if (m == null || !"character".equals(m.optString("type"))) {
                continue;
            }
            JSONObject data = m.optJSONObject("data");
            if (data == null) {
                continue;
            }
            JSONObject reg = data.optJSONObject("region");
            if (reg == null || !region.equalsIgnoreCase(reg.optString("slug"))) {
                continue;
            }
            SearchResult r = new SearchResult();
            r.name = data.optString("name");
            JSONObject realm = data.optJSONObject("realm");
            r.realmName = realm != null ? realm.optString("name") : "";
            r.realmSlug = realm != null ? realm.optString("slug") : "";
            JSONObject cls = data.optJSONObject("class");
            r.cls = cls != null ? cls.optString("name") : "";
            r.faction = data.optString("faction");
            out.add(r);
        }
        return out;
    }

    /** Punteggio M+ estratto da un profilo: score e colore raider.io. */
    public static class Score {
        public double value;
        public String color = "#ffffff";
    }

    /** Estrae il punteggio M+ della stagione corrente da un profilo. */
    public static Score parseSeasonScore(JSONObject profile) {
        Score s = new Score();
        JSONArray seasons = profile.optJSONArray("mythic_plus_scores_by_season");
        if (seasons != null && seasons.length() > 0) {
            JSONObject season = seasons.optJSONObject(0);
            JSONObject all = season.optJSONObject("segments") != null
                    ? season.optJSONObject("segments").optJSONObject("all") : null;
            if (all != null) {
                s.value = all.optDouble("score", 0);
                s.color = all.optString("color", "#ffffff");
            } else if (season.optJSONObject("scores") != null) {
                s.value = season.optJSONObject("scores").optDouble("all", 0);
            }
        }
        return s;
    }

    /** Solo il punteggio M+ corrente (richiesta leggera, usata nei risultati di ricerca). */
    public static Score fetchScore(String region, String realmSlug, String name)
            throws Exception {
        String url = BASE + "?region=" + URLEncoder.encode(region, "UTF-8")
                + "&realm=" + URLEncoder.encode(realmSlug, "UTF-8")
                + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")
                + "&fields=" + URLEncoder.encode("mythic_plus_scores_by_season:current", "UTF-8");
        return parseSeasonScore(new JSONObject(Http.get(url)));
    }

    /** Stagione M+ corrente con date di inizio/fine per la regione. */
    public static class Season {
        public String slug;
        public String name;
        public long startMs;
        public long endMs;
    }

    /**
     * Trova la stagione M+ principale in corso cercando nelle espansioni recenti.
     * (11 = Midnight; il ciclo copre anche le espansioni future.)
     */
    public static Season fetchCurrentSeason(String region) throws Exception {
        long now = System.currentTimeMillis();
        for (int expId = 11; expId <= 15; expId++) {
            JSONArray seasons;
            try {
                JSONObject o = new JSONObject(Http.get(
                        "https://raider.io/api/v1/mythic-plus/static-data?expansion_id=" + expId));
                seasons = o.optJSONArray("seasons");
            } catch (Exception e) {
                continue;
            }
            if (seasons == null) {
                continue;
            }
            for (int i = 0; i < seasons.length(); i++) {
                JSONObject s = seasons.optJSONObject(i);
                if (s == null || !s.optBoolean("is_main_season")) {
                    continue;
                }
                JSONObject starts = s.optJSONObject("starts");
                JSONObject ends = s.optJSONObject("ends");
                if (starts == null || ends == null) {
                    continue;
                }
                String st = starts.optString(region, "");
                String en = ends.optString(region, "");
                if (st.isEmpty() || en.isEmpty()) {
                    continue;
                }
                try {
                    long stm = java.time.Instant.parse(st).toEpochMilli();
                    long enm = java.time.Instant.parse(en).toEpochMilli();
                    if (now >= stm && now < enm) {
                        Season season = new Season();
                        season.slug = s.optString("slug");
                        season.name = s.optString("name");
                        season.startMs = stm;
                        season.endMs = enm;
                        return season;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        throw new Exception("stagione M+ corrente non trovata");
    }

    /** Cutoff dei percentili (p999 = top 0,1%, p990 = top 1%…) per la stagione. */
    public static JSONObject fetchSeasonCutoffs(String region, String seasonSlug)
            throws Exception {
        String url = "https://raider.io/api/v1/mythic-plus/season-cutoffs?region="
                + URLEncoder.encode(region, "UTF-8")
                + "&season=" + URLEncoder.encode(seasonSlug, "UTF-8");
        JSONObject o = new JSONObject(Http.get(url));
        JSONObject cutoffs = o.optJSONObject("cutoffs");
        if (cutoffs == null) {
            throw new Exception("cutoff non disponibili");
        }
        return cutoffs;
    }

    public static JSONObject fetchProfile(String region, String realm, String name)
            throws Exception {
        String url = BASE + "?region=" + URLEncoder.encode(region, "UTF-8")
                + "&realm=" + URLEncoder.encode(realmSlug(realm), "UTF-8")
                + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")
                + "&fields=" + URLEncoder.encode(FIELDS, "UTF-8");
        try {
            return new JSONObject(Http.get(url));
        } catch (Http.HttpException e) {
            String message = null;
            try {
                message = new JSONObject(e.body).optString("message", null);
            } catch (Exception ignored) {
            }
            throw new Exception(message != null ? message : e.getMessage());
        }
    }
}
