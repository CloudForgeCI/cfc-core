package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;

import java.util.List;

public class CertificateFactory extends Construct {

    private final Props p;

    public record Props(
            DeploymentContext cfc
    ) {}

    public CertificateFactory(Construct scope, String id, Props props) {
        super(scope, id);
        this.p = props;
        SystemContext ctx = SystemContext.of(this);




        if(p.cfc.enableSsl()) {
            /*Certificate cert = Certificate.Builder
                    .create(this, id + "Cert")
                    .domainName(p.cfc.fqdn())
                    .validation(CertificateValidation.fromDns(ctx.zone.get().orElseThrow()))
                    .build();*/

            /*ApplicationListener https = ctx.alb.get().orElseThrow().addListener(id + "Https",
                    BaseApplicationListenerProps.builder()
                            .port(443)
                            .protocol(ApplicationProtocol.HTTPS)
                            .certificates(List.of(ListenerCertificate.fromCertificateManager(cert)))
                            .build());

            https.addTargetGroups(id + "Forward",
                    AddApplicationTargetGroupsProps.builder()
                            .targetGroups(List.of(ctx.albTargetGroup.get().orElseThrow()))
                            .build());


            ctx.http.get().orElseThrow().addAction(id + "Redirect",
                    AddApplicationActionProps.builder()
                            .action(ListenerAction.redirect(
                                    RedirectOptions.builder().protocol("HTTPS").port("443").build()))
                            .build());*/
        }
        /*ARecord.Builder alias = ARecord.Builder.create(this, id + "Alias")
                .zone(p.zone.zone())
                .recordName(fqdn.replace("." + zoneName, ""))
                .target(RecordTarget.fromAlias(new LoadBalancerTarget(p.alb.alb())));
        if (!sub.isEmpty()) {
            alias.recordName(sub);
        }
        alias.build();*/

        // Optional: also create AAAA alias for IPv6 if your zone supports it.
        // new AaaaRecord(...).target(RecordTarget.fromAlias(new LoadBalancerTarget(...)));
    }


}