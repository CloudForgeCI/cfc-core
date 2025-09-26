package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.SystemContext;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
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

    public FlowLogFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // SecurityProfileConfiguration is now injected directly via annotation
        LOG.info("FlowLogFactory: Configuring flow logs for security profile: " + ctx.security);

        // Check if flow logs are enabled for this security profile
        if (!config.isFlowLogsEnabled()) {
            LOG.info("Flow logs disabled for security profile: " + ctx.security + " (cost optimization)");
            return;
        }

        // Check if flow logs are already configured
        if (ctx.flowlogs.get().isPresent()) {
            LOG.info("Flow logs already configured, skipping");
            return;
        }

        // Create flow log log group with security profile-based settings
        LogGroup lg = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(config.getFlowLogRetentionDays())
                    .removalPolicy(config.getLogRemovalPolicy())
                    .logGroupName("/aws/vpc/flowlogs/" + ctx.security.name().toLowerCase())
                    .build();

        // Create flow log options with security profile-based traffic type
        FlowLogOptions logGroup = FlowLogOptions.builder()
                .trafficType(config.getFlowLogTrafficType())
                .destination(FlowLogDestination.toCloudWatchLogs(lg))
                .build();

        ctx.flowlogs.set(logGroup);
        
        LOG.info("Flow logs configured for " + ctx.security + " profile: " +
                "traffic=" + config.getFlowLogTrafficType() + 
                ", retention=" + config.getFlowLogRetentionDays() +
                ", removal=" + config.getLogRemovalPolicy());
    }

}
