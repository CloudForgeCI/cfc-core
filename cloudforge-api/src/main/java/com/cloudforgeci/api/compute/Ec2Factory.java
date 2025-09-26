package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.scaling.ScalingFactory;

import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ec2.BlockDevice;
import software.amazon.awscdk.services.ec2.BlockDeviceVolume;
import software.amazon.awscdk.services.ec2.EbsDeviceOptions;
import software.amazon.awscdk.services.ec2.EbsDeviceVolumeType;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.LaunchTemplate;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.UserData;

import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.logging.Logger;
import software.constructs.Construct;

import java.util.List;

/**
 * Factory for creating EC2-based Jenkins compute infrastructure.
 * 
 * <p>This factory creates and configures EC2 instances for Jenkins deployments,
 * including auto-scaling groups, launch templates, and IAM roles. It respects
 * the network mode configuration to place instances in appropriate subnets.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Auto-scaling groups with configurable min/max capacity</li>
 *   <li>Launch templates with Jenkins pre-installed</li>
 *   <li>EBS encryption and proper volume configuration</li>
 *   <li>IAM roles with EFS access (when EFS is available)</li>
 *   <li>CloudWatch logging integration</li>
 *   <li>Network mode awareness (public vs private subnets)</li>
 * </ul>
 * 
 * <p><strong>Storage Options:</strong></p>
 * <ul>
 *   <li><strong>EFS:</strong> When EFS is available, instances mount EFS for persistent storage</li>
 *   <li><strong>EBS:</strong> When EFS is not available, instances use EBS volumes</li>
 * </ul>
 * 
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * Ec2Factory factory = new Ec2Factory(scope, "JenkinsEC2");
 * factory.create(ctx);
 * 
 * // Access created resources
 * AutoScalingGroup asg = ctx.asg.get().orElseThrow();
 * Role instanceRole = ctx.ec2InstanceRole.get().orElseThrow();
 * }</pre>
 * 
 * @author CloudForgeCI
 * @since 1.0.0
 * @see SystemContext
 * @see ScalingFactory
 * @see DeploymentContext#networkMode()
 */
public class Ec2Factory extends BaseFactory {
  private static final Logger LOG = Logger.getLogger(Ec2Factory.class.getName());
  private AutoScalingGroup asg;

  @com.cloudforgeci.api.core.annotation.SystemContext
  private SystemContext ctx;

  public Ec2Factory(Construct scope, String id) {
    super(scope, id);
  }

  @Override
  public void create() {
    createEc2Infrastructure();
  }

  /**
   * Creates the complete EC2-based Jenkins infrastructure.
   * 
   * <p>This method orchestrates the creation of all EC2-related resources:</p>
   * <ul>
   *   <li>IAM role for EC2 instances with appropriate permissions</li>
   *   <li>CloudWatch log group for Jenkins logs</li>
   *   <li>User data script for Jenkins installation and configuration</li>
   *   <li>Launch template with Jenkins pre-installed</li>
   *   <li>Auto-scaling group with configurable capacity</li>
   *   <li>Auto-scaling policies and CloudWatch alarms</li>
   * </ul>
   * 
   * <p>The method respects the network mode setting to place instances in
   * appropriate subnets (public vs private) and configures storage based
   * on EFS availability.</p>
   * 
   * @throws IllegalStateException if required resources are not available in context
   * @see SystemContext
   * @see DeploymentContext#networkMode()
   * @see DeploymentContext#minInstanceCapacity()
   * @see DeploymentContext#maxInstanceCapacity()
   */
  private void createEc2Infrastructure() {
    // Use existing IAM role created by IAM configuration (has CloudWatch Logs permissions)
    Role ec2Role = ctx.ec2InstanceRole.get().orElseThrow(() -> 
        new IllegalStateException("EC2 instance role not found - IAM configuration should have created it"));

    // Use existing instance security group (created by JenkinsFactory)
    SecurityGroup instanceSg = ctx.instanceSg.get().orElseThrow();

    // Create CloudWatch log group
    LogGroup logs = createLogGroup();
    ctx.logs.set(logs);

    // Create user data script
    UserData userData = createUserData();

    // Create launch template
    LaunchTemplate launchTemplate = createLaunchTemplate(ec2Role, instanceSg, userData);

    // Create Auto Scaling Group
    this.asg = createAutoScalingGroup(launchTemplate);
    ctx.asg.set(asg);
    
    // Auto-scaling configuration is handled by the orchestration layer
    // The JenkinsServiceTopologyConfiguration will add the ASG to the target group
  }

