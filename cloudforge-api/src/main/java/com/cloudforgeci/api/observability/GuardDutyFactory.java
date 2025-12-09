package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.services.guardduty.CfnDetector;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for AWS GuardDuty threat detection and compliance automation.
 *
 * <p>GuardDuty automatically enables for compliance frameworks requiring threat detection:
 * <ul>
 *   <li>SOC2 (Common Criteria CC7.2 - Threat detection and response)</li>
 *   <li>PCI-DSS Requirement 11.4 (Intrusion Detection/Prevention Systems)</li>
 * </ul>
 *
 * <p><b>Auto-Enablement:</b> When {@code complianceFrameworks} contains "SOC2" or "PCI-DSS",
 * GuardDuty detector is automatically created without manual configuration.
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

    @DeploymentContext("complianceFrameworks")
    private String complianceFrameworks;

    public GuardDutyFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        boolean autoEnable = shouldAutoEnableForCompliance();

        var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
        if (securityProfileConfig != null && guardDutyEnabled == null) {
            guardDutyEnabled = securityProfileConfig.isGuardDutyEnabled();
            LOG.info("GuardDuty inherited from security profile: " + guardDutyEnabled);
            // If security profile enables GuardDuty and createGuardDutyDetector not explicitly set, enable it
            if (Boolean.TRUE.equals(guardDutyEnabled) && createGuardDutyDetector == null) {
                createGuardDutyDetector = true;
            }
        }

        if (autoEnable && guardDutyEnabled == null) {
            guardDutyEnabled = true;
            createGuardDutyDetector = true;
            LOG.info("GuardDuty auto-enabled for " + complianceFrameworks + " compliance");
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

    /**
     * Determines if GuardDuty should be auto-enabled based on compliance frameworks.
     * GuardDuty is required for:
     * - SOC2 (Common Criteria CC7.2 - Threat detection and response)
     * - PCI-DSS Requirement 11.4 (Intrusion Detection/Prevention Systems)
     *
     * @return true if compliance frameworks require GuardDuty
     */
    private boolean shouldAutoEnableForCompliance() {
        if (complianceFrameworks == null || complianceFrameworks.isEmpty()) {
            return false;
        }

        String frameworks = complianceFrameworks.toUpperCase();
        return frameworks.contains("SOC2") || frameworks.contains("PCI-DSS") || frameworks.contains("PCIDSS");
    }

    private void enableGuardDuty() {
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
