package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
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
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.List;

public class Ec2Factory extends Construct {
  private AutoScalingGroup asg;

  public Ec2Factory(Construct scope, String id) {
    super(scope, id);
    SystemContext ctx = SystemContext.of(this);
  }

  public void create(final SystemContext ctx) {
    // Create IAM role for EC2 instances
    Role ec2Role = createInstanceRole(ctx);
    ctx.ec2InstanceRole.set(ec2Role);

    // Use existing instance security group (created by JenkinsFactory)
    SecurityGroup instanceSg = ctx.instanceSg.get().orElseThrow();

    // Create CloudWatch log group
    LogGroup logs = createLogGroup(ctx);
    ctx.logs.set(logs);

    // Create user data script
    UserData userData = createUserData(ctx);

    // Create launch template
    LaunchTemplate launchTemplate = createLaunchTemplate(ctx, ec2Role, instanceSg, userData);

    // Create Auto Scaling Group
    this.asg = createAutoScalingGroup(ctx, launchTemplate);
    ctx.asg.set(asg);
    
    // Configure Auto Scaling with CPU target utilization from DeploymentContext
    new ScalingFactory(this, "Scaling").scale(asg, ctx);
  }

  private Role createInstanceRole(SystemContext ctx) {
    Role.Builder roleBuilder = Role.Builder.create(this, "JenkinsEc2Role")
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
            ));

    Role role = roleBuilder.build();
    
    // Add EFS permissions if EFS is available
    if (ctx.efs.get().isPresent()) {
      role.addToPolicy(PolicyStatement.Builder.create()
              .sid("EfsClientAccess")
              .actions(List.of(
                      "elasticfilesystem:ClientMount",
                      "elasticfilesystem:ClientWrite",
                      "elasticfilesystem:DescribeMountTargets",
                      "elasticfilesystem:DescribeFileSystems"
              ))
              .resources(List.of(ctx.efs.get().orElseThrow().getFileSystemArn()))
              .build());
    }
    
    return role;
  }

  private SecurityGroup createInstanceSecurityGroup(SystemContext ctx) {
    SecurityGroup instanceSg = SecurityGroup.Builder.create(this, "JenkinsEc2Sg")
            .vpc(ctx.vpc.get().orElseThrow())
            .description("Jenkins EC2 Instance Security Group")
            .allowAllOutbound(true)
            .build();

    // Add ingress rule from ALB security group
    instanceSg.addIngressRule(ctx.albSg.get().orElseThrow(), Port.tcp(8080), "ALB_to_Jenkins");

    return instanceSg;
  }

  private LogGroup createLogGroup(SystemContext ctx) {
    return LogGroup.Builder.create(this, "JenkinsEc2Logs")
            .retention(RetentionDays.ONE_WEEK)
            .build();
  }

  private UserData createUserData(SystemContext ctx) {
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
            "# Configure Jenkins",
            "systemctl enable jenkins",
            "systemctl start jenkins",
            "echo 'jenkins installed and started' >> /var/log/jenkins-userdata.log"
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

  private LaunchTemplate createLaunchTemplate(SystemContext ctx, Role ec2Role, SecurityGroup instanceSg, UserData userData) {
    LaunchTemplate.Builder ltBuilder = LaunchTemplate.Builder.create(this, "JenkinsLt")
            .machineImage(MachineImage.latestAmazonLinux2023())
            .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
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

  private AutoScalingGroup createAutoScalingGroup(SystemContext ctx, LaunchTemplate launchTemplate) {
    // Use DeploymentContext values for AutoScaling Group configuration
    int minCapacity = ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1;
    int maxCapacity = ctx.cfc.maxInstanceCapacity() != null ? ctx.cfc.maxInstanceCapacity() : 3;
    int desiredCapacity = Math.max(minCapacity, Math.min(maxCapacity, minCapacity)); // Start with minimum
    
    return AutoScalingGroup.Builder.create(this, "JenkinsAsg")
            .vpc(ctx.vpc.get().orElseThrow())
            .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
            .minCapacity(minCapacity)
            .desiredCapacity(desiredCapacity)
            .maxCapacity(maxCapacity)
            .launchTemplate(launchTemplate)
            .build();
  }

}
