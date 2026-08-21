package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.acmpca.CertificateAuthority;
import software.amazon.awscdk.services.acmpca.CfnCertificate;
import software.amazon.awscdk.services.acmpca.CfnCertificateAuthority;
import software.amazon.awscdk.services.acmpca.CfnCertificateAuthorityActivation;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.certificatemanager.PrivateCertificate;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.SslPolicy;
import software.constructs.IConstruct;

import java.util.List;
import java.util.logging.Logger;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.require;
import static com.cloudforgeci.api.core.rules.RuleKit.whenAll;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class FargateRuntimeConfiguration implements RuntimeConfiguration {

    private static final Logger LOG = Logger.getLogger(FargateRuntimeConfiguration.class.getName());
  @Override public RuntimeType kind() { return RuntimeType.FARGATE; }
  @Override public String id() { return "runtime:FARGATE"; }



  @Override
  public List<Rule> rules(SystemContext c) {
    var rules = new java.util.ArrayList<Rule>();

    // Always required
    rules.add(require("vpc", x -> x.vpc));
    rules.add(require("alb", x -> x.alb));
    rules.add(require("fargate service", x -> x.fargateService));
    rules.add(require("fargate container", x -> x.container));
    rules.add(forbid("asg", x -> x.asg));
    // Note: albTargetGroup is now allowed for OIDC authentication support
    rules.add(forbid("instanceSg", x -> x.instanceSg));  // EC2-specific

    // HTTP listener is NOT required when HTTPS_STRICT mode is enabled (PCI-DSS requirement 4.1)
    // In HTTPS_STRICT mode, only HTTPS listener is created - no HTTP listener at all
    boolean httpsStrict = c.securityProfileConfig.get()
        .map(config -> config.isHttpsStrictEnabled())
        .orElse(false);
    boolean sslEnabled = Boolean.TRUE.equals(c.cfc.enableSsl());

    if (!(httpsStrict && sslEnabled)) {
      // Standard mode: HTTP listener is required
      rules.add(require("http listener", x -> x.http));
    } else {
      // HTTPS_STRICT mode: HTTPS listener is required instead
      rules.add(require("https listener", x -> x.https));
    }

    return rules;
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
      final String importedCertArn = norm(c.cfc != null ? c.cfc.certificateArn() : null);

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

        // Get application-specific configuration from ApplicationSpec
        int applicationPort = c.applicationSpec.get().map(spec -> spec.applicationPort()).orElse(8080);
        String healthCheckPath = c.applicationSpec.get().map(spec -> spec.healthCheckPath()).orElse("/");

        ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(c, "FargateHttpTargetGroup")
                .vpc(c.vpc.get().orElseThrow())
                .port(applicationPort)
                .protocol(ApplicationProtocol.HTTP)
                .targets(java.util.List.of(svc))
                .healthCheck(HealthCheck.builder()
                        .path(healthCheckPath).healthyHttpCodes("200-399")
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

    // ── 2) SSL MODE ─────────────────────────────────────────────────────────────
    // Three paths, in priority order:
    // A0) certificateArn set → import the existing (already publicly-trusted) ACM certificate
    //     as-is — no Route53 zone needed, works for any publicly-issued cert (ACM's own, a
    //     Let's Encrypt cert, a purchased one, ...) as long as it's already sitting in this
    //     account's ACM under that ARN.
    // A) SSL + custom domain, no certificateArn → ACM public certificate with DNS validation
    //     (needs a Route53 hosted zone this deployment controls)
    // B) SSL + no domain, no certificateArn → AWS Private CA certificate for ALB DNS name
    //    (Required for application-oidc/alb-oidc without custom domain — NOT trusted by
    //    browsers, see the warning logged below; use A0 instead if a real trusted cert matters)

    if (!ssl) {
      return; // No SSL requested
    }

    // 2a) Create certificate - imported ARN, public (with domain), or private (without domain)
    if (importedCertArn != null && !importedCertArn.isBlank()) {
      c.alb.onSet(alb -> {
        if (c.cert.get().isPresent()) {
          return;
        }
        LOG.info("Using imported ACM certificate: " + importedCertArn);
        var cert = Certificate.fromCertificateArn(c, "ImportedHttpsCert", importedCertArn);
        c.cert.set(cert);
      });
    } else if (wantSslDns) {
      // Path A: Public ACM certificate with DNS validation
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

        cert.applyRemovalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY);
        c.cert.set(cert);
      });
    } else {
      // Path B: Private CA certificate for ALB DNS name (no custom domain required)
      // This enables HTTPS for application-oidc/alb-oidc without a custom domain
      c.alb.onSet(alb -> {
        if (c.cert.get().isPresent()) {
          return;
        }

        LOG.info("Creating Private CA certificate for ALB DNS name (no custom domain configured)");
        String albDnsName = alb.getLoadBalancerDnsName();

        // Create a Private Certificate Authority (short-lived, for this stack only)
        CfnCertificateAuthority privateCa = CfnCertificateAuthority.Builder.create(c, "PrivateCA")
                .type("ROOT")
                .keyAlgorithm("RSA_2048")
                .signingAlgorithm("SHA256WITHRSA")
                .subject(CfnCertificateAuthority.SubjectProperty.builder()
                        .commonName("CloudForge Private CA")
                        .organization("CloudForge")
                        .organizationalUnit("Infrastructure")
                        .build())
                .build();
        privateCa.applyRemovalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY);

        // Issue a ROOT CA certificate using the CA's own CSR
        // This creates the self-signed root certificate for CA activation
        CfnCertificate rootCaCert = CfnCertificate.Builder.create(c, "RootCACertificate")
                .certificateAuthorityArn(privateCa.getAttrArn())
                .certificateSigningRequest(privateCa.getAttrCertificateSigningRequest())
                .signingAlgorithm("SHA256WITHRSA")
                .templateArn("arn:aws:acm-pca:::template/RootCACertificate/V1")
                .validity(CfnCertificate.ValidityProperty.builder()
                        .type("YEARS")
                        .value(10)
                        .build())
                .build();
        rootCaCert.applyRemovalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY);

        // Activate the CA with the self-signed root certificate
        CfnCertificateAuthorityActivation caActivation = CfnCertificateAuthorityActivation.Builder.create(c, "PrivateCAActivation")
                .certificateAuthorityArn(privateCa.getAttrArn())
                .certificate(rootCaCert.getAttrCertificate())
                .status("ACTIVE")
                .build();
        caActivation.addDependency(rootCaCert);

        // Create a private certificate for the ALB DNS name
        // Note: PrivateCertificate requires the CA ARN
        PrivateCertificate privateCert = PrivateCertificate.Builder.create(c, "PrivateHttpsCert")
                .domainName(albDnsName)
                .certificateAuthority(CertificateAuthority.fromCertificateAuthorityArn(
                        c, "ImportedPrivateCA", privateCa.getAttrArn()))
                .build();

        // Ensure proper dependency ordering
        privateCert.getNode().addDependency(caActivation);
        privateCert.applyRemovalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY);

        c.cert.set(privateCert);
        c.privateCa.set(privateCa);

        LOG.info("Private CA certificate created for: " + albDnsName);
        LOG.warning("NOTE: Private CA certificates are NOT trusted by browsers. Users will see certificate warnings.");
        LOG.warning("For Cognito OIDC, ensure your IdP is configured to trust this Private CA.");
      });
    }

    // 2b) Create HTTPS listener with certificate
    whenBoth(c.cert, c.alb, (cert, alb) -> {
      if (c.https.get().isPresent()) return;

      // Use TLS 1.2+ policy for PCI-DSS compliance (Requirement 4.1)
      // RECOMMENDED_TLS enforces TLS 1.2 minimum with strong cipher suites
      ApplicationListener https = alb.addListener("Https",
              BaseApplicationListenerProps.builder()
                      .port(443)
                      .certificates(java.util.List.of(ListenerCertificate.fromCertificateManager(cert)))
                      .sslPolicy(SslPolicy.RECOMMENDED_TLS)
                      .build());

      // Add explicit dependency: HTTPS listener depends on certificate
      // This ensures CloudFormation deletes the listener BEFORE the certificate during stack deletion
      CfnListener cfnHttps = (CfnListener) https.getNode().getDefaultChild();
      software.amazon.awscdk.services.certificatemanager.CfnCertificate cfnCert =
              (software.amazon.awscdk.services.certificatemanager.CfnCertificate) cert.getNode().getDefaultChild();
      if (cfnHttps != null && cfnCert != null) {
        cfnHttps.addDependency(cfnCert);
      }

      c.https.set(https);

      // Register AWS Config rules for HTTPS/TLS compliance
      c.requireConfigRule(AwsConfigRule.ALB_HTTPS_ONLY);
      c.requireConfigRule(AwsConfigRule.ELB_TLS_HTTPS_LISTENERS);
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

      // Get application-specific configuration from ApplicationSpec
      int applicationPort = c.applicationSpec.get().map(spec -> spec.applicationPort()).orElse(8080);
      String healthCheckPath = c.applicationSpec.get().map(spec -> spec.healthCheckPath()).orElse("/");

      ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(c, "FargateHttpsTargetGroup")
              .vpc(c.vpc.get().orElseThrow())
              .port(applicationPort)
              .protocol(ApplicationProtocol.HTTP)
              .targets(java.util.List.of(svc))
                .healthCheck(HealthCheck.builder()
                        .path(healthCheckPath).healthyHttpCodes("200-399")
                        .interval(software.amazon.awscdk.Duration.seconds(interval))
                        .timeout(software.amazon.awscdk.Duration.seconds(timeout))
                        .healthyThresholdCount(healthyThreshold).unhealthyThresholdCount(unhealthyThreshold)
                        .build())
              .build();

      // Store target group in context for other factories to reference (e.g., OIDC)
      c.albTargetGroup.set(targetGroup);

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

    // 2d) Make HTTP's DEFAULT action a redirect to HTTPS (don't leave any TG on HTTP)
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
        LOG.info("HTTP → HTTPS redirect configured for Fargate");
      }
    });

    } catch (Exception e) {
      throw e;
    }
  }

  private static String norm(String s) {
    return s == null ? null : s.trim().replaceAll("\\.$", "").toLowerCase(java.util.Locale.ROOT);
  }


}


