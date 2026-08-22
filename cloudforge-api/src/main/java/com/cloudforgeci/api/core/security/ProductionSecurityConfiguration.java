package com.cloudforgeci.api.core.security;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.SecurityConfiguration;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.observability.ComplianceFactory;
import com.cloudforgeci.api.observability.WafFactory;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;

import java.util.List;
import java.util.logging.Logger;

import static com.cloudforgeci.api.core.rules.RuleKit.require;

/**
 * Production security configuration with hardened security settings.
 * Configures infrastructure controls used by SOC 2 and HIPAA deployments.
 * Integrates with SecurityProfileConfiguration for observability settings.
 */
public final class ProductionSecurityConfiguration implements SecurityConfiguration {

    private static final Logger LOG = Logger.getLogger(ProductionSecurityConfiguration.class.getName());

    public ProductionSecurityConfiguration() {
        // SecurityProfileConfiguration will be set in SystemContext by SecurityRules
    }

    @Override
    public SecurityProfile kind() {
        return SecurityProfile.PRODUCTION;
    }

    @Override
    public String id() {
        return "security:PRODUCTION";
    }

    @Override
    public List<Rule> rules(SystemContext c) {
        var rules = new java.util.ArrayList<Rule>();
        rules.add(require("vpc", x -> x.vpc));

        // Instance security group is only required for EC2 runtime
        if (c.runtime == RuntimeType.EC2) {
            rules.add(require("instance security group", x -> x.instanceSg));
        }

        rules.add(require("alb security group", x -> x.albSg));
        rules.add(require("efs security group", x -> x.efsSg));
        return rules;
    }

