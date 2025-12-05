package com.cloudforgeci.api.application.artifactregistry;

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
 * Harbor Container Registry ApplicationSpec implementation.
 *
 * <p>Harbor is an open-source container registry with security, identity, and management.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Container image vulnerability scanning</li>
 *   <li>Image signing and verification</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Replication across registries</li>
 *   <li>Audit logging</li>
 * </ul>
 *
 * <p><strong>Compliance Use Cases:</strong></p>
 * <ul>
 *   <li>SOC2: Container image provenance and audit trails</li>
 *   <li>PCI-DSS: Secure storage of payment processing containers</li>
 *   <li>HIPAA: Vulnerability scanning for healthcare containers</li>
 * </ul>
 *
 * <p><strong>Fintech Applications:</strong></p>
 * <ul>
 *   <li>Store Docker images for payment processing services</li>
 *   <li>Vulnerability scanning for financial applications</li>
 *   <li>Image signing for regulatory compliance</li>
 *   <li>Content trust and notary integration</li>
 * </ul>
 *
 * <p><strong>Database Requirements:</strong></p>
 * <ul>
 *   <li><b>REQUIRED:</b> PostgreSQL 10+ via RDS</li>
 *   <li>Harbor does NOT support embedded databases for production</li>
 *   <li>Recommended: PostgreSQL with db.t3.medium or larger</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Enable content trust for image signing</li>
 *   <li>Configure vulnerability scanning</li>
 *   <li>Use OIDC/LDAP for authentication</li>
 *   <li>Enable audit logging</li>
 * </ul>
 *
 * @see <a href="https://goharbor.io/docs/">Harbor Documentation</a>
 */
@ApplicationPlugin(
    value = "harbor",
    category = "artifactregistry",
    displayName = "Harbor",
    description = "Container image registry with security scanning",
    defaultCpu = 2048,
    defaultMemory = 4096,
    defaultInstanceType = "t3.medium",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false,
    supportsDatabase = true,
    requiresDatabase = true
)

public class HarborApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "harbor";
    private static final String DEFAULT_IMAGE = "goharbor/harbor-core:v2.9.0";
    private static final int APPLICATION_PORT = 80;
    private static final String CONTAINER_DATA_PATH = "/data";
    private static final String EFS_DATA_PATH = "/harbor";
    private static final String VOLUME_NAME = "harborData";
    private static final String CONTAINER_USER = "10000:10000"; // harbor user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/data/harbor";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/harbor/harbor.log",
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
        // Harbor REQUIRES PostgreSQL for registry metadata
        return DatabaseRequirement.required("postgres", "13")
            .withInstanceClass("db.t3.medium")
            .withStorage(50)
            .withDatabaseName("registry");
    }

    @Override
    public Map<String, String> databaseParameters() {
        // PostgreSQL optimization for Harbor registry workload
        return Map.of(
            "max_connections", "250",
            "shared_buffers", "{DBInstanceClassMemory/4096}",
            "work_mem", "16MB",
            "maintenance_work_mem", "256MB",
            "log_statement", "ddl"
        );
    }

    @Override
    public int backupRetentionDays() {
        return 30; // Harbor contains container registry metadata
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        // Delegate to new method with null database connection for backward compatibility
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    /**
     * Container environment variables with database connection support.
     *
     * <p>Configures Harbor to use RDS PostgreSQL for registry metadata.
     * Harbor REQUIRES a database for all deployments.</p>
     */
    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        // Configure hostname
        if (fqdn != null && !fqdn.isBlank()) {
            environment.put("HARBOR_HOSTNAME", fqdn);
            environment.put("HARBOR_EXTERNAL_URL", (sslEnabled ? "https://" : "http://") + fqdn);
        }

        // Database configuration (REQUIRED for Harbor)
        if (dbConn != null) {
            // Use RDS PostgreSQL for registry metadata
            environment.put("POSTGRESQL_HOST", dbConn.endpoint());
            environment.put("POSTGRESQL_PORT", String.valueOf(dbConn.port()));
            environment.put("POSTGRESQL_DATABASE", dbConn.databaseName());
            environment.put("POSTGRESQL_USERNAME", dbConn.username());
            // Password is injected via ECS secret as GITLAB_DATABASE_PASSWORD
            // Harbor will read it as POSTGRESQL_PASSWORD when ECS injects it
            // Don't set it here - ContainerFactory adds it as an ECS secret
            environment.put("POSTGRESQL_SSLMODE", "require");
        } else {
            // NOTE: Harbor REQUIRES a database - this should never happen
            // Set placeholder that will fail fast if database is missing
            environment.put("POSTGRESQL_HOST", "MISSING_DATABASE_CONNECTION");
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

        // Download and configure Harbor
        builder.addCommands(
            "# Download Harbor installer",
            "cd /opt",
            "curl -L https://github.com/goharbor/harbor/releases/download/v2.9.0/harbor-offline-installer-v2.9.0.tgz -o harbor.tgz",
            "tar xzvf harbor.tgz",
            "cd harbor",
            "",
            "# Create Harbor configuration",
            "cat > harbor.yml <<'EOF'",
            "# Harbor Configuration",
            "",
            "hostname: harbor.example.com",
            "",
            "# HTTP settings",
            "http:",
            "  port: 80",
            "",
            "# HTTPS settings (configure for production)",
            "# https:",
            "#   port: 443",
            "#   certificate: /data/cert/server.crt",
            "#   private_key: /data/cert/server.key",
            "",
            "# Harbor admin password (CHANGE THIS!)",
            "harbor_admin_password: Harbor12345",
            "",
            "# Database configuration",
            "database:",
            "  password: root123",
            "  max_idle_conns: 100",
            "  max_open_conns: 900",
            "",
            "# Data volume",
            "data_volume: " + ec2DataPath(),
            "",
            "# Log settings",
            "log:",
            "  level: info",
            "  local:",
            "    rotate_count: 50",
            "    rotate_size: 200M",
            "    location: /var/log/harbor",
            "",
            "_version: '2.9.0'",
            "EOF",
            "",
            "# Prepare Harbor installation",
            "./prepare",
            "",
            "# Install and start Harbor",
            "./install.sh --with-trivy --with-chartmuseum",
            "",
            "echo 'Harbor installation complete' >> /var/log/userdata.log",
            "echo 'Access Harbor at http://harbor.example.com' >> /var/log/userdata.log",
            "echo 'Default credentials: admin / Harbor12345' >> /var/log/userdata.log",
            "echo 'IMPORTANT: Change the admin password immediately!' >> /var/log/userdata.log"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        // Harbor has built-in OIDC support
        // Implementation would configure harbor.yml with OIDC settings
        return null;
    }

    @Override
    public String toString() {
        return "HarborApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
