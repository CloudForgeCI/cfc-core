package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.rules.Rules;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
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
        if (existing.runtime != runtime) {
          throw new IllegalStateException("SystemContext already started with runtime=" + existing.runtime);
        }
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
}