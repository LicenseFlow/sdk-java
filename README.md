# java-sdk

Official Java SDK for LicenseFlow.

## Installation (Maven)

```xml
<dependency>
    <groupId>com.licenseflow</groupId>
    <artifactId>java-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```java
import com.licenseflow.LicenseFlowClient;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        LicenseFlowClient client = new LicenseFlowClient(
            "https://your-project.supabase.co",
            "your-api-key",
            "your-jwt-secret"
        );

        try {
            // 1. Activate License
            Map<String, Object> activation = client.activate("XXXX-YYYY-ZZZZ-AAAA", "My App");
            System.out.println("Activated: " + activation.get("success"));

            // 2. Verify License (Uses internal cache)
            Map<String, Object> verification = client.verify("XXXX-YYYY-ZZZZ-AAAA");
            System.out.println("Valid: " + verification.get("valid"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Features

- **OkHttp Based**: Efficient connection pooling and retries.
- **Auto Hardware ID**: Built-in hostname identification.
- **Smart Caching**: In-memory verification caching.
- **JSON Serialization**: Using Gson for lightweight parsing.

## Phase 5: Entitlements

Check access to specific features:

```java
// Check boolean feature
if (client.hasFeature(verification, "ai_features")) {
    enableAI();
}

// Get specific entitlement value
Object limit = client.getEntitlement(verification, "max_users");
System.out.println("User limit: " + limit);
```

## Phase 5: Release Management

Check for updates and download artifacts:

```java
// Check for updates
Map<String, Object> update = client.checkForUpdates("prod_123", "v1.0.0", "stable");

if (update != null) {
    System.out.println("New version available: " + update.get("version"));
    
    // Get download link
    Map<String, Object> download = client.downloadArtifact(
        "LF-KEY-123", 
        (String) update.get("id"), 
        null, 
        "windows", 
        "x64"
    );
    
    System.out.println("Download URL: " + download.get("url"));
}
```

## Phase 5: Offline Licensing

Verify a license file without internet access:

```java
import java.nio.file.Files;
import java.nio.file.Path;

String licenseContent = Files.readString(Path.of("license.lic"));
String publicKey = "YOUR_ORG_PUBLIC_KEY_HEX";

try {
    Map<String, Object> license = client.verifyOfflineLicense(licenseContent, publicKey);
    System.out.println("Offline license valid!");
} catch (Exception e) {
    System.err.println("Invalid license: " + e.getMessage());
}
```
