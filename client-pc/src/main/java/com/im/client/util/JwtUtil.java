package com.im.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class JwtUtil {

    private static final String TOKEN_FILE = ".jwt_token";
    private static String currentToken;
    private static String currentUsername;
    private static String currentNickname;

    private JwtUtil() {
    }

    public static String getToken() {
        if (currentToken != null) {
            return currentToken;
        }
        try {
            Path path = Paths.get(TOKEN_FILE);
            if (Files.exists(path)) {
                currentToken = Files.readString(path).trim();
            }
        } catch (IOException ignored) {
        }
        return currentToken;
    }

    public static void setToken(String token) {
        currentToken = token;
        try {
            Files.writeString(Paths.get(TOKEN_FILE), token);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void clearToken() {
        currentToken = null;
        currentUsername = null;
        currentNickname = null;
        try {
            Files.deleteIfExists(Paths.get(TOKEN_FILE));
        } catch (IOException ignored) {
        }
    }

    public static String getUsername() { return currentUsername; }
    public static void setUsername(String username) { currentUsername = username; }

    public static String getNickname() { return currentNickname; }
    public static void setNickname(String nickname) { currentNickname = nickname; }
}
