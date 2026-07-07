package com.marco.lanwow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Client per l'API v2 (GraphQL) di warcraftlogs.com.
 * Richiede un client API gratuito creato su https://www.warcraftlogs.com/api/clients
 * le cui credenziali vengono salvate nelle impostazioni dell'app.
 */
public final class WarcraftLogs {

    private static final String TOKEN_URL = "https://www.warcraftlogs.com/oauth/token";
    private static final String API_URL = "https://www.warcraftlogs.com/api/v2/client";
    private static final String PREFS = "wcl";

    private WarcraftLogs() {
    }

    public static boolean hasCredentials(Context c) {
        return !clientId(c).isEmpty() && !clientSecret(c).isEmpty();
    }

    /** Credenziali dalle impostazioni; se vuote usa quelle integrate nell'app. */
    public static String clientId(Context c) {
        String v = prefs(c).getString("client_id", "").trim();
        return v.isEmpty() ? BuildConfig.WCL_CLIENT_ID : v;
    }

    public static String clientSecret(Context c) {
        String v = prefs(c).getString("client_secret", "").trim();
        return v.isEmpty() ? BuildConfig.WCL_CLIENT_SECRET : v;
    }

    /** Solo l'override salvato dall'utente (vuoto se si usano le credenziali integrate). */
    public static String savedClientId(Context c) {
        return prefs(c).getString("client_id", "").trim();
    }

    public static String savedClientSecret(Context c) {
        return prefs(c).getString("client_secret", "").trim();
    }

    public static void saveCredentials(Context c, String id, String secret) {
        prefs(c).edit()
                .putString("client_id", id.trim())
                .putString("client_secret", secret.trim())
                .remove("token")
                .remove("token_expiry")
                .apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Token OAuth client-credentials, riusato finché non scade. */
    private static String token(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String cached = p.getString("token", null);
        long expiry = p.getLong("token_expiry", 0);
        if (cached != null && System.currentTimeMillis() < expiry - 60000) {
            return cached;
        }
        String basic = Base64.encodeToString(
                (clientId(c) + ":" + clientSecret(c)).getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + basic);
        String resp;
        try {
            resp = Http.post(TOKEN_URL, "grant_type=client_credentials",
                    "application/x-www-form-urlencoded", headers);
        } catch (Http.HttpException e) {
            throw new Exception(e.code == 401
                    ? c.getString(R.string.wcl_credenziali_errate) : e.getMessage());
        }
        JSONObject o = new JSONObject(resp);
        String token = o.getString("access_token");
        long expiresIn = o.optLong("expires_in", 3600);
        p.edit()
                .putString("token", token)
                .putLong("token_expiry", System.currentTimeMillis() + expiresIn * 1000)
                .apply();
        return token;
    }

    /** Esegue una query GraphQL e restituisce l'oggetto "data". */
    private static JSONObject graphql(Context c, String query, JSONObject variables)
            throws Exception {
        JSONObject body = new JSONObject().put("query", query);
        if (variables != null) {
            body.put("variables", variables);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token(c));
        String resp = Http.post(API_URL, body.toString(), "application/json", headers);
        JSONObject o = new JSONObject(resp);
        JSONArray errors = o.optJSONArray("errors");
        if (errors != null && errors.length() > 0) {
            throw new Exception(errors.getJSONObject(0).optString("message", "GraphQL error"));
        }
        JSONObject data = o.optJSONObject("data");
        if (data == null) {
            throw new Exception("risposta API vuota");
        }
        return data;
    }

    /** Id della zona Mythic+ corrente su WarcraftLogs (in cache per 7 giorni). */
    private static int mythicPlusZoneId(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        int cached = p.getInt("mplus_zone", 0);
        long ts = p.getLong("mplus_zone_ts", 0);
        if (cached > 0 && System.currentTimeMillis() - ts < 7L * 24 * 3600 * 1000) {
            return cached;
        }
        JSONObject data = graphql(c, "{worldData{zones{id name}}}", null);
        JSONArray zones = data.optJSONObject("worldData") != null
                ? data.optJSONObject("worldData").optJSONArray("zones") : null;
        int best = 0;
        if (zones != null) {
            for (int i = 0; i < zones.length(); i++) {
                JSONObject z = zones.optJSONObject(i);
                if (z != null && z.optString("name", "").contains("Mythic+")
                        && z.optInt("id", 0) > best) {
                    best = z.optInt("id", 0);
                }
            }
        }
        if (best == 0) {
            throw new Exception("zona Mythic+ non trovata su WarcraftLogs");
        }
        p.edit().putInt("mplus_zone", best)
                .putLong("mplus_zone_ts", System.currentTimeMillis()).apply();
        return best;
    }

    /** Parse del personaggio: zoneId 0 = raid corrente, altrimenti la zona indicata. */
    private static JSONObject fetchRankings(Context c, String region, String realmSlug,
                                            String name, int zoneId) throws Exception {
        String rankingsField = zoneId > 0 ? "zoneRankings(zoneID:$zone)" : "zoneRankings";
        String query = "query($name:String!,$server:String!,$region:String!"
                + (zoneId > 0 ? ",$zone:Int" : "") + "){"
                + "characterData{character(name:$name,serverSlug:$server,serverRegion:$region){"
                + "name " + rankingsField + "}}}";
        JSONObject variables = new JSONObject()
                .put("name", name.trim())
                .put("server", realmSlug)
                .put("region", region);
        if (zoneId > 0) {
            variables.put("zone", zoneId);
        }
        JSONObject data = graphql(c, query, variables);
        JSONObject characterData = data.optJSONObject("characterData");
        JSONObject character = characterData != null
                ? characterData.optJSONObject("character") : null;
        if (character == null || character.isNull("zoneRankings")) {
            throw new Exception(c.getString(R.string.wcl_personaggio_non_trovato));
        }
        Object zr = character.get("zoneRankings");
        return zr instanceof JSONObject ? (JSONObject) zr : new JSONObject(zr.toString());
    }

    /** Parse del raid corrente. */
    public static JSONObject fetchRaidRankings(Context c, String region, String realmSlug,
                                               String name) throws Exception {
        return fetchRankings(c, region, realmSlug, name, 0);
    }

    /** Parse delle Mythic+ della stagione corrente. */
    public static JSONObject fetchMythicPlusRankings(Context c, String region, String realmSlug,
                                                     String name) throws Exception {
        return fetchRankings(c, region, realmSlug, name, mythicPlusZoneId(c));
    }
}
