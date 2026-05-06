package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.storage.ContainerFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.iam.Role;
import software.constructs.Construct;
import io.github.cdklabs.cdknag.NagSuppressions;
import io.github.cdklabs.cdknag.NagPackSuppression;

import java.util.List;
import java.util.logging.Logger;

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
 * @see com.cloudforgeci.api.core.DeploymentContext#networkMode()
 */
public class FargateFactory extends BaseFactory {

  private static final Logger LOG = Logger.getLogger(FargateFactory.class.getName());

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
  private NetworkMode networkMode;

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

  @SystemContext("efsSg")
  private SecurityGroup efsSg;

  @SystemContext("security")
  private SecurityProfile security;

  @com.cloudforge.core.annotation.SystemContext("ap")
  private AccessPoint ap;

  @DeploymentContext("authMode")
  private AuthMode authMode;

  @DeploymentContext("stackName")
  private String stackName;

  @DeploymentContext("region")
  private String region;

  @DeploymentContext("containerImage")
  private String containerImage;

  // ========== Optional Port Configuration ==========
  // These flags control which optional ports are exposed in security groups
  // Ports are NOT exposed by default - must be explicitly enabled

  @DeploymentContext("enableAgents")
  private Boolean enableAgents;

  @DeploymentContext("enableSsh")
  private Boolean enableSsh;

  @DeploymentContext("enableSmtp")
  private Boolean enableSmtp;

  @DeploymentContext("enableSmtps")
  private Boolean enableSmtps;

  @DeploymentContext("enableClustering")
  private Boolean enableClustering;

  @DeploymentContext("enableDockerRegistry")
  private Boolean enableDockerRegistry;

  @DeploymentContext("enableMetrics")
  private Boolean enableMetrics;

  @DeploymentContext("enableNotary")
  private Boolean enableNotary;

  @DeploymentContext("enableTrivy")
  private Boolean enableTrivy;

  @DeploymentContext("enableSentinel")
  private Boolean enableSentinel;

  @DeploymentContext("enableCluster")
  private Boolean enableCluster;

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
    // Priority: DeploymentContext > SecurityProfileConfiguration > default
    Integer effectiveMinCapacity = minInstanceCapacity;
    Integer effectiveMaxCapacity = maxInstanceCapacity;

    if (effectiveMinCapacity == null && config != null) {
      effectiveMinCapacity = config.getMinInstanceCount();
      LOG.info("Min instance capacity inherited from security profile: " + effectiveMinCapacity);
    }
    if (effectiveMaxCapacity == null && config != null) {
      effectiveMaxCapacity = config.getMaxInstanceCount();
      LOG.info("Max instance capacity inherited from security profile: " + effectiveMaxCapacity);
    }

