package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.storage.ContainerFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
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

  @DeploymentContext("complianceFrameworks")
  private String complianceFrameworks;

  @DeploymentContext("complianceMode")
  private ComplianceMode complianceMode;

  @DeploymentContext("awsConfigEnabled")
  private Boolean awsConfigEnabled;

  @DeploymentContext("auditManagerEnabled")
  private Boolean auditManagerEnabled;

  @DeploymentContext("guardDutyEnabled")
  private Boolean guardDutyEnabled;

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

    // ECS Exec enables shell access to running Fargate tasks via SSM Session Manager.
    // No open ports or SSH keys required — access is IAM-controlled and CloudTrail-logged.
    // The required ssmmessages:* permissions are already included in all IAM configurations.
    // Enable unconditionally; restrict access via IAM policies rather than at the CDK level.
    boolean enableEcsExec = true;

    // Explicit, not omitted -- omitting runtimePlatform entirely defaults a task definition to
    // X86_64, which only matches applicationSpec.cpuArchitecture()'s own default by coincidence.
    // Every built-in ApplicationSpec keeps that X86_64 default (their images are x86_64-only or
    // x86_64-primary); cloudforge-manager's own spec overrides it to ARM64, since its native-image
    // pipeline publishes arm64-only images now.
    CpuArchitecture cpuArchitecture = applicationSpec.cpuArchitecture()
        == com.cloudforge.core.enums.CpuArchitecture.ARM64
        ? CpuArchitecture.ARM64
        : CpuArchitecture.X86_64;

    FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "Task")
            .cpu(cpu)
            .memoryLimitMiB(memory)
            .runtimePlatform(RuntimePlatform.builder()
                .cpuArchitecture(cpuArchitecture)
                .operatingSystemFamily(OperatingSystemFamily.LINUX)
                .build())
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

    // ECS Exec is enabled unconditionally (see enableEcsExec above) -- not gated on bastionCidr;
    // access is IAM-controlled and CloudTrail-logged rather than restricted at the CDK level.
    FargateService.Builder serviceBuilder = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(effectiveMinCapacity != null ? effectiveMinCapacity : 1)
            .assignPublicIp(assignPublicIp)
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .enableExecuteCommand(enableEcsExec)  // SSM-based shell access; no port 22 needed
            .enableEcsManagedTags(true)  // Helps CloudFormation track and clean up ENIs on stack deletion
            .circuitBreaker(DeploymentCircuitBreaker.builder()
                    .enable(true)
                    .rollback(true)
                    .build());  // Prevents stuck deployments and enables automatic rollback

    // An application whose persistence can't tolerate a normal rolling deployment's brief overlap
    // (e.g. a single-writer embedded-file database) declares that via
    // ApplicationSpec#requiresSequentialDeploymentWithoutDatabase — see its own javadoc. ECS's own
    // default (minHealthyPercent=50/maxPercent=200) starts the new task BEFORE stopping the old
    // one; two tasks briefly holding the same file open at once loses the lock race and crashes
    // the new task on startup, tripping the deployment circuit breaker above and rolling the whole
    // update back. Forcing a stop-then-start replacement (0%/100%) trades a few seconds of
    // downtime per deploy for deploys actually succeeding. Only applies while this application has
    // no managed database connection provisioned (see DatabaseSpec) — once it does, a real
    // database safely handles the overlap and this no longer applies regardless of what the
    // application declares.
    //
    // AvailabilityZoneRebalancing.DISABLED has to go with it: ECS rejects maxHealthyPercent<=100
    // outright ("does not support maximumPercent <= 100% as deployment configuration" — confirmed
    // live) while AZ Rebalancing is on, which is apparently ECS's own default for a new service.
    // Rebalancing tasks across AZs is meaningless anyway for a desiredCount=1 singleton — there's
    // only ever one task to place, nothing to rebalance.
    if (applicationSpec != null && applicationSpec.requiresSequentialDeploymentWithoutDatabase()
        && ctx.dbConnection.get().isEmpty()) {
      serviceBuilder = serviceBuilder.minHealthyPercent(0).maxHealthyPercent(100)
          .availabilityZoneRebalancing(software.amazon.awscdk.services.ecs.AvailabilityZoneRebalancing.DISABLED);
    }

    FargateService service = serviceBuilder.build();

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
    String url = "http://" + ctx.alb.get().get().getLoadBalancerDnsName();

    CfnOutput.Builder.create(this, outputId)
            .description(description)
            .value(url)
            .build();

    // Stable, app-agnostic alias: CloudFormationInventory.preferredUrl (and every other
    // CFN-output consumer) looks for the literal OutputKey "ApplicationUrl", not the per-app
    // "{AppId}Url" key above — without this, AWS deployments never resolve an "Open" link
    // or health-check URL, only LocalStack/MiniStack (which emit their own fixed-name
    // outputs from a different code path). Created directly on the Stack (not `this`,
    // the nested FargateFactory construct) so CDK's logical-id synthesis doesn't need an
    // 8-char disambiguation hash — a construct one level deep needs it to stay globally
    // unique, but that hash would defeat the whole point of a stable, predictable key.
    CfnOutput.Builder.create(software.amazon.awscdk.Stack.of(this), "ApplicationUrl")
            .description("Application URL (ALB DNS)")
            .value(url)
            .build();

    createDeploymentMetadataOutputs();
  }

  /**
   * Publish non-secret deployment posture with the stack. This is the
   * control-plane source of truth for Manager inventory and compliance views;
   * it deliberately avoids depending on a local deployment-context file.
   */
  private void createDeploymentMetadataOutputs() {
    output("CloudForgeSecurityProfile", "CloudForge security profile",
        ctx.security == null ? "unknown" : ctx.security.name());
    output("CloudForgeComplianceFrameworks", "CloudForge compliance frameworks",
        complianceFrameworks == null || complianceFrameworks.isBlank() ? "" : complianceFrameworks);
    output("CloudForgeComplianceMode", "CloudForge compliance mode",
        complianceMode == null ? "ADVISORY" : complianceMode.name());
    output("CloudForgeAwsConfigEnabled", "CloudForge AWS Config enabled",
        String.valueOf(Boolean.TRUE.equals(awsConfigEnabled)));
    output("CloudForgeAuditManagerEnabled", "CloudForge Audit Manager enabled",
        String.valueOf(Boolean.TRUE.equals(auditManagerEnabled)));
    output("CloudForgeGuardDutyEnabled", "CloudForge GuardDuty enabled",
        String.valueOf(Boolean.TRUE.equals(guardDutyEnabled)));
  }

  private void output(String id, String description, String value) {
    CfnOutput.Builder.create(this, id)
        .description(description)
        .value(value)
        .build();
  }

}
