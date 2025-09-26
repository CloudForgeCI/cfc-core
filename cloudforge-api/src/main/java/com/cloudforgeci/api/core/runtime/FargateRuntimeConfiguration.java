package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.elasticloadbalancingv2.RedirectOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.IConstruct;

import java.util.List;
import java.util.logging.Logger;


import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.require;
import static com.cloudforgeci.api.core.rules.RuleKit.when;
import static com.cloudforgeci.api.core.rules.RuleKit.whenAll;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class FargateRuntimeConfiguration implements RuntimeConfiguration {
  
    private static final Logger LOG = Logger.getLogger(FargateRuntimeConfiguration.class.getName());
  @Override public RuntimeType kind() { return RuntimeType.FARGATE; }
  @Override public String id() { return "runtime:FARGATE"; }



  @Override
  public List<Rule> rules(SystemContext c) {
    return List.of(
            require("vpc", x -> x.vpc),
            require("alb", x -> x.alb),
            require("http listener", x -> x.http),
            require("fargate service", x -> x.fargateService),
            require("fargate container", x -> x.container),
            forbid("asg", x -> x.asg),
            forbid("targetGroup", x -> x.albTargetGroup),   // listener-driven in Fargate
            forbid("instanceSg", x -> x.instanceSg)          // EC2-specific
    );
  }

  @Override
  public void wire(SystemContext c) {
    // Ensure this configuration only runs for FARGATE runtime
    if (c.runtime != RuntimeType.FARGATE) {
      return;
    }
    
    // Add explicit guard to prevent duplicate execution
    if (c.wired.get().isPresent()) {
      return;
    }
    c.wired.set(true);
    
    // Track if HTTP and HTTPS listeners have been configured to prevent duplicates
    final boolean[] httpConfigured = {false};
    final boolean[] httpsConfigured = {false};
    
    try {
      // Inputs & flags
      final boolean ssl   = c.cfc != null && Boolean.TRUE.equals(c.cfc.enableSsl());
      final String domain = norm(c.cfc != null ? c.cfc.domain() : null);
      final String fqdn   = norm(c.cfc != null ? c.cfc.fqdn()   : null);

      final boolean haveHost   = (domain != null && !domain.isBlank()) || (fqdn != null && !fqdn.isBlank());
      final boolean wantDns    = haveHost;           // publish A/AAAA when a host is provided
      final boolean wantSslDns = ssl && haveHost;    // SSL mode with host → cert + HTTPS + redirect

    // ── 0) DNS A/AAAA for ALB (works with or without SSL) ──────────────────────
    // Note: DNS record creation is handled by topology configurations to avoid conflicts
    // if (wantDns) {
    //   System.out.println("FargateRuntimeConfiguration: Creating DNS records for domain: " + domain + ", fqdn: " + fqdn);
    //   whenBoth(c.zone, c.alb, (zone, alb) -> {
    //     final String zoneName = norm(zone.getZoneName());
    //     final String host     = (fqdn != null && !fqdn.isBlank()) ? fqdn : (domain != null ? domain : zoneName);
    //
    //     System.out.println("FargateRuntimeConfiguration: Zone: " + zoneName + ", Host: " + host);
    //
    //     if (!host.equals(zoneName) && !host.endsWith("." + zoneName)) {
    //       throw new IllegalArgumentException("Host '" + host + "' is not within zone '" + zoneName + "'");
    //     }
    //     final String recordName = host.equals(zoneName) ? null
    //             : host.substring(0, host.length() - (zoneName.length() + 1)); // "jkns"
    //
    //     System.out.println("FargateRuntimeConfiguration: Record name: " + recordName);
    //
    //     RecordTarget target = RecordTarget.fromAlias(
    //             new LoadBalancerTarget(alb));
    //     Stack stack = Stack.of(alb);
    //
    //     // A record only (ALBs typically only support IPv4)
    //     ARecordProps.Builder aProps = ARecordProps.builder()
    //             .zone(zone).target(target);
    //     if (recordName != null && !recordName.isBlank()) aProps.recordName(recordName);
    //     new ARecord(stack, "AlbAliasA", aProps.build());
    //   });
    // }

    // ── 1) DOMAIN + NO SSL → HTTP only (single TG), NO cert/https/redirect ─────
    if (!ssl) {
      whenBoth(c.http, c.fargateService, (http, svc) -> {
        if (httpConfigured[0]) {
          return;
        }
        httpConfigured[0] = true;
        
        // Create a target group for the Fargate service with configurable health check settings
        int interval = c.cfc.healthCheckInterval() != null ? c.cfc.healthCheckInterval() : 30;
        int timeout = c.cfc.healthCheckTimeout() != null ? c.cfc.healthCheckTimeout() : 5;
        int healthyThreshold = c.cfc.healthyThreshold() != null ? c.cfc.healthyThreshold() : 2;
        int unhealthyThreshold = c.cfc.unhealthyThreshold() != null ? c.cfc.unhealthyThreshold() : 3;
        
        ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(c, "FargateHttpTargetGroup")
                .vpc(c.vpc.get().orElseThrow())
                .port(8080)
                .protocol(ApplicationProtocol.HTTP)
                .targets(java.util.List.of(svc))
                .healthCheck(HealthCheck.builder()
                        .path("/login").healthyHttpCodes("200-299")
                        .interval(software.amazon.awscdk.Duration.seconds(interval))
                        .timeout(software.amazon.awscdk.Duration.seconds(timeout))
                        .healthyThresholdCount(healthyThreshold).unhealthyThresholdCount(unhealthyThreshold)
                        .build())
                .build();
        
        // Update the HTTP listener's default action to forward to the target group
        // Note: This replaces the fixed response with a forward action
        http.addTargetGroups("HttpTargetGroup", AddApplicationTargetGroupsProps.builder()
                .targetGroups(java.util.List.of(targetGroup))
                .build());
        

        // Make ECS wait for HTTP listener & rules
        CfnService cfnSvc  = (CfnService)  svc.getNode().getDefaultChild();
        CfnListener cfnHttp = (CfnListener) http.getNode().getDefaultChild();
        if (cfnHttp != null) cfnSvc.addDependency(cfnHttp);
        for (IConstruct child : http.getNode().getChildren()) {
          IConstruct def = child.getNode().getDefaultChild();
          if (def instanceof CfnListenerRule rule) {
            cfnSvc.addDependency(rule);
          }
        }
      });
      return; // ← CRITICAL: prevents creating cert/https/redirect that detach the HTTP TG
    }
    
    // ── 2) SSL + DOMAIN/FQDN → ACM + HTTPS + HTTP default redirect ─────────────
    if (!wantSslDns) {
      return; // SSL requested but no host → fall back to HTTP-only silently
    }

    // 2a) ACM cert (DNS validation) - wait for zone, alb, AND domain to be available
    
    whenBoth(c.zone, c.alb, (zone, alb) -> {
      if (c.cert.get().isPresent()) {
        return;
      }
      String certDomain = fqdn != null ? fqdn : domain;
      if (certDomain == null || certDomain.isBlank()) {
        LOG.warning("*** FargateRuntimeConfiguration: Certificate creation skipped - no domain available ***");
        return;
      }
      Certificate cert = Certificate.Builder
              .create(c, "HttpsCert")
              .domainName(certDomain)
              .validation(CertificateValidation.fromDns(zone))
              .build();
      c.cert.set(cert);
    });

    // 2b) HTTPS listener - create only when cert and alb are ready
    whenBoth(c.cert, c.alb, (cert, alb) -> {
      if (c.https.get().isPresent()) return; // Avoid duplicate creation
      ApplicationListener https = alb.addListener("Https",
              BaseApplicationListenerProps.builder()
                      .port(443)
                      .certificates(java.util.List.of(ListenerCertificate.fromCertificateManager(cert)))
                      .build());
      c.https.set(https);
    });

    // 2c) Service behind HTTPS - wait for HTTPS listener, Fargate service, AND certificate to be ready
    whenAll(c.https, c.fargateService, c.cert, (https, svc, cert) -> {
      if (httpsConfigured[0]) {
        return;
      }
      httpsConfigured[0] = true;
      
      // Create a target group for the Fargate service with configurable health check settings
      int interval = c.cfc.healthCheckInterval() != null ? c.cfc.healthCheckInterval() : 30;
      int timeout = c.cfc.healthCheckTimeout() != null ? c.cfc.healthCheckTimeout() : 5;
      int healthyThreshold = c.cfc.healthyThreshold() != null ? c.cfc.healthyThreshold() : 2;
      int unhealthyThreshold = c.cfc.unhealthyThreshold() != null ? c.cfc.unhealthyThreshold() : 3;
      
      ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(c, "FargateHttpsTargetGroup")
              .vpc(c.vpc.get().orElseThrow())
              .port(8080)
              .protocol(ApplicationProtocol.HTTP)
              .targets(java.util.List.of(svc))
                .healthCheck(HealthCheck.builder()
                        .path("/login").healthyHttpCodes("200-299")
                        .interval(software.amazon.awscdk.Duration.seconds(interval))
                        .timeout(software.amazon.awscdk.Duration.seconds(timeout))
                        .healthyThresholdCount(healthyThreshold).unhealthyThresholdCount(unhealthyThreshold)
                        .build())
              .build();
      
      // Update the HTTPS listener's default action to forward to the target group
      https.addTargetGroups("HttpsTargetGroup", AddApplicationTargetGroupsProps.builder()
              .targetGroups(java.util.List.of(targetGroup))
              .build());
      

      CfnService cfnSvc  = (CfnService)  svc.getNode().getDefaultChild();
      CfnListener cfnHttps= (CfnListener) https.getNode().getDefaultChild();
      if (cfnHttps != null) cfnSvc.addDependency(cfnHttps);
      for (IConstruct child : https.getNode().getChildren()) {
        IConstruct def = child.getNode().getDefaultChild();
        if (def instanceof CfnListenerRule rule) {
          cfnSvc.addDependency(rule);
        }
      }
    });

    // 2d) HTTP listener is configured to redirect to HTTPS when SSL is enabled
    // No need to create HTTP target group since HTTP traffic will be redirected to HTTPS

    } catch (Exception e) {
      throw e;
    }
  }

  private static String norm(String s) {
    return s == null ? null : s.trim().replaceAll("\\.$", "").toLowerCase(java.util.Locale.ROOT);
  }


}


