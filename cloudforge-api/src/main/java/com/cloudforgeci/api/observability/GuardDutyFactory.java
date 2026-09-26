package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.guardduty.CfnDetector;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for AWS GuardDuty threat detection and compliance automation.
 *
 * <p>{@code SecurityProfileConfiguration#isGuardDutyEnabled()} resolves whether GuardDuty is
 * required -- the compliance matrix first (SOC2 CC7.2, PCI-DSS Req 11.4), then an explicit
 * override, then "always on for PRODUCTION" as the final default. This factory only inherits
 * that resolved value when {@code guardDutyEnabled} isn't set explicitly in deployment context.
 *
 * <p><b>Cost:</b> $30-100/month based on CloudTrail, VPC Flow Logs, and DNS log volume.
 */
public class GuardDutyFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(GuardDutyFactory.class.getName());

    @DeploymentContext("region")
    private String region;

    @DeploymentContext("stackName")
    private String stackName;

    @DeploymentContext("guardDutyEnabled")
    private Boolean guardDutyEnabled;

    @DeploymentContext("createGuardDutyDetector")
    private Boolean createGuardDutyDetector;

    public GuardDutyFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether GuardDuty is required, then creates the detector (an account/Region
     *  singleton) and registers its AWS Config rule when it is. */
    @Override
    public void create() {
        var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
        if (securityProfileConfig != null && guardDutyEnabled == null) {
            // isGuardDutyEnabled() already applies the compliance-matrix requirement (SOC2/
            // PCI-DSS) and then the profile default (always on for PRODUCTION) -- no need to
            // duplicate that logic here.
            guardDutyEnabled = securityProfileConfig.isGuardDutyEnabled();
            LOG.info("GuardDuty inherited from security profile: " + guardDutyEnabled);
        }

        if (Boolean.TRUE.equals(guardDutyEnabled) && createGuardDutyDetector == null) {
            createGuardDutyDetector = true;
        }

        if (Boolean.FALSE.equals(guardDutyEnabled)) {
            LOG.info("GuardDuty disabled (required for PCI-DSS Req 11.4, SOC2 CC7.2)");
            return;
        }

        if (region == null || region.isEmpty() || region.contains("$")) {
            LOG.warning("GuardDuty enabled but region unavailable - skipping setup");
            return;
        }

        enableGuardDuty();
        LOG.info("GuardDuty enabled: " + region + " (CloudTrail, VPC Flow, DNS monitoring)");
    }

    private void enableGuardDuty() {
        // Register AWS Config rule for GuardDuty compliance monitoring
        ctx.requireConfigRule(AwsConfigRule.GUARDDUTY_ENABLED);

        if (Boolean.TRUE.equals(createGuardDutyDetector)) {
            CfnDetector.Builder.create(this, "GuardDutyDetector")
                    .enable(true)
                    .findingPublishingFrequency("FIFTEEN_MINUTES")
                    .build();
            LOG.info("GuardDuty detector created (account-region singleton)");
        } else {
            LOG.info("GuardDuty detector creation skipped - set createGuardDutyDetector=true or use existing detector");
        }
    }
}
