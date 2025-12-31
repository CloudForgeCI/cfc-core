package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.services.ec2.FlowLogDestination;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * VPC Flow Log Factory using annotation-based context injection.
 * Configures VPC flow logs based on security profile settings.
 */
public class FlowLogFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(FlowLogFactory.class.getName());

    @SystemContext("security")
    private SecurityProfile security;

    public FlowLogFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("Configuring flow logs for security profile: " + security);

        // Check if flow logs are enabled for this security profile
        if (!config.isFlowLogsEnabled()) {
            LOG.info("Flow logs disabled for security profile: " + security);
            return;
        }

        // Check if flow logs are already configured
        if (ctx.flowlogs.get().isPresent()) {
            LOG.info("Flow logs already configured, skipping");
            return;
        }

        // Create flow log log group with security profile-based settings
        // Note: logGroupName is intentionally omitted to allow CloudFormation to auto-generate unique names
        // This prevents naming conflicts when deploying multiple stacks with the same security profile
        // Use getLogRetentionDays() which is compliance-aware (respects logRetentionDays override)
        LogGroup.Builder logGroupBuilder = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(config.getLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy());

        // Add KMS encryption when enabled (required for PCI-DSS, HIPAA, SOC2 compliance)
        if (config.isCloudWatchLogsKmsEncryptionEnabled()) {
            Key flowLogsKmsKey = Key.Builder.create(this, "FlowLogsKmsKey")
                    .description("KMS key for VPC Flow Logs encryption")
                    .enableKeyRotation(true)
                    .removalPolicy(config.getLogRemovalPolicy())
                    .build();
            logGroupBuilder.encryptionKey(flowLogsKmsKey);
            LOG.info("VPC Flow Logs KMS encryption enabled");

            // Register AWS Config rule for CloudWatch Logs KMS encryption compliance
            ctx.requireConfigRule(AwsConfigRule.CLOUDWATCH_LOG_GROUP_ENCRYPTED);
        }

        LogGroup logGroup = logGroupBuilder.build();

        // Create flow log options with security profile-based traffic type
        FlowLogOptions flowLogOptions = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(logGroup))
                .build();

        ctx.flowlogs.set(flowLogOptions);

        // Register AWS Config rule for VPC Flow Logs compliance
        ctx.requireConfigRule(AwsConfigRule.VPC_FLOW_LOGS_ENABLED);

        LOG.info("Flow logs configured for " + security + " profile: " +
                "traffic = " + config.getFlowLogTrafficType() +
                ", retention = " + config.getLogRetentionDays() +
                ", removal = " + config.getLogRemovalPolicy());
    }

}
