package com.cloudforgeci.api.network;


import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

public class DomainFactory extends Construct {
    private final Props p;

    public record Props(DeploymentContext cfc) {}


    public DomainFactory(Construct scope, String id, Props props) {
        super(scope, id);
        this.p = props;
        SystemContext ctx = SystemContext.of(this);
        if(ctx.cfc.domain() == null || ctx.cfc.domain().isEmpty()) return;

        IHostedZone zone = HostedZone.fromLookup(this, id + "Zone",
                HostedZoneProviderProps.builder()
                        .privateZone(false)
                        .domainName(ctx.cfc.domain()).build());
        ctx.zone.set(zone);
    }

}
