package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforgeci.api.storage.ContainerFactory;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.iam.Role;
import software.constructs.Construct;

import java.util.List;

// Removed static imports - now using ApplicationSpec

/**
 * Factory for creating Fargate-based Jenkins compute infrastructure.
 *
 * <p>This factory creates and configures AWS Fargate services for Jenkins deployments,
 * providing a serverless container-based approach. It respects network mode configuration
 * to place tasks in appropriate subnets and handles EFS integration for persistent storage.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Fargate task definitions with Jenkins container</li>
 *   <li>ECS cluster and service configuration</li>
 *   <li>EFS access point integration for persistent storage</li>
 *   <li>IAM roles for task execution and EFS access</li>
 *   <li>Network mode awareness (public vs private subnets)</li>
 *   <li>Security group configuration</li>
 * </ul>
 *
 * <p><strong>Network Configuration:</strong></p>
 * <ul>
 *   <li><strong>public-no-nat:</strong> Tasks get public IPs and use public subnets</li>
 *   <li><strong>private-with-nat:</strong> Tasks use private subnets with NAT gateway</li>
 * </ul>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * FargateFactory factory = new FargateFactory(scope, "JenkinsFargate");
 * factory.create();
 *
 * // Access created resources
 * FargateService service = ctx.fargateService.get().orElseThrow();
 * FargateTaskDefinition taskDef = ctx.fargateTaskDef.get().orElseThrow();
 * }</pre>
 *
 * @author CloudForgeCI
 * @since 1.0.0
 * @see DeploymentContext
 * @see SystemContext
 * @see ContainerFactory
 * @see DeploymentContext#networkMode()
 */
public class FargateFactory extends BaseFactory {

  @DeploymentContext("bastionCidr")
  private String bastionCidr;

  @DeploymentContext("cpu")
  private Integer cpu;

  @DeploymentContext("memory")
  private Integer memory;

  @DeploymentContext("minInstanceCapacity")
  private Integer minInstanceCapacity;

  @DeploymentContext("maxInstanceCapacity")
  private Integer maxInstanceCapacity;

  @DeploymentContext("cpuTargetUtilization")
  private Integer cpuTargetUtilization;

  @DeploymentContext("networkMode")
  private String networkMode;

  @DeploymentContext("healthCheckGracePeriod")
  private Integer healthCheckGracePeriod;

  @com.cloudforge.core.annotation.SystemContext("fargateExecutionRole")
  private Role fargateExecutionRole;

  @com.cloudforge.core.annotation.SystemContext("fargateTaskRole")
  private Role fargateTaskRole;

  @com.cloudforge.core.annotation.SystemContext("applicationSpec")
  private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

  @com.cloudforge.core.annotation.SystemContext("efs")
  private software.amazon.awscdk.services.efs.FileSystem efs;

  @com.cloudforge.core.annotation.SystemContext("vpc")
  private software.amazon.awscdk.services.ec2.Vpc vpc;

  @com.cloudforge.core.annotation.SystemContext("albSg")
  private SecurityGroup albSg;

  @com.cloudforge.core.annotation.SystemContext("efsSg")
  private SecurityGroup efsSg;

  @com.cloudforge.core.annotation.SystemContext("ap")
  private AccessPoint ap;

  @DeploymentContext("authMode")
  private String authMode;

  @DeploymentContext("stackName")
  private String stackName;

  @DeploymentContext("region")
  private String region;

  /**
   * Creates a new FargateFactory instance.
   *
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Fargate factory
   */
  public FargateFactory(Construct scope, String id) {
    super(scope, id);
    // All fields are automatically injected by BaseFactory
  }

  @Override
  public void create() {
    // Set scaling configuration in SystemContext slots so topology can wire auto-scaling
    if (minInstanceCapacity != null) {
      ctx.minInstanceCapacity.set(minInstanceCapacity);
    }
    if (maxInstanceCapacity != null) {
      ctx.maxInstanceCapacity.set(maxInstanceCapacity);
    }
    if (cpuTargetUtilization != null) {
      ctx.cpuTargetUtilization.set(cpuTargetUtilization);
    }

    // Use IAM roles from SystemContext created by IAM configuration system
    // These roles are security-profile aware and provide consistent permissions
    if (fargateExecutionRole == null) {
      throw new IllegalStateException("Fargate execution role not found - IAM configuration should have created it");
    }
    if (fargateTaskRole == null) {
      throw new IllegalStateException("Fargate task role not found - IAM configuration should have created it");
    }

    // Check if ECS Exec should be enabled based on bastionCidr configuration
    boolean enableEcsExec = bastionCidr != null && !bastionCidr.isBlank();

    FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "Task")
            .cpu(cpu)
            .memoryLimitMiB(memory)
            .executionRole(fargateExecutionRole)
            .taskRole(fargateTaskRole)
            .build();

    // Validate that EFS and Access Point are available (created by EfsFactory)
    if (efs == null) {
      throw new IllegalStateException("EFS not available - EfsFactory should have created it");
    }
    if (ap == null) {
      throw new IllegalStateException("EFS Access Point not available - EfsFactory should have created it");
    }

    if (vpc == null) {
      throw new IllegalStateException("VPC not available");
    }

