package com.cloudforgeci.api.application.collaboration;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MattermostOidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mattermost Enterprise Edition ApplicationSpec implementation.
 *
 * <p>Mattermost is an open-source, self-hosted team collaboration platform
 * similar to Slack.</p>
 *
 * <p><strong>Edition Information:</strong></p>
 * <p>This uses the Enterprise Edition image which runs in free mode by default.
 * Enterprise features (AD/LDAP group sync, compliance exports, etc.) are unlocked
 * by applying a license through System Console > Edition and License.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Team messaging and channels</li>
 *   <li>File sharing and search</li>
 *   <li>Integrations and webhooks</li>
 *   <li>Mobile and desktop apps</li>
 *   <li>End-to-end encryption (E2EE)</li>
 * </ul>
 *
 * <p><strong>Enterprise Features (require license):</strong></p>
 * <ul>
 *   <li>AD/LDAP group sync for automatic team/channel membership</li>
 *   <li>SAML 2.0 with group synchronization</li>
 *   <li>Compliance exports and eDiscovery</li>
 *   <li>Advanced access controls and permissions</li>
 *   <li>High availability clustering</li>
 * </ul>
 *
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>SOC2: Audit logs for team communications</li>
 *   <li>HIPAA: Secure messaging for healthcare teams</li>
 *   <li>GDPR: Data residency and user data controls</li>
 *   <li>FERPA: Secure communications for educational institutions</li>
 * </ul>
 *
 * <p><strong>Database Requirements:</strong></p>
 * <ul>
 *   <li><b>REQUIRED:</b> PostgreSQL 11+ or MySQL 8.0+ via RDS</li>
 *   <li>Mattermost does NOT support embedded databases for production</li>
 *   <li>Recommended: PostgreSQL with db.t3.small or larger</li>
 * </ul>
 *
 * <p><strong>Licensing:</strong></p>
 * <p>To activate Enterprise features:</p>
 * <ol>
 *   <li>Go to System Console > Edition and License</li>
 *   <li>Upload your license file or start a trial</li>
 *   <li>Enterprise features become available immediately</li>
 * </ol>
 *
 * @see <a href="https://docs.mattermost.com/">Mattermost Documentation</a>
 * @see <a href="https://docs.mattermost.com/about/editions-and-offerings.html">Editions and Offerings</a>
 */
@ApplicationPlugin(
    value = "mattermost-enterprise",
    category = "collaboration",
    displayName = "Mattermost Enterprise",
    description = "Team collaboration with native OIDC & single logout (requires license)",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = true,
    supportsDatabase = true,
    requiresDatabase = true
)

