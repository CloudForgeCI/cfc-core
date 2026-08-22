package com.cloudforge.core.local;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Resolves the best "open this application" URL from a deployed stack's CFN outputs.
 *
 * <p>Shared by {@code cloudforge-manager}'s inventory (live AWS/LocalStack/MiniStack stacks)
 * and {@code cfc-testing}'s post-deploy hint (freshly deployed stack's own output map) so both
 * apply the exact same precedence — edge hostnames for local emulators first, then AWS-style
 * {@code ApplicationUrl}/{@code LoadBalancerDNS}/{@code Url} outputs — instead of two
 * implementations drifting apart.</p>
 *
 * @since 3.2.0
 */
public final class PreferredUrlResolver {

    /** CFN output key CloudForge stacks tag with the deploying application's id. */
    public static final String OUTPUT_APPLICATION_ID = "CloudForgeApplicationId";

    private PreferredUrlResolver() {
    }

    /**
     * @param outputs CFN stack outputs (key → value), or {@code null}/empty
     * @return the best URL to open for this deployment, or {@code null} if none of the known
     *         output keys are present
     */
    public static String preferredUrl(Map<String, String> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }
        String applicationId = outputs.get(OUTPUT_APPLICATION_ID);
        // Local emulator Open links: prefer edge hostnames over ELB / localhost:port.
        if (hasLocalEmulatorBrowserUrl(outputs)) {
            String edge = LocalEmulatorDefaults.edgeApplicationUrl(applicationId);
            if (edge != null) {
                return edge;
            }
        }
        String localApp = outputs.get("LocalStackApplicationUrl");
        if (localApp != null && !localApp.isBlank()) {
            if (requiresLocalStackPathPrefix(applicationId)) {
                return mergeLocalStackHostUrl(localApp, outputs.get("LocalStackLocalUrl"));
            }
            return localApp.trim();
        }
        for (String key : List.of(
            "LocalStackAuthenticatedUrl",
            "MiniStackApplicationUrl",
            "MiniStackAuthenticatedUrl",
            "MiniStackLocalUrl",
            "ApplicationUrl",
            "LoadBalancerDNS",
            "Url")) {
            String value = outputs.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public static boolean hasLocalEmulatorBrowserUrl(Map<String, String> outputs) {
        if (outputs == null) {
            return false;
        }
        for (String key : List.of(
            "LocalStackApplicationUrl",
            "LocalStackLocalUrl",
            "LocalStackElbHostnameUrl",
            "LocalStackAuthenticatedUrl",
            "MiniStackApplicationUrl",
            "MiniStackLocalUrl",
            "MiniStackAuthenticatedUrl")) {
            String value = outputs.get(key);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Jenkins (and Manager) on LocalStack use {@code --prefix=/_aws/elb/{name}}; the
     * host-mapped port alone returns Jetty 404 unless the prefix is included.
     */
    public static boolean requiresLocalStackPathPrefix(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return false;
        }
        return switch (applicationId.trim().toLowerCase()) {
            case "jenkins", "cloudforge-manager" -> true;
            default -> false;
        };
    }

    public static String mergeLocalStackHostUrl(String appUrl, String localUrl) {
        if (appUrl == null || appUrl.isBlank()) {
            return null;
        }
        if (localUrl == null || localUrl.isBlank()) {
            return appUrl.trim();
        }
        try {
            URI app = URI.create(appUrl.trim());
            URI local = URI.create(localUrl.trim());
            String path = local.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return app.toString();
            }
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            return new URI(app.getScheme(), null, app.getHost(), app.getPort(), path, null, null).toString();
        } catch (Exception ignored) {
            return appUrl.trim();
        }
    }
}
