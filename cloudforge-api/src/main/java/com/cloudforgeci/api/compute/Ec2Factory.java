package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
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

  @SystemContext("stackName")
  private String stackName;

  @SystemContext("runtime")
  private RuntimeType runtime;

  @SystemContext("security")
  private SecurityProfile security;

  @SystemContext("applicationSpec")
  private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

  @DeploymentContext("minInstanceCapacity")
  private Integer minInstanceCapacity;

  @DeploymentContext("maxInstanceCapacity")
  private Integer maxInstanceCapacity;

  @DeploymentContext("networkMode")
  private String networkMode;

  @DeploymentContext("retainStorage")
  private Boolean retainStorage;

  @DeploymentContext("instanceType")
  private String instanceType;

  @DeploymentContext("enableEncryption")
  private Boolean enableEncryption;

  public Ec2Factory(Construct scope, String id) {
    super(scope, id);
    // All fields are automatically injected by BaseFactory
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
  @SystemContext("ec2InstanceRole")
  private Role ec2InstanceRole;

  @SystemContext("instanceSg")
  private SecurityGroup instanceSg;

  @SystemContext("vpc")
  private software.amazon.awscdk.services.ec2.Vpc vpc;

  @SystemContext("albSg")
  private SecurityGroup albSg;

  @SystemContext("efs")
  private software.amazon.awscdk.services.efs.FileSystem efs;

  @SystemContext("ap")
  private software.amazon.awscdk.services.efs.AccessPoint ap;

  private void createEc2Infrastructure() {
    // Use existing IAM role created by IAM configuration (has CloudWatch Logs permissions)
    if (ec2InstanceRole == null) {
      throw new IllegalStateException("EC2 instance role not found - IAM configuration should have created it");
    }

    // Use existing instance security group (created by JenkinsFactory)
    if (instanceSg == null) {
      throw new IllegalStateException("Instance security group not found");
    }

    // Create CloudWatch log group
    LogGroup logs = createLogGroup();
    ctx.logs.set(logs);

    // Create user data script
    UserData userData = createUserData();

    // Create launch template
    LaunchTemplate launchTemplate = createLaunchTemplate(ec2InstanceRole, instanceSg, userData);

    // Create Auto Scaling Group
    this.asg = createAutoScalingGroup(launchTemplate);
    ctx.asg.set(asg);

    // Auto-scaling configuration is handled by the orchestration layer
    // The JenkinsServiceTopologyConfiguration will add the ASG to the target group
  }

  // Note: IAM role creation is now handled by IAM configuration (IAMRules)
  // This ensures proper CloudWatch Logs permissions are included

  private SecurityGroup createInstanceSecurityGroup() {
    int appPort = applicationSpec != null ? applicationSpec.applicationPort() : 8080;
    String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

    if (vpc == null) {
      throw new IllegalStateException("VPC not available");
    }
    if (albSg == null) {
      throw new IllegalStateException("ALB security group not available");
    }

    SecurityGroup instanceSg = SecurityGroup.Builder.create(this, appId + "Ec2Sg")
            .vpc(vpc)
            .description(appId + " EC2 Instance Security Group")
            .allowAllOutbound(true)
            .build();

    // Add ingress rule from ALB security group
    instanceSg.addIngressRule(albSg, Port.tcp(appPort), "ALB_to_" + appId);

    return instanceSg;
  }

  private LogGroup createLogGroup() {
    String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
    return LogGroup.Builder.create(this, appId + "Ec2Logs")
            .retention(RetentionDays.ONE_WEEK)
            .build();
  }

  private UserData createUserData() {
    UserData ud = UserData.forLinux();

    // Add bash best practices
    ud.addCommands(
        "#!/bin/bash",
        "set -euxo pipefail",
        "echo 'UserData script started' > /var/log/userdata.log"
    );

    // Create Ec2Context for application configuration
    boolean hasEfs = efs != null && ap != null;
    String efsId = hasEfs ? efs.getFileSystemId() : null;
    String accessPointId = hasEfs ? ap.getAccessPointId() : null;

    com.cloudforgeci.api.core.Ec2ContextImpl ec2Context = new com.cloudforgeci.api.core.Ec2ContextImpl(
        stackName,
        runtime.name().toLowerCase(),
        security.name().toLowerCase(),
        hasEfs,
        efsId,
        accessPointId
    );

    // Create UserDataBuilder and delegate to ApplicationSpec
    com.cloudforgeci.api.core.UserDataBuilderImpl builder =
        new com.cloudforgeci.api.core.UserDataBuilderImpl(ud);

    applicationSpec.configureUserData(builder, ec2Context);

    return ud;
  }

  private LaunchTemplate createLaunchTemplate(Role ec2Role, SecurityGroup instanceSg, UserData userData) {
    String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

    // Determine instance type with priority: DeploymentConfig > ApplicationSpec > default
    String instanceTypeStr;
    if (instanceType != null && !instanceType.isEmpty()) {
      instanceTypeStr = instanceType;
    } else if (applicationSpec != null) {
      instanceTypeStr = applicationSpec.defaultInstanceType();
    } else {
      instanceTypeStr = "t3.micro";  // Fallback default
    }

    // Parse instance type string (e.g., "t3.micro" -> InstanceClass.T3, InstanceSize.MICRO)
    InstanceType parsedInstanceType = parseInstanceType(instanceTypeStr);

    LaunchTemplate.Builder ltBuilder = LaunchTemplate.Builder.create(this, appId + "Lt")
            .machineImage(MachineImage.latestAmazonLinux2023())
            .instanceType(parsedInstanceType)
            .securityGroup(instanceSg)
            .role(ec2Role)
            .userData(userData);

    // Determine EBS retention policy based on retainStorage configuration
    // Root volume is always deleted (system disk), but data volume can be retained
    boolean deleteDataVolume = Boolean.FALSE.equals(retainStorage) || retainStorage == null;

    if (Boolean.TRUE.equals(retainStorage)) {
      LOG.info("EBS data volumes will be RETAINED after instance termination (retainStorage = true)");
      LOG.info("⚠️  You must manually delete EBS volumes from AWS Console to avoid ongoing storage costs");
    } else {
      LOG.info("EBS data volumes will be DESTROYED with instances (retainStorage = false)");
    }

    // Determine encryption setting with priority: DeploymentConfig > SecurityProfile default
    // For production deployments, encryption defaults to true
    boolean encrypt;
    if (enableEncryption != null) {
      encrypt = enableEncryption;
    } else {
      // Default: encrypt for PRODUCTION and STAGING, optional for DEV
      encrypt = (security == SecurityProfile.PRODUCTION || security == SecurityProfile.STAGING);
    }

    // Add block devices
    ltBuilder.blockDevices(List.of(
            BlockDevice.builder()
                    .deviceName("/dev/xvda")
                    .volume(BlockDeviceVolume.ebs(20, EbsDeviceOptions.builder()
                            .encrypted(encrypt)
                            .volumeType(EbsDeviceVolumeType.STANDARD)
                            .deleteOnTermination(true)  // Always delete root volume
                            .build()))
                    .build()
    ));

    // Add data volume if not using EFS
    if (efs == null) {
      String ebsDeviceName = applicationSpec != null ? applicationSpec.ebsDeviceName() : "/dev/xvdh";
      ltBuilder.blockDevices(List.of(
              BlockDevice.builder()
                      .deviceName(ebsDeviceName)
                      .volume(BlockDeviceVolume.ebs(100, EbsDeviceOptions.builder()
                              .encrypted(encrypt)
                              .volumeType(EbsDeviceVolumeType.STANDARD)
                              .deleteOnTermination(deleteDataVolume)  // Respect retainStorage setting
                              .build()))
                      .build()
      ));
    }

    return ltBuilder.build();
  }

  /**
   * Parse EC2 instance type from string (e.g., "t3.micro").
   *
   * @param instanceTypeStr Instance type string (e.g., "t3.micro", "m5.large")
   * @return Parsed InstanceType
   */
  private static InstanceType parseInstanceType(String instanceTypeStr) {
    // Extract class and size from instance type (e.g., "t3.micro")
    String[] parts = instanceTypeStr.split("\\.");
    if (parts.length < 2) {
      return InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO);
    }

    InstanceClass instanceClass = parseInstanceClass(parts[0]);
    InstanceSize instanceSize = parseInstanceSize(parts[1]);

    return InstanceType.of(instanceClass, instanceSize);
  }

  /**
   * Parse instance class from string.
   */
  private static InstanceClass parseInstanceClass(String className) {
    return switch (className.toLowerCase()) {
      case "t3" -> InstanceClass.BURSTABLE3;
      case "t4g" -> InstanceClass.BURSTABLE4_GRAVITON;
      case "m5" -> InstanceClass.M5;
      case "m6g" -> InstanceClass.MEMORY6_GRAVITON;
      case "r5" -> InstanceClass.R5;
      case "r6g" -> InstanceClass.MEMORY6_GRAVITON;
      case "c5" -> InstanceClass.COMPUTE5;
      case "c6g" -> InstanceClass.COMPUTE6_GRAVITON2;
      default -> InstanceClass.BURSTABLE3;
    };
  }

  /**
   * Parse instance size from string.
   */
  private static InstanceSize parseInstanceSize(String size) {
    return switch (size.toLowerCase()) {
      case "micro" -> InstanceSize.MICRO;
      case "small" -> InstanceSize.SMALL;
      case "medium" -> InstanceSize.MEDIUM;
      case "large" -> InstanceSize.LARGE;
      case "xlarge" -> InstanceSize.XLARGE;
      case "2xlarge" -> InstanceSize.XLARGE2;
      case "4xlarge" -> InstanceSize.XLARGE4;
      case "8xlarge" -> InstanceSize.XLARGE8;
      case "12xlarge" -> InstanceSize.XLARGE12;
      case "16xlarge" -> InstanceSize.XLARGE16;
      case "24xlarge" -> InstanceSize.XLARGE24;
      default -> InstanceSize.MICRO;
    };
  }

  private AutoScalingGroup createAutoScalingGroup(LaunchTemplate launchTemplate) {
    // Use annotated field values for AutoScaling Group configuration
    int minCapacity = minInstanceCapacity != null ? minInstanceCapacity : 1;
    int maxCapacity = maxInstanceCapacity != null ? maxInstanceCapacity : 1;
    int desiredCapacity = Math.max(minCapacity, Math.min(maxCapacity, minCapacity)); // Start with minimum

    // Determine subnet type based on network mode
    SubnetType subnetType = "public-no-nat".equals(networkMode) ?
            SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;

    if (vpc == null) {
      throw new IllegalStateException("VPC not available");
    }

    String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
    return AutoScalingGroup.Builder.create(this, appId + "Asg")
            .vpc(vpc)
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .minCapacity(minCapacity)
            .desiredCapacity(desiredCapacity)
            .maxCapacity(maxCapacity)
            .launchTemplate(launchTemplate)
            .build();
  }

}
