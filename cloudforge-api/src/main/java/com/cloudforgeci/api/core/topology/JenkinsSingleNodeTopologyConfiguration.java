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

public final class JenkinsSingleNodeTopologyConfiguration implements TopologyConfiguration {
  @Override public TopologyType kind() { return TopologyType.JENKINS_SINGLE_NODE; }
  @Override public String id() { return "topology:JENKINS_SINGLE_NODE"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    var r = new ArrayList<Rule>();

    // This topology is only valid with EC2 runtime.
    r.add(ctx -> ctx.runtime != RuntimeType.EC2
            ? List.of("JENKINS_SINGLE_NODE requires runtime=EC2") : List.of());

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
    // TEMPORARY: Comment out AutoScalingGroup forbid rule to debug
    // r.add(forbid("AutoScalingGroup", x -> x.asg));

    return r;
  }

  @Override
  public void wire(SystemContext c) {
    System.out.println("*** DEBUG: JenkinsSingleNodeTopologyConfiguration.wire() called ***");
    System.out.println("*** DEBUG: Zone present: " + c.zone.get().isPresent() + " ***");
    System.out.println("*** DEBUG: ALB present: " + c.alb.get().isPresent() + " ***");
    System.out.println("*** DEBUG: Domain: " + c.cfc.domain() + " ***");
    System.out.println("*** DEBUG: Subdomain: " + c.cfc.subdomain() + " ***");
    
    // Check if we have domain configuration
    if (c.cfc.domain() == null || c.cfc.domain().isBlank()) {
      System.out.println("*** DEBUG: No domain configured, skipping DNS record creation ***");
      return;
    }
    
    // Check if DNS records callback has already been registered to prevent multiple registrations
    if (c.dnsRecordsCallbackRegistered.get().isPresent()) {
      System.out.println("*** DEBUG: DNS records callback already registered, skipping ***");
      return;
    }
    
    // Route53 A + AAAA aliases to ALB (only if zone + alb present)
    whenBoth(c.zone, c.alb, (zone, alb) -> {
      // Check if DNS records have already been created (inside callback to prevent multiple executions)
      if (c.dnsRecordsCreated.get().isPresent()) {
        System.out.println("*** DEBUG: DNS records already created, skipping ***");
        return;
      }
      
      System.out.println("*** DEBUG: Creating DNS records for zone: " + zone.getZoneName() + " ***");
      
      // Use subdomain for DNS record name, not the full FQDN
      String recordName = c.cfc.subdomain();
      if (recordName == null || recordName.isBlank()) {
        System.out.println("*** DEBUG: No subdomain provided, skipping DNS record creation ***");
        return;
      }
      
      System.out.println("*** DEBUG: DNS record name: " + recordName + " ***");

      var target = RecordTarget.fromAlias(new LoadBalancerTarget(alb));
      // Include stack name in construct ID to ensure uniqueness across different deployments
      String constructIdPrefix = "SingleNodeAlbAlias_" + c.stackName + "_" + c.topology + "_" + c.runtime;
      new ARecord(c, constructIdPrefix + "A", ARecordProps.builder()
              .zone(zone).recordName(recordName).target(target).build());
      new AaaaRecord(c, constructIdPrefix + "AAAA", AaaaRecordProps.builder()
              .zone(zone).recordName(recordName).target(target).build());
      
      System.out.println("*** DEBUG: DNS records created successfully ***");
      
      // Set the DNS records created flag to prevent duplicate execution
      c.dnsRecordsCreated.set(true);
      System.out.println("*** DEBUG: dnsRecordsCreated set to true ***");
    });
    
    // Mark that the DNS records callback has been registered
    c.dnsRecordsCallbackRegistered.set(true);
    System.out.println("*** DEBUG: DNS records callback registered ***");

  }
}