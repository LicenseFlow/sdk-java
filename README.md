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
