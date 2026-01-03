package com.licenseflow;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.time.Instant;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.encoders.Hex;

public class LicenseFlowClient {
    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    public LicenseFlowClient(String baseUrl, String apiKey, String jwtSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
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

    public Map<String, Object> deactivate(String licenseKey) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("device_id", getHardwareId());

        Map<String, Object> res = post("functions/v1/deactivate-license", payload);
        cache.clear(); // Clear cache
        return res;
    }

    public boolean hasFeature(Map<String, Object> verification, String featureCode) {
        if (!Boolean.TRUE.equals(verification.get("valid")) || !verification.containsKey("entitlements")) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> entitlements = (Map<String, Object>) verification.get("entitlements");
        Object ent = entitlements.get(featureCode);
        if (ent == null)
            return false;

        if (ent instanceof Boolean)
            return (Boolean) ent;
        if (ent instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) ent;
            return Boolean.TRUE.equals(map.get("enabled")) || Boolean.TRUE.equals(map.get("value"));
        }
        if (ent instanceof String) {
            return "true".equalsIgnoreCase((String) ent);
        }
        return false;
    }

    public Object getEntitlement(Map<String, Object> verification, String featureCode) {
        if (!Boolean.TRUE.equals(verification.get("valid")) || !verification.containsKey("entitlements")) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> entitlements = (Map<String, Object>) verification.get("entitlements");
        return entitlements.get(featureCode);
    }

    public Map<String, Object> checkForUpdates(String productId, String currentVersion, String channel)
            throws IOException {
        String url = baseUrl + "functions/v1/release-management/latest?product_id=" + productId + "&channel="
                + (channel != null ? channel : "stable");

        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404)
                return null;
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            String body = response.body().string();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(body, Map.class);

            if (data == null || currentVersion.equals(data.get("version"))) {
                return null;
            }
            return data;
        }
    }

    public Map<String, Object> downloadArtifact(String licenseKey, String releaseId, String artifactId, String platform,
            String architecture) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        if (releaseId != null)
            payload.put("release_id", releaseId);
        if (artifactId != null)
            payload.put("artifact_id", artifactId);
        if (platform != null)
            payload.put("platform", platform);
        if (architecture != null)
            payload.put("architecture", architecture);

        return post("functions/v1/artifact-download", payload);
    }

    public Map<String, Object> verifyOfflineLicense(String licenseContent, String publicKeyHex) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = gson.fromJson(licenseContent, Map.class);
        if (!data.containsKey("license") || !data.containsKey("signature")) {
            throw new Exception("Invalid offline license format");
        }

        String message = gson.toJson(data.get("license"));
        byte[] signature = Base64.getDecoder().decode((String) data.get("signature"));
        byte[] pubKeyBytes = Hex.decode(publicKeyHex);

        Ed25519PublicKeyParameters pubKeyParams = new Ed25519PublicKeyParameters(pubKeyBytes, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, pubKeyParams);
        signer.update(message.getBytes(), 0, message.length());

        if (!signer.verifySignature(signature)) {
            throw new Exception("Invalid offline license signature");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> license = (Map<String, Object>) data.get("license");
        if (license.containsKey("valid_until")) {
            Instant validUntil = Instant.parse((String) license.get("valid_until"));
            if (Instant.now().isAfter(validUntil)) {
                throw new Exception("Offline license has expired");
            }
        }

        return license;
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
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(responseBody, Map.class);

            if (!response.isSuccessful()) {
                String code = "UNKNOWN_ERROR";
                if (response.code() == 429)
                    code = "RATE_LIMIT_EXCEEDED";
                else if (response.code() == 400 || response.code() == 404)
                    code = "INVALID_LICENSE";

                String msg = (String) result.getOrDefault("message",
                        result.getOrDefault("error", "HTTP " + response.code()));
                throw new LicenseFlowException(msg, code, response.code());
            }

            return result;
        }
    }
}
