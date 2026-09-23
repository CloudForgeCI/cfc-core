package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.macie.CfnSession;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for Amazon Macie (sensitive-data discovery), mirroring {@link GuardDutyFactory}.
 *
 * <p>{@code AWS::Macie::Session} enables Macie for the account in this region -- one session
 * per account-region, same account-level-singleton shape as the GuardDuty detector. Automated
 * discovery jobs ({@code macieAutomatedDiscoveryEnabled}) are a separate concern from the
 * session itself and aren't modeled here yet -- there's no {@code CfnClassificationJob}
 * equivalent for "continuous discovery" wired up; this factory only turns Macie on.
 */
public class MacieFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(MacieFactory.class.getName());

    @DeploymentContext("macieEnabled")
    private Boolean macieEnabled;

    public MacieFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        if (macieEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                macieEnabled = securityProfileConfig.isMacieEnabled();
                LOG.info("Macie inherited from security profile: " + macieEnabled);
            }
        }

        if (!Boolean.TRUE.equals(macieEnabled)) {
            LOG.info("Macie disabled");
            return;
        }

        CfnSession.Builder.create(this, "MacieSession")
            .status("ENABLED")
            .findingPublishingFrequency("FIFTEEN_MINUTES")
            .build();

        LOG.info("Macie enabled (account-region session)");
    }
}
