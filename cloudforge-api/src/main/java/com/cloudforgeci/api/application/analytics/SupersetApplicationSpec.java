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
 * Apache Superset Business Intelligence ApplicationSpec implementation.
 *
 * <p>Superset is a modern data exploration and visualization platform originally
 * developed at Airbnb.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Rich interactive visualizations</li>
 *   <li>SQL IDE with syntax highlighting</li>
 *   <li>Semantic layer for defining custom dimensions and metrics</li>
 *   <li>Support for most SQL-speaking databases</li>
 *   <li>Extensible security model</li>
 * </ul>
 *
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>SOC2: Security event analytics and metrics dashboards</li>
 *   <li>GDPR: Data subject rights request tracking</li>
 *   <li>PCI-DSS: Transaction monitoring and anomaly detection</li>
 * </ul>
 *
 * <p><strong>Fintech Applications:</strong></p>
 * <ul>
 *   <li>Real-time payment transaction dashboards</li>
 *   <li>Fraud detection and risk analytics</li>
 *   <li>Financial performance metrics and KPIs</li>
 *   <li>Customer lifetime value (CLV) analysis</li>
 *   <li>Regulatory reporting and compliance dashboards</li>
 * </ul>
 *
 * <p><strong>Database Requirements:</strong></p>
 * <ul>
 *   <li><b>REQUIRED:</b> PostgreSQL 10+ or MySQL 5.7+ for metadata storage</li>
 *   <li>Superset does NOT support SQLite for production (single-process limitation)</li>
 *   <li>Recommended: PostgreSQL with db.t3.small or larger</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Use database read-only credentials for analytics data sources</li>
 *   <li>Enable OIDC/LDAP for authentication</li>
 *   <li>Configure row-level security (RLS)</li>
 *   <li>Enable audit logging</li>
 * </ul>
 *
 * @see <a href="https://superset.apache.org/docs/intro">Superset Documentation</a>
 */
@ApplicationPlugin(
    value = "superset",
    category = "analytics",
    displayName = "Superset",
    description = "Data exploration and visualization platform",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false,
    supportsDatabase = true,
    requiresDatabase = true
)

