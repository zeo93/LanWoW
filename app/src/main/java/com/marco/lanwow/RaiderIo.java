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

    /** Stagione M+ con date di inizio/fine per la regione. */
    public static class Season {
        public String slug;
        public String name;
        public long startMs;
        public long endMs;
        /** Data in cui i cutoff dei titoli si congelano (0 = non nota). */
        public long cutoffEndMs;
        public int blizzardId;

        /** true se la stagione è già iniziata. */
        boolean started() {
            return System.currentTimeMillis() >= startMs;
        }
    }

    /**
     * Stagioni M+ principali già iniziate, dalla più recente alla più vecchia:
     * la prima è quella in corso, le altre formano l'archivio.
     * (11 = Midnight; il ciclo copre anche espansioni future e passate.)
     */
    public static List<Season> fetchSeasons(String region) throws Exception {
        // una stagione può comparire più volte (es. "• Post"): tengo lo slug più corto
        java.util.Map<Integer, Season> byId = new java.util.HashMap<>();
        // le varianti "-cutoffs" indicano quando i cutoff sono stati congelati
        java.util.Map<Integer, Long> cutoffEnds = new java.util.HashMap<>();
        for (int expId = 13; expId >= 9; expId--) {
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
                if (s == null) {
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
                Season season = new Season();
                try {
                    season.startMs = java.time.Instant.parse(st).toEpochMilli();
                    season.endMs = java.time.Instant.parse(en).toEpochMilli();
                } catch (Exception e) {
                    continue;
                }
                season.slug = s.optString("slug");
                if (season.slug.endsWith("-cutoffs")) {
                    cutoffEnds.put(s.optInt("blizzard_season_id", -1), season.endMs);
                    continue;
                }
                if (!s.optBoolean("is_main_season")) {
                    continue;
                }
                season.name = cleanSeasonName(s.optString("name"));
                season.blizzardId = s.optInt("blizzard_season_id", -1);
                Season prev = byId.get(season.blizzardId);
                if (prev == null || season.slug.length() < prev.slug.length()) {
                    byId.put(season.blizzardId, season);
                }
            }
        }
        List<Season> out = new ArrayList<>();
        for (Season s : byId.values()) {
            if (s.started()) {
                Long cutoffEnd = cutoffEnds.get(s.blizzardId);
                s.cutoffEndMs = cutoffEnd != null ? cutoffEnd : 0;
                out.add(s);
            }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.startMs, a.startMs));
        if (out.isEmpty()) {
            throw new Exception("elenco stagioni non disponibile");
        }
        return out;
    }

    /** "MN Season 1 • Full" → "MN Season 1". */
    private static String cleanSeasonName(String name) {
        int i = name.indexOf('•');
        return (i > 0 ? name.substring(0, i) : name).trim();
    }

    /** Stagione attualmente in corso (la più recente già iniziata). */
    public static Season fetchCurrentSeason(String region) throws Exception {
        return fetchSeasons(region).get(0);
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