  // Note: IAM role creation is now handled by IAM configuration (IAMRules)
  // This ensures proper CloudWatch Logs permissions are included

  private SecurityGroup createInstanceSecurityGroup() {
    SecurityGroup instanceSg = SecurityGroup.Builder.create(this, "JenkinsEc2Sg")
            .vpc(ctx.vpc.get().orElseThrow())
            .description("Jenkins EC2 Instance Security Group")
            .allowAllOutbound(true)
            .build();

    // Add ingress rule from ALB security group
    instanceSg.addIngressRule(ctx.albSg.get().orElseThrow(), Port.tcp(8080), "ALB_to_Jenkins");

    return instanceSg;
  }

  private LogGroup createLogGroup() {
    return LogGroup.Builder.create(this, "JenkinsEc2Logs")
            .retention(RetentionDays.ONE_WEEK)
            .build();
  }

  private UserData createUserData() {
    UserData ud = UserData.forLinux();
    ud.addCommands(
            "#!/bin/bash",
            "set -euxo pipefail",
            "command -v dnf >/dev/null && dnf -y update || yum -y update",
            "command -v dnf >/dev/null && dnf -y install java-17-amazon-corretto-headless || yum -y install java-17-amazon-corretto-headless",
            "echo 'userdata start OK' > /var/log/jenkins-userdata.log",
            "",
            "# Install Jenkins",
            "curl -fsSL https://pkg.jenkins.io/redhat-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo",
            "rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key",
            "command -v dnf >/dev/null && dnf -y install jenkins || yum -y install jenkins",
            "",
            "# Install CloudWatch Agent",
            "echo 'Installing CloudWatch Agent...' >> /var/log/jenkins-userdata.log",
            "wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm",
            "rpm -U ./amazon-cloudwatch-agent.rpm",
            "rm -f ./amazon-cloudwatch-agent.rpm",
            "",
            "# Configure CloudWatch Agent",
            "echo 'Configuring CloudWatch Agent...' >> /var/log/jenkins-userdata.log",
            "mkdir -p /opt/aws/amazon-cloudwatch-agent/etc",
            String.format("cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << 'EOF'%n" +
                    "{%n" +
                    "  \"agent\": {%n" +
                    "    \"metrics_collection_interval\": 60,%n" +
                    "    \"run_as_user\": \"root\"%n" +
                    "  },%n" +
                    "  \"logs\": {%n" +
                    "    \"logs_collected\": {%n" +
                    "      \"files\": {%n" +
                    "        \"collect_list\": [%n" +
                    "          {%n" +
                    "            \"file_path\": \"/var/log/jenkins/jenkins.log\",%n" +
                    "            \"log_group_name\": \"/aws/jenkins/%s/%s/%s\",%n" +
                    "            \"log_stream_name\": \"{instance_id}/jenkins.log\",%n" +
                    "            \"timezone\": \"UTC\"%n" +
                    "          },%n" +
                    "          {%n" +
                    "            \"file_path\": \"/var/log/jenkins-userdata.log\",%n" +
                    "            \"log_group_name\": \"/aws/jenkins/%s/%s/%s\",%n" +
                    "            \"log_stream_name\": \"{instance_id}/userdata.log\",%n" +
                    "            \"timezone\": \"UTC\"%n" +
                    "          },%n" +
                    "          {%n" +
                    "            \"file_path\": \"/var/log/messages\",%n" +
                    "            \"log_group_name\": \"/aws/jenkins/%s/%s/%s\",%n" +
                    "            \"log_stream_name\": \"{instance_id}/messages\",%n" +
                    "            \"timezone\": \"UTC\"%n" +
                    "          }%n" +
                    "        ]%n" +
                    "      }%n" +
                    "    }%n" +
                    "  }%n" +
                    "}%n" +
                    "EOF", 
                    ctx.stackName, ctx.runtime.name().toLowerCase(), ctx.security.name().toLowerCase(),
                    ctx.stackName, ctx.runtime.name().toLowerCase(), ctx.security.name().toLowerCase(),
                    ctx.stackName, ctx.runtime.name().toLowerCase(), ctx.security.name().toLowerCase()),
            "",
            "# Start CloudWatch Agent",
            "echo 'Starting CloudWatch Agent...' >> /var/log/jenkins-userdata.log",
            "/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s",
            "",
            "# Configure Jenkins",
            "systemctl enable jenkins",
            "systemctl start jenkins",
            "echo 'jenkins installed and started' >> /var/log/jenkins-userdata.log",
            "",
            "# Wait for Jenkins to generate admin password and log it",
            "echo 'Waiting for Jenkins to generate admin password...' >> /var/log/jenkins-userdata.log",
            "sleep 60",
            "if [ -f /var/lib/jenkins/secrets/initialAdminPassword ]; then",
            "  echo 'Jenkins Admin Password:' >> /var/log/jenkins-userdata.log",
            "  cat /var/lib/jenkins/secrets/initialAdminPassword >> /var/log/jenkins-userdata.log",
            "  echo 'Jenkins Admin Password logged to CloudWatch' >> /var/log/jenkins-userdata.log",
            "else",
            "  echo 'Jenkins admin password file not found yet' >> /var/log/jenkins-userdata.log",
            "fi"
    );

    // Add EFS mounting if EFS is available
    if (ctx.efs.get().isPresent() && ctx.ap.get().isPresent()) {
      ud.addCommands(
              "command -v dnf >/dev/null && dnf -y install amazon-efs-utils || yum -y install amazon-efs-utils",
              "mkdir -p /var/lib/jenkins",
              String.format("echo \"%s:/ /var/lib/jenkins efs _netdev,tls,iam,accesspoint=%s 0 0\" >> /etc/fstab",
                      ctx.efs.get().orElseThrow().getFileSystemId(), ctx.ap.get().orElseThrow().getAccessPointId()),
              "mount -a || (journalctl -xe; exit 1)",
              "chown -R 1000:1000 /var/lib/jenkins || true",
              "echo 'efs mounted' >> /var/log/jenkins-userdata.log"
      );
    } else {
      // Use EBS for storage
      String dataDev = "/dev/xvdh";
      ud.addCommands(
              "DATA_DEV=\"" + dataDev + "\"",
              "if [ ! -b \"$DATA_DEV\" ]; then DATA_DEV=$(readlink -f /dev/nvme1n1 || true); fi",
              "mkfs -t xfs -f \"$DATA_DEV\" || true",
              "mkdir -p /var/lib/jenkins",
              "echo \"$DATA_DEV /var/lib/jenkins xfs defaults,noatime 0 2\" >> /etc/fstab",
              "mount -a",
              "chown -R 1000:1000 /var/lib/jenkins || true",
              "echo 'ebs mounted' >> /var/log/jenkins-userdata.log"
      );
    }

    return ud;
  }

