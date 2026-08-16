package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import com.cloudforgeci.api.interfaces.Rule;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.AaaaRecordProps;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.amazon.awscdk.services.s3.Bucket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static com.cloudforgeci.api.core.rules.RuleKit.forbid;
import static com.cloudforgeci.api.core.rules.RuleKit.when;
import static com.cloudforgeci.api.core.rules.RuleKit.whenBoth;

/**
 * CMS-specific topology that auto-wires infrastructure from {@link CmsSpec} capabilities.
 *
 * <p>Unlike the generic {@link ApplicationServiceTopologyConfiguration}, this topology
 * reads the CMS plugin's declared capabilities and conditionally provisions:</p>
 *
 * <ul>
 *   <li><strong>S3 media bucket</strong> — when {@code CmsSpec.supportsS3MediaStorage() == true}</li>
 *   <li><strong>ElastiCache Redis</strong> — when {@code CmsSpec.supportsObjectCache() == true} and
 *       {@code preferredCacheBackend() == "redis"}</li>
 *   <li><strong>CloudFront CDN</strong> — when {@code CmsSpec.supportsCdnIntegration() == true}
 *       and either a custom domain or S3 media bucket is present</li>
 *   <li><strong>Route53 DNS records</strong> — when a hosted zone and ALB are available</li>
 *   <li><strong>ECS/EC2 auto-scaling</strong> — when min/max capacity is configured</li>
 * </ul>
 *
 * <h2>cdk.json example</h2>
 * <pre>{@code
 * {
 *   "context": {
 *     "cfc": {
 *       "topology": "cms-service",
 *       "applicationId": "wordpress",
 *       "runtime": "fargate",
 *       "env": "prod",
 *       "domain": "example.com",
 *       "subdomain": "blog",
 *       "enableSsl": true,
 *       "authMode": "alb-oidc"
 *     }
 *   }
 * }
 * }</pre>
 *
 * @since 3.1.0
 * @see CmsSpec
 * @see com.cloudforgeci.api.compute.CmsLoader
 */
public final class CmsServiceTopologyConfiguration implements TopologyConfiguration {

    private static final Logger LOG = Logger.getLogger(CmsServiceTopologyConfiguration.class.getName());

    @Override
    public TopologyType kind() { return TopologyType.CMS_SERVICE; }

    @Override
    public String id() { return "topology:CMS_SERVICE"; }

    // -------------------------------------------------------------------------
    // Rules
    // -------------------------------------------------------------------------

    @Override
    public List<Rule> rules(SystemContext c) {
        var r = new ArrayList<Rule>();

        // Requires Fargate or EC2
        r.add(ctx -> (ctx.runtime != RuntimeType.FARGATE && ctx.runtime != RuntimeType.EC2)
                ? List.of("CMS_SERVICE requires runtime = fargate or runtime = ec2")
                : List.of());

        // Must specify an application ID that resolves to a registered CmsSpec
        r.add(ctx -> {
            String appId = ctx.cfc.applicationId();
            if (appId == null || appId.isBlank()) {
                return List.of(
                    "CMS_SERVICE requires applicationId in the deployment context (e.g., \"applicationId\": \"wordpress\"). " +
                    "Available CMS IDs: wordpress, woocommerce, magento, drupal, joomla, prestashop, " +
                    "opencart, sylius, bagisto, phpbb, flarum, mybb, mediawiki, moodle, dolphin-una, " +
                    "concrete-cms, october-cms, typo3, suitecrm"
                );
            }
            Optional<CmsSpec> resolved = ctx.cfc.cmsSpec();
            if (resolved.isEmpty()) {
                return List.of(
                    "CMS_SERVICE: unknown application '" + appId + "'. " +
                    "Ensure the CmsSpec is registered in META-INF/services/com.cloudforge.core.interfaces.ApplicationSpec."
                );
            }
            return List.of();
        });

        // ALB-OIDC requires TLS
        r.add(ctx -> {
            String mode = ctx.authMode.get().orElse(null);
            Boolean sslEnabled = ctx.sslEnabled.get().orElse(false);
            if (AuthMode.ALB_OIDC == AuthMode.fromString(mode) && !sslEnabled) {
                return List.of("authMode = alb-oidc requires enableSsl = true");
            }
            return List.of();
        });

        // SSL requires FQDN
        r.add(ctx -> {
            Boolean sslEnabled = ctx.sslEnabled.get().orElse(false);
            if (!sslEnabled) return List.of();
            String fqdn = ctx.fqdn.get().orElse(null);
            boolean hasFqdn = fqdn != null && !fqdn.isBlank();
            String subdomain = ctx.subdomain.get().orElse(null);
            String domain = ctx.domain.get().orElse(null);
            boolean canCompute = subdomain != null && domain != null;
            return (hasFqdn || canCompute)
                    ? List.of()
                    : List.of("enableSsl = true requires fqdn OR (subdomain + domain)");
        });

        // No ASG on Fargate
        boolean isFargate = c.runtime.equals(RuntimeType.FARGATE);
        r.add(when(isFargate, forbid("AutoScalingGroup", x -> x.asg)));

        return r;
    }

    // -------------------------------------------------------------------------
    // Wiring
    // -------------------------------------------------------------------------

