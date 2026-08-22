package com.cloudforgeci.api.application.monitoring;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;

import java.util.List;

/**
 * Prometheus ApplicationSpec implementation.
 *
 * <p>Prometheus is an open-source systems monitoring and alerting toolkit.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Multi-dimensional time-series data</li>
 *   <li>PromQL query language</li>
 *   <li>Service discovery</li>
 *   <li>Alerting with Alertmanager</li>
 * </ul>
 *
 * @see <a href="https://prometheus.io/docs/">Prometheus Documentation</a>
 */
@ApplicationPlugin(
    value = "prometheus",
    category = "monitoring",
    displayName = "Prometheus",
    description = "Systems monitoring and alerting toolkit",
    defaultCpu = 1024,
    defaultMemory = 2048,
    defaultInstanceType = "t3.small",
    supportsFargate = true,
    supportsEc2 = true,
    supportsOidc = false
)
public class PrometheusApplicationSpec implements ApplicationSpec {

    private static final String APPLICATION_ID = "prometheus";
    private static final String DEFAULT_IMAGE = "prom/prometheus:latest";
    private static final int APPLICATION_PORT = 9090;
    private static final String CONTAINER_DATA_PATH = "/prometheus";
    private static final String EFS_DATA_PATH = "/prometheus";
    private static final String VOLUME_NAME = "prometheusData";
    private static final String CONTAINER_USER = "65534:65534"; // nobody user
    private static final String EFS_PERMISSIONS = "755";
    private static final String EBS_DEVICE_NAME = "/dev/xvdh";
    private static final String EC2_DATA_PATH = "/var/lib/prometheus";
    private static final List<String> EC2_LOG_PATHS = List.of(
        "/var/log/prometheus/prometheus.log",
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

        // Create Prometheus configuration
        builder.addCommands(
            "# Create Prometheus configuration",
            "mkdir -p " + ec2DataPath() + "/config",
            "cat > " + ec2DataPath() + "/config/prometheus.yml <<'EOF'",
            "global:",
            "  scrape_interval: 15s",
            "  evaluation_interval: 15s",
            "",
            "scrape_configs:",
            "  - job_name: 'prometheus'",
            "    static_configs:",
            "      - targets: ['localhost:9090']",
            "EOF",
            "chown -R " + uid + ":" + gid + " " + ec2DataPath() + "/config"
        );

        // Run Prometheus container
        builder.addCommands(
            "# Run Prometheus container",
            "docker run -d \\",
            "  --name prometheus \\",
            "  -p 9090:9090 \\",
            "  -v " + ec2DataPath() + "/config/prometheus.yml:/etc/prometheus/prometheus.yml \\",
            "  -v " + ec2DataPath() + ":/prometheus \\",
            "  " + DEFAULT_IMAGE + " \\",
            "  --config.file=/etc/prometheus/prometheus.yml \\",
            "  --storage.tsdb.path=/prometheus",
            "echo 'Prometheus container started' >> /var/log/userdata.log",
            "echo 'Prometheus UI available on port 9090' >> /var/log/userdata.log"
        );
    }

    @Override
    public String toString() {
        return "PrometheusApplicationSpec{" +
                "applicationId='" + APPLICATION_ID + '\'' +
                ", defaultImage='" + DEFAULT_IMAGE + '\'' +
                ", applicationPort=" + APPLICATION_PORT +
                '}';
    }
}
