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
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.when;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class JenkinsServiceTopologyConfiguration implements TopologyConfiguration {

  private static final Logger LOG = Logger.getLogger(JenkinsServiceTopologyConfiguration.class.getName());

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
    LOG.info("*** DEBUG: JenkinsServiceTopologyConfiguration.wire() called ***");
    LOG.info("*** DEBUG: Zone present: " + c.zone.get().isPresent() + " ***");
    LOG.info("*** DEBUG: ALB present: " + c.alb.get().isPresent() + " ***");
    
    // Auto-scaling configuration for Fargate services
    boolean scale = c.cfc.minInstanceCapacity() > 0 && c.cfc.maxInstanceCapacity() > c.cfc.minInstanceCapacity();
    if(scale) {
      whenBoth(c.fargateService, c.albTargetGroup, (service, tg) -> {
        ScalableTaskCount scalable = service.autoScaleTaskCount(EnableScalingProps.builder().minCapacity(c.cfc.minInstanceCapacity()).maxCapacity(c.cfc.maxInstanceCapacity()).build());
        scalable.scaleOnCpuUtilization("CpuScaleSvc", CpuUtilizationScalingProps.builder().targetUtilizationPercent(c.cfc.cpuTargetUtilization())
                .scaleInCooldown(Duration.minutes(2)).scaleOutCooldown(Duration.minutes(2)).build());
      });
    }
    
    // DNS A/AAAA records for ALB (works with or without SSL) - use SystemContext slot to prevent duplicate execution
    LOG.info("*** DEBUG: About to register DNS records callback ***");
    whenBoth(c.zone, c.alb, (zone, alb) -> {
      // Check if DNS records have already been created (inside the callback to prevent multiple executions)
      if (c.dnsRecordsCreated.get().isPresent()) {
        LOG.info("*** DEBUG: DNS records already created, skipping ***");
        return;
      }
      
      LOG.info("*** DEBUG: Creating DNS records for zone: " + zone.getZoneName() + " ***");
      String record = c.cfc.fqdn();
      if (record == null || record.isBlank()) {
        record = c.cfc.subdomain() == null ? "" : c.cfc.subdomain();
      }
      LOG.info("*** DEBUG: DNS record name: " + record + " ***");

      var target = RecordTarget.fromAlias(new LoadBalancerTarget(alb));
      new ARecord(c, "ServiceAlbAliasA_" + c.topology + "_" + c.runtime, ARecordProps.builder()
              .zone(zone).recordName(record).target(target).build());
      new AaaaRecord(c, "ServiceAlbAliasAAAA_" + c.topology + "_" + c.runtime, AaaaRecordProps.builder()
              .zone(zone).recordName(record).target(target).build());
      LOG.info("*** DEBUG: DNS records created successfully ***");
      
      // Set the DNS records created flag to prevent duplicate execution
      c.dnsRecordsCreated.set(true);
      LOG.info("*** DEBUG: dnsRecordsCreated set to true ***");
    });
    LOG.info("*** DEBUG: DNS records callback registered ***");
  }
}
