package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * CloudWatch Logging Factory using annotation-based context injection.
 * Configures CloudWatch log groups based on security profile settings.
 */
public class LoggingCwFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(LoggingCwFactory.class.getName());

    public LoggingCwFactory(Construct scope, String id) {
        super(scope, id);
    }
    
    @Override
    public void create() {
        try {
            // SecurityProfileConfiguration is now injected directly via annotation
            LOG.info("LoggingCwFactory: Starting create() method");
            LOG.info("LoggingCwFactory: ctx is null: " + (ctx == null));
            if (ctx != null) {
                LOG.info("LoggingCwFactory: ctx.security is null: " + (ctx.security == null));
                if (ctx.security != null) {
                    LOG.info("LoggingCwFactory: ctx.security = " + ctx.security);
                }
            }
            LOG.info("LoggingCwFactory: config is null: " + (config == null));
            
            if (config == null) {
                LOG.severe("LoggingCwFactory: SecurityProfileConfiguration is null!");
                throw new IllegalStateException("SecurityProfileConfiguration is null");
            }

            LOG.info("LoggingCwFactory: About to check if logs are already configured");
            // Check if logs are already configured
            if (ctx.logs.get().isPresent()) {
                LOG.info("CloudWatch logs already configured, skipping");
                return;
            }

            LOG.info("LoggingCwFactory: About to create LogGroup");
            // Create log group with security profile-based settings
            String securityProfileName = (ctx.security != null) ? ctx.security.name().toLowerCase() : "unknown";
            String runtimeName = (ctx.runtime != null) ? ctx.runtime.name().toLowerCase() : "unknown";
            String stackName = ctx.stackName;
            String logGroupName = "/aws/jenkins/" + stackName + "/" + runtimeName + "/" + securityProfileName;
            LOG.info("LoggingCwFactory: Creating log group with name: " + logGroupName);
        // Use configurable log retention from DeploymentContext if monitoring is enabled
        RetentionDays retentionDays = config.getLogRetentionDays();
        if (ctx.cfc.enableMonitoring() && ctx.cfc.logRetentionDays() != null) {
            // Map integer days to RetentionDays enum
            int days = ctx.cfc.logRetentionDays();
            if (days <= 1) {
                retentionDays = RetentionDays.ONE_DAY;
            } else if (days <= 3) {
                retentionDays = RetentionDays.THREE_DAYS;
            } else if (days <= 5) {
                retentionDays = RetentionDays.FIVE_DAYS;
            } else if (days <= 7) {
                retentionDays = RetentionDays.ONE_WEEK;
            } else if (days <= 14) {
                retentionDays = RetentionDays.TWO_WEEKS;
            } else if (days <= 30) {
                retentionDays = RetentionDays.ONE_MONTH;
            } else if (days <= 60) {
                retentionDays = RetentionDays.TWO_MONTHS;
            } else if (days <= 90) {
                retentionDays = RetentionDays.THREE_MONTHS;
            } else if (days <= 120) {
                retentionDays = RetentionDays.FOUR_MONTHS;
            } else if (days <= 150) {
                retentionDays = RetentionDays.FIVE_MONTHS;
            } else if (days <= 180) {
                retentionDays = RetentionDays.SIX_MONTHS;
            } else if (days <= 365) {
                retentionDays = RetentionDays.ONE_YEAR;
            } else {
                retentionDays = RetentionDays.TWO_YEARS;
            }
        }
        
        LogGroup logGroup = LogGroup.Builder.create(this, "SecurityProfileLogs")
                .retention(retentionDays)
                .removalPolicy(config.getLogRemovalPolicy())
                .logGroupName(logGroupName)
                .build();

            LOG.info("LoggingCwFactory: About to set logs in context");
            ctx.logs.set(logGroup);
            
            LOG.info("CloudWatch logs configured for " + ctx.security + " profile: " +
                    "retention=" + config.getLogRetentionDays() +
                    ", removal=" + config.getLogRemovalPolicy());
        } catch (Exception e) {
            LOG.severe("LoggingCwFactory: Exception in create() method: " + e.getMessage());
            LOG.severe("LoggingCwFactory: Exception type: " + e.getClass().getSimpleName());
            e.printStackTrace();
            throw e;
        }
    }

}
