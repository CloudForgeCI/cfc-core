package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.util.RetentionDaysConverter;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CloudWatch Logging Factory using annotation-based context injection.
 * Configures CloudWatch log groups based on security profile settings.
 */
public class LoggingCwFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(LoggingCwFactory.class.getName());

    @SystemContext("security")
    private SecurityProfile security;

    @SystemContext("runtime")
    private RuntimeType runtime;

    @SystemContext("logs")
    private LogGroup logs;

    @SystemContext("stackName")
    private String stackName;

    @DeploymentContext("enableMonitoring")
    private Boolean enableMonitoring;

    @DeploymentContext("logRetentionDays")
    private Integer logRetentionDays;

    public LoggingCwFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        try {
            // SecurityProfileConfiguration is now injected directly via annotation
            LOG.info("LoggingCwFactory: Starting create() method");
            LOG.info("LoggingCwFactory: ctx is null: " + (ctx == null));

            // Guard against null context - critical for operations
            if (ctx == null) {
                LOG.severe("LoggingCwFactory: SystemContext (ctx) is null!");
                throw new IllegalStateException("SystemContext is null - cannot configure logging");
            }

            if (security != null) {
                LOG.info("LoggingCwFactory: security = " + security);
            }
            LOG.info("LoggingCwFactory: config is null: " + (config == null));

            if (config == null) {
                LOG.severe("LoggingCwFactory: SecurityProfileConfiguration is null!");
                throw new IllegalStateException("SecurityProfileConfiguration is null");
            }

            LOG.info("LoggingCwFactory: About to check if logs are already configured");
            // Check if logs are already configured
            if (logs != null) {
                LOG.info("CloudWatch logs already configured, skipping");
                return;
            }

            LOG.info("LoggingCwFactory: About to create LogGroup");
            // Create log group with security profile-based settings
            String securityProfileName = (security != null) ? security.name().toLowerCase() : "unknown";
            String runtimeName = (runtime != null) ? runtime.name().toLowerCase() : "unknown";
            String logGroupName = "/aws/jenkins/" + stackName + "/" + runtimeName + "/" + securityProfileName;
            LOG.info("LoggingCwFactory: Creating log group with name: " + logGroupName);

            // Use configurable log retention from DeploymentContext if monitoring is enabled
            RetentionDays retentionDays = config.getLogRetentionDays();
            if (Boolean.TRUE.equals(enableMonitoring) && logRetentionDays != null) {
                // Use RetentionDaysConverter for consistent retention mapping across all factories
                // This ensures compliance-aware thresholds (PCI-DSS, HIPAA, etc.) are properly handled
                retentionDays = RetentionDaysConverter.fromDays(logRetentionDays);
            }

            // Create log group with explicit name and removal policy
            // If RemovalPolicy is RETAIN, the log group will persist after stack deletion
            // To avoid conflicts on redeployment, we only set the logGroupName if removal policy is DESTROY
            software.amazon.awscdk.services.logs.LogGroup.Builder logGroupBuilder = LogGroup.Builder.create(this, "SecurityProfileLogs")
                    .retention(retentionDays)
                    .removalPolicy(config.getLogRemovalPolicy());

            // Only set explicit name if using DESTROY policy, otherwise let CDK generate unique name
            if (config.getLogRemovalPolicy() == software.amazon.awscdk.RemovalPolicy.DESTROY) {
                logGroupBuilder.logGroupName(logGroupName);
            }

            LogGroup logGroup = logGroupBuilder.build();

            LOG.info("LoggingCwFactory: Configured log group: " + logGroupName +
                     " with retention=" + retentionDays +
                     ", removal=" + config.getLogRemovalPolicy());

            LOG.info("LoggingCwFactory: About to set logs in context");
            ctx.logs.set(logGroup);

            LOG.info("CloudWatch logs configured for " + security + " profile: " +
                    "retention=" + config.getLogRetentionDays() +
                    ", removal=" + config.getLogRemovalPolicy());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "LoggingCwFactory: Exception in create() method", e);
            throw e;
        }
    }

}
