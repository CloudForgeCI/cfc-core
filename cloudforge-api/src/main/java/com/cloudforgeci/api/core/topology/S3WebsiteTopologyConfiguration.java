package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;

import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3Origin;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketAccessControl;
import software.amazon.awscdk.services.s3.BucketProps;

import java.util.ArrayList;
import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.*;


public final class S3WebsiteTopologyConfiguration implements TopologyConfiguration {
  @Override public TopologyType kind() { return TopologyType.S3_WEBSITE; }
  @Override public String id() { return "topology:S3_WEBSITE"; }

  @Override
  public List<Rule> rules(SystemContext c) {
    var r = new ArrayList<Rule>();

    boolean domainEnabled = c.cfc != null && c.cfc.domain() != null && !c.cfc.domain().isBlank();
    boolean cfEnabled = c.cfc != null && c.cfc.cloudfrontEnabled();

    // S3 website topology does not provision Jenkins compute (runtime is irrelevant here).
    // If TLS is enabled, we front with CloudFront (viewer TLS at edge).
    r.add(ctx -> ctx.cfc.enableSsl() && !ctx.cfc.cloudfrontEnabled()
            ? List.of("S3_WEBSITE with enableSsl=true requires cloudfront=true (viewer TLS at edge)") : List.of());

    // If cloudfront + custom host expected, ensure fqdn (or can compute).
    r.add(ctx -> {
      if (!ctx.cfc.cloudfrontEnabled()) return List.of();
      boolean hasFqdn = ctx.cfc.fqdn() != null && !ctx.cfc.fqdn().isBlank();
      boolean canCompute = ctx.cfc.subdomain() != null && ctx.cfc.domain() != null;
      return (hasFqdn || canCompute) ? List.of() : List.of("cloudfront=true requires fqdn OR (subdomain + domain)");
    });

    r.addAll(List.of(
            require("websiteBucket", x -> x.websiteBucket),
            when(cfEnabled, require("distribution", x -> x.distribution)),
            when(domainEnabled, require("hosted zone", x -> x.zone)),
            when(domainEnabled && cfEnabled, require("certificate", x -> x.cert)),
            forbid("asg", x -> x.asg),
            forbid("fargate", x -> x.fargateService),
            forbid("alb", x -> x.alb)
    ));
    return r;
  }

  @Override
  public void wire(SystemContext c) {
    // 1) Website bucket (simple static hosting; fine to keep private and serve via CF+OAC later)
    c.once("S3:WebsiteBucket", () -> {
      if (c.websiteBucket.get().isPresent()) return;
      Bucket bucket = new Bucket(c, "WebsiteBucket", BucketProps.builder()
              .publicReadAccess(false) // prefer CF rather than public bucket
              .accessControl(BucketAccessControl.PRIVATE)
              .websiteIndexDocument("index.html")
              .websiteErrorDocument("error.html")
              .build());
      c.websiteBucket.set(bucket);
    });

    // 2) CloudFront distribution (optional; required if enableSsl=true)
    c.once("S3:Distribution", () -> {
      if (!c.cfc.cloudfrontEnabled()) return;

      var origin = new S3Origin(c.websiteBucket.get().orElseThrow(
              () -> new IllegalStateException("websiteBucket must exist before creating CloudFront Distribution")));

      Distribution dist = Distribution.Builder.create(c, "WebsiteDist")
              .defaultBehavior(BehaviorOptions.builder()
                      .origin(origin)
                      .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                      .build())
              // If you’re passing a custom viewer cert via some upstream step, attach it through c.cert slot.
              .certificate(c.cert.get().orElse(null))
              .domainNames(resolveDomainNames(c))
              .build();

      c.distribution.set(dist);
    });

    // 3) Route53 alias to CF (ONLY if a zone is already set deterministically)
    c.once("S3:CfAlias", () -> whenBoth(c.zone, c.distribution, (zone, dist) -> {
      String rec = c.cfc.fqdn();
      if (rec == null || rec.isBlank()) {
        rec = c.cfc.subdomain() == null ? "" : c.cfc.subdomain();
      }
      var target = RecordTarget.fromAlias(new CloudFrontTarget(dist));
      new ARecord(c, "CfAliasA", ARecordProps.builder()
              .zone(zone).recordName(rec).target(target).build());
      new AaaaRecord(c, "CfAliasAAAA", AaaaRecordProps.builder()
              .zone(zone).recordName(rec).target(target).build());
    }));

    // (Optional) hook to add security headers / OAC policy if you also attach cert into c.cert
    c.once("S3:Hardening", () -> whenAll(c.websiteBucket, c.distribution, c.cert, (bucket, dist, cert) -> {
      // e.g., add response headers policy, S3 OAC, etc. Left empty intentionally for now.
    }));
  }

  private static java.util.List<String> resolveDomainNames(SystemContext c) {
    if (!c.cfc.cloudfrontEnabled()) return java.util.List.of();
    String fqdn = c.cfc.fqdn();
    if (fqdn == null || fqdn.isBlank()) {
      if (c.cfc.subdomain() != null && c.cfc.domain() != null) {
        fqdn = c.cfc.subdomain().isBlank() ? c.cfc.domain() : c.cfc.subdomain() + "." + c.cfc.domain();
      }
    }
    return (fqdn == null || fqdn.isBlank()) ? java.util.List.of() : java.util.List.of(fqdn);
  }

}