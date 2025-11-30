package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.UserDataBuilder;
import software.amazon.awscdk.services.ec2.UserData;

import java.util.List;

/**
 * Implementation of UserDataBuilder that generates bash commands for EC2 UserData scripts.
 *
 * <p>This class encapsulates the complex bash logic for common infrastructure tasks,
 * allowing applications to focus on their specific installation and configuration needs.</p>
 */
public class UserDataBuilderImpl implements UserDataBuilder {
    private final UserData userData;

    public UserDataBuilderImpl(UserData userData) {
        this.userData = userData;
    }

    @Override
    public void addSystemUpdate() {
        userData.addCommands(
            "command -v dnf >/dev/null && dnf -y update || yum -y update"
        );
    }

    @Override
    public void installCloudWatchAgent(String logGroupName, List<String> logFilePaths) {
        userData.addCommands(
            "# Install CloudWatch Agent",
            "echo 'Installing CloudWatch Agent...' >> /var/log/userdata.log",
            "wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm",
            "rpm -U ./amazon-cloudwatch-agent.rpm",
            "rm -f ./amazon-cloudwatch-agent.rpm",
            "",
            "# Configure CloudWatch Agent",
            "echo 'Configuring CloudWatch Agent...' >> /var/log/userdata.log",
            "mkdir -p /opt/aws/amazon-cloudwatch-agent/etc"
        );

        // Build the CloudWatch Agent configuration JSON
        StringBuilder configJson = new StringBuilder();
        configJson.append("cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << 'EOF'\n");
        configJson.append("{\n");
        configJson.append("  \"agent\": {\n");
        configJson.append("    \"metrics_collection_interval\": 60,\n");
        configJson.append("    \"run_as_user\": \"root\"\n");
        configJson.append("  },\n");
        configJson.append("  \"logs\": {\n");
        configJson.append("    \"logs_collected\": {\n");
        configJson.append("      \"files\": {\n");
        configJson.append("        \"collect_list\": [\n");

        for (int i = 0; i < logFilePaths.size(); i++) {
            String logPath = logFilePaths.get(i);
            String logStreamName = logPath.substring(logPath.lastIndexOf('/') + 1);

            configJson.append("          {\n");
            configJson.append("            \"file_path\": \"").append(logPath).append("\",\n");
            configJson.append("            \"log_group_name\": \"").append(logGroupName).append("\",\n");
            configJson.append("            \"log_stream_name\": \"{instance_id}/").append(logStreamName).append("\",\n");
            configJson.append("            \"timezone\": \"UTC\"\n");
            configJson.append("          }");

            if (i < logFilePaths.size() - 1) {
                configJson.append(",");
            }
            configJson.append("\n");
        }

        configJson.append("        ]\n");
        configJson.append("      }\n");
        configJson.append("    }\n");
        configJson.append("  }\n");
        configJson.append("}\n");
        configJson.append("EOF");

        userData.addCommands(
            configJson.toString(),
            "",
            "# Start CloudWatch Agent",
            "echo 'Starting CloudWatch Agent...' >> /var/log/userdata.log",
            "/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s"
        );
    }

    @Override
    public void mountEfs(String efsId, String accessPointId, String mountPath, String uid, String gid) {
        userData.addCommands(
            "# Mount EFS",
            "command -v dnf >/dev/null && dnf -y install amazon-efs-utils || yum -y install amazon-efs-utils",
            "mkdir -p " + mountPath,
            String.format("echo \"%s:/ %s efs _netdev,tls,iam,accesspoint=%s 0 0\" >> /etc/fstab",
                efsId, mountPath, accessPointId),
            "mount -a || (journalctl -xe; exit 1)",
            String.format("chown -R %s:%s %s || true", uid, gid, mountPath),
            "echo 'EFS mounted successfully' >> /var/log/userdata.log"
        );
    }

    @Override
    public void mountEbs(String deviceName, String mountPath, String uid, String gid) {
        userData.addCommands(
            "# Mount EBS",
            "DATA_DEV=\"" + deviceName + "\"",
            "if [ ! -b \"$DATA_DEV\" ]; then DATA_DEV=$(readlink -f /dev/nvme1n1 || true); fi",
            "mkfs -t xfs -f \"$DATA_DEV\" || true",
            "mkdir -p " + mountPath,
            String.format("echo \"$DATA_DEV %s xfs defaults,noatime 0 2\" >> /etc/fstab", mountPath),
            "mount -a",
            String.format("chown -R %s:%s %s || true", uid, gid, mountPath),
            "echo 'EBS mounted successfully' >> /var/log/userdata.log"
        );
    }

    @Override
    public void addCommands(String... commands) {
        userData.addCommands(commands);
    }

    @Override
    public void addCommand(String command) {
        userData.addCommands(command);
    }
}
