package com.cloudforgeci.api.application.collaboration;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforge.core.oidc.MattermostGitLabOidcIntegration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mattermost Team Edition ApplicationSpec implementation (FREE).
 *
 * <p>Mattermost Team Edition is the free, open-source version of Mattermost.</p>
 *
 * <p><strong>Team Edition vs Enterprise:</strong></p>
 * <ul>
 *   <li>Free to use with no license required</li>
 *   <li>Uses GitLab OAuth for SSO (no single logout)</li>
 *   <li>All core collaboration features included</li>
 *   <li>Community support</li>
 * </ul>
 *
 * <p><strong>Limitations (vs Enterprise):</strong></p>
 * <ul>
 *   <li>No single logout - Cognito session persists after Mattermost logout</li>
 *   <li>No SAML 2.0 support</li>
 *   <li>No AD/LDAP group sync</li>
 *   <li>No compliance exports</li>
 *   <li>No high availability clustering</li>
 * </ul>
 *
 * <p><strong>For Enterprise Features:</strong></p>
 * <p>Use {@link MattermostApplicationSpec} (mattermost-enterprise) instead.</p>
 *
 * @see MattermostApplicationSpec
 * @see <a href="https://docs.mattermost.com/">Mattermost Documentation</a>
 */
@ApplicationPlugin(
    value = "mattermost-team",
    category = "collaboration",
    displayName = "Mattermost Team (Free)",
    description = "Team collaboration - free edition (no single logout)",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = true,
    supportsDatabase = true,
    requiresDatabase = true
)

public class MattermostTeamApplicationSpec implements ApplicationSpec, DatabaseSpec {

    private static final String APPLICATION_ID = "mattermost-team";
    // Uses Enterprise Edition image (runs in free mode without license)
    // Same image as mattermost-enterprise, but uses GitLab OAuth (no single logout)
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
            OptionalPort.outboundTcp(465, "enableSmtps", "SMTP Email (TLS)")
            // Note: Clustering not available in Team Edition
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
        return DatabaseRequirement.required("postgres", "14")
            .withInstanceClass("db.t3.small")
            .withStorage(30)
            .withDatabaseName("mattermost");
    }

    @Override
    public Map<String, String> databaseParameters() {
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
        return 14;
    }

    @Override
    public Map<String, String> containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode) {
        return containerEnvironmentVariables(fqdn, sslEnabled, authMode, null);
    }

    public Map<String, String> containerEnvironmentVariables(
            String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
        Map<String, String> environment = new HashMap<>();

        if (fqdn != null && !fqdn.isBlank()) {
            String siteUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("MM_SERVICESETTINGS_SITEURL", siteUrl);
        }

        environment.put("MM_SERVICESETTINGS_TRUSTEDPROXYIPHEADER", "X-Forwarded-For,X-Real-IP");
        environment.put("MM_SERVICESETTINGS_FORWARD80TO443", "false");
        environment.put("MM_SERVICESETTINGS_WEBSOCKETURL", "");

        if (dbConn != null) {
            environment.put("MM_SQLSETTINGS_DRIVERNAME", "postgres");
        } else {
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

        builder.addCommands(
            "# Install Docker",
            "yum install -y docker",
            "systemctl enable docker",
            "systemctl start docker",
            "echo 'Docker installed' >> /var/log/userdata.log"
        );

        String logGroupName = String.format("/aws/%s/%s/%s",
            context.stackName(),
            context.runtimeType(),
            context.securityProfile());
        builder.installCloudWatchAgent(logGroupName, ec2LogPaths());

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

        builder.addCommands(
            "# Retrieve database credentials from Secrets Manager",
            "MM_DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id ${STACK_NAME:-mattermost}/db-password --query SecretString --output text 2>/dev/null || echo 'CONFIGURE_DB_PASSWORD')",
            "",
            "# Run Mattermost container (Enterprise image in free mode)",
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
            "  " + DEFAULT_IMAGE,
            "echo 'Mattermost container started' >> /var/log/userdata.log"
        );
    }

    @Override
    public boolean supportsOidcIntegration() {
        return true;
    }

    @Override
    public OidcIntegration getOidcIntegration() {
        // Team Edition uses GitLab OAuth (free, but no single logout)
        return new MattermostGitLabOidcIntegration();
    }

    @Override
    public String toString() {
        return "MattermostTeamApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
