package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;

import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;

import software.amazon.awscdk.services.elasticloadbalancingv2.*;

import software.constructs.Construct;

public class AlbFactory extends Construct {

  private final Props p;

  public record Props(DeploymentContext cfc) {  }


  public AlbFactory(Construct scope, String id, Props props) {
    super(scope, id);
    this.p = props;
    SystemContext ctx = SystemContext.of(this);

    SecurityGroup albSg = SecurityGroup.Builder.create(this, "AlbSg")
            .vpc(ctx.vpc.get().orElseThrow()).allowAllOutbound(true).build();

    albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(80),  "Enable Http to Load Balancer",  false);
    ctx.albSg.set(albSg);

    ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, "Alb")
            .vpc(ctx.vpc.get().orElseThrow())
            .securityGroup(albSg)
            .internetFacing(true).build();
    ctx.alb.set(alb);

   ApplicationListener http = alb.addListener("Http",
            BaseApplicationListenerProps.builder()
                    .port(80)
                    .defaultAction(ListenerAction.redirect(
                            RedirectOptions.builder().protocol("HTTPS").port("443").build()))
                    .build());
    ctx.http.set(http);



  }



}
