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
 * Staging security configuration with moderate security settings.
 * Balances security with operational flexibility for testing environments.
 * Integrates with SecurityProfileConfiguration for observability settings.
 */
public final class StagingSecurityConfiguration implements SecurityConfiguration {

    private static final Logger LOG = Logger.getLogger(StagingSecurityConfiguration.class.getName());

    public StagingSecurityConfiguration() {
        // SecurityProfileConfiguration will be set in SystemContext by SecurityRules
    }

    @Override
    public SecurityProfile kind() { 
        return SecurityProfile.STAGING; 
    }

    @Override
    public String id() { 
        return "security:STAGING"; 
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
        rules.add(require("efs security group", x -> x.efsSg));
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        LOG.info("Configuring STAGING security profile with observability settings");
        
        // Configure observability based on security profile
        configureObservability(c);
        
        // Staging security settings - moderate restrictions
        
        // Instance security group - only for EC2 runtime
        if (c.runtime == com.cloudforgeci.api.interfaces.RuntimeType.EC2) {
            whenBoth(c.vpc, c.instanceSg, (vpc, instanceSg) -> {
                // SSH only from VPC CIDR
                instanceSg.addIngressRule(
                    Peer.ipv4(vpc.getVpcCidrBlock()), 
                    Port.tcp(22), 
                    "SSH from VPC CIDR (STAGING)", 
                    false
                );
                
                // Jenkins port only from ALB security group
                if (c.albSg.get().isPresent()) {
                    instanceSg.addIngressRule(
                        Peer.securityGroupId(c.albSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(8080), 
                        "Jenkins from ALB (STAGING)", 
                        false
                    );
                }
            });
        }

        // ALB security group - allow HTTP/HTTPS from anywhere (needed for external access)
        whenBoth(c.vpc, c.albSg, (vpc, albSg) -> {
            albSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(80), 
                "HTTP_from_anywhere_(STAGING)", 
                false
            );
            albSg.addIngressRule(
                Peer.anyIpv4(), 
                Port.tcp(443), 
                "HTTPS_from_anywhere_(STAGING)", 
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
                        "NFS_from_Fargate_tasks_(STAGING)",
                        false
                    );
                }
            } else {
                // For EC2, allow NFS from instance security group
                if (c.instanceSg.get().isPresent()) {
                    efsSg.addIngressRule(
                        Peer.securityGroupId(c.instanceSg.get().orElseThrow().getSecurityGroupId()),
                        Port.tcp(2049),
                        "NFS_from_Jenkins_instances_(STAGING)",
                        false
                    );
                }
            }
        });

        // Fargate security group - allow from ALB only
        whenBoth(c.vpc, c.fargateServiceSg, (vpc, fargateSg) -> {
            fargateSg.addIngressRule(
                Peer.securityGroupId(c.albSg.get().orElseThrow().getSecurityGroupId()),
                Port.tcp(8080),
                "HTTP from ALB (STAGING)",
                false
            );
        });
    }

    /**
     * Configure observability settings based on STAGING security profile.
     */
    private void configureObservability(SystemContext c) {
        LOG.info("Configuring STAGING observability settings");
        
        // Get security profile configuration from SystemContext
        if (!c.securityProfileConfig.get().isPresent()) {
            LOG.warning("SecurityProfileConfiguration not available in SystemContext");
            return;
        }
        
        SecurityProfileConfiguration profileConfig = c.securityProfileConfig.get().orElseThrow();
        
        // Configure logging retention (moderate for staging)
        if (profileConfig.getLogRetentionDays() != null) {
            LOG.info("STAGING profile configured with log retention: " + profileConfig.getLogRetentionDays());
        }
        
        // Configure flow logs (enabled for staging)
        if (profileConfig.isFlowLogsEnabled()) {
            LOG.info("Flow logs enabled for STAGING profile with traffic type: " + profileConfig.getFlowLogTrafficType());
        }
        
        // Configure security monitoring (moderate for staging)
        if (profileConfig.isSecurityMonitoringEnabled()) {
            LOG.info("Security monitoring enabled for STAGING profile");
            
            if (profileConfig.isCloudTrailEnabled()) {
                LOG.info("CloudTrail enabled for STAGING profile");
            }
            
            if (profileConfig.isConfigEnabled()) {
                LOG.info("Config enabled for STAGING profile");
            }
        }
        
        // Configure encryption (full enabled for staging)
        if (profileConfig.isEbsEncryptionEnabled()) {
            LOG.info("EBS encryption enabled for STAGING profile");
        }
        
        if (profileConfig.isEfsEncryptionInTransitEnabled()) {
            LOG.info("EFS encryption in transit enabled for STAGING profile");
        }
        
        // Configure backup (automated for staging)
        if (profileConfig.isAutomatedBackupEnabled()) {
            LOG.info("Automated backup enabled for STAGING profile with retention: " + profileConfig.getBackupRetentionDays() + " days");
        }
        
        // Configure auto-scaling (enabled for staging)
        if (profileConfig.isAutoScalingEnabled()) {
            LOG.info("Auto-scaling enabled for STAGING profile: " + profileConfig.getMinInstanceCount() + "-" + profileConfig.getMaxInstanceCount() + " instances");
        }
        
        // Configure network security (moderate for staging)
        if (profileConfig.isVpcEndpointsEnabled()) {
            LOG.info("VPC endpoints enabled for STAGING profile");
        }
        
        if (profileConfig.isWafEnabled()) {
            LOG.info("WAF enabled for STAGING profile");
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
}
