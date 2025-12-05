package com.cloudforgeci.api.application.monitoring;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.GrafanaOidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grafana ApplicationSpec implementation.
 *
 * <p>Grafana is an open-source platform for monitoring and observability.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Metrics visualization and dashboards</li>
 *   <li>Multiple data source support (Prometheus, InfluxDB, etc.)</li>
 *   <li>Alerting and notifications</li>
 *   <li>Plugin ecosystem</li>
 * </ul>
 *
 * <p><strong>Database Configuration:</strong></p>
 * <ul>
 *   <li><b>Development/Staging:</b> SQLite embedded database (single instance only)</li>
 *   <li><b>Production:</b> PostgreSQL/MySQL via RDS for multi-instance deployments</li>
 *   <li>SQLite cannot support multiple instances due to file locking</li>
 * </ul>
 *
 * @see <a href="https://grafana.com/docs/grafana/latest/">Grafana Documentation</a>
 */
@ApplicationPlugin(
    value = "grafana",
    category = "monitoring",
    displayName = "Grafana",
    description = "Metrics visualization and observability platform",
    defaultCpu = 512,
    defaultMemory = 1024,
    defaultInstanceType = "t3.micro",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = true,
    supportsDatabase = true,
    requiresDatabase = false
)
public class GrafanaApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "grafana";
    private static final String DEFAULT_IMAGE = "grafana/grafana:latest";
    private static final int APPLICATION_PORT = 3000;
    private static final String CONTAINER_DATA_PATH = "/var/lib/grafana";
    private static final String EFS_DATA_PATH = "/grafana";
    private static final String VOLUME_NAME = "grafanaData";
    private static final String CONTAINER_USER = "472:472"; // grafana user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/grafana";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/grafana/grafana.log",
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
        // Grafana can use SQLite (embedded) OR PostgreSQL/MySQL (RDS)
        return DatabaseRequirement.optional("postgres", "14")
            .withInstanceClass("db.t3.micro")
            .withStorage(20)
            .withDatabaseName("grafana");
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>If database connection is provided, configures Grafana to use RDS PostgreSQL.
     * Otherwise, falls back to SQLite embedded database (single instance only).</p>
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        // GF_SERVER_ROOT_URL and GF_SERVER_DOMAIN are CRITICAL for Grafana OAuth and public dashboards
        // Without these, OAuth callbacks and embedded dashboards won't work properly
        if (fqdn != null && !fqdn.isBlank()) {
            String rootUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("GF_SERVER_ROOT_URL", rootUrl);
            environment.put("GF_SERVER_DOMAIN", fqdn);
        }

        // Database configuration
        if (dbConn != null) {
            // Use RDS PostgreSQL for production multi-instance deployments
            environment.put("GF_DATABASE_TYPE", "postgres");
            environment.put("GF_DATABASE_HOST", dbConn.endpoint() + ":" + dbConn.port());
            environment.put("GF_DATABASE_NAME", dbConn.databaseName());
            environment.put("GF_DATABASE_USER", dbConn.username());
            // Password is injected via ECS secret as GITLAB_DATABASE_PASSWORD
            // Grafana will read it as GF_DATABASE_PASSWORD when ECS injects it
            // Don't set it here - ContainerFactory adds it as an ECS secret
            environment.put("GF_DATABASE_SSL_MODE", "require");
        } else {
            // Fallback to SQLite embedded database (single instance only)
            // NOTE: SQLite is file-based and cannot support multiple instances
            environment.put("GF_DATABASE_TYPE", "sqlite3");
            environment.put("GF_DATABASE_PATH", "/var/lib/grafana/grafana.db");
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

        // Run Grafana container
        builder.addCommands(
            "# Run Grafana container",
            "docker run -d \\",
            "  --name grafana \\",
            "  -p 3000:3000 \\",
            "  -v " + ec2DataPath() + ":/var/lib/grafana \\",
            "  -e GF_SECURITY_ADMIN_PASSWORD=admin \\",
            "  -e GF_INSTALL_PLUGINS=grafana-clock-panel,grafana-simple-json-datasource \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Grafana container started' >> /var/log/userdata.log",
            "",
            "# Wait for Grafana to start",
            "sleep 15",
            "echo 'Grafana should be available on port 3000' >> /var/log/userdata.log",
            "echo 'Default credentials: admin/admin' >> /var/log/userdata.log"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        return new GrafanaOidcIntegration();
    }

    @Override
    public String toString() {
        return "GrafanaApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
