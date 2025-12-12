package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.services.ec2.FlowLogDestination;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
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
        LogGroup logGroup = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(config.getLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy())
                    .build();

        // Create flow log options with security profile-based traffic type
        FlowLogOptions flowLogOptions = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(logGroup))
                .build();

        ctx.flowlogs.set(flowLogOptions);

        LOG.info("Flow logs configured for " + security + " profile: " +
                "traffic = " + config.getFlowLogTrafficType() +
                ", retention = " + config.getLogRetentionDays() +
                ", removal = " + config.getLogRemovalPolicy());
    }

}
