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
        // SecurityProfileConfiguration is now injected directly via annotation
        LOG.info("LoggingCwFactory: Configuring CloudWatch logs for security profile: " + ctx.security);

        // Check if logs are already configured
        if (ctx.logs.get().isPresent()) {
            LOG.info("CloudWatch logs already configured, skipping");
            return;
        }

        // Create log group with security profile-based settings
        LogGroup logGroup = LogGroup.Builder.create(this, "SecurityProfileLogs")
                .retention(config.getLogRetentionDays())
                .removalPolicy(config.getLogRemovalPolicy())
                .logGroupName("/aws/jenkins/" + ctx.security.name().toLowerCase())
                .build();

        ctx.logs.set(logGroup);
        
        LOG.info("CloudWatch logs configured for " + ctx.security + " profile: " +
                "retention=" + config.getLogRetentionDays() +
                ", removal=" + config.getLogRemovalPolicy());
    }

}
