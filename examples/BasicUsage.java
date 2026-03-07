package com.licenseflow.examples;

import java.util.Map;

import com.licenseflow.LicenseFlowClient;

public class BasicUsage {
    public static void main(String[] args) {
        LicenseFlowClient client = new LicenseFlowClient(
                "https://api.licenseflow.dev/v1",
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

        } catch (com.licenseflow.LicenseFlowException e) {
            System.err.println("LicenseFlow Error [" + e.getCode() + "]: " + e.getMessage());
        } catch (java.io.IOException e) {
            System.err.println("Network Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected Error: " + e.getMessage());
        }
    }
}
