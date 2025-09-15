package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.FlowLogDestination;
import software.amazon.awscdk.services.ec2.FlowLogOptions;
import software.amazon.awscdk.services.ec2.FlowLogTrafficType;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

public class FlowLogFactory extends Construct {


    public FlowLogFactory(Construct scope, String id) {
        super(scope, id);
        SystemContext ctx = SystemContext.of(this);

        if(!ctx.cfc.enableFlowlogs()) return;

        LogGroup lg = LogGroup.Builder.create(this, "VpcFlowLogsGroup")
                    .retention(RetentionDays.ONE_MONTH)
                    .removalPolicy(RemovalPolicy.DESTROY) // flip to RETAIN for prod if you prefer
                    .build();

        FlowLogOptions logGroup = FlowLogOptions.builder()
                .trafficType(FlowLogTrafficType.ALL) // ACCEPT/REJECT/ALL
                .destination(FlowLogDestination.toCloudWatchLogs(lg))
                .build();

        ctx.flowlogs.set(logGroup);
    }

}
