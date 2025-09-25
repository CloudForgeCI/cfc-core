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
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.InstanceTarget;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.AccessPointOptions;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.efs.Acl;
import software.constructs.Construct;

import java.util.List;
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
      
      // Map security profile to appropriate IAM profile
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);
      LOG.info("*** IAM profile assigned: " + iamProfile + " ***");
      LOG.info("*** IAM profile assigned successfully, about to start SystemContext ***");
      
      // Force EC2 runtime for EC2 deployments
      SystemContext ctx = SystemContext.start(scope, cfc.topology(), RuntimeType.EC2, security, iamProfile, cfc);
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
    
      // Use orchestration layer to create infrastructure factories
      LOG.info("*** Using orchestration layer to create infrastructure factories ***");
      SystemContext.InfrastructureFactories infra = ctx.createInfrastructureFactories(scope, id);
      LOG.info("*** Infrastructure factories created successfully via orchestration layer ***");
      
      // Create instance security group for EC2 runtime (handled by orchestration layer)
      LOG.info("*** Instance security group already created by orchestration layer ***");
      
      // AutoScalingGroup for JENKINS_SERVICE topology only if maxInstanceCapacity > 1
      // Otherwise, create single instance for JENKINS_SERVICE topology
      LOG.info("*** About to check topology for EC2 deployment: " + cfc.topology() + " ***");
      if (cfc.topology() == TopologyType.JENKINS_SERVICE) {
          if (cfc.maxInstanceCapacity() != null && cfc.maxInstanceCapacity() > 1) {
              LOG.info("*** Creating Auto Scaling Group for JENKINS_SERVICE topology with maxInstanceCapacity: " + cfc.maxInstanceCapacity() + " ***");
              Ec2Factory ec2 = new Ec2Factory(scope, id + "Ec2");
              ec2.create();
          } else {
              LOG.info("*** Creating single instance for JENKINS_SERVICE topology (maxInstanceCapacity <= 1) ***");
              createSingleEc2Instance(scope, id + "SingleInstance", ctx);
          }
      } else if (cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
          LOG.info("*** Creating single instance for JENKINS_SINGLE_NODE topology ***");
          createSingleEc2Instance(scope, id + "SingleInstance", ctx);
      }
      
      new AlarmFactory(scope, id + "Alarms", null);
      
      // Create domain factory if domain is provided (for DNS records)
      if (cfc.domain() != null && !cfc.domain().isBlank()) {
          DomainFactory domain = new DomainFactory(scope, id + "Domain");
          domain.create();
      }
      
      JenkinsSystem result = new JenkinsSystem(infra.vpc(), infra.alb(), infra.efs());
      
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
    
      // Use orchestration layer to create infrastructure factories
      LOG.info("*** Using orchestration layer to create infrastructure factories ***");
      SystemContext.InfrastructureFactories infra = ctx.createInfrastructureFactories(scope, id);
      LOG.info("*** Infrastructure factories created successfully via orchestration layer ***");
      
      // Create Jenkins-specific factories
      LOG.info("*** Creating Jenkins-specific factories ***");
      
      try {
        FargateFactory fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
        fargate.injectContexts(); // Manual injection after SystemContext.start()
        fargate.create(); // Call create() to populate fargateTaskDef slot
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
        result = new JenkinsSystem(infra.vpc(), infra.alb(), infra.efs());
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
    // Use the provided IAM profile (mapped from security profile)
    IAMProfile effectiveIamProfile = iamProfile;
    // Force EC2 runtime for EC2 deployments
    SystemContext ctx = SystemContext.start(scope, cfc.topology(), RuntimeType.EC2, security, effectiveIamProfile, cfc);
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
    
    // Use orchestration layer to create infrastructure factories
    LOG.info("*** Using orchestration layer to create infrastructure factories ***");
    SystemContext.InfrastructureFactories infra = ctx.createInfrastructureFactories(scope, id);
    LOG.info("*** Infrastructure factories created successfully via orchestration layer ***");
    
    // Create instance security group for EC2 runtime (handled by orchestration layer)
    LOG.info("*** Instance security group already created by orchestration layer ***");
    
    // AutoScalingGroup for JENKINS_SERVICE topology only if maxInstanceCapacity > 1
    // Otherwise, create single instance for JENKINS_SERVICE topology
    LOG.info("*** Checking topology for EC2 deployment: " + cfc.topology() + " ***");
    if (cfc.topology() == TopologyType.JENKINS_SERVICE) {
        if (cfc.maxInstanceCapacity() != null && cfc.maxInstanceCapacity() > 1) {
            LOG.info("*** Creating multi-instance deployment with AutoScalingGroup (maxInstanceCapacity: " + cfc.maxInstanceCapacity() + ") ***");
            Ec2Factory ec2 = new Ec2Factory(scope, id + "Ec2");
            ec2.create();
        } else {
            LOG.info("*** Creating single-instance deployment for JENKINS_SERVICE topology (maxInstanceCapacity <= 1) ***");
            try {
                createSingleEc2Instance(scope, id + "SingleInstance", ctx);
                LOG.info("*** Single instance created successfully ***");
            } catch (Exception e) {
                LOG.severe("*** Error creating single instance: " + e.getMessage() + " ***");
                throw e;
            }
        }
    } else if (cfc.topology() == TopologyType.JENKINS_SINGLE_NODE) {
        LOG.info("*** Creating single-instance deployment for JENKINS_SINGLE_NODE topology ***");
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
        
        // SSL is handled by FargateRuntimeConfiguration
    }
    
    // Execute deferred actions (runtime configuration wiring) after all factories are created
    LOG.info("*** About to execute deferred actions for runtime configuration ***");
    LOG.info("*** JenkinsFactory.createEc2: About to execute deferred actions ***");
    ctx.executeDeferredActions();
    LOG.info("*** JenkinsFactory.createEc2: Deferred actions executed successfully ***");
    
    return new JenkinsSystem(infra.vpc(), infra.alb(), infra.efs());
  }

  /**
   * Creates a single EC2 instance for JENKINS_SINGLE_NODE topology.
   * This avoids AutoScalingGroup which is forbidden for single-node deployments.
   * Uses EFS for persistent Jenkins storage, same as Fargate deployment.
   * Uses the existing target group from the orchestration layer instead of creating a new one.
   */
  private static void createSingleEc2Instance(Construct scope, String id, SystemContext ctx) {
    LOG.info("*** createSingleEc2Instance called for JENKINS_SINGLE_NODE topology ***");
    LOG.info("*** DEBUG: createSingleEc2Instance called ***");
    
    // Create EFS Access Point for Jenkins persistent storage (same as Fargate)
    AccessPoint jenkinsAp = null;
    if (ctx.efs.get().isPresent()) {
      jenkinsAp = ctx.efs.get().orElseThrow().addAccessPoint(id + "JenkinsAp", 
        AccessPointOptions.builder()
          .path("/jenkins")
          .posixUser(PosixUser.builder().uid("1000").gid("1000").build())
          .createAcl(Acl.builder().ownerUid("1000").ownerGid("1000").permissions("750").build())
          .build());
      ctx.ap.set(jenkinsAp);
      LOG.info("*** DEBUG: EFS Access Point created for Jenkins persistent storage ***");
    }
    
    // Create Jenkins installation user data script with EFS mounting
    UserData userData = createJenkinsUserDataWithEfs(ctx, jenkinsAp);
    
    // Create IAM role for the EC2 instance with EFS permissions
    Role ec2Role = Role.Builder.create(scope, id + "Role")
        .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
        .managedPolicies(List.of(
            ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
            ManagedPolicy.fromAwsManagedPolicyName("AmazonElasticFileSystemClientFullAccess")
        ))
        .build();
    
    // Determine subnet type based on network mode
    SubnetType subnetType = "public-no-nat".equals(ctx.cfc.networkMode()) ? 
            SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;
    
            // Parse instance type from DeploymentContext
            String instanceTypeStr = ctx.cfc.instanceType() != null ? ctx.cfc.instanceType() : "t3.micro";
            InstanceType instanceType = parseInstanceType(instanceTypeStr);
            
            // Create a single EC2 instance with Jenkins installed
            Instance instance = Instance.Builder.create(scope, id)
                .vpc(ctx.vpc.get().orElseThrow())
                .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
                .instanceType(instanceType)
                .machineImage(MachineImage.latestAmazonLinux2023())
                .securityGroup(ctx.instanceSg.get().orElseThrow())
                .role(ec2Role)
                .userData(userData)
                .build();
    
    // Set the instance in SystemContext for compatibility
    ctx.ec2Instance.set(instance);
    
    // Use the existing target group from the orchestration layer instead of creating a new one
    if (ctx.albTargetGroup.get().isPresent()) {
      ApplicationTargetGroup existingTargetGroup = ctx.albTargetGroup.get().orElseThrow();
      LOG.info("*** Using existing target group: " + existingTargetGroup.getNode().getId() + " ***");
      
      // Add the instance to the existing target group so ALB can route traffic to it
      existingTargetGroup.addTarget(new InstanceTarget(instance));
      LOG.info("*** Instance added to existing target group successfully ***");
    } else {
      LOG.severe("*** ERROR: No target group found! Target groups should be created by orchestration layer ***");
      throw new IllegalStateException("Target group not found - orchestration layer should have created it");
    }
    
    LOG.info("*** createSingleEc2Instance: Instance created and added to target group successfully ***");
    
    // Note: We don't set asg slot since it's forbidden for JENKINS_SINGLE_NODE
    // The instance is now properly connected to the ALB via the target group
  }
  
  /**
   * Parses instance type string into CDK InstanceType.
   * Supports common T3 instance types.
   */
  private static InstanceType parseInstanceType(String instanceTypeStr) {
    if (instanceTypeStr == null || instanceTypeStr.isEmpty()) {
      return InstanceType.of(InstanceClass.T3, InstanceSize.MICRO);
    }
    
    String[] parts = instanceTypeStr.toLowerCase().split("\\.");
    if (parts.length != 2) {
      LOG.warning("Invalid instance type format: " + instanceTypeStr + ", using t3.micro");
      return InstanceType.of(InstanceClass.T3, InstanceSize.MICRO);
    }
    
    String family = parts[0];
    String size = parts[1];
    
    InstanceClass instanceClass;
    InstanceSize instanceSize;
    
    // Parse instance class
    switch (family) {
      case "t3":
        instanceClass = InstanceClass.T3;
        break;
      case "t4g":
        instanceClass = InstanceClass.T4G;
        break;
      case "m5":
        instanceClass = InstanceClass.M5;
        break;
      case "m6i":
        instanceClass = InstanceClass.M6I;
        break;
      default:
        LOG.warning("Unsupported instance class: " + family + ", using t3");
        instanceClass = InstanceClass.T3;
    }
    
    // Parse instance size
    switch (size) {
      case "micro":
        instanceSize = InstanceSize.MICRO;
        break;
      case "small":
        instanceSize = InstanceSize.SMALL;
        break;
      case "medium":
        instanceSize = InstanceSize.MEDIUM;
        break;
      case "large":
        instanceSize = InstanceSize.LARGE;
        break;
      case "xlarge":
        instanceSize = InstanceSize.XLARGE;
        break;
      case "2xlarge":
        instanceSize = InstanceSize.XLARGE2;
        break;
      default:
        LOG.warning("Unsupported instance size: " + size + ", using micro");
        instanceSize = InstanceSize.MICRO;
    }
    
    return InstanceType.of(instanceClass, instanceSize);
  }

  /**
   * Creates Jenkins installation user data script for EC2 instances with EFS mounting.
   * Uses the same EFS approach as Fargate deployment for persistent storage.
   */
  private static UserData createJenkinsUserDataWithEfs(SystemContext ctx, AccessPoint jenkinsAp) {
    UserData ud = UserData.forLinux();
    ud.addCommands(
        "#!/bin/bash",
        "set -euxo pipefail",
        "echo '=== Jenkins EC2 User Data Script Started ===' >> /var/log/jenkins-userdata.log",
        "echo 'Timestamp: ' $(date) >> /var/log/jenkins-userdata.log",
        "",
        "# Update system packages",
        "echo 'Step 1: Updating system packages...' >> /var/log/jenkins-userdata.log",
        "command -v dnf >/dev/null && dnf -y update || yum -y update",
        "echo 'System packages updated successfully' >> /var/log/jenkins-userdata.log",
        "",
        "# Install Java 17",
        "echo 'Step 2: Installing Java 17...' >> /var/log/jenkins-userdata.log",
        "command -v dnf >/dev/null && dnf -y install java-17-amazon-corretto-headless || yum -y install java-17-amazon-corretto-headless",
        "echo 'Java 17 installed successfully' >> /var/log/jenkins-userdata.log",
        "",
        "# Install Jenkins",
        "echo 'Step 3: Installing Jenkins...' >> /var/log/jenkins-userdata.log",
        "curl -fsSL https://pkg.jenkins.io/redhat-stable/jenkins.repo -o /etc/yum.repos.d/jenkins.repo",
        "rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key",
        "command -v dnf >/dev/null && dnf -y install jenkins || yum -y install jenkins",
        "echo 'Jenkins installed successfully' >> /var/log/jenkins-userdata.log",
        "",
        "# Install EFS utilities and mount EFS for persistent Jenkins storage",
        "echo 'Step 4: Installing EFS utilities...' >> /var/log/jenkins-userdata.log",
        "command -v dnf >/dev/null && dnf -y install amazon-efs-utils || yum -y install amazon-efs-utils",
        "echo 'EFS utilities installed successfully' >> /var/log/jenkins-userdata.log",
        "",
        "# Mount EFS with access point (same as Fargate approach)",
        "echo 'Step 5: Mounting EFS for Jenkins persistent storage...' >> /var/log/jenkins-userdata.log",
        "mkdir -p /var/lib/jenkins",
        String.format("echo \"%s:/ /var/lib/jenkins efs _netdev,tls,iam,accesspoint=%s 0 0\" >> /etc/fstab",
            ctx.efs.get().orElseThrow().getFileSystemId(), 
            jenkinsAp != null ? jenkinsAp.getAccessPointId() : "none"),
        "mount -a || (echo 'EFS mount failed, checking logs...' >> /var/log/jenkins-userdata.log; journalctl -xe >> /var/log/jenkins-userdata.log; exit 1)",
        "chown -R 1000:1000 /var/lib/jenkins || true",
        "echo 'EFS mounted successfully for Jenkins persistent storage' >> /var/log/jenkins-userdata.log",
        "",
        "# Configure Jenkins",
        "echo 'Step 6: Configuring and starting Jenkins...' >> /var/log/jenkins-userdata.log",
        "systemctl enable jenkins",
        "systemctl start jenkins",
        "echo 'Jenkins service started successfully' >> /var/log/jenkins-userdata.log",
        "",
        "# Wait for Jenkins to be ready and log status",
        "echo 'Step 7: Waiting for Jenkins to be ready...' >> /var/log/jenkins-userdata.log",
        "sleep 30",
        "systemctl status jenkins >> /var/log/jenkins-userdata.log 2>&1",
        "echo 'Jenkins installation and configuration completed successfully!' >> /var/log/jenkins-userdata.log",
        "echo 'Timestamp: ' $(date) >> /var/log/jenkins-userdata.log",
        "echo '=== Jenkins EC2 User Data Script Completed ===' >> /var/log/jenkins-userdata.log"
    );
    return ud;
  }

  /**
   * Creates Jenkins installation user data script for EC2 instances.
   */
  private static UserData createJenkinsUserData(SystemContext ctx) {
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
        "echo 'jenkins installed and started' >> /var/log/jenkins-userdata.log",
        "",
        "# Wait for Jenkins to be ready",
        "sleep 30",
        "echo 'jenkins ready' >> /var/log/jenkins-userdata.log"
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
    
      // Use orchestration layer to create infrastructure factories
      LOG.info("*** Using orchestration layer to create infrastructure factories ***");
      SystemContext.InfrastructureFactories infra = ctx.createInfrastructureFactories(scope, id);
      LOG.info("*** Infrastructure factories created successfully via orchestration layer ***");
      
      // Create FlowLogFactory
      LOG.info("*** Step 2: Creating FlowLogFactory ***");
      try {
        new FlowLogFactory(scope, id + "Flowlog");
        LOG.info("*** FlowLogFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in FlowLogFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }

      LOG.info("*** Step 3: Creating FargateFactory ***");
      LOG.info("*** VERY SPECIFIC LOG FOR DEBUGGING: About to create FargateFactory in 5-param method ***");
      FargateFactory fargate;
      try {
        LOG.info("*** About to instantiate FargateFactory ***");
        fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
        LOG.info("*** FargateFactory instantiated successfully ***");
        fargate.injectContexts(); // Manual injection after SystemContext.start()
        LOG.info("*** FargateFactory contexts injected successfully ***");
        fargate.create();
        LOG.info("*** FargateFactory.create() completed successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in FargateFactory instantiation/creation: " + e.getClass().getSimpleName() + ": " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 4: Creating JenkinsBootstrap ***");
      try {
    new JenkinsBootstrap(scope, id + "Jenkins", new JenkinsBootstrap.Props(cfc));
        LOG.info("*** JenkinsBootstrap created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in JenkinsBootstrap: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 5: Creating AlarmFactory ***");
      try {
    new AlarmFactory(scope, id + "Alarms", null);
        LOG.info("*** AlarmFactory created successfully ***");
      } catch (Exception e) {
        LOG.severe("*** CRITICAL: Exception in AlarmFactory: " + e.getMessage() + " ***");
        e.printStackTrace();
        throw e;
      }
      
      LOG.info("*** Step 6: Creating DomainFactory ***");
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
      
      // SSL certificate and DNS records are handled by runtime configurations

      LOG.info("*** Step 7: Creating JenkinsSystem result ***");
      JenkinsSystem result;
      try {
        LOG.info("*** About to create JenkinsSystem with infra factories ***");
        result = new JenkinsSystem(infra.vpc(), infra.alb(), infra.efs());
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