public class SupersetApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "superset";
    private static final String DEFAULT_IMAGE = "apache/superset:latest";
    private static final int APPLICATION_PORT = 8088;
    private static final String CONTAINER_DATA_PATH = "/app/superset_home";
    private static final String EFS_DATA_PATH = "/superset";
    private static final String VOLUME_NAME = "supersetData";
    private static final String CONTAINER_USER = "0:0"; // runs as root in container
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/opt/superset/data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/opt/superset/logs/superset.log",
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
        // Superset REQUIRES PostgreSQL/MySQL for metadata storage
        // SQLite is not suitable for production (single-process limitation)
        return DatabaseRequirement.required("postgres", "13")
            .withInstanceClass("db.t3.small")
            .withStorage(20)
            .withDatabaseName("superset");
    }

    @Override
    public Map<String, String> databaseParameters() {
        // PostgreSQL optimization for Superset metadata workload
        return Map.of(
            "max_connections", "150",
            "shared_buffers", "{DBInstanceClassMemory/4096}",
            "work_mem", "8MB",
            "log_statement", "ddl"
        );
    }

    @Override
    public int backupRetentionDays() {
        return 14; // Superset contains dashboards and analytics metadata
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>Configures Superset to use RDS PostgreSQL for metadata storage.
     * Superset REQUIRES a database for production deployments.</p>
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        // Superset secret key for session encryption
        environment.put("SUPERSET_SECRET_KEY", "CHANGE_THIS_TO_A_LONG_RANDOM_STRING");

        // Database configuration (REQUIRED for Superset)
        if (dbConn != null) {
            // Use RDS PostgreSQL for metadata storage
            environment.put("DATABASE_DIALECT", "postgresql");
            environment.put("DATABASE_HOST", dbConn.endpoint());
            environment.put("DATABASE_PORT", String.valueOf(dbConn.port()));
            environment.put("DATABASE_DB", dbConn.databaseName());
            environment.put("DATABASE_USER", dbConn.username());
            // Password is injected via ECS secret as GITLAB_DATABASE_PASSWORD
            // Don't set DATABASE_PASSWORD here - it will be set by ECS from Secrets Manager

            // Build SQLAlchemy connection string using environment variable for password
            // Password will be injected at runtime by ECS from Secrets Manager
            String password = "${GITLAB_DATABASE_PASSWORD}";
            String sqlalchemyUri = String.format(
                "postgresql://%s:%s@%s:%d/%s",
                dbConn.username(),
                password,
                dbConn.endpoint(),
                dbConn.port(),
                dbConn.databaseName()
            );
            environment.put("SQLALCHEMY_DATABASE_URI", sqlalchemyUri);
        } else {
            // NOTE: Superset REQUIRES a database - this should never happen
            // Set placeholder that will fail fast if database is missing
            environment.put("DATABASE_DIALECT", "postgresql");
            environment.put("SQLALCHEMY_DATABASE_URI", "postgresql://MISSING_DATABASE_CONNECTION");
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

        // Install Docker and Docker Compose
        builder.addCommands(
            "# Install Docker",
            "yum install -y docker",
            "systemctl enable docker",
            "systemctl start docker",
            "echo 'Docker installed' >> /var/log/userdata.log",
            "",
            "# Install Docker Compose",
            "curl -L \"https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)\" -o /usr/local/bin/docker-compose",
            "chmod +x /usr/local/bin/docker-compose",
            "echo 'Docker Compose installed' >> /var/log/userdata.log"
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
            "# Create Superset directories",
            "mkdir -p /opt/superset/logs",
            "mkdir -p /opt/superset/config",
            "",
            "# Create Superset configuration",
            "cat > /opt/superset/config/superset_config.py <<'EOF'",
            "# Superset Configuration",
            "",
            "import os",
            "",
            "# Flask App Builder configuration",
            "ROW_LIMIT = 5000",
            "",
            "# Flask Secret Key - CHANGE THIS!",
            "SECRET_KEY = 'changeme_use_a_secure_random_key'",
            "",
            "# SQLAlchemy database URI for Superset metadata",
            "SQLALCHEMY_DATABASE_URI = 'sqlite:////app/superset_home/superset.db'",
            "",
            "# For production, use PostgreSQL:",
            "# SQLALCHEMY_DATABASE_URI = 'postgresql://superset:password@postgres:5432/superset'",
            "",
            "# Superset specific config",
            "WTF_CSRF_ENABLED = True",
            "",
            "# Set this API key to enable Mapbox visualizations",
            "MAPBOX_API_KEY = os.getenv('MAPBOX_API_KEY', '')",
            "EOF"
        );

        // Run Superset container
        builder.addCommands(
            "# Run Superset container",
            "docker run -d \\",
            "  --name superset \\",
            "  -p 8088:8088 \\",
            "  -v " + ec2DataPath() + ":/app/superset_home \\",
            "  -v /opt/superset/config:/app/docker \\",
            "  -e SUPERSET_SECRET_KEY=changeme_use_a_secure_random_key \\",
            "  " + DEFAULT_IMAGE,
            "",
            "# Wait for container to start",
            "sleep 10",
            "",
            "# Initialize Superset database",
            "docker exec superset superset db upgrade",
            "",
            "# Create admin user",
            "docker exec superset superset fab create-admin \\",
            "  --username admin \\",
            "  --firstname Admin \\",
            "  --lastname User \\",
            "  --email admin@example.com \\",
            "  --password admin",
            "",
            "# Initialize Superset",
            "docker exec superset superset init",
            "",
            "echo 'Superset initialization complete' >> /var/log/userdata.log",
            "echo 'Superset should be available on port 8088' >> /var/log/userdata.log",
            "",
            "cat >> /var/log/userdata.log <<'INSTRUCTIONS'",
            "================================================================================",
            "SUPERSET POST-DEPLOYMENT SETUP",
            "================================================================================",
            "",
            "1. Access Superset:",
            "   - Navigate to http://superset.example.com:8088",
            "   - Login with: admin / admin",
            "   - CHANGE THE PASSWORD IMMEDIATELY!",
            "",
            "2. Connect to databases:",
            "   - Go to Data > Databases",
            "   - Add PostgreSQL, MySQL, or other data sources",
            "   - Use read-only credentials for analytics queries",
            "",
            "3. Configure OIDC authentication:",
            "   - Edit superset_config.py",
            "   - Configure Flask-OIDC or Flask-AppBuilder OIDC",
            "",
            "4. Enable row-level security:",
            "   - Go to Security > Row Level Security",
            "   - Define RLS filters for sensitive data",
            "",
            "5. Security hardening:",
            "   - Change SECRET_KEY in superset_config.py",
            "   - Use PostgreSQL instead of SQLite for metadata",
            "   - Enable HTTPS/TLS",
            "   - Configure CORS if embedding dashboards",
            "",
            "6. Production configuration:",
            "   docker run -d \\",
            "     --name superset \\",
            "     -e SUPERSET_SECRET_KEY='your-production-key' \\",
            "     -e DATABASE_DB=superset \\",
            "     -e DATABASE_HOST=postgres.example.com \\",
            "     -e DATABASE_PASSWORD=changeme \\",
            "     -e DATABASE_USER=superset \\",
            "     apache/superset",
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
        // Superset supports OIDC via Flask-AppBuilder
        // Implementation would configure superset_config.py with OIDC settings
        return null;
    }

    @Override
    public String toString() {
        return "SupersetApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
