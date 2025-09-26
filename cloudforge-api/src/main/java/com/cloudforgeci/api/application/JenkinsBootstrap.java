package com.cloudforgeci.api.application;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.ec2.Port;
import software.constructs.Construct;

public class JenkinsBootstrap extends BaseFactory {

    @com.cloudforgeci.api.core.annotation.SystemContext
    private SystemContext ctx;

    @com.cloudforgeci.api.core.annotation.DeploymentContext
    private DeploymentContext cfc;

    public record Props(DeploymentContext cfc) { }


    public JenkinsBootstrap(Construct scope, String id, Props props) {
        super(scope, id);
        // Props not currently used but kept for future extensibility
    }

    @Override
    public void create() {
        // Volume creation is now handled by FargateFactory
        // Configure security group rules for EFS and ALB access
        ctx.efsSg.get().orElseThrow().addIngressRule(ctx.fargateServiceSg.get().orElseThrow(), Port.tcp(2049), "NFS_from_Fargate_service", false);
        ctx.fargateServiceSg.get().orElseThrow().addIngressRule(ctx.albSg.get().orElseThrow(), Port.tcp(8080), "HTTP_from_ALB", false);

        CfnOutput.Builder.create(this, "JenkinsUrl")
                .description("Jenkins URL (ALB DNS) - Test")
                .value("http://" + ctx.alb.get().orElseThrow().getLoadBalancerDnsName())
                .build();
    }

}
