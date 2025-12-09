package com.cloudforgeci.api.application.cicd;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.GitLabOidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab ApplicationSpec implementation.
 *
 * <p>GitLab is a complete DevOps platform with Git repository, CI/CD, and collaboration features.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Git repository management</li>
 *   <li>Built-in CI/CD pipelines</li>
 *   <li>Issue tracking and project management</li>
 *   <li>Container registry</li>
 *   <li>Security scanning</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li><b>Production:</b> PostgreSQL 12+ via RDS (REQUIRED for multi-instance)</li>
 *   <li><b>Development:</b> Embedded PostgreSQL (single instance only)</li>
 *   <li>Redis for caching (included in GitLab container)</li>
 *   <li>Minimum 4GB RAM recommended</li>
 * </ul>
 *
 * <p><strong>Database Configuration:</strong></p>
 * <ul>
 *   <li>GitLab REQUIRES PostgreSQL for production deployments</li>
 *   <li>Embedded database only suitable for development/staging (single instance)</li>
 *   <li>RDS recommended: db.t3.medium or larger with Multi-AZ</li>
 * </ul>
 *
 * @see <a href="https://docs.gitlab.com/">GitLab Documentation</a>
 */
@ApplicationPlugin(
    value = "gitlab",
    category = "cicd",
    displayName = "GitLab",
    description = "Complete DevOps platform with Git, CI/CD, and security",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = true,
    supportsDatabase = true,
    requiresDatabase = true
)
public class GitLabApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "gitlab";
    private static final String DEFAULT_IMAGE = "gitlab/gitlab-ce:latest";
    private static final int APPLICATION_PORT = 80;
    private static final int SSH_PORT = 22;
    private static final String CONTAINER_DATA_PATH = "/var/opt/gitlab";
    private static final String EFS_DATA_PATH = "/gitlab";
    private static final String VOLUME_NAME = "gitlabData";
    // GitLab runs as root - no CONTAINER_USER restriction needed
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/opt/gitlab";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/gitlab/gitlab-rails/production.log",
        "/var/log/gitlab/gitlab-rails/api_json.log",
        "/var/log/gitlab/puma/puma_stderr.log",  // Puma replaced Unicorn in GitLab 13.0+
        "/var/log/userdata.log"
    );

    @Override
    public String applicationId() {
        return APPLICATION_ID;
    }

    @Override
    public String defaultContainerImage() {
        return DEFAULT_IMAGE;
    }

    @Override
    public int applicationPort() {
        return APPLICATION_PORT;
    }

    @Override
    public java.util.List<OptionalPort> optionalPorts() {
        return java.util.List.of(
            // Git over SSH - inbound for repository access
            OptionalPort.inboundTcp(22, "enableSsh", "Git SSH"),
            // Container Registry - inbound for Docker image hosting
            OptionalPort.inboundTcp(5050, "enableRegistry", "Container Registry"),
            // Prometheus metrics - inbound for monitoring integration
            OptionalPort.inboundTcp(9090, "enableMetrics", "Prometheus Metrics")
        );
    }

    @Override
    public String containerDataPath() {
        return CONTAINER_DATA_PATH;
    }

    @Override
    public String efsDataPath() {
        return EFS_DATA_PATH;
    }

    @Override
    public String volumeName() {
        return VOLUME_NAME;
    }

    @Override
    public String containerUser() {
        // GitLab container needs to run as root to properly initialize
        // Returning null will prevent setting the user in ContainerFactory
        return null;
    }

    @Override
    public DatabaseRequirement databaseRequirement() {
        // GitLab REQUIRES PostgreSQL 16+ for production deployments
        // Embedded PostgreSQL only suitable for dev/staging (single instance)
        return DatabaseRequirement.required("postgres", "16")
            .withInstanceClass("db.t3.medium")
            .withStorage(50)
            .withDatabaseName("gitlabhq_production");
    }

    @Override
    public Map<String, String> databaseParameters() {
        // PostgreSQL optimization for GitLab workload
        return Map.of(
            "max_connections", "300",
            "shared_buffers", "{DBInstanceClassMemory/4096}",
            "effective_cache_size", "{DBInstanceClassMemory*3/4096}",
            "work_mem", "16MB",
            "maintenance_work_mem", "256MB",
            "random_page_cost", "1.1",
            "log_statement", "ddl"
        );
    }

    @Override
    public int backupRetentionDays() {
        return 30; // GitLab contains critical repository data
    }

    @Override
    public String healthCheckPath() {
        // Use /users/sign_in for health checks - this ensures Rails is actually responding
        // This returns 200 when GitLab is fully initialized
        // Combined with 600s grace period, this prevents premature health check failures
        return "/users/sign_in";
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>If database connection is provided, configures GitLab to use RDS PostgreSQL.
     * Otherwise, falls back to embedded PostgreSQL (single instance only).</p>
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        // Build GitLab Omnibus configuration
        StringBuilder omnibusConfig = new StringBuilder();

        // GITLAB_OMNIBUS_CONFIG is CRITICAL for GitLab to work properly behind a reverse proxy
        // This configures the external URL for OAuth callbacks, webhooks, and user-facing URLs
        if (fqdn != null && !fqdn.isBlank()) {
            String externalUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            omnibusConfig.append(String.format("external_url '%s'; ", externalUrl));
        }

        // NGINX reverse proxy configuration
        omnibusConfig.append("nginx['listen_port'] = 80; ");
        omnibusConfig.append("nginx['listen_https'] = false; ");
        omnibusConfig.append("gitlab_rails['gitlab_shell_ssh_port'] = 22; ");

        // Trust X-Forwarded-* headers from ALB for proper IP logging
        // Without this, GitLab shows internal container IPs in audit logs
        omnibusConfig.append("nginx['real_ip_header'] = 'X-Forwarded-For'; ");
        omnibusConfig.append("nginx['real_ip_recursive'] = 'on'; ");
        // Trust all private IP ranges (ALB uses VPC internal IPs)
        omnibusConfig.append("nginx['real_ip_trusted_addresses'] = ['10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16']; ");
        // Detect HTTPS from ALB via X-Forwarded-Proto
        omnibusConfig.append("nginx['proxy_set_headers'] = { 'X-Forwarded-Proto' => 'https', 'X-Forwarded-Ssl' => 'on' }; ");

        // Redis configuration - enable embedded Redis for caching and background jobs
        omnibusConfig.append("redis['enable'] = true; ");

        // Monitoring whitelist - allow health checks from ALB without token
        // This allows /-/health, /-/readiness, /-/liveness to be accessed from any IP
        omnibusConfig.append("gitlab_rails['monitoring_whitelist'] = ['0.0.0.0/0', '::/0']; ");

        // Database configuration
        if (dbConn != null) {
            // Use RDS PostgreSQL for production multi-instance deployments
            omnibusConfig.append("postgresql['enable'] = false; "); // Disable embedded PostgreSQL
            omnibusConfig.append(String.format("gitlab_rails['db_adapter'] = 'postgresql'; "));
            omnibusConfig.append(String.format("gitlab_rails['db_host'] = '%s'; ", dbConn.endpoint()));
            omnibusConfig.append(String.format("gitlab_rails['db_port'] = %d; ", dbConn.port()));
            omnibusConfig.append(String.format("gitlab_rails['db_database'] = '%s'; ", dbConn.databaseName()));
            omnibusConfig.append(String.format("gitlab_rails['db_username'] = '%s'; ", dbConn.username()));

            // Password is injected via ECS secret as GITLAB_DATABASE_PASSWORD environment variable
            // ContainerFactory adds this to ecsSecrets from Secrets Manager
            omnibusConfig.append("gitlab_rails['db_password'] = ENV['GITLAB_DATABASE_PASSWORD']; ");
        } else {
            // Fallback to embedded PostgreSQL (single instance only)
            // NOTE: Embedded PostgreSQL cannot support multiple instances
            omnibusConfig.append("postgresql['enable'] = true; ");
        }

        environment.put("GITLAB_OMNIBUS_CONFIG", omnibusConfig.toString());

        return environment;
    }

    @Override
    public String efsPermissions() {
        return EFS_PERMISSIONS;
    }

    @Override
    public String ebsDeviceName() {
        return EBS_DEVICE_NAME;
    }

    @Override
    public String ec2DataPath() {
        return EC2_DATA_PATH;
    }

    @Override
    public List<String> ec2LogPaths() {
        return EC2_LOG_PATHS;
    }

    @Override
    public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        builder.addSystemUpdate();

        // Install Docker
        builder.addCommands(
            "# Install Docker",
            "yum install -y docker",
            "systemctl enable docker",
            "systemctl start docker",
            "echo 'Docker installed' >> /var/log/userdata.log"
        );

        // Install and configure CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage (EFS or EBS)
        // GitLab runs as root, so use 0:0 for ownership
        String uid = "0";
        String gid = "0";

        if (context.hasEfs()) {
            builder.mountEfs(
                context.efsId().orElseThrow(),
                context.accessPointId().orElseThrow(),
                ec2DataPath(),
                uid,
                gid
            );
        } else {
            builder.mountEbs(
                ebsDeviceName(),
                ec2DataPath(),
                uid,
                gid
            );
        }

        // Run GitLab container
        builder.addCommands(
            "# Run GitLab container",
            "docker run -d \\",
            "  --name gitlab \\",
            "  --hostname gitlab.example.com \\",
            "  -p 80:80 -p 443:443 -p 22:22 \\",
            "  -v " + ec2DataPath() + "/config:/etc/gitlab \\",
            "  -v " + ec2DataPath() + "/logs:/var/log/gitlab \\",
            "  -v " + ec2DataPath() + "/data:/var/opt/gitlab \\",
            "  " + DEFAULT_IMAGE,
            "echo 'GitLab container started' >> /var/log/userdata.log"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new GitLabOidcIntegration();
    }

    @Override
    public int defaultHealthCheckGracePeriod() {
        // GitLab requires longer grace period due to:
        // - Database migrations on startup
        // - Asset compilation
        // - Service initialization (PostgreSQL, Redis, Puma/Unicorn)
        // - First-time setup can take 15+ minutes
        return 900; // 15 minutes
    }

    @Override
    public String toString() {
        return "GitLabApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                ", sshPort=" + SSH_PORT +
                '}';
    }
}