public class MattermostApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "mattermost-enterprise";
    // Enterprise Edition runs in free mode without a license
    // Users can activate enterprise features by uploading a license
    // Same database schema as Team Edition - no migration needed
    private static final String DEFAULT_IMAGE = "mattermost/mattermost-enterprise-edition:latest";
    private static final int APPLICATION_PORT = 8065;
    private static final String CONTAINER_DATA_PATH = "/mattermost/data";
    private static final String EFS_DATA_PATH = "/mattermost";
    private static final String VOLUME_NAME = "mattermostData";
    private static final String CONTAINER_USER = "2000:2000"; // mattermost user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/opt/mattermost/data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/opt/mattermost/logs/mattermost.log",
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
            // Email - outbound connections to SMTP servers
            OptionalPort.outboundTcp(587, "enableSmtp", "SMTP Email (STARTTLS)"),
            OptionalPort.outboundTcp(465, "enableSmtps", "SMTP Email (TLS)"),
            // Clustering - inbound for high availability deployments
            OptionalPort.inboundTcp(8074, "enableClustering", "Cluster Gossip"),
            OptionalPort.inboundTcp(8075, "enableClustering", "Cluster Gossip")
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
        return CONTAINER_USER;
    }

    @Override
    public DatabaseRequirement databaseRequirement() {
        // Mattermost 11.x REQUIRES PostgreSQL 14+ or MySQL 8.0+ for all deployments
        // See: https://docs.mattermost.com/install/software-hardware-requirements.html
        return DatabaseRequirement.required("postgres", "14")
            .withInstanceClass("db.t3.small")
            .withStorage(30)
            .withDatabaseName("mattermost");
    }

    @Override
    public Map<String, String> databaseParameters() {
        // PostgreSQL optimization for Mattermost workload
        return Map.of(
            "max_connections", "200",
            "shared_buffers", "{DBInstanceClassMemory/4096}",
            "work_mem", "8MB",
            "maintenance_work_mem", "128MB",
            "log_statement", "ddl"
        );
    }

    @Override
    public int backupRetentionDays() {
        return 14; // Mattermost contains team communications
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>Configures Mattermost to use RDS PostgreSQL. Mattermost REQUIRES a database
     * and does not support embedded databases.</p>
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        // Configure site URL for OAuth callbacks and webhooks
        if (fqdn != null && !fqdn.isBlank()) {
            String siteUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("MM_SERVICESETTINGS_SITEURL", siteUrl);
        }

        // Proxy/Load Balancer configuration - CRITICAL for ALB deployments
        // Trust X-Forwarded-For and X-Real-IP headers from ALB
        // Without this, Mattermost shows internal IPs in security audit logs
        environment.put("MM_SERVICESETTINGS_TRUSTEDPROXYIPHEADER", "X-Forwarded-For,X-Real-IP");
        // Forward the original client IP from ALB
        environment.put("MM_SERVICESETTINGS_FORWARD80TO443", "false");  // ALB handles HTTPS termination
        // Enable using websocket headers through proxy
        environment.put("MM_SERVICESETTINGS_WEBSOCKETURL", "");  // Auto-derive from SiteURL

        // Database configuration (REQUIRED for Mattermost)
        // NOTE: Mattermost is distroless (no shell) so we can't use env var substitution
        // The MM_SQLSETTINGS_DATASOURCE is injected by ContainerFactory from an SSM Parameter
        // that RdsFactory creates with the complete connection string (including password via dynamic reference)
        if (dbConn != null) {
            // Use RDS PostgreSQL - just set the driver name
            // The full datasource URL is injected as MM_SQLSETTINGS_DATASOURCE by ContainerFactory
            environment.put("MM_SQLSETTINGS_DRIVERNAME", "postgres");
        } else {
            // NOTE: Mattermost REQUIRES a database - this should never happen
            // Set placeholder that will fail fast if database is missing
            environment.put("MM_SQLSETTINGS_DRIVERNAME", "postgres");
            environment.put("MM_SQLSETTINGS_DATASOURCE", "postgres://MISSING_DATABASE_CONNECTION");
        }

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

        // Install CloudWatch Agent
        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

        // Mount storage
        String[] userParts = containerUser().split(":");
        String uid = userParts[0];
        String gid = userParts[1];

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

        // Create directory structure
        builder.addCommands(
            "# Create Mattermost directories",
            "mkdir -p /opt/mattermost/config",
            "mkdir -p /opt/mattermost/logs",
            "mkdir -p /opt/mattermost/plugins",
            "mkdir -p /opt/mattermost/client/plugins",
            "",
            "# Set ownership",
            "chown -R " + uid + ":" + gid + " /opt/mattermost"
        );

        // Run Mattermost container
        builder.addCommands(
            "# Retrieve database credentials from Secrets Manager",
            "MM_DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id ${STACK_NAME:-mattermost}/db-password --query SecretString --output text 2>/dev/null || echo 'CONFIGURE_DB_PASSWORD')",
            "",
            "# Run Mattermost container",
            "# Note: Requires PostgreSQL database",
            "docker run -d \\",
            "  --name mattermost \\",
            "  -p 8065:8065 \\",
            "  -v " + ec2DataPath() + ":/mattermost/data \\",
            "  -v /opt/mattermost/config:/mattermost/config \\",
            "  -v /opt/mattermost/logs:/mattermost/logs \\",
            "  -v /opt/mattermost/plugins:/mattermost/plugins \\",
            "  -v /opt/mattermost/client/plugins:/mattermost/client/plugins \\",
            "  -e MM_SQLSETTINGS_DRIVERNAME=postgres \\",
            "  -e MM_SQLSETTINGS_DATASOURCE=\"postgres://mattermost:$MM_DB_PASSWORD@postgres:5432/mattermost?sslmode=disable&connect_timeout=10\" \\",
            "  -e MM_SERVICESETTINGS_SITEURL=http://mattermost.example.com \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Mattermost container started' >> /var/log/userdata.log",
            "",
            "# Wait for Mattermost to start",
            "sleep 15",
            "echo 'Mattermost should be available on port 8065' >> /var/log/userdata.log",
            "echo 'Configure PostgreSQL connection in System Console' >> /var/log/userdata.log",
            "",
            "cat >> /var/log/userdata.log <<'INSTRUCTIONS'",
            "================================================================================",
            "MATTERMOST POST-DEPLOYMENT SETUP",
            "================================================================================",
            "",
            "1. Create the first admin user:",
            "   - Navigate to http://mattermost.example.com:8065",
            "   - Create admin account through the web interface",
            "",
            "2. Configure database (if not using container link):",
            "   - Go to System Console > Database",
            "   - Set PostgreSQL connection string",
            "",
            "3. Configure OIDC/SAML (Enterprise Edition):",
            "   - System Console > Authentication > OpenID Connect",
            "   - Or: Authentication > SAML 2.0",
            "",
            "4. Enable compliance features:",
            "   - System Console > Compliance",
            "   - Enable compliance exports",
            "   - Configure data retention policies",
            "",
            "5. Security hardening:",
            "   - Enable multi-factor authentication (MFA)",
            "   - Configure session lengths",
            "   - Set password requirements",
            "   - Enable audit logging",
            "================================================================================",
            "INSTRUCTIONS"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        // Return OIDC integration by default
        // For SAML support (cognito-saml or identity-center), use MattermostSamlIntegration
        // OIDC: Simple OAuth 2.0 flow, works directly with Cognito
        // SAML: Requires AD/LDAP for group sync, supports cognito-saml (Keycloak) or identity-center
        return new MattermostOidcIntegration();
    }

    @Override
    public String toString() {
        return "MattermostApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}

