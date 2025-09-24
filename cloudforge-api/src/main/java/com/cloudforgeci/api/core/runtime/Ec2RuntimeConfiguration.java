package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.FixedResponseOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.require;
import static com.cloudforgeci.api.core.rules.RuleKit.whenAll;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class Ec2RuntimeConfiguration implements RuntimeConfiguration {

  private static final Logger LOG = Logger.getLogger(Ec2RuntimeConfiguration.class.getName());

  @Override
  public RuntimeType kind() { return RuntimeType.EC2; }

  @Override
  public String id() { return "runtime:EC2"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    var rules = new ArrayList<Rule>();
    
    // Always required
    rules.add(require("vpc", x -> x.vpc));
    rules.add(require("alb", x -> x.alb));
    rules.add(require("targetGroup", x -> x.albTargetGroup));
    rules.add(require("instanceSg", x -> x.instanceSg));
    rules.add(forbid("fargate", x -> x.fargateService));
    
    // AutoScalingGroup is only required for JENKINS_SERVICE topology
    // JENKINS_SINGLE_NODE forbids AutoScalingGroup
    if (c.topology == TopologyType.JENKINS_SERVICE) {
        rules.add(require("asg", x -> x.asg));
    }
    
    return rules;
  }

  @Override
  public void wire(SystemContext c) {
    // Ensure this configuration only runs for EC2 runtime
    if (c.runtime != RuntimeType.EC2) {
      LOG.info("*** Ec2RuntimeConfiguration: Skipping wire() for runtime: " + c.runtime + " ***");
      return;
    }
    
    LOG.info("*** Ec2RuntimeConfiguration: Starting wire() for EC2 runtime ***");
    
    // Inputs & flags
    final boolean ssl = c.cfc != null && Boolean.TRUE.equals(c.cfc.enableSsl());
    final String domain = norm(c.cfc != null ? c.cfc.domain() : null);
    final String fqdn = norm(c.cfc != null ? c.cfc.fqdn() : null);

    final boolean haveHost = (domain != null && !domain.isBlank()) || (fqdn != null && !fqdn.isBlank());
    final boolean wantSslDns = ssl && haveHost; // SSL mode with host → cert + HTTPS + redirect

    // ── 0) DNS A record for ALB (works with or without SSL) ──────────────────────
    // Note: DNS record creation is handled by topology configurations to avoid conflicts
    // if (wantDns) {
    //   whenBoth(c.zone, c.alb, (zone, alb) -> {
    //     final String zoneName = norm(zone.getZoneName());
    //     final String host = (fqdn != null && !fqdn.isBlank()) ? fqdn : (domain != null ? domain : zoneName);
    //
    //     if (!host.equals(zoneName) && !host.endsWith("." + zoneName)) {
    //       throw new IllegalArgumentException("Host '" + host + "' is not within zone '" + zoneName + "'");
    //     }
    //     final String recordName = host.equals(zoneName) ? null
    //             : host.substring(0, host.length() - (zoneName.length() + 1)); // "jenkins"
    //
    //     RecordTarget target = RecordTarget.fromAlias(new LoadBalancerTarget(alb));
    //     ARecordProps.Builder aProps = ARecordProps.builder()
    //             .zone(zone).target(target);
    //     if (recordName != null && !recordName.isBlank()) aProps.recordName(recordName);
    //     new ARecord(c, "AlbAliasA", aProps.build());
    //   });
    // }

    // ── 1) ASG -> TargetGroup wiring (only for multi-instance deployments) ─────
    // For single-instance deployments, the instance is already added to target group in createSingleEc2Instance()
    whenBoth(c.asg, c.albTargetGroup, (asg, tg) -> tg.addTarget(asg));

    // ── 2) ALB SG -> Instance SG :8080 ──────────────────────────────────────────
    whenBoth(c.alb, c.instanceSg, (alb, isg) -> {
      ISecurityGroup albSg = alb.getConnections().getSecurityGroups().get(0);
      isg.addIngressRule(Peer.securityGroupId(albSg.getSecurityGroupId()),
              Port.tcp(8080), "ALB_to_Jenkins_8080", false);
    });

    // ── 3) DOMAIN + NO SSL → HTTP only (single TG), NO cert/https/redirect ─────
    if (!ssl) {
      whenBoth(c.http, c.albTargetGroup, (http, tg) -> {
        // HTTP listener already has the target group as default action from AlbFactory
        // No additional wiring needed for HTTP-only mode
      });
      return; // ← CRITICAL: prevents creating cert/https/redirect
    }

    // ── 4) SSL + DOMAIN/FQDN → ACM + HTTPS + HTTP default redirect ─────────────
    if (!wantSslDns) return; // SSL requested but no host → fall back to HTTP-only silently

    // 4a) ACM cert (DNS validation)
    whenBoth(c.zone, c.alb, (zone, alb) -> {
      if (c.cert.get().isPresent()) return;
      Certificate cert = Certificate.Builder
              .create(c, "HttpsCert")
              .domainName(fqdn != null ? fqdn : domain)
              .validation(CertificateValidation.fromDns(zone))
              .build();
      c.cert.set(cert);
    });

    // 4b) HTTPS listener - create only when cert and alb are ready
    whenBoth(c.cert, c.alb, (cert, alb) -> {
      if (c.https.get().isPresent()) return; // Avoid duplicate creation
      LOG.info("*** Ec2RuntimeConfiguration: Creating HTTPS listener ***");
      
      ApplicationListener https;
      if (c.albTargetGroup.get().isPresent()) {
        // Target group is available, create listener with target group
        LOG.info("*** Ec2RuntimeConfiguration: Creating HTTPS listener with target group ***");
        https = alb.addListener("Https",
                BaseApplicationListenerProps.builder()
                        .port(443)
                        .certificates(List.of(ListenerCertificate.fromCertificateManager(cert)))
                        .defaultAction(ListenerAction.forward(List.of(c.albTargetGroup.get().orElseThrow())))
                        .build());
      } else {
        // Target group is not available, create listener with fixed response
        LOG.info("*** Ec2RuntimeConfiguration: Creating HTTPS listener with fixed response ***");
        https = alb.addListener("Https",
                BaseApplicationListenerProps.builder()
                        .port(443)
                        .certificates(List.of(ListenerCertificate.fromCertificateManager(cert)))
                        .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                                .contentType("text/plain")
                                .messageBody("Jenkins is starting up...")
                                .build()))
                        .build());
      }
      c.https.set(https);
      LOG.info("*** Ec2RuntimeConfiguration: HTTPS listener created and set in SystemContext ***");
    });

    // 4c) Service behind HTTPS - wait for all components to be ready
    // Handle both AutoScalingGroup (multi-instance) and single EC2 instance cases
    whenBoth(c.https, c.albTargetGroup, (https, tg) -> {
      LOG.info("*** Ec2RuntimeConfiguration: Adding target group to HTTPS listener ***");
      https.addTargetGroups("SvcHttps",
              AddApplicationTargetGroupsProps.builder()
                      .targetGroups(List.of(tg))
                      .build());
      LOG.info("*** Ec2RuntimeConfiguration: Target group added to HTTPS listener successfully ***");
    });

    // 4d) Make HTTP's DEFAULT action a redirect to HTTPS (don't leave any TG on HTTP)
    whenBoth(c.http, c.https, (http, https) -> {
      CfnListener cfnHttp = (CfnListener) http.getNode().getDefaultChild();
      if (cfnHttp != null) {
        cfnHttp.setDefaultActions(List.of(
                CfnListener.ActionProperty.builder()
                        .type("redirect")
                        .redirectConfig(
                                CfnListener.RedirectConfigProperty.builder()
                                        .protocol("HTTPS").port("443").statusCode("HTTP_301").build())
                        .build()
        ));
      }
    });
    
    // Auto Scaling Configuration - EC2 runtime (when ASG is available)
    whenAll(c.minInstanceCapacity, c.maxInstanceCapacity, c.cpuTargetUtilization, 
            (minCapacity, maxCapacity, cpuTarget) -> {
        // Configure EC2 Auto Scaling Group with all required parameters
        // This ensures all scaling parameters are available before configuration
        // ASG scaling configuration would go here
        // Example: Configure auto scaling policies, target tracking, etc.
    });
  }

  private static String norm(String s) {
    return s == null ? null : s.trim().replaceAll("\\.$", "").toLowerCase(Locale.ROOT);
  }
}