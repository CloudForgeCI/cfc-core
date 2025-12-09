package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.ApplicationPlugin;

import java.util.List;

/**
 * Application specification interface defining application-specific configuration.
 * This enables CloudForge to deploy any application (Jenkins, GitLab, Vault, etc.)
 * using the same infrastructure patterns.
 *
 * <p>Implementations provide configuration for both container (Fargate) and
 * EC2 deployments, supporting both EFS and EBS storage strategies.</p>
 *
 * <p>CloudForge 3.0.0: Universal Application Support</p>
 *
 * <p>Example implementations:</p>
 * <ul>
 *   <li>JenkinsApplicationSpec: Jenkins CI/CD automation server</li>
 *   <li>GitLabApplicationSpec: GitLab DevOps platform</li>
 *   <li>GrafanaApplicationSpec: Grafana metrics visualization</li>
 *   <li>PostgreSQLApplicationSpec: PostgreSQL database</li>
 *   <li>VaultApplicationSpec: HashiCorp Vault secrets management</li>
 *   <li>+ 9 more built-in applications</li>
 * </ul>
 *
 * <h2>Plugin Metadata:</h2>
 * <p>Implementations should be annotated with {@link ApplicationPlugin} for auto-discovery
 * and metadata support:</p>
 * <pre>{@code
 * @ApplicationPlugin(
 *     value = "jenkins",
 *     category = "cicd",
 *     displayName = "Jenkins",
 *     description = "Open-source automation server for CI/CD"
 * )
 * public class JenkinsApplicationSpec implements ApplicationSpec {
 *     // ...
 * }
 * }</pre>
 */
public interface ApplicationSpec {

    // ========== Application Identity ==========

    /**
     * Returns a unique identifier for this application.
     * Used for logging, metrics, and resource naming.
     *
     * @return application identifier (e.g., "jenkins", "gitlab", "vault")
     */
    String applicationId();

    // ========== Container Configuration ==========

    /**
     * Returns the default container image for this application.
     * Can be overridden by deployment context configuration.
     *
     * @return container image string (e.g., "jenkins/jenkins:lts")
     */
    String defaultContainerImage();

    /**
     * Returns the primary application port.
     * This is the port the application listens on inside the container.
     *
     * @return application port (e.g., 8080 for Jenkins)
     */
    int applicationPort();

    /**
     * Returns the container path where application data is stored.
     * This is where the volume will be mounted inside the container.
     *
     * @return container mount path (e.g., "/var/jenkins_home")
     */
    String containerDataPath();

    /**
     * Returns the EFS path for this application's data.
     * This is the path within the EFS filesystem.
     *
     * @return EFS path (e.g., "/jenkins")
     */
    String efsDataPath();

    /**
     * Returns the volume name for this application.
     * Used to reference the volume in task definitions.
     *
     * @return volume name (e.g., "jenkinsHome")
     */
    String volumeName();

    /**
     * Returns the container user (UID:GID) to run as.
     * Important for file permissions when using EFS.
     *
     * @return user in format "UID:GID" (e.g., "1000:1000")
     */
    String containerUser();

    /**
     * Returns the EFS permissions for the access point.
     *
     * @return permissions string (e.g., "750")
     */
    String efsPermissions();

