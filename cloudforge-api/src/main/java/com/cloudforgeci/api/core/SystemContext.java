package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforgeci.api.core.rules.ComplianceMatrix;
import com.cloudforgeci.api.core.rules.Rules;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.observability.LoggingCwFactory;
import com.cloudforgeci.api.observability.GuardDutyFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.storage.ContainerFactory;
import com.cloudforgeci.api.observability.AlarmFactory;
import com.cloudforgeci.api.compute.Ec2Factory;
// Note: S3BucketFactory, CloudFrontFactory, and Ec2InstanceFactory will be created later
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.security.ApplicationOidcFactory;
import com.cloudforgeci.api.security.ApplicationSamlFactory;
import com.cloudforgeci.api.security.CertificateFactory;
import com.cloudforgeci.api.security.CognitoAuthenticationFactory;
import com.cloudforgeci.api.security.IdentityCenterFactory;
import com.cloudforgeci.api.security.IdentityCenterSamlFactory;
import com.cloudforgeci.api.security.OidcAuthenticationFactory;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.constructs.Construct;
import software.constructs.IConstruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class SystemContext extends Construct {

  private static final String NODE_ID = "SystemContext";
    private static final Logger LOG = Logger.getLogger(SystemContext.class.getName());

  public final TopologyType topology;
  public final RuntimeType runtime;
  public final SecurityProfile security;
  public final IAMProfile iamProfile;
  public final DeploymentContext cfc;
  public final String stackName;

  // Security Profile Configuration
  public final Slot<SecurityProfileConfiguration> securityProfileConfig = new Slot<>();

  // Application Specification
  public final Slot<com.cloudforge.core.interfaces.ApplicationSpec> applicationSpec = new Slot<>();

  // Common slots
  public final Slot<software.amazon.awscdk.services.ec2.Vpc> vpc = new Slot<>();
  public final Slot<software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer> alb = new Slot<>();
  public final Slot<software.amazon.awscdk.services.autoscaling.AutoScalingGroup> asg = new Slot<>();
  public final Slot<software.amazon.awscdk.services.ec2.Instance> ec2Instance = new Slot<>();
  public final Slot<software.amazon.awscdk.services.efs.FileSystem> efs = new Slot<>();
  public final Slot<software.amazon.awscdk.services.logs.LogGroup> logs = new Slot<>();
  public final Slot<software.amazon.awscdk.services.route53.IHostedZone> zone = new Slot<>();
  public final Slot<software.amazon.awscdk.services.ec2.SecurityGroup> instanceSg = new Slot<>();

  // ALB Properties
  public final Slot<ApplicationTargetGroup> albTargetGroup = new Slot<>();
  public final Slot<Boolean> httpsTargetsAdded = new Slot<>();
  public final Slot<Boolean> wired = new Slot<>();
  public final Slot<Boolean> dnsRecordsCreated = new Slot<>();
  public final Slot<Boolean> dnsRecordsCallbackRegistered = new Slot<>();
  public final Slot<Boolean> asgAddedToTargetGroup = new Slot<>();
  public final Slot<Boolean> scalingPoliciesApplied = new Slot<>();
  public final Slot<Boolean> fargateAutoscalingConfigured = new Slot<>();
  public final Slot<Boolean> fargateAutoscalingCallbackRegistered = new Slot<>();
  public final Slot<Boolean> ec2AutoscalingCallbackRegistered = new Slot<>();
  public final Slot<SecurityGroup> albSg = new Slot<>();
  public final Slot<ApplicationListener> http = new Slot<>();

  // EFS Properties
  public final Slot<SecurityGroup> efsSg = new Slot<>();
  public final Slot<AccessPoint> ap = new Slot<>();

  // Fargate Properties
  public final Slot<FargateService> fargateService = new Slot<>();
  public final Slot<SecurityGroup> fargateServiceSg = new Slot<>();
  public final Slot<TaskDefinition> fargateTaskDef = new Slot<>();

  // ECS Container Properties
  public final Slot<ContainerDefinition> container = new Slot<>();

  // Certificate Properties
  public final Slot<ApplicationListener> https = new Slot<>();
  public final Slot<software.amazon.awscdk.services.certificatemanager.ICertificate> cert = new Slot<>();
  public final Slot<software.amazon.awscdk.services.acmpca.CfnCertificateAuthority> privateCa = new Slot<>();

  // Identity Center Properties (for auto-provisioned OIDC)
  public final Slot<software.amazon.awscdk.CustomResource> identityCenter = new Slot<>();

  // Cognito Properties (for Cognito User Pool OIDC)
  public final Slot<String> cognitoIssuer = new Slot<>();
  public final Slot<String> cognitoAuthorizationEndpoint = new Slot<>();
  public final Slot<String> cognitoTokenEndpoint = new Slot<>();
  public final Slot<String> cognitoUserInfoEndpoint = new Slot<>();
  public final Slot<String> cognitoLogoutEndpoint = new Slot<>();
  public final Slot<String> cognitoClientId = new Slot<>();
  public final Slot<String> cognitoClientSecretName = new Slot<>();
  public final Slot<String> cognitoUserPoolId = new Slot<>();
  public final Slot<String> cognitoDomainPrefix = new Slot<>();

  // Cognito CDK Objects (for native ALB Cognito authentication)
  public final Slot<software.amazon.awscdk.services.cognito.IUserPool> cognitoUserPool = new Slot<>();
  public final Slot<software.amazon.awscdk.services.cognito.IUserPoolClient> cognitoUserPoolClient = new Slot<>();
  public final Slot<software.amazon.awscdk.services.cognito.IUserPoolDomain> cognitoUserPoolDomain = new Slot<>();

  // Database Properties (RDS)
  public final Slot<software.amazon.awscdk.services.rds.DatabaseInstance> rdsDatabase = new Slot<>();
  public final Slot<software.amazon.awscdk.services.secretsmanager.Secret> dbCredentials = new Slot<>();
  public final Slot<com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection> dbConnection = new Slot<>();
  public final Slot<SecurityGroup> dbSecurityGroup = new Slot<>();
  // Connection string components for distroless containers (Mattermost) that can't do shell substitution
  public final Slot<java.util.Map<String, String>> dbConnectionStringComponents = new Slot<>();
  // SSM Parameter containing the complete datasource URL (for distroless containers)
  // Store the actual parameter object to avoid lookup issues
  public final Slot<software.amazon.awscdk.services.ssm.StringParameter> dbDatasourceParameter = new Slot<>();

  // INTERNAL USE ONLY: Cognito client secret Custom Resource (for CDK dependency tracking)
  // This is used internally to ensure ECS tasks don't start before the secret is created in Secrets Manager.
  // Do not use this field directly - it is managed automatically by CognitoAuthenticationFactory.
  public final Slot<software.constructs.IConstruct> cognitoClientSecretResourceInternal = new Slot<>();

  // Application-level OIDC configuration (for application-oidc mode)
  // Built by ApplicationOidcFactory and used by ApplicationSpec implementations
  public final Slot<com.cloudforge.core.interfaces.OidcConfiguration> applicationOidcConfig = new Slot<>();

  // INTERNAL USE ONLY: Application OIDC client secret Custom Resource (for CDK dependency tracking)
  // This is used internally to ensure ECS tasks don't start before the secret is created in Secrets Manager.
  // Do not use this field directly - it is managed automatically by ApplicationOidcFactory.
  public final Slot<software.constructs.IConstruct> applicationOidcClientSecretResource = new Slot<>();

  // Keycloak SAML Bridge Deployment Tracking (for cognito-saml provider)
  // Tracks whether Keycloak has been deployed in this stack (shared by all SAML apps)
  public final Slot<Boolean> keycloakDeployed = new Slot<>();
  public final Slot<String> keycloakServiceUrl = new Slot<>(); // e.g., https://auth.example.com

  // SSL Configuration Properties
  public final Slot<Boolean> sslEnabled = new Slot<>();
  public final Slot<Boolean> httpRedirectEnabled = new Slot<>();

  // Networking Configuration Properties (used in SecurityProfile)
  public final Slot<String> networkMode = new Slot<>(); // "public-no-nat" | "private-with-nat"
  public final Slot<Boolean> wafEnabled = new Slot<>();
  public final Slot<Boolean> cloudfront = new Slot<>();
  public final Slot<String> lbType = new Slot<>(); // "alb" | "nlb"

  // Auto Scaling Configuration Properties (used in RuntimeConfiguration)
  public final Slot<Integer> minInstanceCapacity = new Slot<>();
  public final Slot<Integer> maxInstanceCapacity = new Slot<>();
  public final Slot<Integer> cpuTargetUtilization = new Slot<>();

  // Container Configuration Properties (used in FargateRuntimeConfiguration)
  public final Slot<Integer> cpu = new Slot<>();
  public final Slot<Integer> memory = new Slot<>();

  // Authentication Configuration Properties (used in SecurityProfile)
  public final Slot<String> authMode = new Slot<>(); // "none" | "alb-oidc" | "jenkins-oidc" | "application-oidc"
  public final Slot<String> ssoInstanceArn = new Slot<>();
  public final Slot<String> ssoGroupId = new Slot<>();
  public final Slot<String> ssoTargetAccountId = new Slot<>();

  // SAML Configuration (used by CognitoSamlFactory for Cognito SAML IdP)
  // These slots are provider-agnostic and work with Cognito SAML
  public final Slot<String> samlSiteUrl = new Slot<>();           // SP site URL (Entity ID)
  public final Slot<String> samlAcsUrl = new Slot<>();            // Assertion Consumer Service URL
  public final Slot<String> samlIdpMetadataUrl = new Slot<>();    // IdP SAML metadata URL
  public final Slot<String> samlIdpSsoUrl = new Slot<>();         // IdP SSO endpoint URL
  public final Slot<String> samlIdpEntityId = new Slot<>();       // IdP Entity ID (issuer)
  public final Slot<String> samlIdpLogoutUrl = new Slot<>();      // IdP SLO endpoint URL
  public final Slot<String> samlProviderType = new Slot<>();      // Provider type: "cognito"
  public final Slot<String> samlConfigSecretArn = new Slot<>();   // Secrets Manager ARN for IdP config

  // Storage Configuration Properties (used in IAMConfiguration)
  public final Slot<String> artifactsBucket = new Slot<>();
  public final Slot<String> artifactsPrefix = new Slot<>();
  public final Slot<Boolean> enableFlowlogs = new Slot<>();

  // DNS Configuration Properties (used in TopologyConfiguration)
  public final Slot<String> domain = new Slot<>();
  public final Slot<String> subdomain = new Slot<>();
  public final Slot<String> fqdn = new Slot<>();

  // S3 website bits
  public final Slot<software.amazon.awscdk.services.s3.Bucket> websiteBucket = new Slot<>();
  public final Slot<software.amazon.awscdk.services.cloudfront.Distribution> distribution = new Slot<>();

  // Logging
  public final Slot<FlowLogOptions> flowlogs = new Slot<>();

  // Security
  public final Slot<software.amazon.awscdk.services.wafv2.CfnWebACL> wafWebAcl = new Slot<>();

  // IAM Roles
  public final Slot<software.amazon.awscdk.services.iam.Role> ec2InstanceRole = new Slot<>();
  public final Slot<software.amazon.awscdk.services.iam.Role> fargateExecutionRole = new Slot<>();
  public final Slot<software.amazon.awscdk.services.iam.Role> fargateTaskRole = new Slot<>();

  private final Set<String> onceKeys = new HashSet<>();
  private final List<Runnable> deferredActions = new ArrayList<>();
  private final Set<AwsConfigRule> requiredConfigRules = new HashSet<>();
  private boolean installed = false;

  private SystemContext(Stack stack, TopologyType topology, RuntimeType runtime, SecurityProfile security, IAMProfile iamProfile, DeploymentContext cfc) {
    super(stack, NODE_ID);
    this.topology = topology;
    this.runtime = runtime;
    this.security = security;
    this.iamProfile = iamProfile;
    this.cfc = cfc;
    this.stackName = stack.getStackName();
  }

  /** Start once at the entry point; installs runtime + topology + security + iam rules and wiring. */
  public static SystemContext start(Construct scope, TopologyType topology, RuntimeType runtime, SecurityProfile security, IAMProfile iamProfile, DeploymentContext cfc) {

    try {
      Stack stack = Stack.of(scope);

      SystemContext existing = (SystemContext) stack.getNode().tryFindChild(NODE_ID);
      if (existing != null) {
        // Allow multiple runtime types in the same stack for testing purposes
        // Runtime type check removed to allow EC2 and Fargate in same stack
        if (existing.topology != topology) {
          throw new IllegalStateException("SystemContext already started with topology = " + existing.topology);
        }
        if (existing.security != security) {
          throw new IllegalStateException("SystemContext already started with security = " + existing.security);
        }
        if (existing.iamProfile != iamProfile) {
          throw new IllegalStateException("SystemContext already started with iamProfile = " + existing.iamProfile);
        }

        return existing;
      }

      var ctx = new SystemContext(stack, topology, runtime, security, iamProfile, cfc);

      if (!ctx.installed) {
        try {
          Rules.installAll(ctx);   // installs RuntimeRules, TopologyRules, SecurityRules, and IAMRules
        } catch (Exception e) {
          LOG.log(Level.SEVERE, "*** ERROR in Rules.installAll() ***", e);
          throw e;
        }
        ctx.installed = true;

        // Stack-level CDK-nag suppressions for PRODUCTION and STAGING
        // STAGING has same compliance requirements as PRODUCTION
        if (security == SecurityProfile.PRODUCTION || security == SecurityProfile.STAGING) {
          applyStackLevelSuppressions(stack, security);
        }
      } else {
      }

      return ctx;

    } catch (Exception e) {
      LOG.log(Level.SEVERE, "*** ERROR in SystemContext.start() ***", e);
      throw e;
    }
  }

  /** Fetch the already-started context anywhere down the tree. */
  public static SystemContext of(Construct scope) {
    for (Construct cur = scope; cur != null; ) {
      Construct child = (Construct) cur.getNode().tryFindChild(NODE_ID);
      if (child instanceof SystemContext sc) return sc;
      IConstruct parent = cur.getNode().getScope();
      cur = parent instanceof Construct c ? c : null;
    }
    throw new IllegalStateException("SystemContext not started yet. Call SystemContext.start(...) first.");
  }

  /** Guard to register a wiring block only once per Stack. */
  public boolean once(String key, Runnable r) {
    if (!onceKeys.add(key)) return false;
    deferredActions.add(r);
    return true;
  }

  /** Execute all deferred actions. Call this after all factories are created. */
  public void executeDeferredActions() {
    // Create a copy to avoid ConcurrentModificationException
    List<Runnable> actionsToExecute = new ArrayList<>(deferredActions);
    // Clear the original list to prevent interference from new actions
    deferredActions.clear();
    for (Runnable action : actionsToExecute) {
      try {
        action.run();
      } catch (Exception e) {
        LOG.log(Level.SEVERE, "*** Error executing deferred action ***", e);
        throw e;
      }
    }
  }

  // ============================================================================
  // AWS CONFIG RULES COLLECTOR
  // ============================================================================

  /**
   * Register an AWS Config rule as required for this deployment.
   * Factories call this method where they create the infrastructure being monitored.
   * Duplicate rules are automatically deduplicated via Set.
   *
   * @param rule The AWS Config rule to require
   */
  public void requireConfigRule(AwsConfigRule rule) {
    requiredConfigRules.add(rule);
  }

  /**
   * Register all AWS Config rules for a specific security control.
   * Use this when enabling a security control (e.g., ENCRYPTION_AT_REST)
   * to automatically include all related Config rules.
   *
   * @param control The security control to get rules for
   */
  public void requireConfigRulesForControl(ComplianceMatrix.SecurityControl control) {
    requiredConfigRules.addAll(AwsConfigRule.getRulesForControl(control));
  }

  /**
   * Get all required AWS Config rules collected from factories.
   * Called by ComplianceFactory to deploy the rules.
   *
   * @return Unmodifiable set of required Config rules
   */
  public Set<AwsConfigRule> getRequiredConfigRules() {
    return Set.copyOf(requiredConfigRules);
  }

  public String debugPath(Construct scope) {
    String here = scope.getNode().getPath();
    String sc   = this.getNode().getPath();
    return "scopePath = " + here + " | ctxPath = " + sc + " | runtime = " + runtime + " | topology = " + topology + " | security = " + security + " | iamProfile = " + iamProfile + " | slots = " + presentSlots();
  }

  public String presentSlots() {
    return "vpc = "+vpc.get().isPresent()
            +", alb = "+alb.get().isPresent()
            +", efs = "+efs.get().isPresent()
            +", http = "+http.get().isPresent()
            +", tg = "+albTargetGroup.get().isPresent()
            +", ap = "+ap.get().isPresent()
            +", asg = "+asg.get().isPresent()
            +", fargate = "+fargateService.get().isPresent()
            +", instSg = "+instanceSg.get().isPresent();
  }

  // ============================================================================
  // ORCHESTRATION LAYER - Centralized Factory Creation
  // ============================================================================

  /**
   * Creates infrastructure factories in the correct order with proper context injection.
   * This orchestration layer ensures that infrastructure factories are created consistently
   * and can be reused across different application factories.
   *
   * @param scope The CDK construct scope
   * @param idPrefix Prefix for factory IDs (e.g., "Jenkins", "MyApp")
   * @return InfrastructureFactories containing references to created factories
   */
  public InfrastructureFactories createInfrastructureFactories(Construct scope, String idPrefix) {

    // Create infrastructure factories in dependency order
    VpcFactory vpcFactory = createVpcFactory(scope, idPrefix);
    AlbFactory albFactory = createAlbFactory(scope, idPrefix);
    EfsFactory efsFactory = createEfsFactory(scope, idPrefix);
    LoggingCwFactory loggingFactory = createLoggingFactory(scope, idPrefix);

    // Create GuardDuty threat detection (account-level service)
    createGuardDutyFactory(scope, idPrefix);

    // Note: Security factories (Certificate, OIDC, Identity Center) are created
    // in JenkinsFactory after DomainFactory, as they require the hosted zone

    // Create instance security group only for EC2 deployments
    if (this.runtime == RuntimeType.EC2) {
      createInstanceSecurityGroup(scope, idPrefix);
    }

    // Create target groups (orchestrated by SystemContext)
    createTargetGroups(scope, idPrefix);


    return new InfrastructureFactories(vpcFactory, albFactory, efsFactory, loggingFactory);
  }

  /**
   * Creates a VPC factory with proper context injection.
   */
  public VpcFactory createVpcFactory(Construct scope, String idPrefix) {
    VpcFactory vpcFactory = new VpcFactory(scope, idPrefix + "Vpc");
    vpcFactory.create();
    return vpcFactory;
  }

  /**
   * Creates an ALB factory with proper context injection.
   */
  public AlbFactory createAlbFactory(Construct scope, String idPrefix) {
    AlbFactory albFactory = new AlbFactory(scope, idPrefix + "Alb");
    albFactory.create();
    return albFactory;
  }

  /**
   * Creates an EFS factory with proper context injection.
   */
  public EfsFactory createEfsFactory(Construct scope, String idPrefix) {
    EfsFactory efsFactory = new EfsFactory(scope, idPrefix + "Efs");
    efsFactory.create();
    return efsFactory;
  }

  /**
   * Creates a logging factory with proper context injection.
   */
  public LoggingCwFactory createLoggingFactory(Construct scope, String idPrefix) {
    LoggingCwFactory loggingFactory = new LoggingCwFactory(scope, idPrefix + "Logging");
    loggingFactory.create();
    return loggingFactory;
  }

  /**
   * Creates GuardDuty threat detection factory.
   * Conditionally enabled based on security profile or explicit configuration.
   */
  public void createGuardDutyFactory(Construct scope, String idPrefix) {
    GuardDutyFactory guardDutyFactory = new GuardDutyFactory(scope, idPrefix + "GuardDuty");
    guardDutyFactory.create();
  }

  /**
   * Creates security-related factories (Certificate, OIDC, Identity Center).
   * These factories are conditionally created based on context configuration.
   *
   * IMPORTANT: Certificate is created LAST to ensure proper CloudFormation deletion order.
   * When deleting a stack, CloudFormation deletes resources in reverse creation order.
   * By creating the certificate last, it will be deleted first, before the ALB HTTPS listener,
   * preventing "Certificate in use" deletion errors.
   */
  public void createSecurityFactories(Construct scope, String idPrefix) {
    // Create Cognito authentication factory (auto-provisions Cognito User Pool if configured)
    // This must run BEFORE OidcAuthenticationFactory so endpoints are available
    CognitoAuthenticationFactory cognitoFactory = new CognitoAuthenticationFactory(scope, idPrefix + "Cognito");
    cognitoFactory.create();

    // Create Identity Center factory (creates placeholder secret for manual OIDC setup)
    IdentityCenterFactory identityCenterFactory = new IdentityCenterFactory(scope, idPrefix + "IdentityCenter");
    identityCenterFactory.create();

    // Create Identity Center SAML factory (auto-provisions SAML app if autoProvisionIdentityCenter = true)
    // This creates a SAML 2.0 application in IAM Identity Center for Mattermost, etc.
    IdentityCenterSamlFactory identityCenterSamlFactory = new IdentityCenterSamlFactory(scope, idPrefix + "IdentityCenterSaml");
    identityCenterSamlFactory.create();

    // Create OIDC authentication factory (configures ALB OIDC if authMode = alb-oidc)
    // This checks for Cognito endpoints first, then IAM Identity Center, then manual config
    OidcAuthenticationFactory oidcFactory = new OidcAuthenticationFactory(scope, idPrefix + "OidcAuth");
    oidcFactory.create();

    // Create Application OIDC factory (configures application-level OIDC if authMode = application-oidc)
    // This handles OIDC integration within the application itself (Jenkins, GitLab, etc.)
    ApplicationOidcFactory applicationOidcFactory = new ApplicationOidcFactory(scope, idPrefix + "ApplicationOidc");
    applicationOidcFactory.create();

    // Create Application SAML factory (configures application-level SAML if authMode = application-oidc)
    // This handles SAML integration for applications that require SAML authentication
    // - cognito-saml: Deploys Keycloak as SAML bridge to Cognito (works with ANY SAML-enabled app)
    // - identity-center: Uses IAM Identity Center SAML (configured by IdentityCenterSamlFactory)
    ApplicationSamlFactory applicationSamlFactory = new ApplicationSamlFactory(scope, idPrefix + "ApplicationSaml");
    applicationSamlFactory.create();

    // NOTE: ComplianceFactory is now created by security profile configurations
    // (ProductionSecurityConfiguration, StagingSecurityConfiguration) to avoid duplicates
    // and ensure proper integration with security profile settings.

    // Create Certificate LAST so it's deleted FIRST during stack teardown
    // This prevents "Certificate in use" errors when deleting the HTTPS listener
    CertificateFactory certificateFactory = new CertificateFactory(scope, idPrefix + "Certificate");
    certificateFactory.create();
  }

  /**
   * Creates target groups orchestrated by SystemContext.
   * This centralizes target group management and prevents duplicates.
   *
   * For HTTPS_STRICT mode (PCI-DSS compliance), target group creation is deferred
   * to Ec2RuntimeConfiguration which creates them after the HTTPS listener exists.
   */
  public void createTargetGroups(Construct scope, String idPrefix) {

    if (this.runtime == RuntimeType.EC2) {
      // Check if HTTPS strict mode is enabled - defer to RuntimeConfiguration if so
      boolean httpsStrict = securityProfileConfig.get()
          .map(config -> config.isHttpsStrictEnabled())
          .orElse(false);
      boolean sslEnabled = Boolean.TRUE.equals(cfc.enableSsl());

      if (httpsStrict && sslEnabled) {
        // HTTPS_STRICT mode: No HTTP listener exists, HTTPS listener not yet created
        // Defer target group creation to Ec2RuntimeConfiguration.wire()
        LOG.info("HTTPS strict mode: Deferring target group creation to Ec2RuntimeConfiguration");
        return;
      }

      // Create target group for EC2 runtime (standard HTTP mode)
      ApplicationLoadBalancer alb = this.alb.get().orElseThrow(() ->
        new IllegalStateException("ALB not found when creating target groups"));

      ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(scope, idPrefix + "Tg")
          .vpc(this.vpc.get().orElseThrow())
          .port(8080)
          .protocol(ApplicationProtocol.HTTP)
          .targetType(TargetType.INSTANCE)
          .healthCheck(HealthCheck.builder()
              .path("/login")
              .healthyHttpCodes("200-299")
              .interval(software.amazon.awscdk.Duration.seconds(
                  cfc.healthCheckInterval() != null ? cfc.healthCheckInterval() : 30))
              .timeout(software.amazon.awscdk.Duration.seconds(
                  cfc.healthCheckTimeout() != null ? cfc.healthCheckTimeout() : 5))
              .healthyThresholdCount(
                  cfc.healthyThreshold() != null ? cfc.healthyThreshold() : 2)
              .unhealthyThresholdCount(
                  cfc.unhealthyThreshold() != null ? cfc.unhealthyThreshold() : 3)
              .build())
          .build();

      this.albTargetGroup.set(targetGroup);

      // Add target group to the HTTP listener
      Optional<ApplicationListener> httpListener = this.http.get();
      if (httpListener.isPresent()) {
        httpListener.get().addTargetGroups(idPrefix + "HttpTg", AddApplicationTargetGroupsProps.builder()
            .targetGroups(List.of(targetGroup))
            .build());
      } else {
        throw new IllegalStateException("HTTP listener not found when creating target groups (non-HTTPS_STRICT mode)");
      }

    } else if (this.runtime == RuntimeType.FARGATE) {
      // For Fargate, target groups are created by FargateRuntimeConfiguration
      // We should not create target groups here to avoid conflicts
    } else {
      LOG.warning("*** SystemContext: Unknown runtime type: " + this.runtime + " ***");
    }
  }

  /**
   * Creates instance security group for EC2 deployments.
   * This is infrastructure-specific but not a full factory.
   */
  public SecurityGroup createInstanceSecurityGroup(Construct scope, String idPrefix) {

    // Check if instance security group already exists
    if (this.instanceSg.get().isPresent()) {
      return this.instanceSg.get().orElseThrow();
    }

    // Check if egress should be restricted to VPC CIDR only
    boolean restrictEgress = securityProfileConfig.get()
        .map(SecurityProfileConfiguration::isRestrictSecurityGroupEgressEnabled)
        .orElse(false);

    var vpcInstance = vpc.get().orElseThrow();
    SecurityGroup instanceSg = SecurityGroup.Builder.create(scope, idPrefix + "InstanceSg")
            .vpc(vpcInstance)
            .description("EC2 Instance Security Group")
            .allowAllOutbound(!restrictEgress)
            .build();

    // If egress is restricted, add explicit egress rule for VPC CIDR only
    if (restrictEgress) {
      instanceSg.addEgressRule(
          Peer.ipv4(vpcInstance.getVpcCidrBlock()),
          Port.allTraffic(),
          "Allow egress to VPC CIDR only"
      );
    }

    this.instanceSg.set(instanceSg);
    return instanceSg;
  }

  // ============================================================================
  // DEPLOYMENT TYPE ORCHESTRATION - High-level deployment methods
  // ============================================================================

  /**
   * Creates a complete Jenkins deployment with infrastructure and Jenkins-specific resources.
   * Supports both Fargate and EC2 runtimes with optional domain and SSL.
   *
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Jenkins deployment
   * @return JenkinsDeployment containing all created resources
   */
  public JenkinsDeployment createJenkinsDeployment(Construct scope, String id) {

    // Create infrastructure factories
    InfrastructureFactories infra = createInfrastructureFactories(scope, id);

    // Create Jenkins-specific factories based on runtime
    JenkinsSpecificFactories jenkins = createJenkinsSpecificFactories(scope, id);

    // Create domain and SSL if configured
    DomainAndSslFactories domainSsl = createDomainAndSslFactories(scope, id);


    return new JenkinsDeployment(infra, jenkins, domainSsl);
  }

  /**
   * Creates a complete S3 + CloudFront deployment for static web applications.
   * Supports Angular, React, or any static site with optional domain.
   *
   * @param scope The CDK construct scope
   * @param id Unique identifier for the S3 deployment
   * @return S3CloudFrontDeployment containing all created resources
   */
  public S3CloudFrontDeployment createS3CloudFrontDeployment(Construct scope, String id) {

    // Create S3 and CloudFront factories
    S3CloudFrontFactories s3cf = createS3CloudFrontFactories(scope, id);

    // Create domain if configured
    DomainAndSslFactories domainSsl = createDomainAndSslFactories(scope, id);


    return new S3CloudFrontDeployment(s3cf, domainSsl);
  }

  /**
   * Creates Jenkins-specific factories based on the current runtime configuration.
   */
  private JenkinsSpecificFactories createJenkinsSpecificFactories(Construct scope, String id) {

    if (runtime == RuntimeType.FARGATE) {
      return createFargateJenkinsFactories(scope, id);
    } else if (runtime == RuntimeType.EC2) {
      return createEc2JenkinsFactories(scope, id);
    } else {
      throw new IllegalArgumentException("Unsupported runtime for Jenkins deployment: " + runtime);
    }
  }

  /**
   * Creates Fargate-specific Jenkins factories.
   */
  private JenkinsSpecificFactories createFargateJenkinsFactories(Construct scope, String id) {

    // Create Fargate factory
    FargateFactory fargate = new FargateFactory(scope, id + "Fargate");
    fargate.create();

    // Create container factory
    ContainerFactory container = new ContainerFactory(scope, id + "Container",
        software.amazon.awscdk.services.ecs.ContainerImage.fromRegistry("jenkins/jenkins:lts"));
    container.create();

    // JenkinsBootstrap removed - logic migrated to FargateFactory (security groups, CFN output)

    // Create alarms
    AlarmFactory alarms = new AlarmFactory(scope, id + "Alarms", null);
    alarms.create();

    return new JenkinsSpecificFactories(fargate, container, alarms, null, null);
  }

  /**
   * Creates EC2-specific Jenkins factories.
   */
  private JenkinsSpecificFactories createEc2JenkinsFactories(Construct scope, String id) {

    // Instance security group is already created by createInfrastructureFactories()
    // No need to create it again here

    // Create EC2 factory (Auto Scaling Group) if maxInstanceCapacity is provided
    Ec2Factory ec2 = null;
    if (cfc.maxInstanceCapacity() != null) {
      ec2 = new Ec2Factory(scope, id + "Ec2");
      ec2.create();
    }

    // Create single EC2 instance if no Auto Scaling Group
    // Note: Ec2InstanceFactory will be created later
    Object singleInstance = null;
    if (cfc.maxInstanceCapacity() == null) {
      // TODO: Create Ec2InstanceFactory for single instances
    }

    // Create alarms
    AlarmFactory alarms = new AlarmFactory(scope, id + "Alarms", null);
    alarms.create();

    return new JenkinsSpecificFactories(null, null, alarms, ec2, singleInstance);
  }

  /**
   * Creates S3 and CloudFront factories for static web applications.
   */
  private S3CloudFrontFactories createS3CloudFrontFactories(Construct scope, String id) {

    // Create S3 bucket factory
    // TODO: Create S3BucketFactory
    Object s3 = null;

    // Create CloudFront distribution factory
    // TODO: Create CloudFrontFactory
    Object cloudfront = null;

    return new S3CloudFrontFactories(s3, cloudfront);
  }

  /**
   * Creates domain and SSL factories if configured in the deployment context.
   */
  private DomainAndSslFactories createDomainAndSslFactories(Construct scope, String id) {

    DomainFactory domain = null;

    // Create domain factory if domain is provided
    if (cfc.domain() != null && !cfc.domain().isBlank()) {
      domain = new DomainFactory(scope, id + "Domain");
      domain.create();
    }

    // SSL certificate creation is handled by runtime configurations (FargateRuntimeConfiguration)

    return new DomainAndSslFactories(domain, null);
  }

  // ============================================================================
  // DEPLOYMENT CONTAINERS - Records for different deployment types
  // ============================================================================

  /**
   * Container for infrastructure factories created by the orchestration layer.
   * This provides a clean interface for accessing infrastructure components.
   */
  public record InfrastructureFactories(
      VpcFactory vpc,
      AlbFactory alb,
      EfsFactory efs,
      LoggingCwFactory logging
  ) {}

  /**
   * Container for Jenkins-specific factories.
   * Note: JenkinsBootstrap removed - logic migrated to FargateFactory
   */
  public record JenkinsSpecificFactories(
      FargateFactory fargate,
      ContainerFactory container,
      AlarmFactory alarms,
      Ec2Factory ec2,
      Object singleInstance
  ) {}

  /**
   * Container for S3 and CloudFront factories.
   */
  public record S3CloudFrontFactories(
      Object s3,
      Object cloudfront
  ) {}

  /**
   * Container for domain and SSL factories.
   */
  public record DomainAndSslFactories(
      DomainFactory domain,
      Object ssl  // SSL is handled by runtime configurations
  ) {}

  /**
   * Container for complete Jenkins deployment.
   */
  public record JenkinsDeployment(
      InfrastructureFactories infrastructure,
      JenkinsSpecificFactories jenkins,
      DomainAndSslFactories domainSsl
  ) {}

  /**
   * Container for complete S3 + CloudFront deployment.
   */
  public record S3CloudFrontDeployment(
      S3CloudFrontFactories s3CloudFront,
      DomainAndSslFactories domainSsl
  ) {}

  /**
   * Apply stack-level CDK-nag suppressions for common architectural patterns.
   * These suppressions cover CDK framework limitations and intentional architectural decisions.
   */
  private static void applyStackLevelSuppressions(Stack stack, SecurityProfile security) {
    List<NagPackSuppression> suppressions = new ArrayList<>();

    // =====================================================================
    // AwsSolutions Rules (common CDK-nag pack)
    // =====================================================================

    // IAM5 - Wildcard permissions are required for many AWS services
    suppressions.add(NagPackSuppression.builder()
        .id("AwsSolutions-IAM5")
        .reason("Wildcard permissions are required for: (1) CloudWatch Logs - log group/stream patterns, " +
                "(2) S3 bucket operations with object prefixes, (3) SSM parameter paths, " +
                "(4) AWS Backup cross-resource operations. Permissions are scoped to specific " +
                "resource patterns where possible.")
        .build());

    // IAM4 - AWS managed policies for service roles
    suppressions.add(NagPackSuppression.builder()
        .id("AwsSolutions-IAM4")
        .reason("AWS managed policies are used for service roles where AWS recommends them: " +
                "(1) AWSBackupServiceRolePolicyForBackup - AWS Backup service role, " +
                "(2) AWSLambdaBasicExecutionRole - CDK custom resource Lambda functions, " +
                "(3) AmazonECSTaskExecutionRolePolicy - ECS task execution. " +
                "These are AWS-maintained and follow least privilege for their service.")
        .build());

    // L1 - Lambda runtime managed by CDK
    suppressions.add(NagPackSuppression.builder()
        .id("AwsSolutions-L1")
        .reason("Lambda runtime version is managed by CDK AwsCustomResource construct. " +
                "Runtime updates are applied through CDK version upgrades.")
        .build());

    // S1 - S3 server access logs for log buckets
    suppressions.add(NagPackSuppression.builder()
        .id("AwsSolutions-S1")
        .reason("S3 server access logging is disabled for log destination buckets (ALB logs, " +
                "CloudTrail logs) to prevent circular dependencies. CloudTrail S3 data events " +
                "provide audit logging for these buckets.")
        .build());

    // EC23 - Security group open access for public-facing services
    suppressions.add(NagPackSuppression.builder()
        .id("AwsSolutions-EC23")
        .reason("Security groups allow 0.0.0.0/0 ingress for: (1) ALB on ports 80/443 for " +
                "public web traffic, (2) EC2 optional ports (SSH, JNLP) when explicitly enabled. " +
                "Backend instances are protected in private subnets behind ALB.")
        .build());

    // ECS4 - Container Insights (cost optimization for non-production)
    if (security != SecurityProfile.PRODUCTION) {
      suppressions.add(NagPackSuppression.builder()
          .id("AwsSolutions-ECS4")
          .reason("CloudWatch Container Insights is disabled for DEV/STAGING to reduce costs. " +
                  "Production deployments enable Container Insights for operational visibility.")
          .build());
    }

    // =====================================================================
    // HIPAA.Security Rules
    // =====================================================================

    String inlinePolicyReason = "Inline policies are used for resource-specific permissions that are " +
        "tightly scoped to specific resources. This approach provides better security than managed " +
        "policies for application-specific access patterns. Permissions follow least privilege.";

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-IAMNoInlinePolicy")
        .reason(inlinePolicyReason)
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-CloudWatchLogGroupEncrypted")
        .reason("CloudWatch Log Groups use AWS-managed encryption by default. KMS encryption " +
                "is configurable via security profile for environments requiring customer-managed keys.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-VPCSubnetAutoAssignPublicIpDisabled")
        .reason("Public subnets require auto-assign public IP for resources that need direct " +
                "internet access (ALB, NAT gateways). Private subnets do not auto-assign public IPs.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-VPCNoUnrestrictedRouteToIGW")
        .reason("Public subnets require routes to Internet Gateway for ALB and NAT gateway " +
                "functionality. Private subnets route through NAT gateways, not directly to IGW.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-ALBHttpDropInvalidHeaderEnabled")
        .reason("ALB drop invalid headers is configured via configureAlbSecurity() method. " +
                "This setting may not be detected by CDK-nag during synthesis.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-ELBLoggingEnabled")
        .reason("ELB logging is enabled for production security profiles. DEV/STAGING may " +
                "disable logging for cost optimization. Enable via albAccessLogging config.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-LambdaConcurrency")
        .reason("CDK AwsCustomResource Lambda functions are short-lived and don't require " +
                "concurrency limits. They execute only during CloudFormation operations.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-LambdaDLQ")
        .reason("CDK AwsCustomResource Lambda functions report errors through CloudFormation. " +
                "DLQ is not needed as failures are visible in stack events.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-LambdaInsideVPC")
        .reason("CDK AwsCustomResource Lambda functions only make AWS API calls and don't " +
                "require VPC access. Running in VPC would add unnecessary complexity.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-ELBv2ACMCertificateRequired")
        .reason("Private CA certificates are used for OIDC authentication when no custom domain " +
                "is configured. For HIPAA production environments, configure a custom domain with " +
                "ACM public certificate. Private CA certificates enable development/testing without " +
                "requiring domain registration.")
        .build());

    // =====================================================================
    // PCI.DSS.321 Rules
    // =====================================================================

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-IAMNoInlinePolicy")
        .reason(inlinePolicyReason)
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-CloudWatchLogGroupEncrypted")
        .reason("CloudWatch Log Groups use AWS-managed encryption by default. KMS encryption " +
                "is configurable via security profile for PCI-DSS environments.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-VPCSubnetAutoAssignPublicIpDisabled")
        .reason("Public subnets require auto-assign public IP for ALB and NAT gateways. " +
                "Cardholder data environment uses private subnets without public IPs.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-VPCNoUnrestrictedRouteToIGW")
        .reason("Public subnets require IGW routes for ALB traffic. Private subnets with " +
                "cardholder data route through NAT, providing network segmentation per Req 1.3.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-ALBHttpDropInvalidHeaderEnabled")
        .reason("ALB drop invalid headers is configured via configureAlbSecurity(). " +
                "Complies with PCI-DSS Req 4.1 for secure transmission.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-ELBLoggingEnabled")
        .reason("ELB logging is enabled for production per PCI-DSS Req 10. Non-production " +
                "environments may disable for cost optimization.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-LambdaInsideVPC")
        .reason("CDK custom resource Lambdas make AWS API calls only and don't access " +
                "cardholder data environment. VPC placement not required for PCI scope.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-SecretsManagerUsingKMSKey")
        .reason("Secrets Manager uses AWS-managed encryption by default. Customer-managed KMS keys " +
                "can be configured for production PCI-DSS environments requiring key management control. " +
                "Default encryption meets PCI-DSS Req 3.4 for encryption at rest.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-RDSLoggingEnabled")
        .reason("RDS logging is configured based on database engine type. PostgreSQL/MySQL enable " +
                "appropriate log exports. Some log types (e.g., upgrade) may not be applicable to all " +
                "configurations. Audit logging per PCI-DSS Req 10 is ensured via CloudTrail and " +
                "engine-specific logs.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("PCI.DSS.321-ELBv2ACMCertificateRequired")
        .reason("Private CA certificates are used for OIDC authentication when no custom domain " +
                "is configured. For PCI-DSS production, configure a custom domain with ACM public certificate.")
        .build());

    // HIPAA suppressions
    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-SecretsManagerRotationEnabled")
        .reason("Secrets rotation is application-dependent and configured per deployment requirements. " +
                "Applications using database credentials can enable rotation through deployment config.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-SecretsManagerUsingKMSKey")
        .reason("Secrets Manager uses AWS-managed encryption by default. Customer-managed KMS keys " +
                "can be configured for HIPAA environments requiring key management control.")
        .build());

    suppressions.add(NagPackSuppression.builder()
        .id("HIPAA.Security-RDSLoggingEnabled")
        .reason("RDS logging is configured based on database engine type. Some log types (e.g., upgrade) " +
                "may not be applicable. Audit logging per HIPAA is ensured via CloudTrail and engine-specific logs.")
        .build());

    NagSuppressions.addStackSuppressions(stack, suppressions, true);
    LOG.info("Applied " + suppressions.size() + " stack-level CDK-nag suppressions for " + security + " profile");
  }
}
