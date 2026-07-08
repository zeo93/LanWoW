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

    /**
     * Espansioni con relative zone e difficoltà (come sul sito), in cache per 24 ore.
     * Ordinate dalla più recente alla più vecchia.
     */
    public static JSONArray fetchExpansions(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String cached = p.getString("expansions", null);
        long ts = p.getLong("expansions_ts", 0);
        if (cached != null && System.currentTimeMillis() - ts < 24L * 3600 * 1000) {
            return new JSONArray(cached);
        }
        JSONObject data = graphql(c,
                "{worldData{expansions{id name zones{id name difficulties{id name}}}}}", null);
        JSONObject worldData = data.optJSONObject("worldData");
        JSONArray exps = worldData != null ? worldData.optJSONArray("expansions") : null;
        if (exps == null || exps.length() == 0) {
            throw new Exception("elenco espansioni non disponibile");
        }
        p.edit().putString("expansions", exps.toString())
                .putLong("expansions_ts", System.currentTimeMillis()).apply();
        return exps;
    }

    /**
     * Parse del personaggio con filtri come sul sito.
     *
     * @param zoneId     zona (raid o stagione M+); 0 = zona predefinita
     * @param difficulty id difficoltà; 0 = predefinita
     * @param metric     dps, hps, bossdps, playerscore…; null = predefinita
     */
    public static JSONObject fetchRankings(Context c, String region, String realmSlug,
                                           String name, int zoneId, int difficulty,
                                           String metric) throws Exception {
        String query = "query($name:String!,$server:String!,$region:String!,"
                + "$zone:Int,$difficulty:Int,$metric:CharacterPageRankingMetricType){"
                + "characterData{character(name:$name,serverSlug:$server,serverRegion:$region){"
                + "name zoneRankings(zoneID:$zone,difficulty:$difficulty,metric:$metric)}}}";
        JSONObject variables = new JSONObject()
                .put("name", name.trim())
                .put("server", realmSlug)
                .put("region", region);
        if (zoneId > 0) {
            variables.put("zone", zoneId);
        }
        if (difficulty > 0) {
            variables.put("difficulty", difficulty);
        }
        if (metric != null && !metric.isEmpty()) {
            variables.put("metric", metric);
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
}
