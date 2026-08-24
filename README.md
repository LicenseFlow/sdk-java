# LicenseFlow Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.licenseflow/java-sdk)](https://search.maven.org/artifact/com.licenseflow/java-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**Stop Building Licensing Infrastructure. Start Shipping Software.**

The official Java SDK for [LicenseFlow](https://licenseflow.dev). Protect your intellectual property, enforce entitlements, and manage software distribution with enterprise-grade security.

## Installation (Maven)

```xml
<dependency>
    <groupId>com.licenseflow</groupId>
    <artifactId>java-sdk</artifactId>
    <version>2.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.licenseflow:java-sdk:2.1.0'
```

## Quick Start

```java
import com.licenseflow.LicenseFlowClient;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        LicenseFlowClient client = new LicenseFlowClient(
            "https://api.licenseflow.dev",
            "lf_live_xxxxxxxxxxxx",
            "your-jwt-secret"
        );

        Map<String, Object> activation = client.activate("XXXX-YYYY-ZZZZ-AAAA", "My App");
        System.out.println("Activated: " + activation.get("success"));

        Map<String, Object> verification = client.verify("XXXX-YYYY-ZZZZ-AAAA");
        System.out.println("Valid: " + verification.get("valid"));
    }
}
```

---

## Entitlement Caching

`LicenseFlowClient` includes a built-in `EntitlementCache` with TTL and offline grace period.

```java
LicenseFlowClient client = new LicenseFlowClient.Builder()
    .baseUrl("https://api.licenseflow.dev")
    .apiKey("lf_live_xxxxxxxxxxxx")
    .jwtSecret("your-jwt-secret")
    .cacheTtlSeconds(300)        // Cache TTL: 5 minutes
    .offlineGraceSeconds(259200) // Offline grace: 72 hours
    .build();

// First call: live API, result cached in memory
Map<String, Object> result = client.verify("XXXX-YYYY-ZZZZ-AAAA");

// Within TTL: instantly served from memory cache
Map<String, Object> result2 = client.verify("XXXX-YYYY-ZZZZ-AAAA");

// Offline within grace: stale cache returned
// Grace expired: OfflineLicenseException thrown
```

---

## API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `activate(licenseKey, deviceName)` | Activate on a device |
| `verify(licenseKey)` | Verify license (cached) |
| `deactivate(licenseKey)` | Deactivate from a device |
| `recordUsage(licenseKey, metricName, value)` | Track usage metrics |
| `getHardwareId()` | Get hostname-based device ID |

### Entitlements

```java
if (client.hasFeature(verification, "ai_features")) {
    enableAI();
}

Object limit = client.getEntitlement(verification, "max_users");
System.out.println("User limit: " + limit);
```

### Floating Licenses (Leases)

```java
Map<String, Object> lease = client.checkoutLicense(
    "XXXX-XXXX", 3600, "ci-runner-1", "ci_runner"
);
System.out.println("Lease: " + lease.get("lease_key"));

client.checkinLicense((String) lease.get("lease_key"));
Map<String, Object> status = client.getLeaseStatus((String) lease.get("lease_key"));
```

### Credits

```java
Map<String, Object> result = client.consumeCredits(100, "AI tokens", null, null);
System.out.println("Remaining: " + result.get("remaining"));

Map<String, Object> balance = client.getCreditsBalance(null, null);
```

### Release Management

```java
Map<String, Object> update = client.checkForUpdates("prod_123", "v1.0.0", "stable");

if (update != null) {
    Map<String, Object> download = client.downloadArtifact(
        "XXXX-XXXX", (String) update.get("id"), null, "windows", "x64"
    );
    System.out.println("Download: " + download.get("url"));
}
```

### Offline Licensing

```java
String licenseContent = Files.readString(Path.of("license.lic"));
Map<String, Object> license = client.verifyOfflineLicense(licenseContent, "ORG_PUBLIC_KEY_HEX");
System.out.println("Valid until: " + license.get("valid_until"));
```

### Heartbeat

```java
client.startHeartbeat("XXXX-XXXX", 60); // seconds
// ... later
client.stopHeartbeat();
```

---

## Error Handling

```java
try {
    client.activate("XXXX", "Server");
} catch (RateLimitException e) {
    System.err.println("Rate limit exceeded");
} catch (InvalidLicenseException e) {
    System.err.println("Invalid license");
} catch (Exception e) {
    System.err.println("Error: " + e.getMessage());
}
```

## Features

- **OkHttp** — Efficient connection pooling and retries
- **Gson** — Lightweight JSON serialization
- **Thread-safe Caching** — In-memory `EntitlementCache` with configurable TTL
- **Offline Grace Period** — Operate up to 72h without network connectivity
- **Ed25519** — Cryptographic offline license verification

## License

MIT

## Links

- 📖 [Documentation](https://docs.licenseflow.dev)
- 🐛 [Issues](https://github.com/LicenseFlow/sdk-java/issues)
- 🏠 [Homepage](https://licenseflow.dev)
