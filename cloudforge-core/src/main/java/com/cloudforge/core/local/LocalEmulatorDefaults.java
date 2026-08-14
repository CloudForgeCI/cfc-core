package com.cloudforge.core.local;

/**
 * Canonical Docker and gateway defaults shared by all CloudForge local emulators.
 */
public final class LocalEmulatorDefaults {

    public static final String DOCKER_NETWORK = "cfc-network";
    public static final int GATEWAY_PORT = 4566;
    public static final String GATEWAY_HOST = "localhost";
    public static final String DOCKER_SOCKET_ENV = "CFC_DOCKER_SOCKET";
    public static final String DEFAULT_DOCKER_SOCKET = "/var/run/docker.sock";

    public static final String MINISTACK_CONTAINER = "cfc-ministack";
    public static final String LOCALSTACK_CONTAINER = "cfc-localstack";
    public static final String MINISTACK_IMAGE = "ministackorg/ministack:1.4.9";
    public static final String LOCALSTACK_IMAGE = "localstack/localstack:latest";
    public static final String MINISTACK_HEALTH_PATH = "/_ministack/health";
    public static final String LOCALSTACK_HEALTH_PATH = "/_localstack/health";

    public static final String LOCALSTACK_AUTH_TOKEN_ENV = "LOCALSTACK_AUTH_TOKEN";
    public static final String LOCALSTACK_VOLUME_DIR_ENV = "CFC_LOCALSTACK_VOLUME_DIR";
    public static final String LOCALSTACK_VOLUME_DIR_NAME = ".localstack-volumes";

    /** StackPort resource browser (optional; owned by each target module). */
    public static final String MINISTACK_STACKPORT_CONTAINER = "cfc-ministack-stackport";
    public static final String LOCALSTACK_STACKPORT_CONTAINER = "cfc-localstack-stackport";
    public static final String STACKPORT_IMAGE = "davireis/stackport:latest";
    public static final int STACKPORT_HOST_PORT = 8888;
    public static final int STACKPORT_CONTAINER_PORT = 8080;
    public static final String STACKPORT_ENDPOINT_ENV = "CFC_STACKPORT_AWS_ENDPOINT_URL";
    public static final String STACKPORT_ENDPOINTS_ENV = "STACKPORT_ENDPOINTS";
    public static final String STACKPORT_DATA_DIR_ENV = "STACKPORT_DATA_DIR";
    public static final String STACKPORT_VOLUME_DIR_NAME = ".stackport-volumes";

    /** nginx Host-based edge for *.cloudforge.localhost (shared by MiniStack + LocalStack). */
    public static final String EMULATOR_EDGE_CONTAINER = "cfc-emulator-edge";
    public static final String EMULATOR_EDGE_IMAGE = "nginx:1.27-alpine";
    public static final int EMULATOR_EDGE_HOST_PORT = 80;
    public static final int EMULATOR_EDGE_CONTAINER_PORT = 80;
    public static final String EMULATOR_EDGE_VOLUME_DIR_NAME = ".emulator-edge";
    public static final String EMULATOR_EDGE_HTTP_PORT_ENV = "CFC_EDGE_HTTP_PORT";
    /** When false, ministack-start / localstack-start skip StackPort + edge. Default true. */
    public static final String EMULATOR_COMPANIONS_ENV = "CFC_EMULATOR_COMPANIONS";
    /** When false, skip StackPort auto-start with the emulator. Default true. */
    public static final String STACKPORT_AUTOSTART_ENV = "CFC_STACKPORT_AUTOSTART";
    /** When false, skip nginx edge auto-start with the emulator. Default true. */
    public static final String EDGE_AUTOSTART_ENV = "CFC_EDGE_AUTOSTART";

    /** Canonical CloudForge local hostnames (edge + hosts file). */
    public static final String HOST_LOCALSTACK = "localstack.cloudforge.localhost";
    public static final String HOST_MINISTACK = "ministack.cloudforge.localhost";
    /** Shared gateway alias when either emulator owns :4566. */
    public static final String HOST_EMULATOR = "emulator.cloudforge.localhost";
    public static final String HOST_STACKPORT = "stackport.cloudforge.localhost";
    public static final String HOST_NGINX = "nginx.cloudforge.localhost";
    public static final String HOST_MANAGER = "manager.cloudforge.localhost";

    /**
     * Edge hostname for a CloudForge {@code applicationId}.
     * Returns null when there is no conventional edge vhost for the app.
     */
    public static String edgeHostnameForApplication(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return null;
        }
        return switch (applicationId.trim().toLowerCase()) {
            case "cloudforge-manager" -> HOST_MANAGER;
            case "jenkins" -> "jenkins.cloudforge.localhost";
            case "grafana" -> "grafana.cloudforge.localhost";
            case "prometheus" -> "prometheus.cloudforge.localhost";
            case "vault" -> "vault.cloudforge.localhost";
            case "nexus" -> "nexus.cloudforge.localhost";
            case "sonarqube" -> "sonarqube.cloudforge.localhost";
            case "redis" -> "redis.cloudforge.localhost";
            case "postgresql", "postgres" -> "postgres.cloudforge.localhost";
            case "drone" -> "drone.cloudforge.localhost";
            case "gitea" -> "gitea.cloudforge.localhost";
            case "metabase" -> "metabase.cloudforge.localhost";
            case "mattermost", "mattermost-enterprise", "mattermost-team" ->
                "mattermost.cloudforge.localhost";
            default -> null;
        };
    }

    /** Browser URL for an app via the emulator edge. */
    public static String edgeApplicationUrl(String applicationId) {
        String host = edgeHostnameForApplication(applicationId);
        return host == null ? null : "http://" + host + "/";
    }

    private LocalEmulatorDefaults() {
    }
}
