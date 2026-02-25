package com.licenseflow;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.encoders.Hex;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
        } catch (java.net.UnknownHostException e) {
            return "unknown-java-host";
        }
    }

    public Map<String, Object> activate(String licenseKey, String deviceName, String environmentId) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("device_id", getHardwareId());
        payload.put("device_name", deviceName);
        if (environmentId != null) payload.put("environment_id", environmentId);
        return post("functions/v1/activate-license", payload);
    }

    public Map<String, Object> verify(String licenseKey, String environmentId) throws IOException {
        String deviceId = getHardwareId();
        String cacheKey = "verify:" + licenseKey + ":" + deviceId + ":" + (environmentId != null ? environmentId : "default");

        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("licenseKey", licenseKey);
        payload.put("deviceId", deviceId);
        if (environmentId != null) payload.put("environmentId", environmentId);

        Map<String, Object> res = post("functions/v1/verify-license", payload);
        if (Boolean.TRUE.equals(res.get("valid"))) {
            cache.put(cacheKey, res);
        }
        return res;
    }

    public Map<String, Object> deactivate(String licenseKey, String environmentId) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("device_id", getHardwareId());
        if (environmentId != null) payload.put("environment_id", environmentId);

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

        if (ent instanceof Boolean b)
            return b;
        if (ent instanceof Map<?, ?> map) {
            return Boolean.TRUE.equals(map.get("enabled")) || Boolean.TRUE.equals(map.get("value"));
        }
        if (ent instanceof String s) {
            return "true".equalsIgnoreCase(s);
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
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404)
                return null;
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            ResponseBody body = response.body();
            if (body == null) return null;
            
            String bodyString = body.string();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(bodyString, Map.class);

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

    // ── Floating License Lease Methods ──

    public Map<String, Object> checkoutLicense(String licenseKey, int durationSeconds, String requesterId, String requesterType) throws IOException {
        if (requesterId == null || requesterId.isEmpty()) requesterId = getHardwareId();
        if (requesterType == null || requesterType.isEmpty()) requesterType = "sdk";
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("duration_seconds", durationSeconds);
        payload.put("requester_id", requesterId);
        payload.put("requester_type", requesterType);
        return post("functions/v1/checkout-license", payload);
    }

    public Map<String, Object> checkinLicense(String leaseKey) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lease_key", leaseKey);
        return post("functions/v1/checkin-license", payload);
    }

    public Map<String, Object> getLeaseStatus(String leaseKey) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lease_key", leaseKey);
        return post("functions/v1/lease-status", payload);
    }

    // ── Heartbeat ──

    private volatile java.util.concurrent.ScheduledExecutorService heartbeatExecutor;

    public void startHeartbeat(String licenseKey, long intervalMs) {
        stopHeartbeat();
        heartbeatExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try { verify(licenseKey, null); } catch (Exception e) {
                System.err.println("LicenseFlow heartbeat failed: " + e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
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
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            String responseBodyStr = respBody != null ? respBody.string() : "{}";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(responseBodyStr, Map.class);
            if (result == null) result = new HashMap<>();

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
