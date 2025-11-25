package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
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
        LogGroup logGroup = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(config.getFlowLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy())
                    .logGroupName("/aws/vpc/flowlogs/" + security.name().toLowerCase())
                    .build();

        // Create flow log options with security profile-based traffic type
        FlowLogOptions flowLogOptions = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(logGroup))
                .build();

        ctx.flowlogs.set(flowLogOptions);

        LOG.info("Flow logs configured for " + security + " profile: " +
                "traffic = " + config.getFlowLogTrafficType() +
                ", retention = " + config.getFlowLogRetentionDays() +
                ", removal = " + config.getLogRemovalPolicy());
    }

}
