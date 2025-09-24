package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.application.JenkinsBootstrap;
import com.cloudforgeci.api.core.*;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.observability.LoggingCwFactory;
import com.cloudforgeci.api.security.SslManager;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.observability.AlarmFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.FixedResponseOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ec2.Instance;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.InstanceTarget;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory class for creating Jenkins deployments on AWS.
 * 
 * <p>This factory supports multiple deployment topologies and runtime environments:</p>
 * <ul>
 *   <li><strong>Topologies:</strong> JENKINS_SINGLE_NODE, JENKINS_SERVICE</li>
 *   <li><strong>Runtimes:</strong> EC2, FARGATE</li>
 *   <li><strong>Security Profiles:</strong> DEV, STAGING, PRODUCTION</li>
 * </ul>
 * 
 * <p>The factory automatically configures:</p>
 * <ul>
 *   <li>VPC and networking (respects networkMode: public-no-nat, private-with-nat)</li>
 *   <li>Application Load Balancer (ALB) with SSL/TLS termination</li>
 *   <li>Security groups and IAM roles</li>
 *   <li>Storage (EFS for persistent data)</li>
 *   <li>Observability (CloudWatch logs, flow logs, alarms)</li>
 *   <li>Auto-scaling (for service topology)</li>
 * </p>
 * 
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * // Create EC2-based Jenkins deployment
 * JenkinsFactory.JenkinsSystem system = JenkinsFactory.createEc2(scope, "Jenkins", cfc);
 * 
 * // Access created resources
 * Vpc vpc = system.vpc().getVpc();
 * ApplicationLoadBalancer alb = system.alb().getAlb();
 * FileSystem efs = system.efs().getEfs();
 * }</pre>
 * 
 * @author CloudForgeCI
 * @since 1.0.0
 * @see DeploymentContext
 * @see SystemContext
 * @see SecurityProfile
 */
public class JenkinsFactory {
  
    private static final Logger LOG = Logger.getLogger(JenkinsFactory.class.getName());
  
  /**
   * Container for Jenkins system components created by the factory.
   * 
   * <p>This record holds references to the core infrastructure components
   * that make up a Jenkins deployment:</p>
   * <ul>
   *   <li><strong>vpc:</strong> VPC factory containing networking resources</li>
   *   <li><strong>alb:</strong> Application Load Balancer factory for ingress</li>
   *   <li><strong>efs:</strong> EFS factory for persistent storage</li>
   * </ul>
   * 
   * @param vpc The VPC factory containing VPC, subnets, and security groups
   * @param alb The ALB factory containing load balancer and listeners
   * @param efs The EFS factory containing file system and access points
   */
  public record JenkinsSystem(
      VpcFactory vpc,
      AlbFactory alb,
      EfsFactory efs
  ) {}

