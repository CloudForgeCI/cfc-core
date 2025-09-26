package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityConfiguration;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;

import java.util.List;
import java.util.logging.Logger;

import static com.cloudforgeci.api.core.rules.RuleKit.require;

/**
 * Development security configuration with relaxed security settings.
 * Suitable for development environments with minimal security constraints.
 * Integrates with SecurityProfileConfiguration for observability settings.
 */
public final class DevSecurityConfiguration implements SecurityConfiguration {

    private static final Logger LOG = Logger.getLogger(DevSecurityConfiguration.class.getName());

    public DevSecurityConfiguration() {
        // SecurityProfileConfiguration will be set in SystemContext by SecurityRules
    }

    @Override
    public SecurityProfile kind() { 
        return SecurityProfile.DEV; 
    }

    @Override
    public String id() { 
        return "security:DEV"; 
    }

    @Override
    public List<Rule> rules(SystemContext c) {
        var rules = new java.util.ArrayList<Rule>();
        rules.add(require("vpc", x -> x.vpc));
        
        // Instance security group is only required for EC2 runtime
        if (c.runtime == com.cloudforgeci.api.interfaces.RuntimeType.EC2) {
            rules.add(require("instance security group", x -> x.instanceSg));
        }
        
        rules.add(require("alb security group", x -> x.albSg));
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        LOG.info("Configuring DEV security profile with observability settings");
        
        // Configure observability based on security profile
        configureObservability(c);
        
        // Development security settings - minimal restrictions
        
        // Allow broader access for development
        whenBoth(c.vpc, c.instanceSg, (vpc, instanceSg) -> {
            // Allow SSH from anywhere for development convenience
            instanceSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(22), 
                "SSH_from_anywhere_(DEV)", 
                false
            );
            
            // Allow Jenkins port from anywhere for development
            instanceSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(8080), 
                "Jenkins_from_anywhere_(DEV)", 
                false
            );
        });

        // ALB security group - allow HTTP/HTTPS from anywhere
        whenBoth(c.vpc, c.albSg, (vpc, albSg) -> {
            albSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(80), 
                "HTTP_from_anywhere_(DEV)", 
                false
            );
            albSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(443), 
                "HTTPS_from_anywhere_(DEV)", 
                false
            );
        });

        // EFS security group - allow NFS from appropriate security group based on runtime
        whenBoth(c.vpc, c.efsSg, (vpc, efsSg) -> {
            if (c.runtime == com.cloudforgeci.api.interfaces.RuntimeType.FARGATE) {
                // For Fargate, allow NFS from Fargate service security group
                if (c.fargateServiceSg.get().isPresent()) {
                    efsSg.addIngressRule(
                        Peer.securityGroupId(c.fargateServiceSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(2049),
                        "NFS_from_Fargate_tasks_(DEV)",
                        false
                    );
                }
            } else {
                // For EC2, allow NFS from instance security group
                if (c.instanceSg.get().isPresent()) {
                    efsSg.addIngressRule(
                        Peer.securityGroupId(c.instanceSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(2049),
                        "NFS_from_Jenkins_instances_(DEV)",
                        false
                    );
                }
            }
        });

        // Fargate security group - allow from ALB
        whenBoth(c.vpc, c.fargateServiceSg, (vpc, fargateSg) -> {
            fargateSg.addIngressRule(
                Peer.securityGroupId(c.albSg.get().orElseThrow().getSecurityGroupId()),
                Port.tcp(8080),
                "HTTP from ALB (DEV)",
                false
            );
        });

        // Create or lookup hosted zone if domain is provided
        if (c.cfc.domain() != null && !c.cfc.domain().isBlank()) {
            System.out.println("DevSecurityConfiguration: Setting up hosted zone for domain: " + c.cfc.domain());
            if (c.zone.get().isPresent()) {
                System.out.println("DevSecurityConfiguration: Hosted zone already exists");
                return; // Already created
            }
            
            if (c.cfc.createZone()) {
                // Create a new hosted zone when createZone=true
                System.out.println("DevSecurityConfiguration: Creating new hosted zone (createZone=true)");
                software.amazon.awscdk.services.route53.HostedZone zone = 
                    software.amazon.awscdk.services.route53.HostedZone.Builder.create((software.constructs.Construct)c.getNode().getScope(), "DevZone")
                        .zoneName(c.cfc.domain())
                        .build();
                c.zone.set(zone);
                System.out.println("DevSecurityConfiguration: New hosted zone created and set in context");
            } else {
                // Use existing hosted zone when createZone=false
                System.out.println("DevSecurityConfiguration: Looking up existing hosted zone (createZone=false)");
                software.amazon.awscdk.services.route53.IHostedZone zone = 
                    software.amazon.awscdk.services.route53.HostedZone.fromLookup((software.constructs.Construct)c.getNode().getScope(), "DevZoneLookup", 
                        software.amazon.awscdk.services.route53.HostedZoneProviderProps.builder()
                            .domainName(c.cfc.domain())
                            .build());
                c.zone.set(zone);
                System.out.println("DevSecurityConfiguration: Existing hosted zone looked up and set in context");
            }
        }
    }

    /**
     * Configure observability settings based on DEV security profile.
     */
    private void configureObservability(SystemContext c) {
        LOG.info("Configuring DEV observability settings");
        
        // Get security profile configuration from SystemContext
        if (!c.securityProfileConfig.get().isPresent()) {
            LOG.warning("SecurityProfileConfiguration not available in SystemContext");
            return;
        }
        
        SecurityProfileConfiguration profileConfig = c.securityProfileConfig.get().orElseThrow();
        
        // Configure logging retention (minimal for dev)
        if (profileConfig.getLogRetentionDays() != null) {
            LOG.info("DEV profile configured with log retention: " + profileConfig.getLogRetentionDays());
        }
        
        // Configure flow logs (disabled by default for dev)
        if (!profileConfig.isFlowLogsEnabled()) {
            LOG.info("Flow logs disabled for DEV profile (cost optimization)");
        }
        
        // Configure security monitoring (minimal for dev)
        if (!profileConfig.isSecurityMonitoringEnabled()) {
            LOG.info("Security monitoring disabled for DEV profile");
        }
        
        // Configure encryption (basic enabled for dev)
        if (profileConfig.isEbsEncryptionEnabled()) {
            LOG.info("EBS encryption enabled for DEV profile");
        }
        
        // Configure backup (minimal for dev)
        if (!profileConfig.isAutomatedBackupEnabled()) {
            LOG.info("Automated backup disabled for DEV profile (manual backups)");
        }
        
        // Configure auto-scaling (disabled for dev)
        if (!profileConfig.isAutoScalingEnabled()) {
            LOG.info("Auto-scaling disabled for DEV profile");
        }
    }

    private static <A,B> void whenBoth(com.cloudforgeci.api.core.Slot<A> a, com.cloudforgeci.api.core.Slot<B> b, 
                                      java.util.function.BiConsumer<A,B> fn) {
        Runnable tryRun = () -> {
            var ao = a.get(); var bo = b.get();
            if (ao.isPresent() && bo.isPresent()) fn.accept(ao.get(), bo.get());
        };
        a.onSet(x -> tryRun.run()); b.onSet(y -> tryRun.run()); tryRun.run();
    }
    
    private boolean isTestEnvironment() {
        // Check for test environment indicators
        return System.getProperty("java.class.path").contains("test") ||
               System.getProperty("maven.test.skip") != null ||
               System.getProperty("surefire.test.class.path") != null ||
               // Check for JUnit test context in stack trace
               java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                   .anyMatch(element -> element.getClassName().contains("junit") || 
                                       element.getClassName().contains("test"));
    }
}
