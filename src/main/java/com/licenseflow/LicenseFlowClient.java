package com.licenseflow;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LicenseFlowClient {
    private final String baseUrl;
    private final String apiKey;
    private final String jwtSecret;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    public LicenseFlowClient(String baseUrl, String apiKey, String jwtSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.jwtSecret = jwtSecret;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public String getHardwareId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-java-host";
        }
    }

    public Map<String, Object> activate(String licenseKey, String deviceName) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("device_id", getHardwareId());
        payload.put("device_name", deviceName);
        return post("functions/v1/activate-license", payload);
    }

    public Map<String, Object> verify(String licenseKey) throws IOException {
        String deviceId = getHardwareId();
        String cacheKey = "verify:" + licenseKey + ":" + deviceId;

        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("device_id", deviceId);

        Map<String, Object> res = post("functions/v1/verify-license", payload);
        if (Boolean.TRUE.equals(res.get("valid"))) {
            cache.put(cacheKey, res);
        }
        return res;
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) throws IOException {
        String json = gson.toJson(payload);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("x-api-key", apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            Map<String, Object> result = gson.fromJson(responseBody, Map.class);

            if (!response.isSuccessful()) {
                String code = "UNKNOWN_ERROR";
                if (response.code() == 429) code = "RATE_LIMIT_EXCEEDED";
                else if (response.code() == 400 || response.code() == 404) code = "INVALID_LICENSE";

                String msg = (String) result.getOrDefault("message", result.getOrDefault("error", "HTTP " + response.code()));
                throw new LicenseFlowException(msg, code, response.code());
            }

            return result;
        }
    }
}