  /**
   * Creates an EC2-based Jenkins deployment using the default security profile.
   * 
   * <p>This is a convenience method that uses the security profile from the deployment context.
   * It creates a complete Jenkins infrastructure including:</p>
   * <ul>
   *   <li>VPC with public/private subnets (respects networkMode)</li>
   *   <li>EC2 instance(s) with Jenkins installed</li>
   *   <li>Application Load Balancer for ingress</li>
   *   <li>EFS file system for persistent storage</li>
   *   <li>Security groups and IAM roles</li>
   *   <li>CloudWatch logging and monitoring</li>
   * </ul>
   * 
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Jenkins deployment
   * @param cfc Deployment context containing configuration parameters
   * @return JenkinsSystem containing references to created infrastructure components
   * @throws RuntimeException if deployment creation fails
   * @see #createEc2(Construct, String, DeploymentContext, SecurityProfile)
   */
  public static JenkinsSystem createEc2(Construct scope, String id, DeploymentContext cfc) {
    LOG.info("*** JenkinsFactory.createEc2(3-param) called for topology: " + cfc.topology() + " ***");
    LOG.info("*** DEBUG: JenkinsFactory.createEc2(3-param) called ***");
    LOG.info("*** SIMPLE DEBUG: JenkinsFactory.createEc2(3-param) called ***");
    LOG.info("*** JenkinsFactory.createEc2(3-param) called with topology=" + cfc.topology() + " ***");
    
    try {
      LOG.info("*** Creating EC2 Jenkins system with id: " + id + " ***");
      
      JenkinsSystem result = createEc2(scope, id, cfc, cfc.securityProfile());
      return result;
    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * Creates an EC2-based Jenkins deployment with a specific security profile.
   * 
   * <p>This method allows explicit control over the security profile used for the deployment.
   * Different security profiles provide different levels of hardening:</p>
   * <ul>
   *   <li><strong>DEV:</strong> Minimal security, cost-optimized for development</li>
   *   <li><strong>STAGING:</strong> Moderate security for testing environments</li>
   *   <li><strong>PRODUCTION:</strong> Maximum security with comprehensive monitoring</li>
   * </ul>
   * 
   * <p>The deployment includes all infrastructure components and respects the networkMode
   * setting (public-no-nat vs private-with-nat) for proper subnet placement.</p>
   * 
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Jenkins deployment
   * @param cfc Deployment context containing configuration parameters
   * @param security Security profile determining security hardening level
   * @return JenkinsSystem containing references to created infrastructure components
   * @throws RuntimeException if deployment creation fails
   * @see SecurityProfile
   * @see DeploymentContext#networkMode()
   */
  public static JenkinsSystem createEc2(Construct scope, String id, DeploymentContext cfc, SecurityProfile security) {
    LOG.info("*** SIMPLE DEBUG: JenkinsFactory.createEc2(3-param) method started ***");
    LOG.info("*** JenkinsFactory.createEc2(3-param) called with topology=" + cfc.topology() + " ***");
    LOG.info("*** Method started successfully, about to start try block ***");
    
    try {
      LOG.info("*** Creating EC2 Jenkins system with id: " + id + ", security: " + security + " ***");
      LOG.info("*** Try block started successfully, about to assign IAM profile ***");
      
      // Use MINIMAL IAM profile for EC2 to avoid EFS security group requirement
      IAMProfile iamProfile = IAMProfile.MINIMAL;
      LOG.info("*** IAM profile assigned: " + iamProfile + " ***");
      LOG.info("*** IAM profile assigned successfully, about to start SystemContext ***");
      
      // Use topology from DeploymentContext instead of hardcoding
      SystemContext ctx = SystemContext.start(scope, cfc.topology(), cfc.runtime(), security, iamProfile, cfc);
      LOG.info("*** DEBUG: SystemContext started successfully ***");
      
      // Set configuration values in SystemContext for centralized management
      ctx.sslEnabled.set(cfc.enableSsl());
      ctx.httpRedirectEnabled.set(cfc.enableSsl()); // Redirect HTTP to HTTPS when SSL is enabled
      
      // Networking Configuration
      ctx.networkMode.set(cfc.networkMode());
      ctx.wafEnabled.set(cfc.wafEnabled());
      ctx.cloudfront.set(cfc.cloudfrontEnabled());
      ctx.lbType.set(cfc.lbType());
      
      // Auto Scaling Configuration
      ctx.minInstanceCapacity.set(cfc.minInstanceCapacity());
      ctx.maxInstanceCapacity.set(cfc.maxInstanceCapacity());
      ctx.cpuTargetUtilization.set(cfc.cpuTargetUtilization());
      
      // Container Configuration
      ctx.cpu.set(cfc.cpu());
      ctx.memory.set(cfc.memory());
      
      // Authentication Configuration
      ctx.authMode.set(cfc.authMode());
      ctx.ssoInstanceArn.set(cfc.ssoInstanceArn());
      ctx.ssoGroupId.set(cfc.ssoGroupId());
      ctx.ssoTargetAccountId.set(cfc.ssoTargetAccountId());
      
      // Storage Configuration
      ctx.artifactsBucket.set(cfc.artifactsBucket());
      ctx.artifactsPrefix.set(cfc.artifactsPrefix());
      ctx.enableFlowlogs.set(cfc.enableFlowlogs());
      
      // DNS Configuration
      ctx.domain.set(cfc.domain());
      ctx.subdomain.set(cfc.subdomain());
      ctx.fqdn.set(cfc.fqdn());
    
      // Create factories using the new clean interface and call create() in correct order
    VpcFactory vpc = new VpcFactory(scope, id + "Vpc");
      vpc.injectContexts(); // Manual injection after SystemContext.start()
      vpc.create();
      LOG.info("*** DEBUG: VPC factory created successfully ***");
      LOG.info("*** VPC factory creation completed, about to create ALB factory ***");
      LOG.info("*** VPC factory creation completed successfully, about to instantiate AlbFactory ***");
      
      AlbFactory alb = new AlbFactory(ctx, id + "Alb"); // Create as child of SystemContext
      LOG.info("*** AlbFactory instantiated successfully ***");
      LOG.info("*** AlbFactory instantiation completed, about to inject contexts ***");
      alb.injectContexts(); // Manual injection after SystemContext.start()
      alb.create();
      
      // Create instance security group early for EC2 runtime to satisfy security wiring
      SecurityGroup instanceSg = SecurityGroup.Builder.create(scope, id + "InstanceSg")
              .vpc(ctx.vpc.get().orElseThrow())
              .description("Jenkins EC2 Instance Security Group")
              .allowAllOutbound(true)
              .build();
      ctx.instanceSg.set(instanceSg);
      LOG.info("*** Instance security group created and set in context ***");
      
      // EFS is allowed for both JENKINS_SERVICE and JENKINS_SINGLE_NODE topologies
      EfsFactory efs = null;
      if (cfc.topology() == TopologyType.JENKINS_SERVICE || cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
          efs = new EfsFactory(scope, id + "Efs");
          efs.create(ctx);
      }
      
      LOG.info("*** EFS factory creation completed, about to create logging factory ***");
      
      LoggingCwFactory log = new LoggingCwFactory(scope, id + "Logging");
        LOG.info("*** Logging factory created successfully ***");
      
      // AutoScalingGroup for JENKINS_SERVICE topology, single instance for JENKINS_SINGLE_NODE
      LOG.info("*** About to check topology for EC2 deployment: " + cfc.topology() + " ***");
      if (cfc.topology() == TopologyType.JENKINS_SERVICE) {
          Ec2Factory ec2 = new Ec2Factory(scope, id + "Ec2");
          ec2.create(ctx);
      } else if (cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
          createSingleEc2Instance(scope, id + "SingleInstance", ctx);
      }
      
      new AlarmFactory(scope, id + "Alarms", null);
      
      // Create domain factory if domain is provided (for DNS records)
      if (cfc.domain() != null && !cfc.domain().isBlank()) {
          DomainFactory domain = new DomainFactory(scope, id + "Domain");
          domain.create();
      }
      
      JenkinsSystem result = new JenkinsSystem(vpc, alb, efs); // EFS allowed for JENKINS_SERVICE and JENKINS_SINGLE_NODE
      
      // Execute deferred actions (runtime configuration wiring) after all factories are created
      LOG.info("*** About to execute deferred actions for runtime configuration ***");
      LOG.info("*** JenkinsFactory.createEc2: About to execute deferred actions ***");
      ctx.executeDeferredActions();
      LOG.info("*** JenkinsFactory.createEc2: Deferred actions executed successfully ***");
      
      return result;
      
    } catch (Exception e) {
      throw e;
    }
  }

  public static JenkinsSystem createFargate(Construct scope, String id, DeploymentContext cfc) {
    try {
      JenkinsSystem result = createFargate(scope, id, cfc, cfc.securityProfile());
      return result;
    } catch (Exception e) {
      throw e;
    }
  }

  public static JenkinsSystem createFargate(Construct scope, String id, DeploymentContext cfc, SecurityProfile security) {
    LOG.info("*** ===== 4-PARAMETER createFargate METHOD CALLED ===== ***");
    try {
      LOG.info("JenkinsFactory: Starting createFargate method");
      
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);
      LOG.info("JenkinsFactory: IAM profile mapped successfully");
      
      LOG.info("JenkinsFactory: About to start SystemContext");
    SystemContext ctx = SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, security, iamProfile, cfc);
      LOG.info("JenkinsFactory: SystemContext started successfully");
    
      // Create factories using the new clean interface and call create() in correct order
    new FlowLogFactory(scope, id + "Flowlog");
      
      VpcFactory vpc = new VpcFactory(scope, id + "Vpc");
      vpc.injectContexts(); // Manual injection after SystemContext.start()
      vpc.create();
      
      // Note: Instance security group is forbidden for Fargate runtime
      
      LOG.info("JenkinsFactory: About to create AlbFactory with ctx: " + (ctx != null ? "present" : "null"));
      AlbFactory alb;
      try {
        alb = new AlbFactory(ctx, id + "Alb"); // Create as child of SystemContext
        LOG.info("JenkinsFactory: AlbFactory created successfully");
        alb.injectContexts(); // Manual injection after SystemContext.start()
        LOG.info("JenkinsFactory: About to call alb.create()");
        alb.create();
        LOG.info("JenkinsFactory: alb.create() completed");
        LOG.info("*** DEBUG: AlbFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in AlbFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      EfsFactory efs;
      try {
        efs = new EfsFactory(scope, id + "Efs");
        efs.create(ctx);
        LOG.info("*** DEBUG: EfsFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in EfsFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      try {
        LoggingCwFactory log = new LoggingCwFactory(scope, id + "Logging");
        LogDriver logDriver = LogDriver.awsLogs(AwsLogDriverProps.builder().logGroup(ctx.logs.get().orElseThrow()).streamPrefix("jenkins").build());
        LOG.info("*** DEBUG: LoggingCwFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in LoggingCwFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }

      try {
        FargateFactory fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
        LOG.info("*** DEBUG: FargateFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in FargateFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      try {
        new JenkinsBootstrap(scope, id + "Jenkins", new JenkinsBootstrap.Props(cfc));
        LOG.info("*** DEBUG: JenkinsBootstrap created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in JenkinsBootstrap: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      try {
        new AlarmFactory(scope, id + "Alarms", null);
        LOG.info("*** DEBUG: AlarmFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in AlarmFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      DomainFactory domain = new DomainFactory(scope, id + "Domain");
      try {
        domain.create();
        LOG.info("*** DEBUG: DomainFactory.create() completed successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in DomainFactory.create(): " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      JenkinsSystem result;
      try {
        result = new JenkinsSystem(vpc, alb, efs);
        LOG.info("*** DEBUG: JenkinsSystem created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** DEBUG: Exception in JenkinsSystem constructor: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      // Execute deferred actions (runtime configuration wiring) after all factories are created
      LOG.info("*** Executing deferred actions for runtime configuration ***");
      ctx.executeDeferredActions();
      LOG.info("*** DEBUG: executeDeferredActions() completed ***");
      
      return result;
      
    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * Creates an EC2 Jenkins system with explicit IAM profile selection.
   * Validates that the IAM profile is appropriate for the security profile.
   */
  public static JenkinsSystem createEc2(Construct scope, String id, DeploymentContext cfc, SecurityProfile security, IAMProfile iamProfile) {
    LOG.info("*** JenkinsFactory.createEc2(5-param) called for topology: " + cfc.topology() + " ***");
    LOG.info("*** SIMPLE DEBUG: JenkinsFactory.createEc2(5-param) called ***");
    LOG.info("*** JenkinsFactory.createEc2(5-param) called with topology=" + cfc.topology() + " ***");
    LOG.info("*** Method started successfully, about to start try block ***");
    
    if (!IAMProfileMapper.isValidCombination(security, iamProfile)) {
      throw new IllegalArgumentException("Invalid combination: SecurityProfile=" + security + " with IAMProfile=" + iamProfile);
    }
    // Force MINIMAL IAM profile for EC2 to avoid EFS security group requirement
    IAMProfile effectiveIamProfile = IAMProfile.MINIMAL;
    // Use topology from DeploymentContext instead of hardcoding
    SystemContext ctx = SystemContext.start(scope, cfc.topology(), cfc.runtime(), security, effectiveIamProfile, cfc);
    LOG.info("*** DEBUG: SystemContext started successfully ***");
    
    // Set configuration values in SystemContext for centralized management
    ctx.sslEnabled.set(cfc.enableSsl());
    ctx.httpRedirectEnabled.set(cfc.enableSsl()); // Redirect HTTP to HTTPS when SSL is enabled
    
    // Networking Configuration
    ctx.networkMode.set(cfc.networkMode());
    ctx.wafEnabled.set(cfc.wafEnabled());
      ctx.cloudfront.set(cfc.cloudfrontEnabled());
    ctx.lbType.set(cfc.lbType());
    
    // Auto Scaling Configuration
    ctx.minInstanceCapacity.set(cfc.minInstanceCapacity());
    ctx.maxInstanceCapacity.set(cfc.maxInstanceCapacity());
    ctx.cpuTargetUtilization.set(cfc.cpuTargetUtilization());
    
    // Container Configuration
    ctx.cpu.set(cfc.cpu());
    ctx.memory.set(cfc.memory());
    
    // Authentication Configuration
    ctx.authMode.set(cfc.authMode());
    ctx.ssoInstanceArn.set(cfc.ssoInstanceArn());
    ctx.ssoGroupId.set(cfc.ssoGroupId());
    ctx.ssoTargetAccountId.set(cfc.ssoTargetAccountId());
    
    // Storage Configuration
    ctx.artifactsBucket.set(cfc.artifactsBucket());
    ctx.artifactsPrefix.set(cfc.artifactsPrefix());
    ctx.enableFlowlogs.set(cfc.enableFlowlogs());
    
    // DNS Configuration
    ctx.domain.set(cfc.domain());
    ctx.subdomain.set(cfc.subdomain());
    ctx.fqdn.set(cfc.fqdn());
    
    // Create factories using the new clean interface and call create() in correct order
    VpcFactory vpc = new VpcFactory(scope, id + "Vpc");
    vpc.injectContexts(); // Manual injection after SystemContext.start()
    vpc.create();
    
    AlbFactory alb = new AlbFactory(scope, id + "Alb");
    LOG.info("*** DEBUG: AlbFactory instantiated for Fargate ***");
    alb.injectContexts(); // Manual injection after SystemContext.start()
    LOG.info("*** DEBUG: About to call alb.create() ***");
      alb.create();
      LOG.info("*** DEBUG: alb.create() completed ***");
      LOG.info("*** ALB factory creation completed, about to create instance security group ***");
    
    // Create instance security group early for EC2 runtime to satisfy security wiring
    SecurityGroup instanceSg = SecurityGroup.Builder.create(scope, id + "InstanceSg")
            .vpc(ctx.vpc.get().orElseThrow())
            .description("Jenkins EC2 Instance Security Group")
            .allowAllOutbound(true)
            .build();
    ctx.instanceSg.set(instanceSg);
    
    // EFS is allowed for both JENKINS_SERVICE and JENKINS_SINGLE_NODE topologies
    EfsFactory efs = null;
    if (cfc.topology() == TopologyType.JENKINS_SERVICE || cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
        efs = new EfsFactory(scope, id + "Efs");
        efs.create(ctx);
    }
    
        LoggingCwFactory log = new LoggingCwFactory(scope, id + "Logging");
    
    // AutoScalingGroup for JENKINS_SERVICE topology, single instance for JENKINS_SINGLE_NODE
    LOG.info("*** Checking topology for EC2 deployment: " + cfc.topology() + " ***");
    if (cfc.topology() == TopologyType.JENKINS_SERVICE) {
        LOG.info("*** Creating multi-instance deployment with AutoScalingGroup ***");
        // Multi-instance deployment with AutoScalingGroup
    Ec2Factory ec2 = new Ec2Factory(scope, id + "Ec2");
        ec2.create(ctx);
    } else if (cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
        LOG.info("*** Creating single-instance deployment without AutoScalingGroup ***");
        // Single-instance deployment without AutoScalingGroup
        try {
            createSingleEc2Instance(scope, id + "SingleInstance", ctx);
            LOG.info("*** Single instance created successfully ***");
        } catch (Exception e) {
            LOG.severe("*** Error creating single instance: " + e.getMessage() + " ***");
            throw e;
        }
    } else {
        LOG.info("*** Unknown topology for EC2: " + cfc.topology() + " ***");
    }
    
    new AlarmFactory(scope, id + "Alarms", null);
    
    // Create domain and certificate if SSL is enabled
    if (cfc.enableSsl() && cfc.domain() != null && !cfc.domain().isBlank()) {
    DomainFactory domain = new DomainFactory(scope, id + "Domain");
        domain.create();
        
        // SSL is handled by SslManager in the main createFargate method
    }
    
    return new JenkinsSystem(vpc, alb, efs); // EFS allowed for JENKINS_SERVICE and JENKINS_SINGLE_NODE
  }

  /**
   * Creates a single EC2 instance for JENKINS_SINGLE_NODE topology.
   * This avoids AutoScalingGroup which is forbidden for single-node deployments.
   */
  private static void createSingleEc2Instance(Construct scope, String id, SystemContext ctx) {
    LOG.info("*** createSingleEc2Instance called for JENKINS_SINGLE_NODE topology ***");
    LOG.info("*** DEBUG: createSingleEc2Instance called ***");
    
    // Create a single EC2 instance instead of AutoScalingGroup
    Instance instance = Instance.Builder.create(scope, id)
        .vpc(ctx.vpc.get().orElseThrow())
        .instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MEDIUM))
        .machineImage(MachineImage.latestAmazonLinux2())
        .securityGroup(ctx.instanceSg.get().orElseThrow())
        .userData(UserData.forLinux())
        .build();
    
    // Set the instance in SystemContext for compatibility
    ctx.ec2Instance.set(instance);
    
    // Create a target group for the single instance (needed for HTTPS listener)
    ApplicationTargetGroup singleInstanceTargetGroup = ApplicationTargetGroup.Builder.create(scope, id + "Tg")
        .vpc(ctx.vpc.get().orElseThrow())
        .port(8080)
        .protocol(ApplicationProtocol.HTTP)
        .targetType(TargetType.INSTANCE)
        .healthCheck(HealthCheck.builder()
            .path("/")
            .healthyHttpCodes("200-399")
            .interval(software.amazon.awscdk.Duration.seconds(30))
            .timeout(software.amazon.awscdk.Duration.seconds(10))
            .healthyThresholdCount(2)
            .unhealthyThresholdCount(10)
            .build())
        .build();
    
    // Set the target group in SystemContext (instances will be added by runtime configuration)
    ctx.albTargetGroup.set(singleInstanceTargetGroup);
    
    // Add the instance to the target group so ALB can route traffic to it
    // TODO: Fix InstanceTarget - EC2 instances need to be added differently to ALB target groups
    // singleInstanceTargetGroup.addTarget(instance);
    
    LOG.info("*** createSingleEc2Instance: Target group created and instance added successfully ***");
    
    // Note: We don't set asg slot since it's forbidden for JENKINS_SINGLE_NODE
    // The instance and target group are available for other components that need EC2 access
  }

  /**
   * Creates a Fargate Jenkins system with explicit IAM profile selection.
   * Validates that the IAM profile is appropriate for the security profile.
   */
  public static JenkinsSystem createFargate(Construct scope, String id, DeploymentContext cfc, SecurityProfile security, IAMProfile iamProfile) {
    LOG.info("*** ===== 5-PARAMETER createFargate METHOD CALLED ===== ***");
    LOG.info("*** JenkinsFactory.createFargate() called with id: " + id + " (4-parameter version) ***");
    
    try {
      LOG.info("*** Step 1: Starting SystemContext ***");
      SystemContext ctx;
      try {
        ctx = SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, security, iamProfile, cfc);
        LOG.info("*** SystemContext started successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in SystemContext.start(): " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    
      LOG.info("*** Step 2: Creating FlowLogFactory ***");
      try {
    new FlowLogFactory(scope, id + "Flowlog");
        LOG.info("*** FlowLogFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in FlowLogFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    
      LOG.info("*** Step 3: Creating VpcFactory ***");
      VpcFactory vpc;
      try {
        vpc = new VpcFactory(scope, id + "Vpc");
        LOG.info("*** VpcFactory instantiated ***");
        vpc.injectContexts(); // Manual injection after SystemContext.start()
        LOG.info("*** VpcFactory contexts injected ***");
        vpc.create();
        LOG.info("*** VpcFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in VpcFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    
      LOG.info("*** Step 4: Creating AlbFactory ***");
      AlbFactory alb = null;
      try {
        LOG.info("*** About to instantiate AlbFactory with ctx: " + (ctx != null ? "present" : "null") + " ***");
        alb = new AlbFactory(ctx, id + "Alb"); // Create as child of SystemContext
        LOG.info("*** AlbFactory instantiated successfully ***");
        
        LOG.info("*** About to inject contexts into AlbFactory ***");
        alb.injectContexts(); // Manual injection after SystemContext.start()
        LOG.info("*** AlbFactory contexts injected successfully ***");
        
        LOG.info("*** About to call alb.create() ***");
        alb.create();
        LOG.info("*** alb.create() completed successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in AlbFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    
      LOG.info("*** Step 5: Creating EfsFactory ***");
      EfsFactory efs;
      try {
        efs = new EfsFactory(scope, id + "Efs");
        LOG.info("*** EfsFactory instantiated ***");
        efs.create(ctx);
        LOG.info("*** EfsFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in EfsFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
    
      LOG.info("*** Step 6: Creating LoggingCwFactory ***");
      LoggingCwFactory log;
      LogDriver logDriver;
      try {
        log = new LoggingCwFactory(scope, id + "Logging");
        LOG.info("*** LoggingCwFactory instantiated ***");
        LOG.info("*** About to call log.create() ***");
        log.create(); // Create the log group first
        LOG.info("*** LoggingCwFactory.create() completed ***");
        logDriver = LogDriver.awsLogs(AwsLogDriverProps.builder().logGroup(ctx.logs.get().orElseThrow()).streamPrefix("jenkins").build());
        LOG.info("*** LogDriver created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in LoggingCwFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }

      LOG.info("*** Step 7: Creating FargateFactory ***");
      FargateFactory fargate;
      try {
        fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
        LOG.info("*** FargateFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in FargateFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 8: Creating JenkinsBootstrap ***");
      try {
    new JenkinsBootstrap(scope, id + "Jenkins", new JenkinsBootstrap.Props(cfc));
        LOG.info("*** JenkinsBootstrap created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in JenkinsBootstrap: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 9: Creating AlarmFactory ***");
      try {
    new AlarmFactory(scope, id + "Alarms", null);
        LOG.info("*** AlarmFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in AlarmFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 10: Creating DomainFactory ***");
      DomainFactory domain;
      try {
        domain = new DomainFactory(scope, id + "Domain");
        LOG.info("*** DomainFactory instantiated ***");
        domain.injectContexts(); // Manual injection after SystemContext.start()
        LOG.info("*** DomainFactory contexts injected ***");
        domain.create();
        LOG.info("*** DomainFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in DomainFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      // Temporarily disable SslManager to debug HTTPS listener issue
      // TODO: Re-enable SslManager once HTTPS listener issue is resolved
      /*
      if (cfc.enableSsl() && cfc.domain() != null && !cfc.domain().isBlank()) {
          SslManager sslManager = new SslManager(scope, id + "SslManager", new SslManager.Props(cfc));
          sslManager.createSslIfEnabled(ctx);
      }
      */

      LOG.info("*** Step 11: Creating JenkinsSystem result ***");
      JenkinsSystem result;
      try {
        LOG.info("*** About to create JenkinsSystem with vpc=" + (vpc != null) + ", alb=" + (alb != null) + ", efs=" + (efs != null) + " ***");
        result = new JenkinsSystem(vpc, alb, efs);
        LOG.info("*** JenkinsSystem result created successfully ***");
        LOG.info("*** About to proceed to executeDeferredActions() ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in JenkinsSystem creation: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      // Execute deferred actions (runtime configuration wiring) after all factories are created
      LOG.info("*** Executing deferred actions for runtime configuration ***");
      try {
        ctx.executeDeferredActions();
        LOG.info("*** DEBUG: executeDeferredActions() completed ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in executeDeferredActions(): " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** createFargate(4-param) completed successfully ***");
      return result;
      
    } catch (Exception e) {
      LOG.info("*** Exception in createFargate: " + e.getMessage() + " ***");
      e.printStackTrace();
      throw e;
    }
  }
}
