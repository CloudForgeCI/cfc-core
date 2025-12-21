package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.acmpca.CertificateAuthority;
import software.amazon.awscdk.services.acmpca.CfnCertificate;
import software.amazon.awscdk.services.acmpca.CfnCertificateAuthority;
import software.amazon.awscdk.services.acmpca.CfnCertificateAuthorityActivation;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.certificatemanager.PrivateCertificate;
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
import software.amazon.awscdk.services.elasticloadbalancingv2.SslPolicy;

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

    // AutoScalingGroup is only required for JENKINS_SERVICE topology when maxInstanceCapacity > 1
    // When maxInstanceCapacity <= 1, JenkinsFactory creates a single instance instead of ASG
    if (c.topology == TopologyType.JENKINS_SERVICE && c.cfc.maxInstanceCapacity() != null && c.cfc.maxInstanceCapacity() > 1) {
        rules.add(require("asg", x -> x.asg));
    }

    return rules;
  }

  @Override
  public void wire(SystemContext c) {
    LOG.info("=== Ec2RuntimeConfiguration.wire() called == = ");

    // Ensure this configuration only runs for EC2 runtime
    if (c.runtime != RuntimeType.EC2) {
      LOG.info("Skipping EC2 runtime configuration (runtime = " + c.runtime + ")");
      return;
    }

    LOG.info("Configuring EC2 runtime for " + c.topology + " topology, " + c.security + " profile");

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

    // ── 1) ASG -> TargetGroup wiring is now handled by JenkinsServiceTopologyConfiguration ─────
    // This prevents duplicate target group additions and centralizes the logic
    // whenBoth(c.asg, c.albTargetGroup, (asg, tg) -> tg.addTarget(asg)); // REMOVED - handled by topology configuration

    // ── 2) ALB SG -> Instance SG (application port) ──────────────────────────────────────────
    whenBoth(c.alb, c.instanceSg, (alb, isg) -> {
      ISecurityGroup albSg = alb.getConnections().getSecurityGroups().get(0);
      // Use application-specific port from ApplicationSpec, fallback to 8080 for legacy Jenkins
      int appPort = c.applicationSpec.get().map(spec -> spec.applicationPort()).orElse(8080);
      String appId = c.applicationSpec.get().map(spec -> spec.applicationId()).orElse("app");
      isg.addIngressRule(Peer.securityGroupId(albSg.getSecurityGroupId()),
              Port.tcp(appPort), "ALB_to_" + appId + "_" + appPort, false);
    });

    // ── 2a) Auto Scaling Configuration - EC2 runtime (when ASG is available) ────
    // Apply scaling policies to Auto Scaling Group ONLY for PRODUCTION security profile
    // DEV and STAGING profiles have auto-scaling disabled in their security configurations
    boolean isProduction = c.security == SecurityProfile.PRODUCTION;

    if (isProduction) {
      LOG.info("PRODUCTION profile - setting up scaling policy callback");

      // Use whenBoth to wait for ASG to be created, with guard to prevent duplicate execution
      whenBoth(c.asg, c.albTargetGroup, (asg, tg) -> {
        // Check if scaling policies have already been applied
        if (c.scalingPoliciesApplied.get().isPresent()) {
          return; // Already applied, skip
        }

        LOG.info("ASG and target group ready - applying scaling policies");

        if (c.cfc.maxInstanceCapacity() != null && c.cfc.maxInstanceCapacity() > 1) {
          // Apply scaling policies using ScalingFactory
          com.cloudforgeci.api.scaling.ScalingFactory scalingFactory =
              new com.cloudforgeci.api.scaling.ScalingFactory(c, "Ec2ScalingPolicy");
          scalingFactory.scale(asg);
          c.scalingPoliciesApplied.set(true);
          LOG.info("Scaling policies applied successfully");
        }
      });
    }

    // ── 3) DOMAIN + NO SSL → HTTP only (single TG), NO cert/https/redirect ─────
    if (!ssl) {
      whenBoth(c.http, c.albTargetGroup, (http, tg) -> {
        // HTTP listener already has the target group as default action from AlbFactory
        // No additional wiring needed for HTTP-only mode
      });
      return; // ← CRITICAL: prevents creating cert/https/redirect
    }

    // ── 4) SSL → ACM certificate (public with domain) OR Private CA (without domain) ─────────────
    // 4a) Create certificate - either public (with domain) or private (without domain)
    if (wantSslDns) {
      // Path A: Public ACM certificate with DNS validation
      whenBoth(c.zone, c.alb, (zone, alb) -> {
        if (c.cert.get().isPresent()) return;

        String certDomain = fqdn != null ? fqdn : domain;

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
        if (c.cert.get().isPresent()) return;

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
      });
    }

    // 4b) Create HTTPS listener with certificate
    // Use TLS 1.2+ policy for PCI-DSS compliance (Requirement 4.1)
    whenBoth(c.cert, c.alb, (cert, alb) -> {
      if (c.https.get().isPresent()) return;

      ApplicationListener https;
      if (c.albTargetGroup.get().isPresent()) {
        // Target group is available, create listener with target group and certificate
        https = alb.addListener("Https",
                BaseApplicationListenerProps.builder()
                        .port(443)
                        .certificates(List.of(ListenerCertificate.fromCertificateManager(cert)))
                        .sslPolicy(SslPolicy.RECOMMENDED_TLS)
                        .defaultAction(ListenerAction.forward(List.of(c.albTargetGroup.get().orElseThrow())))
                        .build());
      } else {
        // Target group is not available, create listener with fixed response and certificate
        https = alb.addListener("Https",
                BaseApplicationListenerProps.builder()
                        .port(443)
                        .certificates(List.of(ListenerCertificate.fromCertificateManager(cert)))
                        .sslPolicy(SslPolicy.RECOMMENDED_TLS)
                        .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                                .contentType("text/plain")
                                .messageBody("Jenkins is starting up...")
                                .build()))
                        .build());
      }

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

    // 4c) Service behind HTTPS - wait for all components to be ready
    // Handle both AutoScalingGroup (multi-instance) and single EC2 instance cases
    whenBoth(c.https, c.albTargetGroup, (https, tg) -> {
      https.addTargetGroups("SvcHttps",
              AddApplicationTargetGroupsProps.builder()
                      .targetGroups(List.of(tg))
                      .build());
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

    LOG.info("=== Ec2RuntimeConfiguration.wire() completed == = ");
  }

  private static String norm(String s) {
    return s == null ? null : s.trim().replaceAll("\\.$", "").toLowerCase(Locale.ROOT);
  }
}
