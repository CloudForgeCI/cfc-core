package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * ALB Factory using annotation-based context injection.
 * This demonstrates the cleaner approach without passing SystemContext as parameters.
 */
public class AlbFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(AlbFactory.class.getName());

    public AlbFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        if (ctx == null) {
            throw new IllegalStateException("SystemContext is null in AlbFactory.create()");
        }
        
        try {
            // Create security group
            SecurityGroup albSg = createSecurityGroup();
            ctx.albSg.set(albSg);
            
            // Create ALB
            ApplicationLoadBalancer alb = createLoadBalancer(albSg);
            ctx.alb.set(alb);

            // Create target group only for EC2 runtime (Fargate uses listener-driven targets)
            if (ctx.runtime == com.cloudforgeci.api.interfaces.RuntimeType.EC2) {
                ApplicationTargetGroup targetGroup = createTargetGroup(alb);
                ctx.albTargetGroup.set(targetGroup);
                
                // Create HTTP listener with target group
                ApplicationListener http = createHttpListener(alb, targetGroup);
                ctx.http.set(http);
            } else {
                // For Fargate, create HTTP listener with appropriate default action
                ApplicationListener http = createHttpListenerWithoutTargetGroup(alb, ctx.cfc != null && Boolean.TRUE.equals(ctx.cfc.enableSsl()));
                ctx.http.set(http);
            }
            
        } catch (Exception e) {
            LOG.severe("Exception in AlbFactory.create(): " + e.getMessage());
            throw e;
        }
    }

    private SecurityGroup createSecurityGroup() {
        return SecurityGroup.Builder.create(this, "AlbSg")
                .vpc(ctx.vpc.get().orElseThrow())
                .allowAllOutbound(true)
                .build();
    }

    private ApplicationLoadBalancer createLoadBalancer(SecurityGroup albSg) {
        return ApplicationLoadBalancer.Builder.create(this, "JenkinsAlb")
                .vpc(ctx.vpc.get().orElseThrow())
                .securityGroup(albSg)
                .internetFacing(true)
                .build();
    }

    private ApplicationTargetGroup createTargetGroup(ApplicationLoadBalancer alb) {
        return ApplicationTargetGroup.Builder.create(this, "JenkinsTg")
                .vpc(ctx.vpc.get().orElseThrow())
                .port(8080)
                .protocol(ApplicationProtocol.HTTP)
                .targetType(TargetType.INSTANCE)
                .healthCheck(HealthCheck.builder()
                        .path("/")
                        .healthyHttpCodes("200-399")
                        .interval(software.amazon.awscdk.Duration.seconds(30))
                        .timeout(software.amazon.awscdk.Duration.seconds(10))
                        .healthyThresholdCount(2)
                        .unhealthyThresholdCount(10)
                        .build())
                .build();
    }

    private ApplicationListener createHttpListener(ApplicationLoadBalancer alb, ApplicationTargetGroup targetGroup) {
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.forward(List.of(targetGroup)))
                .build());
    }

    private ApplicationListener createFargateHttpListener(ApplicationLoadBalancer alb, SystemContext ctx) {
        // HTTP listener configuration is now handled by SecurityProfile wiring
        // SSL redirect logic is centralized in SecurityProfile.wire() method
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody("Jenkins is starting up...")
                        .build()))
                .build());
    }

    private ApplicationListener createHttpListenerWithoutTargetGroup(ApplicationLoadBalancer alb, boolean sslEnabled) {
        // HTTP listener configuration is now handled by SecurityProfile wiring
        // SSL redirect logic is centralized in SecurityProfile.wire() method
        return alb.addListener("Http", BaseApplicationListenerProps.builder()
                .port(80)
                .defaultAction(ListenerAction.fixedResponse(200, FixedResponseOptions.builder()
                        .contentType("text/plain")
                        .messageBody("Jenkins is starting up...")
                        .build()))
                .build());
    }
}