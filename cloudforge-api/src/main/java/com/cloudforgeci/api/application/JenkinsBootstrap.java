package com.cloudforgeci.api.application;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ecs.AuthorizationConfig;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.Volume;
import software.constructs.Construct;

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_HOME;

public class JenkinsBootstrap extends Construct {

    private final Props p;

    public record Props(DeploymentContext cfc) { }


    public JenkinsBootstrap(Construct scope, String id, Props props) {
        super(scope, id);
        this.p = props;
        SystemContext ctx = SystemContext.of(this);

        ctx.fargateTaskDef.get().orElseThrow().addVolume(Volume.builder()
                .name(JENKINS_HOME)
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(ctx.efs.get().orElseThrow().getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(ctx.ap.get().orElseThrow().getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build());

        ctx.efsSg.get().orElseThrow().addIngressRule(ctx.fargateServiceSg.get().orElseThrow(), Port.tcp(2049), null, false);
        ctx.fargateServiceSg.get().orElseThrow().addIngressRule(ctx.albSg.get().orElseThrow(), Port.tcp(8080), null, false);

        CfnOutput.Builder.create(this, "JenkinsUrl")
                .description("Jenkins URL (ALB DNS) - Test")
                .value("http://" + ctx.alb.get().orElseThrow().getLoadBalancerDnsName())
                .build();


    }

}
