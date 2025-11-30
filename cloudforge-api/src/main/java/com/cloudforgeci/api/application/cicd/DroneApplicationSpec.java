package com.cloudforgeci.api.application.cicd;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Drone CI ApplicationSpec implementation.
 *
 * <p>Drone is a container-native continuous integration platform.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Container-native CI/CD</li>
 *   <li>Pipeline as code (YAML)</li>
 *   <li>GitHub, GitLab, Bitbucket integration</li>
 *   <li>Auto-scaling build agents</li>
 * </ul>
 *
 * @see <a href="https://docs.drone.io/">Drone Documentation</a>
 */
@ApplicationPlugin(
    value = "drone",
    category = "cicd",
    displayName = "Drone",
    description = "Container-native CI platform with pipeline as code",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)
public class DroneApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "drone";
    private static final String DEFAULT_IMAGE = "drone/drone:2";
    private static final int APPLICATION_PORT = 80;
    private static final String CONTAINER_DATA_PATH = "/data";
    private static final String EFS_DATA_PATH = "/drone";
    private static final String VOLUME_NAME = "droneData";
    private static final String CONTAINER_USER = "1000:1000";
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/drone";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/drone/drone.log",
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

        // Run Drone server
        builder.addCommands(
            "# Run Drone server",
            "# Note: DRONE_GITHUB_CLIENT_ID and DRONE_GITHUB_CLIENT_SECRET should be set via environment",
            "docker run -d \\",
            "  --name drone \\",
            "  -p 80:80 \\",
            "  -v " + ec2DataPath() + ":/data \\",
            "  -e DRONE_SERVER_HOST=$(ec2-metadata --public-hostname | cut -d ' ' -f 2) \\",
            "  -e DRONE_SERVER_PROTO=http \\",
            "  -e DRONE_TLS_AUTOCERT=false \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Drone server started' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "DroneApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
