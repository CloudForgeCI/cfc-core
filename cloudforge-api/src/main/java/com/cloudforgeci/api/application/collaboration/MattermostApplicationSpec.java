package com.cloudforgeci.api.application.collaboration;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mattermost Team Collaboration ApplicationSpec implementation.
 *
 * <p>Mattermost is an open-source, self-hosted team collaboration platform
 * similar to Slack.</p>
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
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>SOC2: Audit logs for team communications</li>
 *   <li>HIPAA: Secure messaging for healthcare teams</li>
 *   <li>GDPR: Data residency and user data controls</li>
 *   <li>FERPA: Secure communications for educational institutions</li>
 * </ul>
 *
 * <p><strong>Fintech Applications:</strong></p>
 * <ul>
 *   <li>Secure team communications for financial services</li>
 *   <li>Integration with trading platforms and alerts</li>
 *   <li>Compliance-friendly messaging alternative to Slack</li>
 *   <li>Bot integrations for payment notifications</li>
 * </ul>
 *
 * <p><strong>Database Requirements:</strong></p>
 * <ul>
 *   <li><b>REQUIRED:</b> PostgreSQL 11+ or MySQL 8.0+ via RDS</li>
 *   <li>Mattermost does NOT support embedded databases for production</li>
 *   <li>Recommended: PostgreSQL with db.t3.small or larger</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Enable SAML/OIDC for SSO</li>
 *   <li>Configure data retention policies</li>
 *   <li>Enable audit logging</li>
 *   <li>Use TLS/SSL for all connections</li>
 * </ul>
 *
 * @see <a href="https://docs.mattermost.com/">Mattermost Documentation</a>
 */
@ApplicationPlugin(
    value = "mattermost",
    category = "collaboration",
    displayName = "Mattermost",
    description = "Team collaboration and messaging platform",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false,
    supportsDatabase = true,
    requiresDatabase = true
)

public class MattermostApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "mattermost";
    private static final String DEFAULT_IMAGE = "mattermost/mattermost-team-edition:latest";
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
        // Mattermost REQUIRES PostgreSQL or MySQL for all deployments
        return DatabaseRequirement.required("postgres", "13")
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

        // Database configuration (REQUIRED for Mattermost)
        if (dbConn != null) {
            // Use RDS PostgreSQL
            environment.put("MM_SQLSETTINGS_DRIVERNAME", "postgres");

            // Build PostgreSQL connection string
            // Format: postgres://user:password@host:port/database?sslmode=require
            // Password is injected via ECS secret as GITLAB_DATABASE_PASSWORD
            String password = "${GITLAB_DATABASE_PASSWORD}";
            String dataSource = String.format(
                "postgres://%s:%s@%s:%d/%s?sslmode=require&connect_timeout=10",
                dbConn.username(),
                password,
                dbConn.endpoint(),
                dbConn.port(),
                dbConn.databaseName()
            );
            environment.put("MM_SQLSETTINGS_DATASOURCE", dataSource);
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
            "  -e MM_SQLSETTINGS_DATASOURCE='postgres://mattermost:changeme@postgres:5432/mattermost?sslmode=disable&connect_timeout=10' \\",
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
        // Mattermost has built-in OIDC support
        // Implementation would configure config.json with OIDC settings
        return null;
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
