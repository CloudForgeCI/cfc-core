package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.rules.Rules;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;

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

import java.util.HashSet;
import java.util.Set;


public final class SystemContext extends Construct {

  private static final String NODE_ID = "SystemContext";

  public final TopologyType topology;
  public final RuntimeType runtime;
  public final DeploymentContext cfc;

  // Common slots
  public final Slot<software.amazon.awscdk.services.ec2.Vpc> vpc = new Slot<>();
  public final Slot<software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer> alb = new Slot<>();
  public final Slot<software.amazon.awscdk.services.autoscaling.AutoScalingGroup> asg = new Slot<>();
  public final Slot<software.amazon.awscdk.services.efs.FileSystem> efs = new Slot<>();
  public final Slot<software.amazon.awscdk.services.logs.LogGroup> logs = new Slot<>();
  public final Slot<software.amazon.awscdk.services.route53.IHostedZone> zone = new Slot<>();
  public final Slot<software.amazon.awscdk.services.ec2.SecurityGroup> instanceSg = new Slot<>();

  // ALB Properties
  public final Slot<ApplicationTargetGroup> albTargetGroup = new Slot<>();
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

  // S3 website bits
  public final Slot<software.amazon.awscdk.services.s3.Bucket> websiteBucket = new Slot<>();
  public final Slot<software.amazon.awscdk.services.cloudfront.Distribution> distribution = new Slot<>();

  // Logging
  public final Slot<FlowLogOptions> flowlogs = new Slot<>();

  private final Set<String> onceKeys = new HashSet<>();
  private boolean installed = false;

  private SystemContext(Stack stack, TopologyType topology, RuntimeType runtime, DeploymentContext cfc) {
    super(stack, NODE_ID);
    this.topology = topology;
    this.runtime = runtime;
    this.cfc = cfc;
  }

  /** Start once at the entry point; installs runtime + topology rules and wiring. */
  public static SystemContext start(Construct scope, TopologyType topology, RuntimeType runtime, DeploymentContext cfc) {
    Stack stack = Stack.of(scope);
    SystemContext existing = (SystemContext) stack.getNode().tryFindChild(NODE_ID);
    if (existing != null) {
      if (existing.runtime != runtime) {
        throw new IllegalStateException("SystemContext already started with runtime=" + existing.runtime);
      }
      if (existing.topology != topology) {
        throw new IllegalStateException("SystemContext already started with topology=" + existing.topology);
      }
      return existing;
    }
    var ctx = new SystemContext(stack, topology, runtime, cfc);
    if (!ctx.installed) {
      Rules.installAll(ctx);   // installs both RuntimeRules and TopologyRules
      ctx.installed = true;
    }
    return ctx;
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
    r.run();
    return true;
  }

  public String debugPath(Construct scope) {
    String here = scope.getNode().getPath();
    String sc   = this.getNode().getPath();
    return "scopePath=" + here + " | ctxPath=" + sc + " | runtime=" + runtime + " | topology=" + topology + " | slots=" + presentSlots();
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