  private LaunchTemplate createLaunchTemplate(Role ec2Role, SecurityGroup instanceSg, UserData userData) {
    LaunchTemplate.Builder ltBuilder = LaunchTemplate.Builder.create(this, "JenkinsLt")
            .machineImage(MachineImage.latestAmazonLinux2023())
            .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MICRO))
            .securityGroup(instanceSg)
            .role(ec2Role)
            .userData(userData);

    // Add block devices
    ltBuilder.blockDevices(List.of(
            BlockDevice.builder()
                    .deviceName("/dev/xvda")
                    .volume(BlockDeviceVolume.ebs(20, EbsDeviceOptions.builder()
                            .encrypted(true)
                            .volumeType(EbsDeviceVolumeType.STANDARD)
                            .deleteOnTermination(true)
                            .build()))
                    .build()
    ));

    // Add data volume if not using EFS
    if (ctx.efs.get().isEmpty()) {
      ltBuilder.blockDevices(List.of(
              BlockDevice.builder()
                      .deviceName("/dev/xvdh")
                      .volume(BlockDeviceVolume.ebs(100, EbsDeviceOptions.builder()
                              .encrypted(true)
                              .volumeType(EbsDeviceVolumeType.STANDARD)
                              .deleteOnTermination(true)
                              .build()))
                      .build()
      ));
    }

    return ltBuilder.build();
  }

  private AutoScalingGroup createAutoScalingGroup(LaunchTemplate launchTemplate) {
    // Use DeploymentContext values for AutoScaling Group configuration
    int minCapacity = ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1;
    int maxCapacity = ctx.cfc.maxInstanceCapacity() != null ? ctx.cfc.maxInstanceCapacity() : 1;
    int desiredCapacity = Math.max(minCapacity, Math.min(maxCapacity, minCapacity)); // Start with minimum
    
    // Determine subnet type based on network mode
    SubnetType subnetType = "public-no-nat".equals(ctx.cfc.networkMode()) ? 
            SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;
    
    return AutoScalingGroup.Builder.create(this, "JenkinsAsg")
            .vpc(ctx.vpc.get().orElseThrow())
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .minCapacity(minCapacity)
            .desiredCapacity(desiredCapacity)
            .maxCapacity(maxCapacity)
            .launchTemplate(launchTemplate)
            .build();
  }

}
