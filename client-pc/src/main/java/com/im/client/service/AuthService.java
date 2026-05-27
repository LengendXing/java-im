package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import com.im.client.util.JwtUtil;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AuthService {

    private static AuthService instance;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private AuthService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    public AuthResult login(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        Request request = new Request.Builder()
                .url(Config.getHttpBaseUrl() + "/api/login")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() != null) {
                String respStr = response.body().string();
                JsonObject resp = gson.fromJson(respStr, JsonObject.class);
                int code = resp.has("code") ? resp.get("code").getAsInt() : -1;
                if (code == 0 && resp.has("data")) {
                    JsonObject data = resp.getAsJsonObject("data");
                    String token = data.get("token").getAsString();
                    JwtUtil.setToken(token);
                    if (data.has("username")) JwtUtil.setUsername(data.get("username").getAsString());
                    if (data.has("nickname")) JwtUtil.setNickname(data.get("nickname").getAsString());
                    return new AuthResult(true, "OK", token);
                }
                String msg = resp.has("message") ? resp.get("message").getAsString() : "Login failed";
                return new AuthResult(false, msg, null);
            }
            return new AuthResult(false, "Empty response", null);
        } catch (IOException e) {
            return new AuthResult(false, "Network error: " + e.getMessage(), null);
        }
    }

    public AuthResult register(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        Request request = new Request.Builder()
                .url(Config.getHttpBaseUrl() + "/api/register")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() != null) {
                String respStr = response.body().string();
                JsonObject resp = gson.fromJson(respStr, JsonObject.class);
                int code = resp.has("code") ? resp.get("code").getAsInt() : -1;
                if (code == 0) {
                    return new AuthResult(true, "OK", null);
                }
                String msg = resp.has("message") ? resp.get("message").getAsString() : "Register failed";
                return new AuthResult(false, msg, null);
            }
            return new AuthResult(false, "Empty response", null);
        } catch (IOException e) {
            return new AuthResult(false, "Network error: " + e.getMessage(), null);
        }
    }

    /**
     * Generic HTTP GET with Bearer token auth.
     * Returns the response body string, or null on failure.
     */
    public String httpGet(String url) {
        String token = JwtUtil.getToken();
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Generic HTTP POST with Bearer token auth and JSON body.
     * Returns the response body string, or null on failure.
     */
    public String httpPost(String url, String jsonBody) {
        String token = JwtUtil.getToken();
        RequestBody body = RequestBody.create(jsonBody, JSON_TYPE);
        Request.Builder builder = new Request.Builder().url(url).post(body);
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public record AuthResult(boolean success, String message, String token) {
    }
}
