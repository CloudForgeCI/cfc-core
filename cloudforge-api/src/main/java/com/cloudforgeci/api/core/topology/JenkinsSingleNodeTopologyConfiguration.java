package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

import java.util.ArrayList;
import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.*;
import static java.util.List.of;

public final class JenkinsSingleNodeTopologyConfiguration implements TopologyConfiguration {
  @Override public TopologyType kind() { return TopologyType.JENKINS_SINGLE_NODE; }
  @Override public String id() { return "topology:JENKINS_SINGLE_NODE"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    var r = new ArrayList<Rule>();

    // This topology is only valid with EC2 runtime.
    r.add(ctx -> ctx.runtime != RuntimeType.EC2
            ? List.of("JENKINS_SINGLE_NODE requires runtime=EC2") : List.of());

    // Forbid EFS (single-node Jenkins typically uses EBS).
    r.add(forbid("EFS", x -> x.efs));

    // OIDC requires TLS.
    r.add(ctx -> {
      var mode = ctx.cfc.authMode();
      if ("alb-oidc".equalsIgnoreCase(String.valueOf(mode)) && !ctx.cfc.enableSsl()) {
        return List.of("authMode=alb-oidc requires enableSsl=true");
      }
      return List.of();
    });

    // If TLS + DNS expected, ensure fqdn or ability to compute it.
    r.add(ctx -> {
      if (!ctx.cfc.enableSsl()) return List.of();
      boolean hasFqdn = ctx.cfc.fqdn() != null && !ctx.cfc.fqdn().isBlank();
      boolean canCompute = ctx.cfc.subdomain() != null && ctx.cfc.domain() != null;
      return (hasFqdn || canCompute) ? List.of() : List.of("enableSsl=true requires fqdn OR (subdomain + domain)");
    });
    r.add(forbid("AutoScalingGroup ", x -> x.asg));

    return r;
  }

  @Override
  public void wire(SystemContext c) {
    // Route53 A + AAAA aliases to ALB (only if zone + alb present)
    c.once("Topo:JenkinsSingleNode:AlbAlias", () -> whenBoth(c.zone, c.alb, (zone, alb) -> {
      String record = c.cfc.fqdn();
      if (record == null || record.isBlank()) {
        record = c.cfc.subdomain() == null ? "" : c.cfc.subdomain();
      }

      var target = RecordTarget.fromAlias(new LoadBalancerTarget(alb));
      new ARecord(c, "AlbAliasA", ARecordProps.builder()
              .zone(zone).recordName(record).target(target).build());
      new AaaaRecord(c, "AlbAliasAAAA", AaaaRecordProps.builder()
              .zone(zone).recordName(record).target(target).build());
    }));

  }
}