package com.cloudforgeci.api.application.vcs;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Gitea ApplicationSpec implementation.
 *
 * <p>Gitea is a painless self-hosted Git service written in Go.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Lightweight Git hosting</li>
 *   <li>Built-in CI/CD</li>
 *   <li>Issue tracking</li>
 *   <li>Wiki support</li>
 *   <li>Low resource requirements</li>
 * </ul>
 *
 * @see <a href="https://docs.gitea.io/">Gitea Documentation</a>
 */
@ApplicationPlugin(
    value = "gitea",
    category = "vcs",
    displayName = "Gitea",
    description = "Lightweight self-hosted Git service",
    defaultCpu = 512,
    defaultMemory = 1024,
    defaultInstanceType = "t3.micro",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)
public class GiteaApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "gitea";
    private static final String DEFAULT_IMAGE = "gitea/gitea:latest";
    private static final int APPLICATION_PORT = 3000;
    private static final int SSH_PORT = 22;
    private static final String CONTAINER_DATA_PATH = "/data";
    private static final String EFS_DATA_PATH = "/gitea";
    private static final String VOLUME_NAME = "giteaData";
    private static final String CONTAINER_USER = "1000:1000"; // git user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/gitea";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/lib/gitea/log/gitea.log",
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

        // Run Gitea container
        builder.addCommands(
            "# Run Gitea container",
            "docker run -d \\",
            "  --name gitea \\",
            "  -p 3000:3000 -p 22:22 \\",
            "  -v " + ec2DataPath() + ":/data \\",
            "  -e USER_UID=" + uid + " \\",
            "  -e USER_GID=" + gid + " \\",
            "  " + DEFAULT_IMAGE,
            "echo 'Gitea container started' >> /var/log/userdata.log",
            "",
            "# Wait for Gitea to initialize",
            "sleep 30",
            "echo 'Gitea should be available on port 3000' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "GiteaApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                ", sshPort=" + SSH_PORT +
                '}';
    }
}
