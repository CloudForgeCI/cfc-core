package com.cloudforgeci.api.core.runtime;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificateProps;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationActionProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.RedirectOptions;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;

import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.require;
import static com.cloudforgeci.api.core.rules.RuleKit.whenAll;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;


public final class Ec2RuntimeConfiguration implements RuntimeConfiguration {

  @Override
  public RuntimeType kind() { return RuntimeType.EC2; }

  @Override
  public String id() { return "runtime:EC2"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    return List.of(
            require("vpc", x -> x.vpc),
            require("alb", x -> x.alb),
            //require("targetGroup", x -> x.albTargetGroup),
            require("asg", x -> x.asg),
            //require("instanceSg", x -> x.instanceSg),
            forbid("fargate", x -> x.fargateService)
    );
  }

  @Override
  public void wire(SystemContext c) {
    // ASG -> TargetGroup
    whenBoth(c.asg, c.albTargetGroup, (asg, tg) -> tg.addTarget(asg));

    // ALB SG -> Instance SG :8080
    whenBoth(c.alb, c.instanceSg, (alb, isg) -> {
      ISecurityGroup albSg =  alb.getConnections().getSecurityGroups().get(0);
      isg.addIngressRule(Peer.securityGroupId(albSg.getSecurityGroupId()),
              Port.tcp(8080), "ALB -> Jenkins 8080", false);
    });
    // ---- HTTPS (enableSsl) ----
    boolean sslEnabled  = c.cfc != null && Boolean.TRUE.equals(c.cfc.enableSsl());
    boolean haveDomain  = c.cfc != null && c.cfc.domain() != null && !c.cfc.domain().isBlank();

    if (sslEnabled) {
      // 1) Auto-create ACM cert if absent and we have a Hosted Zone + domain
      c.once("EC2-AcmCert", () -> whenBoth(c.zone, c.alb, (zone, albIface) -> {
        if (c.cert.get().isPresent()) return;
        if (!haveDomain) return;
        String fqdn = c.cfc.fqdn();
        ICertificate cert = new DnsValidatedCertificate(c, "HttpsCert",
                DnsValidatedCertificateProps.builder()
                        .hostedZone(zone)
                        .domainName(fqdn)
                        .region(Stack.of(c).getRegion())   // cert in same region as ALB
                        .build());
        c.cert.set(cert);
      }));

      // 2) Create HTTPS listener when ALB + Certificate (+ TG) exist, and mirror HTTP default
      whenAll(c.alb, c.cert, c.albTargetGroup, (albIface, cert, tg) -> {
        ApplicationLoadBalancer alb = albIface;
        ApplicationListener https = alb.addListener("Https",
                BaseApplicationListenerProps.builder()
                        .port(443)
                        .certificates(java.util.List.of(ListenerCertificate.fromCertificateManager(cert)))
                        .build());
        c.https.set(https);

        https.addTargetGroups("Forward",
                AddApplicationTargetGroupsProps.builder()
                        .targetGroups(List.of(c.albTargetGroup.get().orElseThrow()))
                        .build());

        c.http.get().orElseThrow().addAction("Redirect",
                AddApplicationActionProps.builder()
                        .action(ListenerAction.redirect(
                                RedirectOptions.builder().protocol("HTTPS").port("443").build()))
                        .build());

        // same as HTTP: default forward -> TG
        https.addTargetGroups("HttpsDefault",
                AddApplicationTargetGroupsProps.builder()
                        .targetGroups(java.util.List.of(tg))
                        .build());
      });
    }


    // (Optional) A-record when Zone + ALB appear
    c.once("EC2:ARecord", () -> whenBoth(c.zone, c.alb, (zone, alb) -> {
      new ARecord(c, "AliasToAlb", ARecordProps.builder()
              .zone(zone)
              .recordName(c.cfc.subdomain() == null ? "" : c.cfc.subdomain())
              .target(RecordTarget.fromAlias(new LoadBalancerTarget(alb)))
              .build());
    }));
  }

}