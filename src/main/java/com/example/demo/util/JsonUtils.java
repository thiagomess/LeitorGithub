package com.example.demo.util;

import org.json.JSONObject;

public final class JsonUtils {

    private JsonUtils() {
        // Utility class
    }

    public static String extractMessage(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString("message", json);
        } catch (Exception e) {
            return json;
        }
    }
}
