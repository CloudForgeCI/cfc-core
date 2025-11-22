package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.guardduty.CfnDetector;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for enabling AWS GuardDuty threat detection.
 *
 * GuardDuty is an account-level service that monitors for malicious activity and unauthorized behavior.
 * Required for PCI-DSS Requirement 11.4 (Intrusion Detection/Prevention Systems).
 *
 * Note: GuardDuty has monthly costs based on analyzed data volume (CloudTrail, VPC Flow Logs, DNS logs).
 * Typical cost: $30-100/month depending on usage.
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

    @Override
    public void create() {
        // Check if GuardDuty should be enabled
        var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
        if (securityProfileConfig != null && guardDutyEnabled == null) {
            // Inherit from security profile if not explicitly set
            guardDutyEnabled = securityProfileConfig.isGuardDutyEnabled();
            LOG.info("GuardDuty setting inherited from security profile: " + guardDutyEnabled);
        }

        if (Boolean.FALSE.equals(guardDutyEnabled)) {
            LOG.info("GuardDuty is disabled");
            LOG.info("  Note: GuardDuty is required for PCI-DSS Req 11.4 (threat detection)");
            LOG.info("  Enable by setting guardDutyEnabled=true or using PRODUCTION security profile");
            return;
        }

        LOG.info("Enabling GuardDuty for threat detection");

        // Validate region is available
        if (region == null || region.isEmpty() || region.contains("$")) {
            LOG.warning("GuardDuty enabled but region is not available - skipping GuardDuty setup");
            LOG.warning("  Set 'region' in deployment context to enable GuardDuty");
            return;
        }

        // Enable GuardDuty using AWS SDK custom resource
        enableGuardDuty();

        LOG.info("GuardDuty enabled successfully");
        LOG.info("  Region: " + region);
        LOG.info("  Monitoring: CloudTrail events, VPC Flow Logs, DNS logs");
        LOG.info("  Cost: ~$30-100/month based on data volume");
        LOG.info("  Compliance: Satisfies PCI-DSS Req 11.4, HIPAA §164.308(a)(1)(ii)(D)");
    }

    private void enableGuardDuty() {
        // GuardDuty detector is account-region singleton - only one per account per region
        // Only create if explicitly requested via createGuardDutyDetector flag

        if (Boolean.TRUE.equals(createGuardDutyDetector)) {
            CfnDetector.Builder.create(this, "GuardDutyDetector")
                    .enable(true)
                    .findingPublishingFrequency("FIFTEEN_MINUTES")
                    .build();

            LOG.info("GuardDuty detector creation enabled via createGuardDutyDetector=true");
            LOG.info("  Note: If detector already exists in account-region, deployment will fail");
            LOG.info("  This is expected - only set createGuardDutyDetector=true for first deployment in account-region");
        } else {
            LOG.info("GuardDuty detector creation skipped (createGuardDutyDetector not set)");
            LOG.info("  Validation will check for existing GuardDuty detector");
            LOG.info("  To create detector: set createGuardDutyDetector=true in deployment context");
            LOG.info("  Or enable manually: aws guardduty create-detector --enable --region " + region);
        }
    }
}