    /**
     * Configures application-specific environment variables for the container.
     *
     * <p>Applications can override this to provide custom environment variables
     * based on deployment configuration (FQDN, SSL, authMode, etc.). The infrastructure
     * passes the FQDN, SSL settings, and authentication mode for applications that need
     * reverse proxy configuration or authentication-specific setup.</p>
     *
     * <p>Example use cases:</p>
     * <ul>
     *   <li>Jenkins: JAVA_OPTS, JENKINS_OPTS for reverse proxy configuration, skip setup wizard for application-oidc</li>
     *   <li>GitLab: GITLAB_OMNIBUS_CONFIG for external URL configuration and OIDC setup</li>
     *   <li>Vault: VAULT_ADDR for API endpoint configuration</li>
     * </ul>
     *
     * @param fqdn The fully qualified domain name (may be null)
     * @param sslEnabled Whether SSL is enabled
     * @param authMode The authentication mode (may be null, e.g., "none", "alb-oidc", "application-oidc")
     * @return Map of environment variable key-value pairs (never null, may be empty)
     */
    default java.util.Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        return java.util.Collections.emptyMap();
    }

    /**
     * Returns the health check path for ALB/ELB health checks.
     *
     * <p>Different applications expose health endpoints at different paths:</p>
     * <ul>
     *   <li>Jenkins: /login</li>
     *   <li>GitLab: /users/sign_in</li>
     *   <li>Grafana: /api/health</li>
     *   <li>Metabase: /api/health</li>
     * </ul>
     *
     * @return health check path (e.g., "/login", "/api/health")
     */
    default String healthCheckPath() {
        return "/";
    }

    // ========== EC2 Configuration ==========

    /**
     * Returns the EBS device name for EC2 instances when not using EFS.
     * This is the device that will be formatted and mounted for application data.
     *
     * @return EBS device path (e.g., "/dev/xvdh")
     */
    String ebsDeviceName();

    /**
     * Returns the EC2 data path where application stores persistent data.
     * This may differ from containerDataPath depending on application packaging.
     *
     * @return EC2 mount path (e.g., "/var/lib/jenkins")
     */
    String ec2DataPath();

    /**
     * Returns CloudWatch log file paths for EC2 monitoring.
     * These files will be streamed to CloudWatch Logs for centralized logging.
     *
     * @return list of absolute log file paths (e.g., ["/var/log/jenkins/jenkins.log"])
     */
    List<String> ec2LogPaths();

    /**
     * Configure EC2 UserData script for application installation and setup.
     *
     * <p>The implementation should use the UserDataBuilder to add application-specific
     * installation commands while leveraging infrastructure helpers for storage mounting
     * and CloudWatch configuration.</p>
     *
     * <p>The infrastructure handles:</p>
     * <ul>
     *   <li>System updates</li>
     *   <li>EFS vs EBS storage mounting (based on availability)</li>
     *   <li>CloudWatch Agent installation and configuration</li>
     *   <li>File permissions and ownership</li>
     * </ul>
     *
     * <p>The application provides:</p>
     * <ul>
     *   <li>Application installation commands (yum/dnf install, etc.)</li>
     *   <li>Application configuration</li>
     *   <li>Service startup commands</li>
     * </ul>
     *
     * @param builder The UserDataBuilder providing infrastructure helpers
     * @param context The Ec2Context providing runtime information
     */
    void configureUserData(UserDataBuilder builder, Ec2Context context);

    // ========== OIDC Integration (Optional) ==========

    /**
     * Returns whether this application supports OIDC integration.
     *
     * <p>Applications with built-in OIDC support (GitLab, Grafana, SonarQube) or
     * plugin support (Jenkins) should return true.</p>
     *
     * @return true if application can integrate with OIDC providers
     */
    default boolean supportsOidcIntegration() {
        return false;
    }

    /**
     * Returns the OIDC integration handler for this application.
     *
     * <p>This provides application-specific configuration for integrating with
     * Cognito or IAM Identity Center OIDC.</p>
     *
     * @return OIDC integration handler, or null if not supported
     */
    default OidcIntegration getOidcIntegration() {
        return null;
    }

    /**
     * Returns the list of supported authentication modes for this application.
     *
     * <p>CloudForge supports three authentication modes:</p>
     * <ul>
     *   <li><b>application-oidc</b>: OIDC authentication integrated within the application (requires getOidcIntegration() != null)</li>
     *   <li><b>alb-oidc</b>: OIDC authentication at ALB level (works for all applications)</li>
     *   <li><b>none</b>: No authentication (public access or manually configured)</li>
     * </ul>
     *
     * <p>The list is ordered by preference. The first mode is the recommended default.</p>
     *
     * <p>Default behavior:</p>
     * <ul>
     *   <li>If application has OIDC integration → ["application-oidc", "alb-oidc", "none"]</li>
     *   <li>If application claims OIDC support but lacks integration → ["alb-oidc", "none"]</li>
     *   <li>If application doesn't support OIDC → ["none"]</li>
     * </ul>
     *
     * @return List of supported auth modes in order of preference (never null, never empty)
     */
    default List<String> getSupportedAuthModes() {
        if (getOidcIntegration() != null) {
            // Application has working OIDC integration - prefer application-oidc
            return List.of("application-oidc", "alb-oidc", "none");
        } else if (supportsOidcIntegration()) {
            // Application claims OIDC support but doesn't implement it - use alb-oidc
            return List.of("alb-oidc", "none");
        } else {
            // No OIDC support at all
            return List.of("none");
        }
    }

    /**
     * Returns the recommended (default) authentication mode for this application.
     *
     * <p>This is the first mode from {@link #getSupportedAuthModes()}.</p>
     *
     * @return the recommended auth mode (e.g., "application-oidc", "alb-oidc", "none")
     */
    default String getRecommendedAuthMode() {
        return getSupportedAuthModes().get(0);
    }

    // ========== Optional Ports (Security-Conscious) ==========

    /**
     * Optional service port that can be enabled via deployment configuration.
     *
     * <p>Ports are NOT exposed by default - must be explicitly enabled via the configKey
     * in deployment configuration. This follows the principle of least privilege.</p>
     *
     * @param port The port number
     * @param protocol The protocol ("tcp" or "udp")
     * @param configKey The DeploymentContext key to enable this port (e.g., "enableSmtp")
     * @param service Human-readable service name for logging/prompts
     * @param inbound true if port accepts inbound connections (requires security group rule),
     *                false if outbound only (container connects out, no SG rule needed)
     */
    record OptionalPort(int port, String protocol, String configKey, String service, boolean inbound) {
        /**
         * Convenience constructor for inbound TCP ports.
         */
        public static OptionalPort inboundTcp(int port, String configKey, String service) {
            return new OptionalPort(port, "tcp", configKey, service, true);
        }

        /**
         * Convenience constructor for outbound TCP ports (no security group rule needed).
         */
        public static OptionalPort outboundTcp(int port, String configKey, String service) {
            return new OptionalPort(port, "tcp", configKey, service, false);
        }
    }

    /**
     * Returns optional ports that can be enabled via deployment configuration.
     *
     * <p>These ports are NOT exposed by default. Users must set the corresponding
     * configKey to true in their deployment configuration to enable each port.</p>
     *
     * <p>Example implementation for Mattermost:</p>
     * <pre>{@code
     * @Override
     * public List<OptionalPort> optionalPorts() {
     *     return List.of(
     *         OptionalPort.outboundTcp(587, "enableSmtp", "SMTP Email"),
     *         OptionalPort.inboundTcp(8074, "enableClustering", "Cluster Gossip")
     *     );
     * }
     * }</pre>
     *
     * <p>User enables in deployment-context.json:</p>
     * <pre>{@code
     * {
     *   "enableSmtp": true,
     *   "enableClustering": true
     * }
     * }</pre>
     *
     * @return list of optional ports (empty by default - most apps only need primary port)
     */
    default List<OptionalPort> optionalPorts() {
        return List.of();
    }

    // ========== Plugin Metadata Methods ==========

    /**
     * Get the application category from the {@link ApplicationPlugin} annotation.
     *
     * @return the category (e.g., "cicd", "monitoring", "database")
     */
    default String category() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return "unknown";
        }
        return annotation.category();
    }

    /**
     * Get the human-readable display name for this application.
     *
     * @return the display name, defaulting to capitalized {@link #applicationId()} if not specified
     */
    default String displayName() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            String id = applicationId();
            return id.substring(0, 1).toUpperCase() + id.substring(1);
        }
        String displayName = annotation.displayName();
        if (displayName.isEmpty()) {
            String id = applicationId();
            return id.substring(0, 1).toUpperCase() + id.substring(1);
        }
        return displayName;
    }

    /**
     * Get the application description.
     *
     * @return the application description
     */
    default String description() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return "";
        }
        return annotation.description();
    }

    /**
     * Get the default Fargate CPU units.
     *
     * @return the default CPU units (256, 512, 1024, 2048, 4096)
     */
    default int defaultCpu() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return 1024; // Default 1 vCPU
        }
        return annotation.defaultCpu();
    }

    /**
     * Get the default Fargate memory in MB.
     *
     * @return the default memory in MB
     */
    default int defaultMemory() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return 2048; // Default 2GB
        }
        return annotation.defaultMemory();
    }

    /**
     * Get the default EC2 instance type.
     *
     * @return the default instance type (e.g., "t3.small")
     */
    default String defaultInstanceType() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return "t3.small";
        }
        return annotation.defaultInstanceType();
    }

    /**
     * Check if this application supports Fargate deployment.
     *
     * @return true if Fargate is supported
     */
    default boolean supportsFargate() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return true; // Default to supported
        }
        return annotation.supportsFargate();
    }

    /**
     * Check if this application supports EC2 deployment.
     *
     * @return true if EC2 is supported
     */
    default boolean supportsEc2() {
        ApplicationPlugin annotation = getClass().getAnnotation(ApplicationPlugin.class);
        if (annotation == null) {
            return true; // Default to supported
        }
        return annotation.supportsEc2();
    }

    /**
     * Get the recommended health check grace period for this application.
     *
     * <p>The grace period is how long ECS/ALB waits before starting health checks
     * after a container starts. Applications with longer initialization times
     * (like GitLab) need longer grace periods.</p>
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>Most applications: 300 seconds (5 minutes)</li>
     *   <li>GitLab: 600 seconds (10 minutes) - due to database migrations and initialization</li>
     *   <li>Other database-heavy apps may also need longer periods</li>
     * </ul>
     *
     * @return recommended health check grace period in seconds
     */
    default int defaultHealthCheckGracePeriod() {
        return 300; // Default 5 minutes for most applications
    }
}
