package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import software.amazon.awscdk.services.logs.LogGroup;
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
            LogGroup logGroup = LogGroup.Builder.create(this, "SecurityProfileLogs")
                    .retention(config.getLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy())
                    .logGroupName("/aws/jenkins/" + securityProfileName)
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
