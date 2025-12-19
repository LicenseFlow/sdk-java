package com.licenseflow.examples;

import com.licenseflow.LicenseFlowClient;
import java.util.Map;

public class BasicUsage {
    public static void main(String[] args) {
        LicenseFlowClient client = new LicenseFlowClient(
                "https://api.test",
                "test-api-key",
                "test-secret");

        System.out.println("--- LicenseFlow Java Example ---");

        try {
            // 1. Activate
            System.out.println("Activating license...");
            Map<String, Object> activation = client.activate("DEMO-KEY", "Java-App");
            System.out.println("Result: " + activation);

            // 2. Verify
            System.out.println("Verifying license...");
            Map<String, Object> verification = client.verify("DEMO-KEY");
            System.out.println("Is Valid: " + verification.get("valid"));

            // 3. Deactivate
            System.out.println("Deactivating license...");
            Map<String, Object> deactivation = client.deactivate("DEMO-KEY");
            System.out.println("Result: " + deactivation);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
