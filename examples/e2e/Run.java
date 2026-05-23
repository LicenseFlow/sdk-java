package examples.e2e;

import com.licenseflow.Client;

public class Run {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("LICENSEFLOW_API_URL");
        String key = System.getenv("LICENSEFLOW_API_KEY");
        String lk  = System.getenv("LICENSE_KEY");
        String rk  = System.getenv("REVOKED_LICENSE_KEY");
        if (url == null || key == null || lk == null || rk == null) {
            System.err.println("Missing env"); System.exit(2);
        }
        Client c = new Client(url, key);
        c.activate(lk, "ci-java");
        if (!c.verify(lk).valid) throw new RuntimeException("active must verify");
        if (c.verify(rk).valid)  throw new RuntimeException("revoked must not verify");
        c.deactivate(lk);
        System.out.println("Java SDK E2E \u2713");
    }
}