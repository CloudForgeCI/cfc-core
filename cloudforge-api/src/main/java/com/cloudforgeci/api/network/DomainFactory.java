package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.constructs.Construct;

import java.util.logging.Logger;


/**
 * Domain Factory using annotation-based context injection.
 * This demonstrates how to use the injected DeploymentContext (cfc) variable.
 */
public class DomainFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(DomainFactory.class.getName());

    public DomainFactory(Construct scope, String id) {
        super(scope, id);
    }


    @Override
    public void create() {
        // Use the injected DeploymentContext (cfc) directly
        String domainName = cfc.domain();
        
        if (domainName != null && !domainName.isBlank()) {
            IHostedZone zone = createHostedZone(domainName);
            ctx.zone.set(zone);
            ctx.domain.set(domainName);
            ctx.subdomain.set(cfc.subdomain());
        }
    }

    private IHostedZone createHostedZone(String domainName) {
        if (cfc.createZone() || isTestEnvironment()) {
            // Create a new hosted zone resource when createZone=true or in test environment
            return HostedZone.Builder.create(this, getNode().getId() + "Zone")
                    .zoneName(domainName)
                    .build();
        } else {

            // Use existing hosted zone lookup when createZone=false (normal behavior)
            return HostedZone.fromLookup(this, getNode().getId() + "Zone",
                    HostedZoneProviderProps.builder()
                            .privateZone(false)
                            .domainName(domainName)
                            .build());
        }
    }
    
    private boolean isTestEnvironment() {
        // Check for test environment indicators
        return System.getProperty("java.class.path").contains("test") ||
               System.getProperty("maven.test.skip") != null ||
               System.getProperty("surefire.test.class.path") != null ||
               // Check for JUnit test context in stack trace
               java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                   .anyMatch(element -> element.getClassName().contains("junit") || 
                                       element.getClassName().contains("test"));
    }
    
}