    @Override
    public void wire(SystemContext c) {
        Optional<CmsSpec> specOpt = c.cfc.cmsSpec();
        if (specOpt.isEmpty()) {
            LOG.warning("CmsServiceTopologyConfiguration.wire() called but no CmsSpec resolved — skipping CMS wiring");
            wireBaseAutoscalingAndDns(c);
            return;
        }

        CmsSpec spec = specOpt.get();
        LOG.info("CMS_SERVICE topology wiring for: " + spec.displayName() + " (" + spec.applicationId() + ")");

        // ------------------------------------------------------------------
        // 1. S3 media bucket — created eagerly (no AWS resource dependency)
        // ------------------------------------------------------------------
        if (spec.supportsS3MediaStorage()) {
            LOG.info("  [CMS] Provisioning S3 media bucket for " + spec.displayName());
            Bucket mediaBucket = CmsMediaStorageConfiguration.createMediaBucket(c, spec);
            LOG.info("  [CMS] S3 media bucket created");

            // ------------------------------------------------------------------
            // 2. CloudFront CDN — requires ALB (deferred until ALB slot is set)
            // ------------------------------------------------------------------
            if (spec.supportsCdnIntegration()) {
                c.alb.onSet((ApplicationLoadBalancer alb) -> {
                    LOG.info("  [CMS] Provisioning CloudFront CDN for " + spec.displayName());
                    Distribution distribution = CmsCdnConfiguration.createCmsDistribution(
                            c, spec, mediaBucket, alb.getLoadBalancerDnsName());
                    CmsCdnConfiguration.createDnsRecords(c, distribution);
                    LOG.info("  [CMS] CloudFront CDN wired");
                });
            }
        }

        // ------------------------------------------------------------------
        // 3. ElastiCache Redis — created eagerly once VPC is present
        //    (VPC is provisioned before topology wiring runs)
        // ------------------------------------------------------------------
        if (spec.supportsObjectCache() && "redis".equals(spec.preferredCacheBackend())) {
            LOG.info("  [CMS] Provisioning ElastiCache Redis for " + spec.displayName());
            try {
                CfnCacheCluster redis = CmsObjectCacheConfiguration.createRedisCluster(c, spec);
                LOG.info("  [CMS] ElastiCache Redis provisioned: " + redis.getClusterName());
            } catch (IllegalStateException e) {
                // VPC not yet available — this can happen if VPC is provisioned lazily.
                // Log and skip; callers should ensure VPC is ready before wiring.
                LOG.warning("  [CMS] Skipping Redis — VPC not yet available: " + e.getMessage());
            }
        }

        // ------------------------------------------------------------------
        // 4. Auto-scaling — same pattern as ApplicationServiceTopologyConfiguration
        // ------------------------------------------------------------------
        wireBaseAutoscalingAndDns(c);
    }

    // -------------------------------------------------------------------------
    // Shared helpers (mirrors ApplicationServiceTopologyConfiguration)
    // -------------------------------------------------------------------------

    /**
     * Wires ECS/EC2 auto-scaling and ALB DNS records.
     *
     * <p>Identical behaviour to {@link ApplicationServiceTopologyConfiguration}
     * so CMS deployments get the same scaling and DNS wiring.</p>
     */
    private void wireBaseAutoscalingAndDns(SystemContext c) {
        Integer maxCap = c.maxInstanceCapacity.get().orElse(null);
        Integer minCap = c.minInstanceCapacity.get().orElse(null);
        // Respect an explicit enableAutoScaling=false even when the capacity range would otherwise enable it.
        boolean scale = maxCap != null && minCap != null && minCap > 0 && maxCap > 1
            && !Boolean.FALSE.equals(c.cfc.enableAutoScaling());

        if (scale && !c.fargateAutoscalingCallbackRegistered.get().isPresent()) {
            whenBoth(c.fargateService, c.alb, (service, alb) -> {
                if (c.fargateAutoscalingConfigured.get().isPresent()) return;

                Integer min = c.minInstanceCapacity.get().orElse(1);
                Integer max = c.maxInstanceCapacity.get().orElse(1);
                Integer cpuTarget = c.cpuTargetUtilization.get().orElse(60);

                ScalableTaskCount scalable = service.autoScaleTaskCount(
                        EnableScalingProps.builder().minCapacity(min).maxCapacity(max).build());
                scalable.scaleOnCpuUtilization("CmsCpuScale",
                        CpuUtilizationScalingProps.builder()
                                .targetUtilizationPercent(cpuTarget)
                                .scaleInCooldown(Duration.minutes(3))
                                .scaleOutCooldown(Duration.minutes(2))
                                .build());

                c.fargateAutoscalingConfigured.set(true);
                LOG.info("  [CMS] Fargate auto-scaling configured: min=" + min + " max=" + max);
            });
            c.fargateAutoscalingCallbackRegistered.set(true);
        }

        if (scale && !c.ec2AutoscalingCallbackRegistered.get().isPresent()) {
            whenBoth(c.asg, c.albTargetGroup, (asg, tg) -> {
                if (c.asgAddedToTargetGroup.get().isPresent()) return;
                tg.addTarget(asg);
                c.asgAddedToTargetGroup.set(true);
            });
            c.ec2AutoscalingCallbackRegistered.set(true);
        }

        // DNS A + AAAA records pointing to the ALB
        if (c.dnsRecordsCallbackRegistered.get().isPresent()) return;

        whenBoth(c.zone, c.alb, (zone, alb) -> {
            if (c.dnsRecordsCreated.get().isPresent()) return;

            String record = c.subdomain.get().orElse(null);
            if (record == null || record.isBlank()) {
                record = c.domain.get().orElse(null);
                if (record == null || record.isBlank()) return;
            }

            var target = RecordTarget.fromAlias(new LoadBalancerTarget(alb));
            String prefix = "CmsAlbAlias_" + c.stackName + "_" + c.topology + "_" + c.runtime;

            new ARecord(c, prefix + "A",
                    ARecordProps.builder().zone(zone).recordName(record).target(target).build());
            new AaaaRecord(c, prefix + "AAAA",
                    AaaaRecordProps.builder().zone(zone).recordName(record).target(target).build());

            c.dnsRecordsCreated.set(true);
        });

        c.dnsRecordsCallbackRegistered.set(true);
    }
}