    if (effectiveMinCapacity != null) {
      ctx.minInstanceCapacity.set(effectiveMinCapacity);
    }
    if (effectiveMaxCapacity != null) {
      ctx.maxInstanceCapacity.set(effectiveMaxCapacity);
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

    // Add CDK-nag suppressions for standard ECS patterns
    NagSuppressions.addResourceSuppressions(taskDef, List.of(
        NagPackSuppression.builder()
            .id("AwsSolutions-ECS2")
            .reason("Environment variables are used for non-sensitive container configuration. " +
                    "Sensitive values like secrets use AWS Secrets Manager references.")
            .build()
    ), true);

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

    // Enable Container Insights for PRODUCTION/STAGING profiles (PCI-DSS compliance)
    boolean enableContainerInsights = security != SecurityProfile.DEV;
    Cluster cluster = Cluster.Builder.create(this, "Cluster")
            .vpc(vpc)
            .containerInsights(enableContainerInsights)
            .build();

 
    cluster.applyRemovalPolicy(RemovalPolicy.DESTROY);

    // Check if egress should be restricted to VPC CIDR only (only for private subnets)
    boolean restrictEgress = config != null && config.isRestrictSecurityGroupEgressEnabled()
        && networkMode != NetworkMode.PUBLIC;

    SecurityGroup serviceSg = SecurityGroup.Builder.create(this, getNode().getId() + "SvcSg")
            .vpc(vpc)
            .description("Fargate Service Security Group")
            .allowAllOutbound(!restrictEgress)
            .build();

    // If egress is restricted, add explicit egress rule for VPC CIDR only
    if (restrictEgress) {
      serviceSg.addEgressRule(
          Peer.ipv4(vpc.getVpcCidrBlock()),
          Port.allTraffic(),
          "Allow egress to VPC CIDR only"
      );
    }

    ctx.fargateServiceSg.set(serviceSg);
    // Determine subnet type and public IP assignment based on network mode
    boolean assignPublicIp = networkMode == NetworkMode.PUBLIC;
    SubnetType subnetType = assignPublicIp ? SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;

    // Enable ECS Exec only if bastionCidr is configured (indicates remote access needed)
    FargateService service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(effectiveMinCapacity != null ? effectiveMinCapacity : 1)
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
    // Priority: deployment context > application spec > default (300)
    int defaultGracePeriod = applicationSpec != null ? applicationSpec.defaultHealthCheckGracePeriod() : 300;
    int gracePeriodSeconds = healthCheckGracePeriod != null ? healthCheckGracePeriod : defaultGracePeriod;
    CfnService cfnService = (CfnService) service.getNode().getDefaultChild();
    cfnService.setHealthCheckGracePeriodSeconds(gracePeriodSeconds);

    // Add dependency on Cognito client secret Custom Resource if it exists
    // This ensures the secret is created in Secrets Manager BEFORE ECS tries to pull it
    ctx.cognitoClientSecretResourceInternal.get().ifPresent(secretResource -> {
        service.getNode().addDependency(secretResource);
    });

    // Add dependency on RDS database instance if it exists
    // This ensures the database is fully available BEFORE the Fargate service starts
    // Critical for applications like GitLab that fail if database isn't ready during initialization
    ctx.rdsDatabase.get().ifPresent(dbInstance -> {
        service.getNode().addDependency(dbInstance);
        LOG.info("Added dependency: Fargate service will wait for RDS database to be available");
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
    String image = applicationSpec != null ? applicationSpec.defaultContainerImage() : "jenkins/jenkins:lts";
    // Allow DeploymentContext.containerImage to override the tag portion (after ':')
    if (this.containerImage != null && !this.containerImage.isBlank()) {
        int colonIndex = image.lastIndexOf(':');
        String baseImage = colonIndex > 0 ? image.substring(0, colonIndex) : image;
        image = baseImage + ":" + this.containerImage;
    }
    ContainerFactory containerFactory = new ContainerFactory(this, getNode().getId() + "Container", ContainerImage.fromRegistry(image));
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
    // NFS traffic (Fargate -> EFS) is handled by security profile configurations
    // (DevSecurityConfiguration, StagingSecurityConfiguration, ProductionSecurityConfiguration)

    // Allow HTTP traffic from ALB to Fargate service
    if (albSg != null) {
      int appPort = applicationSpec != null ? applicationSpec.applicationPort() : 8080;
      serviceSg.addIngressRule(albSg, Port.tcp(appPort), "HTTP_from_ALB", false);
    }

    // Allow database traffic from Fargate service to RDS (for applications with external database)
    ctx.dbSecurityGroup.get().ifPresent(dbSg -> {
      ctx.dbConnection.get().ifPresent(dbConn -> {
        int dbPort = dbConn.port();
        dbSg.addIngressRule(serviceSg, Port.tcp(dbPort), "Database_from_Fargate_service", false);
        LOG.info("Added security group rule: Fargate -> RDS on port " + dbPort);
      });
    });

    // Add security group rules for optional inbound ports
    // These are NOT exposed by default - must be explicitly enabled via deployment config
    if (applicationSpec != null) {
      for (ApplicationSpec.OptionalPort optionalPort : applicationSpec.optionalPorts()) {
        // Only add ingress rules for inbound ports that are enabled
        if (optionalPort.inbound() && isOptionalPortEnabled(optionalPort.configKey())) {
          Port port = optionalPort.protocol().equals("udp")
              ? Port.udp(optionalPort.port())
              : Port.tcp(optionalPort.port());
          // Allow from anywhere for optional service ports (e.g., SSH, JNLP agents)
          serviceSg.addIngressRule(
              software.amazon.awscdk.services.ec2.Peer.anyIpv4(),
              port,
              optionalPort.service().replace(" ", "_") + "_inbound",
              false
          );
          LOG.info("  ✅ Added security group rule for optional port: " +
                   optionalPort.port() + "/" + optionalPort.protocol() +
                   " (" + optionalPort.service() + ")");
        }
      }
    }
  }

  /**
   * Check if an optional port is enabled based on the config key.
   */
  private boolean isOptionalPortEnabled(String configKey) {
    return switch (configKey) {
      case "enableAgents" -> Boolean.TRUE.equals(enableAgents);
      case "enableSsh" -> Boolean.TRUE.equals(enableSsh);
      case "enableSmtp" -> Boolean.TRUE.equals(enableSmtp);
      case "enableSmtps" -> Boolean.TRUE.equals(enableSmtps);
      case "enableClustering" -> Boolean.TRUE.equals(enableClustering);
      case "enableDockerRegistry" -> Boolean.TRUE.equals(enableDockerRegistry);
      case "enableMetrics" -> Boolean.TRUE.equals(enableMetrics);
      case "enableNotary" -> Boolean.TRUE.equals(enableNotary);
      case "enableTrivy" -> Boolean.TRUE.equals(enableTrivy);
      case "enableSentinel" -> Boolean.TRUE.equals(enableSentinel);
      case "enableCluster" -> Boolean.TRUE.equals(enableCluster);
      default -> {
        LOG.warning("Unknown optional port config key: " + configKey);
        yield false;
      }
    };
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
