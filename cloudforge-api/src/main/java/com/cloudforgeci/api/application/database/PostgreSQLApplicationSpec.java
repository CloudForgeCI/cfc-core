package com.cloudforgeci.api.application.database;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * PostgreSQL ApplicationSpec implementation.
 *
 * <p>PostgreSQL is a powerful, open-source object-relational database system.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>ACID compliance</li>
 *   <li>Full-text search</li>
 *   <li>JSON/JSONB support</li>
 *   <li>Advanced indexing</li>
 *   <li>Extensible with plugins</li>
 * </ul>
 *
 * <p><strong>Security Note:</strong></p>
 * <ul>
 *   <li>Default password should be changed immediately</li>
 *   <li>Use AWS Secrets Manager for production</li>
 *   <li>Enable SSL/TLS for connections</li>
 * </ul>
 *
 * @see <a href="https://www.postgresql.org/docs/">PostgreSQL Documentation</a>
 */
@ApplicationPlugin(
    value = "postgresql",
    category = "database",
    displayName = "PostgreSQL",
    description = "Object-relational database system",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)

public class PostgreSQLApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "postgresql";
    private static final String DEFAULT_IMAGE = "postgres:15";
    private static final int APPLICATION_PORT = 5432;
    private static final String CONTAINER_DATA_PATH = "/var/lib/postgresql/data";
    private static final String EFS_DATA_PATH = "/postgresql";
    private static final String VOLUME_NAME = "postgresData";
    private static final String CONTAINER_USER = "999:999"; // postgres user
    private static final String EFS_PERMISSIONS = "700";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/postgresql/data";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/lib/postgresql/data/log/postgresql.log",
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

        // Run PostgreSQL container
        builder.addCommands(
            "# Generate secure PostgreSQL password",
            "POSTGRES_PASSWORD=$(aws secretsmanager get-secret-value --secret-id ${STACK_NAME:-postgresql}/password --query SecretString --output text 2>/dev/null || openssl rand -base64 16)",
            "echo \"Generated PostgreSQL password (save this): $POSTGRES_PASSWORD\" >> /var/log/userdata.log",
            "",
            "# Run PostgreSQL container",
            "docker run -d \\",
            "  --name postgresql \\",
            "  -p 5432:5432 \\",
            "  -v " + ec2DataPath() + ":/var/lib/postgresql/data \\",
            "  -e POSTGRES_PASSWORD=\"$POSTGRES_PASSWORD\" \\",
            "  -e POSTGRES_DB=cloudforge \\",
            "  -e POSTGRES_USER=cloudforge \\",
            "  " + DEFAULT_IMAGE,
            "echo 'PostgreSQL container started' >> /var/log/userdata.log",
            "",
            "# Wait for PostgreSQL to be ready",
            "sleep 30",
            "docker exec postgresql pg_isready -U cloudforge && \\",
            "  echo 'PostgreSQL is ready' >> /var/log/userdata.log || \\",
            "  echo 'PostgreSQL not ready yet' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "PostgreSQLApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
