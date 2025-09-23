package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogDestination;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for creating security profile-based observability configurations.
 * Manages logging, flow logs, and security monitoring based on security profiles.
 */
public class SecurityProfileFactory extends Construct {
    
    private static final Logger LOG = Logger.getLogger(SecurityProfileFactory.class.getName());
    
    public SecurityProfileFactory(Construct scope, String id) {
        super(scope, id);
        SystemContext ctx = SystemContext.of(this);
        
        // Get the appropriate security profile configuration
        SecurityProfileConfiguration config = getSecurityProfileConfiguration(ctx.security);
        
        LOG.info("Creating observability configuration for security profile: " + ctx.security);
        
        // Configure CloudWatch Log Groups
        configureCloudWatchLogs(ctx, config);
        
        // Configure VPC Flow Logs
        configureVpcFlowLogs(ctx, config);
        
        // Configure Security Monitoring
        configureSecurityMonitoring(ctx, config);
        
        LOG.info("Security profile observability configuration completed");
    }
    
    /**
     * Get the appropriate security profile configuration based on the security profile.
     */
    private SecurityProfileConfiguration getSecurityProfileConfiguration(SecurityProfile securityProfile) {
        return switch (securityProfile) {
            case DEV -> new DevSecurityProfileConfiguration();
            case STAGING -> new StagingSecurityProfileConfiguration();
            case PRODUCTION -> new ProductionSecurityProfileConfiguration();
        };
    }
    
    /**
     * Configure CloudWatch Log Groups based on security profile.
     */
    private void configureCloudWatchLogs(SystemContext ctx, SecurityProfileConfiguration config) {
        if (ctx.logs.get().isPresent()) {
            LOG.info("CloudWatch logs already configured, skipping");
            return;
        }
        
        LogGroup logGroup = LogGroup.Builder.create(this, "SecurityProfileLogs")
                .retention(config.getLogRetentionDays())
                .removalPolicy(config.getLogRemovalPolicy())
                .logGroupName("/aws/jenkins/" + ctx.security.name().toLowerCase())
                .build();
        
        ctx.logs.set(logGroup);
        LOG.info("Created CloudWatch log group with retention: " + config.getLogRetentionDays());
    }
    
    /**
     * Configure VPC Flow Logs based on security profile.
     */
    private void configureVpcFlowLogs(SystemContext ctx, SecurityProfileConfiguration config) {
        // Check if flow logs are enabled for this security profile
        if (!config.isFlowLogsEnabled()) {
            LOG.info("Flow logs disabled for security profile: " + ctx.security);
            return;
        }
        
        // Check if flow logs are already configured
        if (ctx.flowlogs.get().isPresent()) {
            LOG.info("Flow logs already configured, skipping");
            return;
        }
        
        // Check if VPC is available
        if (!ctx.vpc.get().isPresent()) {
            LOG.warning("VPC not available for flow logs configuration");
            return;
        }
        
        // Create flow log log group
        LogGroup flowLogGroup = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                .retention(config.getFlowLogRetentionDays())
                .removalPolicy(config.getLogRemovalPolicy())
                .logGroupName("/aws/vpc/flowlogs/" + ctx.security.name().toLowerCase())
                .build();
        
        // Create flow log options
        FlowLogOptions flowLogOptions = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(flowLogGroup))
                .build();
        
        ctx.flowlogs.set(flowLogOptions);
        LOG.info("Created VPC flow logs with traffic type: " + config.getFlowLogTrafficType() + 
                " and retention: " + config.getFlowLogRetentionDays());
    }
    
    /**
     * Configure security monitoring based on security profile.
     */
    private void configureSecurityMonitoring(SystemContext ctx, SecurityProfileConfiguration config) {
        if (!config.isSecurityMonitoringEnabled()) {
            LOG.info("Security monitoring disabled for security profile: " + ctx.security);
            return;
        }
        
        LOG.info("Configuring security monitoring for profile: " + ctx.security);
        
        // Configure CloudTrail if enabled
        if (config.isCloudTrailEnabled()) {
            configureCloudTrail(ctx, config);
        }
        
        // Configure GuardDuty if enabled
        if (config.isGuardDutyEnabled()) {
            configureGuardDuty(ctx, config);
        }
        
        // Configure Config if enabled
        if (config.isConfigEnabled()) {
            configureConfig(ctx, config);
        }
        
        // Configure ALB access logging if enabled
        if (config.isAlbAccessLoggingEnabled()) {
            configureAlbAccessLogging(ctx, config);
        }
    }
    
    /**
     * Configure CloudTrail for audit logging.
     */
    private void configureCloudTrail(SystemContext ctx, SecurityProfileConfiguration config) {
        LOG.info("Configuring CloudTrail for security profile: " + ctx.security);
        // CloudTrail configuration would be implemented here
        // This is a placeholder for future CloudTrail integration
    }
    
    /**
     * Configure GuardDuty for threat detection.
     */
    private void configureGuardDuty(SystemContext ctx, SecurityProfileConfiguration config) {
        LOG.info("Configuring GuardDuty for security profile: " + ctx.security);
        // GuardDuty configuration would be implemented here
        // This is a placeholder for future GuardDuty integration
    }
    
    /**
     * Configure Config for compliance monitoring.
     */
    private void configureConfig(SystemContext ctx, SecurityProfileConfiguration config) {
        LOG.info("Configuring Config for security profile: " + ctx.security);
        // Config configuration would be implemented here
        // This is a placeholder for future Config integration
    }
    
    /**
     * Configure ALB access logging.
     */
    private void configureAlbAccessLogging(SystemContext ctx, SecurityProfileConfiguration config) {
        LOG.info("Configuring ALB access logging for security profile: " + ctx.security);
        // ALB access logging configuration would be implemented here
        // This is a placeholder for future ALB access logging integration
    }
}
