package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.IConstruct;

import java.util.List;


import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.require;
import static com.cloudforgeci.api.core.rules.RuleKit.when;
import static com.cloudforgeci.api.core.rules.RuleKit.whenAll;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class FargateRuntimeConfiguration implements RuntimeConfiguration {
  @Override public RuntimeType kind() { return RuntimeType.FARGATE; }
  @Override public String id() { return "runtime:FARGATE"; }

  //@Override public List<Rule> rules(SystemContext c) {
 //   return List.of();
  //}

  //@Override public void wire(SystemContext c) {
  //  fargateProfile().wire(c);
  //}

  @Override
  public List<Rule> rules(SystemContext c) {
    boolean ssl = c.cfc != null && Boolean.TRUE.equals(c.cfc.enableSsl());
    String domain = c.cfc != null ? c.cfc.domain() : null;
    String fqdn   = c.cfc != null ? c.cfc.fqdn()   : null;
    boolean haveHost   = (domain != null && !domain.isBlank()) || (fqdn != null && !fqdn.isBlank());
    boolean wantDns    = haveHost;           // publish A/AAAA when host provided (w/ or w/o SSL)
    boolean wantSslDns = ssl && haveHost;    // HTTPS only when SSL + host

    return List.of(
            require("vpc", x -> x.vpc),
            require("alb", x -> x.alb),
            require("http listener", x -> x.http),
            require("fargate service", x -> x.fargateService),
            require("fargate container", x -> x.container),
            forbid("asg", x -> x.asg),
            forbid("targetGroup", x -> x.albTargetGroup),   // listener-driven in Fargate

            // DNS/zone presence only when a host is provided
            when(wantDns,     require("hosted zone",  x -> x.zone)),

            // SSL stack only when SSL + host
            when(wantSslDns,  require("certificate",  x -> x.cert)),
            when(wantSslDns,  require("https listener", x -> x.https)),

            // Forbid HTTPS bits explicitly when SSL is OFF
            when(!ssl,        forbid("https listener", x -> x.https)),
            when(!ssl,        forbid("certificate",    x -> x.cert))
    );
  }

  @Override
  public void wire(SystemContext c) {
    // Inputs & flags
    final boolean ssl   = c.cfc != null && Boolean.TRUE.equals(c.cfc.enableSsl());
    final String domain = norm(c.cfc != null ? c.cfc.domain() : null);
    final String fqdn   = norm(c.cfc != null ? c.cfc.fqdn()   : null);

    final boolean haveHost   = (domain != null && !domain.isBlank()) || (fqdn != null && !fqdn.isBlank());
    final boolean wantDns    = haveHost;           // publish A/AAAA when a host is provided
    final boolean wantSslDns = ssl && haveHost;    // SSL mode with host → cert + HTTPS + redirect

    // ── 0) DNS A/AAAA for ALB (works with or without SSL) ──────────────────────
    if (wantDns) {
      whenBoth(c.zone, c.alb, (zone, alb) -> {
        final String zoneName = norm(zone.getZoneName());
        final String host     = (fqdn != null && !fqdn.isBlank()) ? fqdn : (domain != null ? domain : zoneName);

        if (!host.equals(zoneName) && !host.endsWith("." + zoneName)) {
          throw new IllegalArgumentException("Host '" + host + "' is not within zone '" + zoneName + "'");
        }
        final String recordName = host.equals(zoneName) ? null
                : host.substring(0, host.length() - (zoneName.length() + 1)); // "jkns"

        RecordTarget target = RecordTarget.fromAlias(
                new LoadBalancerTarget(alb));
        Stack stack = Stack.of(alb);

        // A
        ARecordProps.Builder aProps = ARecordProps.builder()
                .zone(zone).target(target);
        if (recordName != null && !recordName.isBlank()) aProps.recordName(recordName);
        new ARecord(stack, "AlbAliasA", aProps.build());

        // AAAA
        AaaaRecordProps.Builder aaaaProps = AaaaRecordProps.builder()
                .zone(zone).target(target);
        if (recordName != null && !recordName.isBlank()) aaaaProps.recordName(recordName);
        new AaaaRecord(stack, "AlbAliasAAAA", aaaaProps.build());
      });
    }

    // ── 1) DOMAIN + NO SSL → HTTP only (single TG), NO cert/https/redirect ─────
    if (!ssl) {
      whenAll(c.http, c.fargateService, c.container, (http, svc, container) -> {
        http.addTargets("SvcHttp",
                AddApplicationTargetsProps.builder()
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .targets(java.util.List.of(svc))
                        .healthCheck(HealthCheck.builder()
                                .path("/login").healthyHttpCodes("200-399")
                                .interval(software.amazon.awscdk.Duration.seconds(15))
                                .timeout(software.amazon.awscdk.Duration.seconds(5))
                                .healthyThresholdCount(2).unhealthyThresholdCount(3)
                                .build())
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
    if (!wantSslDns) return; // SSL requested but no host → fall back to HTTP-only silently

    // 2a) ACM cert (DNS validation)
    c.once("Fargate-AcmCert", () -> whenBoth(c.zone, c.alb, (zone, alb) -> {
      if (c.cert.get().isPresent()) return;
      Certificate cert = Certificate.Builder
              .create(c, "HttpsCert")
              .domainName(fqdn != null ? fqdn : domain)
              .validation(software.amazon.awscdk.services.certificatemanager.CertificateValidation.fromDns(zone))
              .build();
      c.cert.set(cert);
    }));

    // 2b) HTTPS listener
    whenBoth(c.alb, c.cert, (albIface, cert) -> {
      ApplicationLoadBalancer alb = albIface;
      ApplicationListener https = alb.addListener("Https",
              BaseApplicationListenerProps.builder()
                      .port(443)
                      .certificates(java.util.List.of(ListenerCertificate.fromCertificateManager(cert)))
                      .build());
      c.https.set(https);
    });

    // 2c) Service behind HTTPS
    whenBoth(c.https, c.fargateService, (https, svc) -> {
      https.addTargets("SvcHttps",
              AddApplicationTargetsProps.builder()
                      .port(8080)
                      .protocol(ApplicationProtocol.HTTP)
                      .targets(java.util.List.of(svc))
                      .healthCheck(HealthCheck.builder()
                              .path("/login").healthyHttpCodes("200-399")
                              .interval(software.amazon.awscdk.Duration.seconds(15))
                              .timeout(software.amazon.awscdk.Duration.seconds(5))
                              .healthyThresholdCount(2).unhealthyThresholdCount(3)
                              .build())
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

    // 2d) Make HTTP's DEFAULT action a redirect to HTTPS (don’t leave any TG on HTTP)
    whenBoth(c.http, c.https, (http, https) -> {
      CfnListener cfnHttp = (CfnListener) http.getNode().getDefaultChild();
      if (cfnHttp != null) {
        cfnHttp.setDefaultActions(java.util.List.of(
                CfnListener.ActionProperty.builder()
                        .type("redirect")
                        .redirectConfig(
                                CfnListener.RedirectConfigProperty.builder()
                                        .protocol("HTTPS").port("443").statusCode("HTTP_301").build())
                        .build()
        ));
      }
    });
  }

  private static String norm(String s) {
    return s == null ? null : s.trim().replaceAll("\\.$", "").toLowerCase(java.util.Locale.ROOT);
  }


}


