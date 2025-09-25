package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.rules.Rules;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.observability.LoggingCwFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.storage.ContainerFactory;
import com.cloudforgeci.api.application.JenkinsBootstrap;
import com.cloudforgeci.api.observability.AlarmFactory;
import com.cloudforgeci.api.compute.Ec2Factory;
// Note: S3BucketFactory, CloudFrontFactory, and Ec2InstanceFactory will be created later
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.security.SslManager;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
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
import software.constructs.Construct;
import software.constructs.IConstruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  public final Slot<Boolean> asgAddedToTargetGroup = new Slot<>();
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
  public final Slot<String> authMode = new Slot<>(); // "none" | "alb-oidc" | "jenkins-oidc"
  public final Slot<String> ssoInstanceArn = new Slot<>();
  public final Slot<String> ssoGroupId = new Slot<>();
  public final Slot<String> ssoTargetAccountId = new Slot<>();
  
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
  private boolean installed = false;

  private SystemContext(Stack stack, TopologyType topology, RuntimeType runtime, SecurityProfile security, IAMProfile iamProfile, DeploymentContext cfc) {
    super(stack, NODE_ID);
    LOG.info("*** SystemContext constructor started ***");
    this.topology = topology;
    this.runtime = runtime;
    this.security = security;
    this.iamProfile = iamProfile;
    this.cfc = cfc;
    this.stackName = stack.getStackName();
  }

  /** Start once at the entry point; installs runtime + topology + security + iam rules and wiring. */
  public static SystemContext start(Construct scope, TopologyType topology, RuntimeType runtime, SecurityProfile security, IAMProfile iamProfile, DeploymentContext cfc) {
    LOG.info("*** SystemContext.start() called with topology=" + topology + ", runtime=" + runtime + ", security=" + security + ", iamProfile=" + iamProfile + " ***");
    System.out.println("*** DEBUG: SystemContext.start() called ***");
    
    try {
      Stack stack = Stack.of(scope);
      LOG.info("*** Stack resolved: " + stack.getStackName() + " ***");
      
      SystemContext existing = (SystemContext) stack.getNode().tryFindChild(NODE_ID);
      if (existing != null) {
        LOG.info("*** Existing SystemContext found, checking compatibility ***");
        System.out.println("*** DEBUG: Existing SystemContext found ***");
        // Allow multiple runtime types in the same stack for testing purposes
        // Runtime type check removed to allow EC2 and Fargate in same stack
        if (existing.topology != topology) {
          throw new IllegalStateException("SystemContext already started with topology=" + existing.topology);
        }
        if (existing.security != security) {
          throw new IllegalStateException("SystemContext already started with security=" + existing.security);
        }
        if (existing.iamProfile != iamProfile) {
          throw new IllegalStateException("SystemContext already started with iamProfile=" + existing.iamProfile);
        }
        
        LOG.info("*** Returning existing SystemContext ***");
        System.out.println("*** DEBUG: Returning existing SystemContext ***");
        return existing;
      }
      
      var ctx = new SystemContext(stack, topology, runtime, security, iamProfile, cfc);
      LOG.info("*** SystemContext constructor completed successfully ***");
      
      LOG.info("*** ctx.installed = " + ctx.installed + " ***");
      if (!ctx.installed) {
        LOG.info("*** About to call Rules.installAll() ***");
        System.out.println("*** DEBUG: About to call Rules.installAll() ***");
        try {
          Rules.installAll(ctx);   // installs RuntimeRules, TopologyRules, SecurityRules, and IAMRules
          LOG.info("*** Rules installation completed successfully ***");
          System.out.println("*** DEBUG: Rules installation completed successfully ***");
        } catch (Exception e) {
          LOG.severe("*** ERROR in Rules.installAll(): " + e.getMessage() + " ***");
          System.out.println("*** DEBUG: ERROR in Rules.installAll(): " + e.getMessage() + " ***");
          e.printStackTrace();
          throw e;
        }
        ctx.installed = true;
      } else {
        LOG.info("*** Rules already installed, skipping ***");
        System.out.println("*** DEBUG: Rules already installed, skipping ***");
      }
      
      return ctx;
      
    } catch (Exception e) {
      LOG.severe("*** ERROR in SystemContext.start(): " + e.getMessage() + " ***");
      System.out.println("*** DEBUG: ERROR in SystemContext.start(): " + e.getMessage() + " ***");
      e.printStackTrace();
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
    LOG.info("*** SystemContext.once(): Deferring execution of " + key + " ***");
    deferredActions.add(r);
    return true;
  }

  /** Execute all deferred actions. Call this after all factories are created. */
  public void executeDeferredActions() {
    LOG.info("*** SystemContext.executeDeferredActions(): Executing " + deferredActions.size() + " deferred actions ***");
    LOG.info("*** Deferred action keys: " + onceKeys + " ***");
    // Create a copy to avoid ConcurrentModificationException
    List<Runnable> actionsToExecute = new ArrayList<>(deferredActions);
    // Clear the original list to prevent interference from new actions
    deferredActions.clear();
    for (Runnable action : actionsToExecute) {
      try {
        LOG.info("*** Executing deferred action ***");
        action.run();
        LOG.info("*** Deferred action executed successfully ***");
      } catch (Exception e) {
        LOG.severe("*** Error executing deferred action: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    }
    LOG.info("*** All deferred actions executed successfully ***");
  }

  public String debugPath(Construct scope) {
    String here = scope.getNode().getPath();
    String sc   = this.getNode().getPath();
    return "scopePath=" + here + " | ctxPath=" + sc + " | runtime=" + runtime + " | topology=" + topology + " | security=" + security + " | iamProfile=" + iamProfile + " | slots=" + presentSlots();
  }

  public String presentSlots() {
    return "vpc="+vpc.get().isPresent()
            +", alb="+alb.get().isPresent()
            +", efs="+efs.get().isPresent()
            +", http="+http.get().isPresent()
            +", tg="+albTargetGroup.get().isPresent()
            +", ap="+ap.get().isPresent()
            +", asg="+asg.get().isPresent()
            +", fargate="+fargateService.get().isPresent()
            +", instSg="+instanceSg.get().isPresent();
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
    LOG.info("*** SystemContext: Creating infrastructure factories with prefix: " + idPrefix + " ***");
    
    // Create infrastructure factories in dependency order
    VpcFactory vpcFactory = createVpcFactory(scope, idPrefix);
    AlbFactory albFactory = createAlbFactory(scope, idPrefix);
    EfsFactory efsFactory = createEfsFactory(scope, idPrefix);
    LoggingCwFactory loggingFactory = createLoggingFactory(scope, idPrefix);
    
    // Create instance security group only for EC2 deployments
    if (this.runtime == RuntimeType.EC2) {
      createInstanceSecurityGroup(scope, idPrefix);
    }
    
    // Create target groups (orchestrated by SystemContext)
    createTargetGroups(scope, idPrefix);
    
    LOG.info("*** SystemContext: Infrastructure factories created successfully ***");
    
    return new InfrastructureFactories(vpcFactory, albFactory, efsFactory, loggingFactory);
  }
  
  /**
   * Creates a VPC factory with proper context injection.
   */
  public VpcFactory createVpcFactory(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating VpcFactory ***");
    VpcFactory vpcFactory = new VpcFactory(scope, idPrefix + "Vpc");
    vpcFactory.injectContexts();
    vpcFactory.create();
    LOG.info("*** SystemContext: VpcFactory created successfully ***");
    return vpcFactory;
  }
  
  /**
   * Creates an ALB factory with proper context injection.
   */
  public AlbFactory createAlbFactory(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating AlbFactory ***");
    AlbFactory albFactory = new AlbFactory(scope, idPrefix + "Alb");
    albFactory.injectContexts();
    albFactory.create();
    LOG.info("*** SystemContext: AlbFactory created successfully ***");
    return albFactory;
  }
  
  /**
   * Creates an EFS factory with proper context injection.
   */
  public EfsFactory createEfsFactory(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating EfsFactory ***");
    EfsFactory efsFactory = new EfsFactory(scope, idPrefix + "Efs");
    efsFactory.injectContexts();
    efsFactory.create();
    LOG.info("*** SystemContext: EfsFactory created successfully ***");
    return efsFactory;
  }
  
  /**
   * Creates a logging factory with proper context injection.
   */
  public LoggingCwFactory createLoggingFactory(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating LoggingCwFactory ***");
    LoggingCwFactory loggingFactory = new LoggingCwFactory(scope, idPrefix + "Logging");
    loggingFactory.injectContexts();
    loggingFactory.create();
    LOG.info("*** SystemContext: LoggingCwFactory created successfully ***");
    return loggingFactory;
  }
  
  /**
   * Creates target groups orchestrated by SystemContext.
   * This centralizes target group management and prevents duplicates.
   */
  public void createTargetGroups(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating target groups for runtime: " + this.runtime + " ***");
    
    if (this.runtime == RuntimeType.EC2) {
      // Create target group for EC2 runtime
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
      LOG.info("*** SystemContext: Target group created for EC2 runtime ***");
      
      // Update HTTP listener to use the target group
      ApplicationListener http = this.http.get().orElseThrow(() -> 
        new IllegalStateException("HTTP listener not found when updating target group"));
      
      http.addTargetGroups(idPrefix + "HttpTg", AddApplicationTargetGroupsProps.builder()
          .targetGroups(List.of(targetGroup))
          .build());
      
      LOG.info("*** SystemContext: HTTP listener updated with target group ***");
      
    } else {
      // For Fargate, target groups are created by FargateRuntimeConfiguration
      LOG.info("*** SystemContext: Target groups for Fargate will be created by FargateRuntimeConfiguration ***");
    }
  }
  
  /**
   * Creates instance security group for EC2 deployments.
   * This is infrastructure-specific but not a full factory.
   */
  public SecurityGroup createInstanceSecurityGroup(Construct scope, String idPrefix) {
    LOG.info("*** SystemContext: Creating instance security group ***");
    
    // Check if instance security group already exists
    if (this.instanceSg.get().isPresent()) {
      LOG.info("*** SystemContext: Instance security group already exists, returning existing one ***");
      return this.instanceSg.get().orElseThrow();
    }
    
    SecurityGroup instanceSg = SecurityGroup.Builder.create(scope, idPrefix + "InstanceSg")
            .vpc(vpc.get().orElseThrow())
            .description("EC2 Instance Security Group")
            .allowAllOutbound(true)
            .build();
    this.instanceSg.set(instanceSg);
    LOG.info("*** SystemContext: Instance security group created successfully ***");
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
    LOG.info("*** SystemContext: Creating Jenkins deployment with id: " + id + " ***");
    
    // Create infrastructure factories
    InfrastructureFactories infra = createInfrastructureFactories(scope, id);
    
    // Create Jenkins-specific factories based on runtime
    JenkinsSpecificFactories jenkins = createJenkinsSpecificFactories(scope, id);
    
    // Create domain and SSL if configured
    DomainAndSslFactories domainSsl = createDomainAndSslFactories(scope, id);
    
    LOG.info("*** SystemContext: Jenkins deployment created successfully ***");
    
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
    LOG.info("*** SystemContext: Creating S3 + CloudFront deployment with id: " + id + " ***");
    
    // Create S3 and CloudFront factories
    S3CloudFrontFactories s3cf = createS3CloudFrontFactories(scope, id);
    
    // Create domain if configured
    DomainAndSslFactories domainSsl = createDomainAndSslFactories(scope, id);
    
    LOG.info("*** SystemContext: S3 + CloudFront deployment created successfully ***");
    
    return new S3CloudFrontDeployment(s3cf, domainSsl);
  }
  
  /**
   * Creates Jenkins-specific factories based on the current runtime configuration.
   */
  private JenkinsSpecificFactories createJenkinsSpecificFactories(Construct scope, String id) {
    LOG.info("*** SystemContext: Creating Jenkins-specific factories for runtime: " + runtime + " ***");
    
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
    LOG.info("*** SystemContext: Creating Fargate Jenkins factories ***");
    
    // Create Fargate factory
    FargateFactory fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
    fargate.injectContexts();
    fargate.create();
    
    // Create container factory
    ContainerFactory container = new ContainerFactory(scope, id + "Container", 
        software.amazon.awscdk.services.ecs.ContainerImage.fromRegistry("jenkins/jenkins:lts"));
    container.injectContexts();
    container.create();
    
    // Create Jenkins bootstrap
    JenkinsBootstrap bootstrap = new JenkinsBootstrap(scope, id + "Bootstrap", new JenkinsBootstrap.Props(cfc));
    bootstrap.injectContexts();
    bootstrap.create();
    
    // Create alarms
    AlarmFactory alarms = new AlarmFactory(scope, id + "Alarms", null);
    alarms.injectContexts();
    alarms.create();
    
    return new JenkinsSpecificFactories(fargate, container, bootstrap, alarms, null, null);
  }
  
  /**
   * Creates EC2-specific Jenkins factories.
   */
  private JenkinsSpecificFactories createEc2JenkinsFactories(Construct scope, String id) {
    LOG.info("*** SystemContext: Creating EC2 Jenkins factories ***");
    
    // Instance security group is already created by createInfrastructureFactories()
    // No need to create it again here
    
    // Create EC2 factory (Auto Scaling Group) if maxInstanceCapacity is provided
    Ec2Factory ec2 = null;
    if (cfc.maxInstanceCapacity() != null) {
      ec2 = new Ec2Factory(scope, id + "Ec2");
      ec2.injectContexts();
      ec2.create();
    }
    
    // Create single EC2 instance if no Auto Scaling Group
    // Note: Ec2InstanceFactory will be created later
    Object singleInstance = null;
    if (cfc.maxInstanceCapacity() == null) {
      // TODO: Create Ec2InstanceFactory for single instances
      LOG.info("*** SystemContext: Single EC2 instance creation not yet implemented ***");
    }
    
    // Create alarms
    AlarmFactory alarms = new AlarmFactory(scope, id + "Alarms", null);
    alarms.injectContexts();
    alarms.create();
    
    return new JenkinsSpecificFactories(null, null, null, alarms, ec2, singleInstance);
  }
  
  /**
   * Creates S3 and CloudFront factories for static web applications.
   */
  private S3CloudFrontFactories createS3CloudFrontFactories(Construct scope, String id) {
    LOG.info("*** SystemContext: Creating S3 + CloudFront factories ***");
    
    // Create S3 bucket factory
    // TODO: Create S3BucketFactory
    Object s3 = null;
    LOG.info("*** SystemContext: S3BucketFactory not yet implemented ***");
    
    // Create CloudFront distribution factory
    // TODO: Create CloudFrontFactory
    Object cloudfront = null;
    LOG.info("*** SystemContext: CloudFrontFactory not yet implemented ***");
    
    return new S3CloudFrontFactories(s3, cloudfront);
  }
  
  /**
   * Creates domain and SSL factories if configured in the deployment context.
   */
  private DomainAndSslFactories createDomainAndSslFactories(Construct scope, String id) {
    LOG.info("*** SystemContext: Creating domain and SSL factories ***");
    
    DomainFactory domain = null;
    SslManager ssl = null;
    
    // Create domain factory if domain is provided
    if (cfc.domain() != null && !cfc.domain().isBlank()) {
      domain = new DomainFactory(scope, id + "Domain");
      domain.injectContexts();
      domain.create();
    }
    
    // Create SSL manager if SSL is enabled and domain is provided
    if (cfc.enableSsl() && cfc.domain() != null && !cfc.domain().isBlank()) {
      ssl = new SslManager(scope, id + "SslManager", new SslManager.Props(cfc));
      ssl.injectContexts();
      ssl.create();
    }
    
    return new DomainAndSslFactories(domain, ssl);
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
   */
  public record JenkinsSpecificFactories(
      FargateFactory fargate,
      ContainerFactory container,
      JenkinsBootstrap bootstrap,
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
      SslManager ssl
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
}