package com.cloudforgeci.api.application.analytics;

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
 * Metabase Business Intelligence ApplicationSpec implementation.
 *
 * <p>Metabase is an open-source business intelligence tool for interactive dashboards
 * and data visualization.</p>
 *
 * <p><b>Database Configuration:</b></p>
 * <ul>
 *   <li><b>Development/Staging:</b> H2 embedded database (single instance only)</li>
 *   <li><b>Production:</b> Use RDS PostgreSQL/MySQL for multi-instance deployments</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> H2 database cannot support multiple instances due to file locking.
 * For high availability, configure {@code maxInstanceCapacity=1} or use external RDS database.</p>
 *
 * <p><b>RDS Configuration (Production):</b></p>
 * Set environment variables:
 * <ul>
 *   <li>MB_DB_TYPE=postgres</li>
 *   <li>MB_DB_HOST=your-rds-endpoint.rds.amazonaws.com</li>
 *   <li>MB_DB_PORT=5432</li>
 *   <li>MB_DB_DBNAME=metabase</li>
 *   <li>MB_DB_USER=metabase_user</li>
 *   <li>MB_DB_PASS=from-secrets-manager</li>
 * </ul>
 *
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>SOC2: Audit log analytics and security metrics</li>
 *   <li>GDPR: Data subject access request reporting</li>
 *   <li>PCI-DSS: Transaction monitoring dashboards</li>
 *   <li>Fintech: Fraud detection, revenue metrics, compliance reporting</li>
 * </ul>
 *
 * @see <a href="https://www.metabase.com/docs/latest/">Metabase Documentation</a>
 */
@ApplicationPlugin(
    value = "metabase",
    category = "analytics",
    displayName = "Metabase",
    description = "Business intelligence and analytics platform",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false,
    supportsDatabase = true,
    requiresDatabase = false
)

public class MetabaseApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "metabase";
    private static final String DEFAULT_IMAGE = "metabase/metabase:latest";
    private static final int APPLICATION_PORT = 3000;
    private static final String CONTAINER_DATA_PATH = "/metabase-data";
    private static final String EFS_DATA_PATH = "/metabase";
    private static final String VOLUME_NAME = "metabaseData";
    private static final String CONTAINER_USER = "2000:2000"; // metabase user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/opt/metabase/data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/opt/metabase/logs/metabase.log",
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
        // Metabase can use H2 (embedded) OR PostgreSQL/MySQL (RDS)
        return DatabaseRequirement.optional("postgres", "15")
            .withInstanceClass("db.t3.small")
            .withStorage(20)
            .withDatabaseName("metabase");
    }

    @Override
    public java.util.Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>If database connection is provided, configures Metabase to use RDS PostgreSQL.
     * Otherwise, falls back to H2 embedded database (single instance only).</p>
     */
    public java.util.Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        java.util.Map<String, String> environment = new HashMap<>();

        // MB_SITE_URL is CRITICAL for Metabase to work properly behind a reverse proxy
        // Without this, Metabase generates incorrect URLs for emails, embeds, OAuth redirects, etc.
        if (fqdn != null && !fqdn.isBlank()) {
            String siteUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("MB_SITE_URL", siteUrl);
        }

        // Database configuration
        if (dbConn != null) {
            // Use RDS PostgreSQL for production multi-instance deployments
            environment.put("MB_DB_TYPE", "postgres");
            environment.put("MB_DB_HOST", dbConn.endpoint());
            environment.put("MB_DB_PORT", String.valueOf(dbConn.port()));
            environment.put("MB_DB_DBNAME", dbConn.databaseName());
            environment.put("MB_DB_USER", dbConn.username());
            // Password retrieved from Secrets Manager at runtime
            environment.put("MB_DB_PASS", "{{resolve:secretsmanager:" + dbConn.passwordSecretArn() + ":SecretString:password}}");
        } else {
            // Fallback to H2 embedded database (single instance only)
            // NOTE: H2 is embedded and cannot support multiple instances - use RDS for production
            environment.put("MB_DB_TYPE", "h2");
            environment.put("MB_DB_FILE", CONTAINER_DATA_PATH + "/metabase.db");
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
            "# Create Metabase directories",
            "mkdir -p /opt/metabase/logs",
            "mkdir -p /opt/metabase/plugins",
            "",
            "# Set ownership",
            "chown -R " + uid + ":" + gid + " /opt/metabase"
        );

        // Run Metabase container
        builder.addCommands(
            "# Run Metabase container",
            "docker run -d \\",
            "  --name metabase \\",
            "  -p 3000:3000 \\",
            "  -v " + ec2DataPath() + ":/metabase-data \\",
            "  -v /opt/metabase/plugins:/plugins \\",
            "  -e MB_DB_TYPE=h2 \\",
            "  -e MB_DB_FILE=/metabase-data/metabase.db \\",
            "  -e MB_JETTY_HOST=0.0.0.0 \\",
            "  -e MB_JETTY_PORT=3000 \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Metabase container started' >> /var/log/userdata.log",
            "",
            "# Wait for Metabase to start",
            "sleep 30",
            "echo 'Metabase should be available on port 3000' >> /var/log/userdata.log",
            "",
            "cat >> /var/log/userdata.log <<'INSTRUCTIONS'",
            "================================================================================",
            "METABASE POST-DEPLOYMENT SETUP",
            "================================================================================",
            "",
            "1. Complete initial setup:",
            "   - Navigate to http://metabase.example.com:3000",
            "   - Create admin account",
            "   - Configure language and region",
            "",
            "2. Connect to databases:",
            "   - Add PostgreSQL, MySQL, or other data sources",
            "   - Use read-only credentials for security",
            "",
            "3. Configure SAML/OIDC (Enterprise Edition):",
            "   - Admin > Settings > Authentication",
            "   - Or use JWT for embedding",
            "",
            "4. Security hardening:",
            "   - Enable SSL/TLS",
            "   - Configure session timeout",
            "   - Set up row-level permissions",
            "   - Enable audit logging (Enterprise)",
            "",
            "5. For production, use PostgreSQL instead of H2:",
            "   docker run -d \\",
            "     --name metabase \\",
            "     -e MB_DB_TYPE=postgres \\",
            "     -e MB_DB_DBNAME=metabase \\",
            "     -e MB_DB_PORT=5432 \\",
            "     -e MB_DB_USER=metabase \\",
            "     -e MB_DB_PASS=changeme \\",
            "     -e MB_DB_HOST=postgres.example.com \\",
            "     metabase/metabase",
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
        // Metabase Enterprise has SAML/OIDC support
        // Open-source version supports JWT for embedding
        return null;
    }

    @Override
    public String toString() {
        return "MetabaseApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
