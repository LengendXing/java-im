package com.im.client.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.im.client.util.Config;
import com.im.client.util.JwtUtil;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FileUploadService {

    private static FileUploadService instance;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    private FileUploadService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized FileUploadService getInstance() {
        if (instance == null) {
            instance = new FileUploadService();
        }
        return instance;
    }

    /**
     * Upload a file to the server.
     * @param file the file to upload
     * @return UploadResult with url, fileName, fileSize; or null on failure
     */
    public UploadResult upload(File file) {
        String token = JwtUtil.getToken();
        String fileName = file.getName();
        RequestBody fileBody = RequestBody.create(file, OCTET_STREAM);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();

        Request.Builder builder = new Request.Builder()
                .url(Config.getHttpBaseUrl() + "/api/file/upload")
                .post(multipartBody);
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                String respStr = response.body().string();
                JsonObject resp = gson.fromJson(respStr, JsonObject.class);
                int code = resp.has("code") ? resp.get("code").getAsInt() : -1;
                if (code == 0 && resp.has("data")) {
                    JsonObject data = resp.getAsJsonObject("data");
                    String url = data.has("url") ? data.get("url").getAsString() : null;
                    String fname = data.has("fileName") ? data.get("fileName").getAsString() : fileName;
                    long fsize = data.has("fileSize") ? data.get("fileSize").getAsLong() : file.length();
                    return new UploadResult(url, fname, fsize);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public record UploadResult(String url, String fileName, long fileSize) {
    }
}
