package com.cloudforgeci.api.compute;


import com.cloudforgeci.api.core.*;

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
  private final Props p;

  public record Props(DeploymentContext cfc) {
    }


  public Ec2Factory(Construct scope, String id, Props p) {
    super(scope, id);
    this.p = p;
    SystemContext ctx = SystemContext.of(this);
    /*software.constructs.Node.of(this).addValidation(() -> {
      var errs = new java.util.ArrayList<String>();
      if (p == null || p.cfc == null) errs.add("JenkinsEc2Factory: cfc is required");
      else {
        String v = p.cfc.runtime();
        if (v != null && !v.isBlank() && !v.equalsIgnoreCase("ec2")) errs.add("JenkinsEc2Factory: runtime must be 'ec2'");
      }
      if (p.vpc == null) errs.add("JenkinsEc2Factory: vpc is required");
      if (p.alb.targetGroup() == null) errs.add("JenkinsEc2Factory: albTargetGroup is required");
      if (p.efs == null) errs.add("JenkinsEc2Factory: efs is required");
      return errs;
    });*/
/*
    Role ec2Role = Role.Builder.create(this, "Ec2Role")
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .managedPolicies(java.util.List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
            ))
            .build();

    SecurityGroup jenkinsSg = SecurityGroup.Builder.create(this, "JenkinsEc2Sg")
            .vpc(p.vpc)
            .allowAllOutbound(true)
            .build();

    UserData ud = UserData.forLinux();
    ud.addCommands(
            "#!/bin/bash",
    "set -euxo pipefail",
    "DATA_DEV=\"/dev/xvdh\"",
    "if [ ! -b \"$DATA_DEV\" ]; then",
    "        DATA_DEV=\"$(readlink -f /dev/nvme1n1 || true)\"",
    "fi",
    "mkfs -t xfs -f \"$DATA_DEV\" || true",
    "mkdir -p /var/lib/jenkins",
    "echo \"$DATA_DEV /var/lib/jenkins xfs defaults,noatime 0 2\" >> /etc/fstab",
    "mount -a",
    "chown -R jenkins:jenkins /var/lib/jenkins || true"
    );

    LaunchTemplate lt = LaunchTemplate.Builder.create(this, "JenkinsLt")
            .machineImage(MachineImage.latestAmazonLinux2023())
            .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
            .securityGroup(jenkinsSg)
            .userData(ud)
            .role(ec2Role)
            .blockDevices(java.util.List.of(
                    BlockDevice.builder()
                            .deviceName("/dev/xvda")
                            .volume(BlockDeviceVolume.ebs(50, EbsDeviceOptions.builder().encrypted(true).build()))
                            .build()
            ))
            .build();

    this.asg = AutoScalingGroup.Builder.create(this, "JenkinsAsg")
        .vpc(p.vpc)
        .desiredCapacity(1).minCapacity(1).maxCapacity(3)
            .launchTemplate(lt)
        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
        .build();
    p.albTargetGroup.addTarget(asg);
    //asg.addUserData("yum install -y amazon-efs-utils","mkdir -p /var/lib/jenkins","mount -t efs -o tls " + p.efs.getFileSystemId() + ":/ /var/lib/jenkins");
*/
    boolean useEfs = false;
    // ---- 1) Instance role (SSM + (optional) EFS IAM client perms)
    Role ec2Role = Role.Builder.create(this, "JenkinsEc2Role")
            .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
            .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
            ))
            .build();

    if (useEfs) {
      ec2Role.addToPolicy(PolicyStatement.Builder.create()
              .sid("EfsClientAccess")
              .actions(List.of(
                      "elasticfilesystem:ClientMount",
                      "elasticfilesystem:ClientWrite",
                      "elasticfilesystem:DescribeMountTargets",
                      "elasticfilesystem:DescribeFileSystems"
              ))
              .resources(List.of("*")) // or the specific FS ARN
              .build());
    }

    // ---- 2) Security groups
    SecurityGroup instanceSg = SecurityGroup.Builder.create(this, "JenkinsEc2Sg")
            .vpc(ctx.vpc.get().orElseThrow())
            .description("InstanceSecurityGroup")
            .allowAllOutbound(true)
            .build();
    //if (p.alb.albSg != null) {
      // ALB -> Jenkins on 8080
    //  instanceSg.addIngressRule(p.albSg, Port.tcp(8080), "ALB -> Jenkins");
    //}



    // ---- 3) Optional CloudWatch LogGroup (handy for user-data diagnostics)
    LogGroup logs = LogGroup.Builder.create(this, "JenkinsEc2Logs")
            .retention(RetentionDays.ONE_WEEK).build();
    ctx.logs.set(logs);
    // ---- 4) User data (differs by storage)
    UserData ud = UserData.forLinux();
    ud.addCommands(
            "#!/bin/bash",
            "set -euxo pipefail",
            "command -v dnf >/dev/null && dnf -y update || yum -y update",
            "command -v dnf >/dev/null && dnf -y install java-17-amazon-corretto-headless || yum -y install java-17-amazon-corretto-headless",
            "echo 'userdata start OK' > /var/log/jenkins-userdata.log"
    );

    LaunchTemplate.Builder ltBuilder = LaunchTemplate.Builder.create(this, "JenkinsLt")
            .machineImage(MachineImage.latestAmazonLinux2023())
            .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.SMALL))
            .securityGroup(instanceSg)
            .role(ec2Role)
            .userData(ud);

    if (useEfs) {
      // ---- 5A) EFS path
      //efsSg = ctx.efsSg.get().orElseThrow();//SecurityGroup.Builder.create(this, "EfsSg")
              //.vpc(p.vpc).allowAllOutbound(true).build();
      //efsSg.addIngressRule(instanceSg, Port.tcp(2049), "Instances -> EFS (NFS)");



      // EFS user-data (mount at /var/lib/jenkins)
      ud.addCommands(
              "command -v dnf >/dev/null && dnf -y install amazon-efs-utils || yum -y install amazon-efs-utils",
              "mkdir -p /var/lib/jenkins",
              String.format("echo \"%s:/ /var/lib/jenkins efs _netdev,tls,iam,accesspoint=%s 0 0\" >> /etc/fstab",
                      ctx.efs.get().orElseThrow().getFileSystemId(), ctx.ap.get().orElseThrow().getAccessPointId()),
              "mount -a || (journalctl -xe; exit 1)",
              "chown -R 1000:1000 /var/lib/jenkins || true",
              "echo 'efs mounted' >> /var/log/jenkins-userdata.log"
      );

      // No extra EBS data disk; root volume is fine.

    } else {
      // ---- 5B) EBS path (attach a dedicated data volume and mount it)
      String dataDev = "/dev/xvdh"; // will appear as /dev/nvme1n1 on Nitro

      ltBuilder.blockDevices(List.of(
              BlockDevice.builder()
                      .deviceName("/dev/xvda") // root override (optional)
                      .volume(BlockDeviceVolume.ebs(20, EbsDeviceOptions.builder()
                              .encrypted(true)
                              .volumeType(EbsDeviceVolumeType.STANDARD)
                              //.throughput(125)
                              //.iops(3000)
                              .deleteOnTermination(true)
                              .build()))
                      .build(),
              BlockDevice.builder()
                      .deviceName(dataDev)     // data volume
                      .volume(BlockDeviceVolume.ebs(100, EbsDeviceOptions.builder()
                              .encrypted(true)
                              .volumeType(EbsDeviceVolumeType.STANDARD)
                              //.throughput(125)
                              //.iops(3000)
                              .deleteOnTermination(true)
                              .build()))
                      .build()
      ));
      // EBS UserData
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

    LaunchTemplate lt = ltBuilder.build();

    AutoScalingGroup asg = AutoScalingGroup.Builder.create(this, "JenkinsAsg")
            .vpc(ctx.vpc.get().orElseThrow())
            .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
            .minCapacity(1).desiredCapacity(1).maxCapacity(3)
            .launchTemplate(lt) // << critical (no LaunchConfiguration)
            //.healthCheck(HealthCheck.elb(Duration.minutes(5))) // if behind ALB; otherwise EC2 healthCheck()
            .build();
    ctx.asg.set(asg);
    instanceSg.addIngressRule(ctx.albSg.get().orElseThrow(), Port.tcp(8080), "ALB -> Jenkins");
    //ctx.albTargetGroup.get().orElseThrow().addTarget(asg);
  }

}