    Cluster cluster = Cluster.Builder.create(this, "Cluster").vpc(vpc).build();
    SecurityGroup serviceSg = SecurityGroup.Builder.create(this, getNode().getId() + "SvcSg")
            .vpc(vpc)
            .allowAllOutbound(true).build();
    ctx.fargateServiceSg.set(serviceSg);
    // Determine subnet type and public IP assignment based on network mode
    boolean assignPublicIp = "public-no-nat".equals(networkMode);
    SubnetType subnetType = assignPublicIp ? SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;

    // Enable ECS Exec only if bastionCidr is configured (indicates remote access needed)
    FargateService service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(minInstanceCapacity != null ? minInstanceCapacity : 1)
            .assignPublicIp(assignPublicIp)
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .enableExecuteCommand(enableEcsExec)  // Enable ECS Exec for shell access when bastionCidr is set
            .enableEcsManagedTags(true)  // Helps CloudFormation track and clean up ENIs on stack deletion
            .circuitBreaker(DeploymentCircuitBreaker.builder()
                    .enable(true)
                    .rollback(true)
                    .build())  // Prevents stuck deployments and enables automatic rollback
            .build();

    // Set health check grace period (critical for slow-starting apps like GitLab)
    // Must be set on the underlying CfnService after FargateService creation
    int gracePeriodSeconds = healthCheckGracePeriod != null ? healthCheckGracePeriod : 300;
    CfnService cfnService = (CfnService) service.getNode().getDefaultChild();
    cfnService.setHealthCheckGracePeriodSeconds(gracePeriodSeconds);

    // Add dependency on Cognito client secret Custom Resource if it exists
    // This ensures the secret is created in Secrets Manager BEFORE ECS tries to pull it
    ctx.cognitoClientSecretResourceInternal.get().ifPresent(secretResource -> {
        service.getNode().addDependency(secretResource);
    });

    // Set task definition in context first (needed by ContainerFactory)
    // Note: EFS permissions are handled by IAMRules based on security profile
    ctx.fargateTaskDef.set(taskDef);

    // Add EFS volume to task definition (needed by ContainerFactory)
    String volumeName = applicationSpec != null ? applicationSpec.volumeName() : "jenkinsHome";
    ctx.fargateTaskDef.get().orElseThrow().addVolume(Volume.builder()
            .name(volumeName)
            .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                    .fileSystemId(efs.getFileSystemId())
                    .transitEncryption("ENABLED")
                    .authorizationConfig(AuthorizationConfig.builder()
                            .accessPointId(ap.getAccessPointId())
                            .iam("ENABLED")
                            .build())
                    .build())
            .build());

    // Create container (now that task definition and volume are available)
    // Get container image from ApplicationSpec or use default
    String containerImage = applicationSpec != null ? applicationSpec.defaultContainerImage() : "jenkins/jenkins:lts";
    ContainerFactory containerFactory = new ContainerFactory(this, getNode().getId() + "Container", ContainerImage.fromRegistry(containerImage));
    containerFactory.create();

    // Now set the service in context after container is created
    ctx.fargateService.set(service);

    // Configure security group rules (migrated from JenkinsBootstrap)
    configureSecurityGroupRules(serviceSg);

    // Create CloudFormation output for application URL (migrated from JenkinsBootstrap)
    createApplicationUrlOutput();

    // Note: Auto-scaling is handled by JenkinsServiceTopologyConfiguration
    // to avoid conflicts with duplicate auto-scaling configuration
  }

  /**
   * Configure security group rules for Fargate service.
   * Allows EFS access from Fargate and HTTP traffic from ALB.
   *
   * <p>This logic was migrated from JenkinsBootstrap to consolidate
   * Fargate-specific configuration in one place.</p>
   */
  private void configureSecurityGroupRules(SecurityGroup serviceSg) {
    // Allow NFS traffic from Fargate service to EFS
    if (efsSg != null) {
      efsSg.addIngressRule(serviceSg, Port.tcp(2049), "NFS_from_Fargate_service", false);
    }

    // Allow HTTP traffic from ALB to Fargate service
    if (albSg != null) {
      int appPort = applicationSpec != null ? applicationSpec.applicationPort() : 8080;
      serviceSg.addIngressRule(albSg, Port.tcp(appPort), "HTTP_from_ALB", false);
    }
  }

  /**
   * Create CloudFormation output for application URL.
   * Uses applicationId from ApplicationSpec to create generic output.
   *
   * <p>This logic was migrated from JenkinsBootstrap and made generic
   * to support any application type.</p>
   */
  private void createApplicationUrlOutput() {
    // Only create output if ALB is available
    if (ctx.alb.get().isEmpty()) {
      return;
    }

    String appId = applicationSpec != null ? applicationSpec.applicationId() : "application";
    String outputId = appId.substring(0, 1).toUpperCase() + appId.substring(1) + "Url";
    String description = appId.substring(0, 1).toUpperCase() + appId.substring(1) + " URL (ALB DNS)";

    CfnOutput.Builder.create(this, outputId)
            .description(description)
            .value("http://" + ctx.alb.get().get().getLoadBalancerDnsName())
            .build();
  }

}
