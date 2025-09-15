package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.SystemContext;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

public class LoggingFactory extends Construct {


    public LoggingFactory(Construct scope, String id) {
        super(scope, id);
        SystemContext ctx = SystemContext.of(this);
        LogGroup logGroup = LogGroup.Builder.create(this, "Logs")
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        ctx.logs.set(logGroup);
    }

}
