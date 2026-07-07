package com.marco.lanwow;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;

/** Client per l'API pubblica di raider.io. */
public final class RaiderIo {

    private static final String BASE = "https://raider.io/api/v1/characters/profile";
    private static final String FIELDS = "gear,guild,mythic_plus_scores_by_season:current,"
            + "raid_progression,mythic_plus_best_runs,mythic_plus_recent_runs";

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
