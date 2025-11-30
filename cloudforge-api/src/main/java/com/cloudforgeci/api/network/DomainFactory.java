package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.logging.Logger;


/**
 * Domain Factory using annotation-based context extraction.
 * Fields annotated with @DeploymentContext automatically extract values from the context.
 */
public class DomainFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(DomainFactory.class.getName());

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("createZone")
    private boolean createZone;

    @SystemContext("security")
    private SecurityProfile security;

    public DomainFactory(Construct scope, String id) {
        super(scope, id);
        // Values are automatically injected by BaseFactory via annotations
    }


    @Override
    public void create() {
        if (domain != null && !domain.isBlank()) {
            IHostedZone zone = createHostedZone(domain);
            ctx.zone.set(zone);
            ctx.domain.set(domain);
            ctx.subdomain.set(subdomain);
        }
    }

    private IHostedZone createHostedZone(String domainName) {
        if (createZone) {
            // Create a new hosted zone resource when createZone = true
            HostedZone zone = HostedZone.Builder.create(this, getNode().getId() + "Zone")
                    .zoneName(domainName)
                    .build();

            // Set removal policy based on security profile
            // PRODUCTION: RETAIN (keep DNS records for safety)
            // DEV/STAGING: DESTROY (clean up test resources)
            RemovalPolicy policy = (security == SecurityProfile.PRODUCTION)
                ? RemovalPolicy.RETAIN
                : RemovalPolicy.DESTROY;
            zone.applyRemovalPolicy(policy);

            LOG.info("Created hosted zone for " + domainName + " with removal policy: " + policy);
            return zone;
        } else {
            // Use existing hosted zone lookup when createZone = false (normal behavior)
            LOG.info("Looking up existing hosted zone for " + domainName);
            return HostedZone.fromLookup(this, getNode().getId() + "Zone",
                    HostedZoneProviderProps.builder()
                            .privateZone(false)
                            .domainName(domainName)
                            .build());
        }
    }

}
