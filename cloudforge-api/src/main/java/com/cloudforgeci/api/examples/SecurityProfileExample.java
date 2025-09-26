package com.cloudforgeci.api.examples;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Example demonstrating how to access SecurityProfileConfiguration from SystemContext.
 * This shows how any factory or component can access security profile settings via ctx.
 * Uses annotation-based context injection for cleaner code.
 */
public class SecurityProfileExample extends BaseFactory {
    
    private static final Logger LOG = Logger.getLogger(SecurityProfileExample.class.getName());
    
    public SecurityProfileExample(Construct scope, String id) {
        super(scope, id);
    }
    
    @Override
    public void create() {
        // SecurityProfileConfiguration is now injected directly via annotation
        LOG.info("Security Profile Configuration Example:");
        LOG.info("Profile: " + config.getSecurityProfile());
        LOG.info("Log Retention: " + config.getLogRetentionDays());
        LOG.info("Flow Logs Enabled: " + config.isFlowLogsEnabled());
        LOG.info("Security Monitoring Enabled: " + config.isSecurityMonitoringEnabled());
        LOG.info("EBS Encryption Enabled: " + config.isEbsEncryptionEnabled());
        LOG.info("Auto-scaling Enabled: " + config.isAutoScalingEnabled());
        LOG.info("Min Instance Count: " + config.getMinInstanceCount());
        LOG.info("Max Instance Count: " + config.getMaxInstanceCount());
        
        // Example: Configure resources based on security profile
        configureResourcesBasedOnProfile();
    }
    
    /**
     * Example method showing how to configure resources based on security profile settings.
     */
    private void configureResourcesBasedOnProfile() {
        LOG.info("Configuring resources based on security profile: " + config.getSecurityProfile());
        
        // Example: Set auto-scaling parameters based on profile
        if (config.isAutoScalingEnabled()) {
            LOG.info("Configuring auto-scaling with min: " + config.getMinInstanceCount() + 
                    ", max: " + config.getMaxInstanceCount());
            // Here you would configure actual auto-scaling resources
        }
        
        // Example: Configure encryption based on profile
        if (config.isEbsEncryptionEnabled()) {
            LOG.info("Configuring EBS encryption for security profile");
            // Here you would configure EBS encryption
        }
        
        // Example: Configure monitoring based on profile
        if (config.isSecurityMonitoringEnabled()) {
            LOG.info("Configuring security monitoring for profile");
            // Here you would configure CloudWatch alarms, etc.
        }
        
        // Example: Configure backup based on profile
        if (config.isAutomatedBackupEnabled()) {
            LOG.info("Configuring automated backup with retention: " + config.getBackupRetentionDays() + " days");
            // Here you would configure backup policies
        }
    }
}