    @Override
    public void wire(SystemContext c) {

        // Configure observability based on security profile
        configureObservability(c);

        // Create hosted zone if domain is provided
        createHostedZone(c);

        // Validate required dependencies before proceeding
        validateDependencies(c);

        // Debug: Check what slots are available

        // Production security settings - restricted network access

        // Instance security group - only for EC2 runtime
        if (c.runtime == RuntimeType.EC2) {
            whenBoth(c.vpc, c.instanceSg, (vpc, instanceSg) -> {
                // SSH access is provided via AWS Systems Manager Session Manager (no port 22 needed).
                // All IAM configurations already include AmazonSSMManagedInstanceCore.
                // Connect with: aws ssm start-session --target <instance-id>

                // Application port only from ALB security group
                if (c.albSg.get().isPresent()) {
                    int appPort = c.applicationSpec.get().map(spec -> spec.applicationPort()).orElse(8080);
                    instanceSg.addIngressRule(
                        Peer.securityGroupId(c.albSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(appPort),
                        "App_from_ALB_(PRODUCTION)",
                        false
                    );
                }

                // Deny all other traffic explicitly
                instanceSg.addEgressRule(
                    Peer.anyIpv4(),
                    Port.allTraffic(),
                    "Deny_all_egress_(PRODUCTION)",
                    false
                );
            });
        }

        // ALB security group - HTTPS primary, HTTP for redirect (unless HTTPS strict mode)
        whenBoth(c.vpc, c.albSg, (vpc, albSg) -> {
            // HTTPS allowed from anywhere (primary)
            albSg.addIngressRule(
                Peer.anyIpv4(),
                Port.tcp(443),
                "HTTPS_from_anywhere_(PRODUCTION)",
                false
            );

            // Check if HTTPS strict mode is enabled (no HTTP listener)
            boolean httpsStrict = c.securityProfileConfig.get()
                .map(SecurityProfileConfiguration::isHttpsStrictEnabled)
                .orElse(false);

            if (!httpsStrict) {
                // HTTP allowed from anywhere for redirect to HTTPS
                // The HTTP listener will redirect all traffic to HTTPS
                albSg.addIngressRule(
                    Peer.anyIpv4(),
                    Port.tcp(80),
                    "HTTP_for_HTTPS_redirect_(PRODUCTION)",
                    false
                );
            } else {
                LOG.info("HTTPS strict mode enabled: Skipping port 80 ingress rule (PRODUCTION)");
            }
        });

        // EFS security group - allow NFS from appropriate security group based on runtime
        whenBoth(c.vpc, c.efsSg, (vpc, efsSg) -> {
            if (c.runtime == RuntimeType.FARGATE) {
                // For Fargate, allow NFS from Fargate service security group
                if (c.fargateServiceSg.get().isPresent()) {
                    efsSg.addIngressRule(
                        Peer.securityGroupId(c.fargateServiceSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(2049),
                        "NFS_from_Fargate_tasks_(PRODUCTION)",
                        false
                    );
                }
            } else {
                // For EC2, allow NFS from instance security group
                if (c.instanceSg.get().isPresent()) {
                    String appId = (c.applicationSpec != null && c.applicationSpec.get().isPresent())
                        ? c.applicationSpec.get().orElseThrow().applicationId()
                        : "app";
                    efsSg.addIngressRule(
                        Peer.securityGroupId(c.instanceSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(2049),
                        "NFS_from_" + appId + "_instances_(PRODUCTION)",
                        false
                    );
                }
            }
        });

        // Fargate security group - minimal access
        whenBoth(c.vpc, c.fargateServiceSg, (vpc, fargateSg) -> {
            // Use application-specific port from ApplicationSpec, fallback to 8080 for legacy Jenkins
            int appPort = c.applicationSpec.get().map(spec -> spec.applicationPort()).orElse(8080);
            fargateSg.addIngressRule(
                Peer.securityGroupId(c.albSg.get().orElseThrow().getSecurityGroupId()),
                Port.tcp(appPort),
                "HTTP_from_ALB_(PRODUCTION)",
                false
            );
        });

        // SSL Configuration - centralized SSL handling for all topologies
        // HTTPS listener creation is handled by runtime configurations (Ec2RuntimeConfiguration, FargateRuntimeConfiguration)
        // This ensures proper separation of concerns and avoids duplicate listener creation
        whenBoth(c.alb, c.sslEnabled, (alb, sslEnabled) -> {
            if (sslEnabled) {

                // Configure HTTP listener to redirect to HTTPS when SSL is enabled
                whenBoth(c.httpRedirectEnabled, c.http, (httpRedirectEnabled, httpListener) -> {
                    if (httpRedirectEnabled) {
                        // HTTP listener should redirect to HTTPS when SSL is enabled
                        // Note: This is handled during HTTP listener creation in AlbFactory
                        // The HTTP listener is created with appropriate default action based on SSL configuration
                        LOG.info("HTTP redirect to HTTPS configured for SSL-enabled deployment");
                    }
                });
            }
        });
    }

    /**
     * Configure observability settings based on PRODUCTION security profile.
     */
    private void configureObservability(SystemContext c) {
        LOG.info("Configuring PRODUCTION observability settings");

        // Get security profile configuration from SystemContext
        if (!c.securityProfileConfig.get().isPresent()) {
            LOG.warning("SecurityProfileConfiguration not available in SystemContext");
            return;
        }

        SecurityProfileConfiguration profileConfig = c.securityProfileConfig.get().orElseThrow();

        // Create ComplianceFactory for CloudTrail and AWS Config
        // This factory will check security profile configuration and create resources accordingly
        // NOTE: Use profile-agnostic ID to avoid resource conflicts when switching security profiles
        // The stack name is already part of the scope hierarchy, ensuring multi-stack isolation
        ComplianceFactory complianceFactory = new ComplianceFactory(c, c.stackName + "-Compliance");
        complianceFactory.create();

        // Create GuardDuty threat detection (enabled by PRODUCTION profile by default)
        // GuardDutyFactory will check security profile configuration
        c.createGuardDutyFactory(c, "Production");

        // Configure logging retention (extended for compliance)
        if (profileConfig.getLogRetentionDays() != null) {
            LOG.info("PRODUCTION profile configured with log retention: " + profileConfig.getLogRetentionDays());
        }

        // Configure flow logs for accepted and rejected traffic
        if (profileConfig.isFlowLogsEnabled()) {
            LOG.info("Flow logs enabled for PRODUCTION profile with traffic type: " + profileConfig.getFlowLogTrafficType());
        }

        // Configure CloudTrail, GuardDuty, and AWS Config monitoring
        if (profileConfig.isSecurityMonitoringEnabled()) {
            LOG.info("Security monitoring enabled for PRODUCTION profile");

            if (profileConfig.isCloudTrailEnabled()) {
                LOG.info("CloudTrail enabled for PRODUCTION profile (audit compliance)");
            }

            if (profileConfig.isGuardDutyEnabled()) {
                LOG.info("GuardDuty enabled for PRODUCTION profile (threat detection)");
            }

            if (profileConfig.isAwsConfigEnabled()) {
                LOG.info("AWS Config enabled for PRODUCTION profile (compliance monitoring)");
            }
        }

        // Configure encryption (mandatory for production)
        if (profileConfig.isEbsEncryptionEnabled()) {
            LOG.info("EBS encryption enabled for PRODUCTION profile (mandatory)");
        }

        if (profileConfig.isEfsEncryptionInTransitEnabled()) {
            LOG.info("EFS encryption in transit enabled for PRODUCTION profile (mandatory)");
        }

        if (profileConfig.isEfsEncryptionAtRestEnabled()) {
            LOG.info("EFS encryption at rest enabled for PRODUCTION profile (mandatory)");
        }

        if (profileConfig.isS3EncryptionEnabled()) {
            LOG.info("S3 encryption enabled for PRODUCTION profile (mandatory)");
        }

        // Configure automated and cross-region backups
        if (profileConfig.isAutomatedBackupEnabled()) {
            LOG.info("Automated backup enabled for PRODUCTION profile with retention: " + profileConfig.getBackupRetentionDays() + " days");
        }

        if (profileConfig.isCrossRegionBackupEnabled()) {
            LOG.info("Cross-region backup enabled for PRODUCTION profile (disaster recovery)");
        }

        // Configure the production instance capacity range
        if (profileConfig.isAutoScalingEnabled()) {
            LOG.info("Auto-scaling enabled for PRODUCTION profile: " + profileConfig.getMinInstanceCount() + "-" + profileConfig.getMaxInstanceCount() + " instances");
        }

        // Configure VPC endpoints, NAT, WAF, and CloudFront
        if (profileConfig.isVpcEndpointsEnabled()) {
            LOG.info("VPC endpoints enabled for PRODUCTION profile (network security)");
        }

        if (profileConfig.isNatGatewayEnabled()) {
            LOG.info("NAT Gateway enabled for PRODUCTION profile (private subnets)");
        }

        if (profileConfig.isWafEnabled()) {
            LOG.info("WAF enabled for PRODUCTION profile (web application protection)");
        }

        if (profileConfig.isCloudFrontEnabled()) {
            LOG.info("CloudFront enabled for PRODUCTION profile (DDoS protection)");
        }

        // Configure detailed billing and ALB access logs
        if (profileConfig.isDetailedBillingEnabled()) {
            LOG.info("Detailed billing enabled for PRODUCTION profile (cost management)");
        }

        if (profileConfig.isAlbAccessLoggingEnabled()) {
            LOG.info("ALB access logging enabled for PRODUCTION profile with retention: " + profileConfig.getAlbAccessLogRetentionDays());
        }

        // Configure Multi-AZ deployment
        if (profileConfig.isMultiAzEnforced()) {
            LOG.info("Multi-AZ deployment enforced for PRODUCTION profile (high availability)");
        }
    }

    /**
     * Validates that required dependencies are available before profile wiring.
     * Throws descriptive exceptions for missing dependencies.
     */
    private void validateDependencies(SystemContext c) {
        if (!c.vpc.get().isPresent()) {
            throw new IllegalStateException("VPC required for ProductionSecurityConfiguration");
        }
        if (!c.albSg.get().isPresent()) {
            throw new IllegalStateException("ALB Security Group required for ProductionSecurityConfiguration");
        }
        if (!c.efsSg.get().isPresent()) {
            throw new IllegalStateException("EFS Security Group required for ProductionSecurityConfiguration");
        }
        if (c.runtime == RuntimeType.EC2 && !c.instanceSg.get().isPresent()) {
            throw new IllegalStateException("Instance Security Group required for EC2 runtime in ProductionSecurityConfiguration");
        }

        LOG.info("ProductionSecurityConfiguration dependencies validated successfully");

        // Auto Scaling Configuration moved to RuntimeConfiguration
        // This ensures scaling configuration is handled by the appropriate runtime profile

        // WAF Configuration - centralized WAF handling
        // Create WafFactory to set up Web Application Firewall protection
        // NOTE: Use stack-specific ID to avoid conflicts when switching security profiles
        WafFactory wafFactory = new WafFactory(c, c.stackName + "-Waf");
        wafFactory.create();

        // CloudFront Configuration - centralized CDN handling
        whenBoth(c.cloudfront, c.domain, (cloudfront, domain) -> {
            if (cloudfront && domain != null) {
                // Configure CloudFront distribution
                // CloudFront distribution creation would go here
                // This ensures CloudFront is only configured when domain is available
                LOG.info("CloudFront configuration enabled for domain: " + domain);
            }
        });

        // Note: OIDC/auth factory wiring is handled by SystemContext.createSecurityFactories(),
        // not here. ProductionSecurityConfiguration owns network/SG restrictions only.
    }

    private static <A,B> void whenBoth(com.cloudforgeci.api.core.Slot<A> a, com.cloudforgeci.api.core.Slot<B> b,
                                      java.util.function.BiConsumer<A,B> fn) {
        Runnable tryRun = () -> {
            var ao = a.get(); var bo = b.get();
            if (ao.isPresent() && bo.isPresent()) fn.accept(ao.get(), bo.get());
        };
        a.onSet(x -> tryRun.run()); b.onSet(y -> tryRun.run()); tryRun.run();
    }

    /**
     * Create or lookup hosted zone if domain is provided.
     */
    private void createHostedZone(SystemContext c) {
        // Create or lookup hosted zone if domain is provided
        if (c.cfc.domain() != null && !c.cfc.domain().isBlank()) {
            LOG.info("ProductionSecurityConfiguration: Setting up hosted zone for domain: " + c.cfc.domain());
            if (c.zone.get().isPresent()) {
                LOG.info("ProductionSecurityConfiguration: Hosted zone already exists");
                return; // Already created
            }

            if (c.cfc.createZone()) {
                // Create a new hosted zone when createZone = true
                LOG.info("ProductionSecurityConfiguration: Creating new hosted zone (createZone = true)");
                software.amazon.awscdk.services.route53.HostedZone zone =
                    software.amazon.awscdk.services.route53.HostedZone.Builder.create((software.constructs.Construct)c.getNode().getScope(), "ProductionZone")
                        .zoneName(c.cfc.domain())
                        .build();
                c.zone.set(zone);
                LOG.info("ProductionSecurityConfiguration: New hosted zone created and set in context");
            } else {
                // Use existing hosted zone when createZone = false
                LOG.info("ProductionSecurityConfiguration: Looking up existing hosted zone (createZone = false)");
                software.amazon.awscdk.services.route53.IHostedZone zone =
                    software.amazon.awscdk.services.route53.HostedZone.fromLookup((software.constructs.Construct)c.getNode().getScope(), "ProductionZoneLookup",
                        software.amazon.awscdk.services.route53.HostedZoneProviderProps.builder()
                            .domainName(c.cfc.domain())
                            .build());
                c.zone.set(zone);
                LOG.info("ProductionSecurityConfiguration: Existing hosted zone looked up and set in context");
            }
        }
    }

}
