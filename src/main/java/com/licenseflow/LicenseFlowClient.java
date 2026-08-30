package com.licenseflow;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    private final String jwtSecret;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private final long cacheTtlMs;

    private static class CacheEntry {
        final Map<String, Object> value;
        final long expiresAt;
        CacheEntry(Map<String, Object> value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
    private final Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public LicenseFlowClient(String baseUrl, String apiKey, String jwtSecret) {
        this(baseUrl, apiKey, jwtSecret, TimeUnit.MINUTES.toMillis(5));
    }

    public LicenseFlowClient(String baseUrl, String apiKey, String jwtSecret, long cacheTtlMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.jwtSecret = jwtSecret;
        this.cacheTtlMs = cacheTtlMs > 0 ? cacheTtlMs : TimeUnit.MINUTES.toMillis(5);
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

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null) {
            if (System.currentTimeMillis() < entry.expiresAt) {
                return entry.value;
            }
            cache.remove(cacheKey);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("licenseKey", licenseKey);
        payload.put("deviceId", deviceId);
        if (environmentId != null) payload.put("environmentId", environmentId);

        Map<String, Object> res = post("functions/v1/verify-license", payload);
        if (Boolean.TRUE.equals(res.get("valid"))) {
            cache.put(cacheKey, new CacheEntry(res, System.currentTimeMillis() + cacheTtlMs));
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

    /**
     * Identity-based (keyless) entitlement resolution.
     * Resolves everything an authenticated person is entitled to from their
     * email alone — licenses they own plus any seats assigned to them.
     */
    public Map<String, Object> resolveForIdentity(String email, String productId, String environmentId) throws IOException {
        String cacheKey = "identity:" + email + ":" + (productId != null ? productId : "all") + ":" + (environmentId != null ? environmentId : "default");
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.value;
        } else if (cached != null) {
            cache.remove(cacheKey);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        if (productId != null) payload.put("productId", productId);
        if (environmentId != null) payload.put("environmentId", environmentId);

        Map<String, Object> res = post("functions/v1/resolve-entitlements", payload);

        if (Boolean.TRUE.equals(res.get("resolved"))) {
            cache.put(cacheKey, new CacheEntry(res, System.currentTimeMillis() + cacheTtlMs));
        }

        return res;
    }

    public Map<String, Object> resolveForIdentity(String email) throws IOException {
        return resolveForIdentity(email, null, null);
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

    // ── Usage Recording ──

    public Map<String, Object> recordUsage(String licenseKey, String metricName, double value, boolean increment, String environmentId) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_key", licenseKey);
        payload.put("metric_name", metricName);
        payload.put("value", value);
        payload.put("increment", increment);
        if (environmentId != null) payload.put("environment_id", environmentId);

        Map<String, Object> res = post("functions/v1/record-usage", payload);
        res.put("success", true);
        return res;
    }

    // ── Credits / Usage-Based Billing ──

    public Map<String, Object> consumeCredits(int amount, String description, String productId, String currency) throws IOException {
        return consumeCredits(amount, description, productId, currency, null, null, null);
    }

    public Map<String, Object> consumeCredits(int amount, String description, String productId, String currency,
                                              String referenceId, String referenceType, Map<String, Object> metadata) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        if (description != null) payload.put("description", description);
        if (productId != null) payload.put("product_id", productId);
        if (currency != null && !currency.equals("credits")) payload.put("currency", currency);
        if (referenceId != null) payload.put("reference_id", referenceId);
        if (referenceType != null) payload.put("reference_type", referenceType);
        if (metadata != null) payload.put("metadata", metadata);
        return post("functions/v1/consume-credits", payload);
    }

    public Map<String, Object> getCreditsBalance(String productId, String currency) throws IOException {
        StringBuilder url = new StringBuilder(baseUrl + "functions/v1/get-credit-balance");
        String sep = "?";
        if (productId != null) { url.append(sep).append("product_id=").append(productId); sep = "&"; }
        if (currency != null) { url.append(sep).append("currency=").append(currency); }

        Request request = new Request.Builder()
                .url(url.toString())
                .addHeader("x-api-key", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body == null) return new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(body.string(), Map.class);
            return data != null ? data : new HashMap<>();
        }
    }

    // ── Entitlements Management ──

    public Object listEntitlements() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "functions/v1/manage-entitlements")
                .addHeader("x-api-key", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body == null) return new java.util.ArrayList<>();
            return gson.fromJson(body.string(), java.util.List.class);
        }
    }

    public Map<String, Object> createEntitlement(String code, String name, String dataType) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("name", name);
        payload.put("data_type", dataType != null ? dataType : "boolean");
        return post("functions/v1/manage-entitlements", payload);
    }

    public Map<String, Object> deleteEntitlement(String entitlementId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "functions/v1/manage-entitlements/" + entitlementId)
                .addHeader("x-api-key", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body == null) return new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(body.string(), Map.class);
            return data != null ? data : new HashMap<>();
        }
    }

    public Map<String, Object> updateEntitlement(String entitlementId, Map<String, Object> updates) throws IOException {
        String json = gson.toJson(updates != null ? updates : new HashMap<>());
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + "functions/v1/manage-entitlements/" + entitlementId)
                .addHeader("x-api-key", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .put(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            String responseBodyStr = respBody != null ? respBody.string() : "{}";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(responseBodyStr, Map.class);
            if (result == null) result = new HashMap<>();
            if (!response.isSuccessful()) {
                throw new LicenseFlowException(
                    (String) result.getOrDefault("error", "HTTP " + response.code()),
                    "UNKNOWN_ERROR", response.code());
            }
            return result;
        }
    }

    public Map<String, Object> assignEntitlementToLicense(String entitlementId, String licenseId, Map<String, Object> value) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("license_id", licenseId);
        payload.put("value", value);
        return post("functions/v1/manage-entitlements/" + entitlementId + "/assign-to-license", payload);
    }

    public Map<String, Object> assignEntitlementToPolicy(String entitlementId, String policyId, Map<String, Object> defaultValue) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("policy_id", policyId);
        payload.put("default_value", defaultValue);
        return post("functions/v1/manage-entitlements/" + entitlementId + "/assign-to-policy", payload);
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

    /**
     * Validate a signed JWT proof token offline using HS256.
     * Returns a map with "valid":true and "payload":{...} on success,
     * or "valid":false with an "error" field on failure.
     */
    public Map<String, Object> validateProofOffline(String proof, String secret) throws Exception {
        String key = secret != null ? secret : this.jwtSecret;
        if (key == null || key.isEmpty()) {
            throw new Exception("JWT secret is required for offline validation");
        }
        Map<String, Object> result = new HashMap<>();
        String[] parts = proof.split("\\.");
        if (parts.length != 3) {
            result.put("valid", false); result.put("error", "invalid token format"); return result;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            byte[] expectedSig = Base64.getUrlDecoder().decode(parts[2]);
            String signingInput = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
            byte[] computedSig = mac.doFinal(signingInput.getBytes("UTF-8"));
            if (!java.security.MessageDigest.isEqual(expectedSig, computedSig)) {
                result.put("valid", false); result.put("error", "signature verification failed"); return result;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = gson.fromJson(new String(payloadBytes, "UTF-8"), Map.class);
            result.put("valid", true); result.put("payload", payload); return result;
        } catch (Exception e) {
            result.put("valid", false); result.put("error", e.getMessage()); return result;
        }
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
