package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;

import java.util.ArrayList;
import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.when;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class JenkinsServiceTopologyConfiguration implements TopologyConfiguration {

  @Override public TopologyType kind() { return TopologyType.JENKINS_SERVICE; }
  @Override public String id() { return "topology:JENKINS_SERVICE"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    var r = new ArrayList<Rule>();

    // This topology supports both Fargate and EC2 runtimes.
    r.add(ctx -> (ctx.runtime != RuntimeType.FARGATE && ctx.runtime != RuntimeType.EC2)
            ? List.of("JENKINS_SERVICE requires runtime=FARGATE or runtime=EC2") : List.of());

    // OIDC requires TLS at ALB. (Runtime profile handles cert wiring; we enforce semantics here.)
    r.add(ctx -> {
      var mode = ctx.cfc.authMode();
      if ("alb-oidc".equalsIgnoreCase(String.valueOf(mode)) && !ctx.cfc.enableSsl()) {
        return List.of("authMode=alb-oidc requires enableSsl=true");
      }
      return List.of();
    });

    // If enableSsl=true and caller expects DNS, they should also provide fqdn (either explicit or subdomain+domain).
    r.add(ctx -> {
      if (!ctx.cfc.enableSsl()) return List.of();
      boolean hasFqdn = ctx.cfc.fqdn() != null && !ctx.cfc.fqdn().isBlank();
      boolean canCompute = ctx.cfc.subdomain() != null && ctx.cfc.domain() != null;
      return (hasFqdn || canCompute) ? List.of() : List.of("enableSsl=true requires fqdn OR (subdomain + domain)");
    });
    // AutoScalingGroup is forbidden for Fargate runtime, but allowed for EC2 runtime
    boolean isFargate = c.cfc.runtime().equals(RuntimeType.FARGATE);
    r.add(when(isFargate , forbid("AutoScalingGroup", x -> x.asg)));

    return r;
  }

  @Override
  public void wire(SystemContext c) {
    boolean scale = c.cfc.minInstanceCapacity() > 0 && c.cfc.maxInstanceCapacity() > c.cfc.minInstanceCapacity();
    if(scale) {
      whenBoth(c.fargateService, c.albTargetGroup, (service, tg) -> {
        ScalableTaskCount scalable = service.autoScaleTaskCount(EnableScalingProps.builder().minCapacity(c.cfc.minInstanceCapacity()).maxCapacity(c.cfc.maxInstanceCapacity()).build());
        scalable.scaleOnCpuUtilization("CpuScaleSvc", CpuUtilizationScalingProps.builder().targetUtilizationPercent(c.cfc.cpuTargetUtilization())
                .scaleInCooldown(Duration.minutes(2)).scaleOutCooldown(Duration.minutes(2)).build());
      });
    }
  }
}
