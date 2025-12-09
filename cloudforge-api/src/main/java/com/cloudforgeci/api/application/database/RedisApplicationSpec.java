package com.cloudforgeci.api.application.database;

import com.cloudforge.core.annotation.ApplicationPlugin;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Redis ApplicationSpec implementation.
 *
 * <p>Redis is an in-memory data structure store used as database, cache, and message broker.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>In-memory key-value store</li>
 *   <li>Support for multiple data structures (strings, lists, sets, hashes)</li>
 *   <li>Pub/sub messaging</li>
 *   <li>Persistence options (RDB, AOF)</li>
 *   <li>High performance</li>
 * </ul>
 *
 * @see <a href="https://redis.io/documentation">Redis Documentation</a>
 */
@ApplicationPlugin(
    value = "redis",
    category = "database",
    displayName = "Redis",
    description = "In-memory data store and cache",
    defaultCpu = 512,
    defaultMemory = 1024,
    defaultInstanceType = "t3.micro",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)

public class RedisApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "redis";
    private static final String DEFAULT_IMAGE = "redis:7-alpine";
    private static final int APPLICATION_PORT = 6379;
    private static final String CONTAINER_DATA_PATH = "/data";
    private static final String EFS_DATA_PATH = "/redis";
    private static final String VOLUME_NAME = "redisData";
    private static final String CONTAINER_USER = "999:999"; // redis user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/redis";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/redis/redis.log",
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
            // Redis Cluster Bus - inbound for cluster node communication
            OptionalPort.inboundTcp(16379, "enableCluster", "Cluster Bus"),
            // Redis Sentinel - inbound for HA failover coordination
            OptionalPort.inboundTcp(26379, "enableSentinel", "Sentinel")
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

        // Run Redis container
        // NOTE: REDIS_PASSWORD should be set via EC2 instance metadata or Secrets Manager
        builder.addCommands(
            "# Retrieve Redis password from Secrets Manager or use placeholder",
            "REDIS_PASSWORD=$(aws secretsmanager get-secret-value --secret-id ${STACK_NAME:-redis}/redis-password --query SecretString --output text 2>/dev/null || echo 'REPLACE_WITH_SECURE_PASSWORD')",
            "",
            "# Run Redis container with persistence enabled",
            "docker run -d \\",
            "  --name redis \\",
            "  -p 6379:6379 \\",
            "  -v " + ec2DataPath() + ":/data \\",
            "  " + DEFAULT_IMAGE + " \\",
            "  redis-server --appendonly yes --requirepass \"$REDIS_PASSWORD\"",
            "echo 'Redis container started with AOF persistence' >> /var/log/userdata.log",
            "",
            "# Test Redis connection",
            "sleep 5",
            "docker exec redis redis-cli -a \"$REDIS_PASSWORD\" ping && \\",
            "  echo 'Redis is responding' >> /var/log/userdata.log || \\",
            "  echo 'Redis not responding yet' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "RedisApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
