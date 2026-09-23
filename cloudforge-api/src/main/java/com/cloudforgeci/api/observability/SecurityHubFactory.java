package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.securityhub.CfnHub;
import software.amazon.awscdk.services.securityhub.CfnStandard;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for AWS Security Hub and its standards subscriptions.
 *
 * <p>Mirrors {@link GuardDutyFactory}'s shape: a single account-region {@code CfnHub}, then one
 * {@code CfnStandard} per enabled standard. Each standard is its own CloudFormation resource and
 * depends on the hub.
 *
 * <p><b>Standard versions are config, not hardcoded</b> -- AWS periodically ships new standard
 * versions (CIS has shipped 1.2.0, 1.4.0, 3.0.0, and 5.0.0; PCI DSS moved to 4.0.1 after the PCI
 * Council retired the prior version), so a version bump here is a {@code securityHub*Version}
 * config change, not a code change. Defaults are current as of this writing -- verify against
 * {@code securityhub:DescribeStandards} if in doubt.
 */
public class SecurityHubFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(SecurityHubFactory.class.getName());

    // CIS AWS Foundations Benchmark ARNs are global (no region segment); FSBP and PCI-DSS are
    // region-scoped.
    private static final String CIS_STANDARD_ARN_TEMPLATE =
        "arn:aws:securityhub:::standards/cis-aws-foundations-benchmark/v/%s";
    private static final String FSBP_STANDARD_ARN_TEMPLATE =
        "arn:aws:securityhub:%s::standards/aws-foundational-security-best-practices/v/%s";
    private static final String PCI_DSS_STANDARD_ARN_TEMPLATE =
        "arn:aws:securityhub:%s::standards/pci-dss/v/%s";

    @DeploymentContext("securityHubEnabled")
    private Boolean securityHubEnabled;

    @DeploymentContext("securityHubCisEnabled")
    private Boolean securityHubCisEnabled;

    @DeploymentContext("securityHubCisVersion")
    private String securityHubCisVersion;

    @DeploymentContext("securityHubAwsFoundationalEnabled")
    private Boolean securityHubAwsFoundationalEnabled;

    @DeploymentContext("securityHubFsbpVersion")
    private String securityHubFsbpVersion;

    @DeploymentContext("securityHubPciDssEnabled")
    private Boolean securityHubPciDssEnabled;

    @DeploymentContext("securityHubPciDssVersion")
    private String securityHubPciDssVersion;

    @DeploymentContext("complianceFrameworks")
    private String complianceFrameworks;

    public SecurityHubFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        boolean autoEnable = shouldAutoEnableForCompliance();

        var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
        if (securityProfileConfig != null && securityHubEnabled == null) {
            securityHubEnabled = securityProfileConfig.isSecurityHubEnabled();
            LOG.info("Security Hub inherited from security profile: " + securityHubEnabled);
        }

        if (autoEnable && securityHubEnabled == null) {
            securityHubEnabled = true;
            LOG.info("Security Hub auto-enabled for " + complianceFrameworks + " compliance");
        }

        if (!Boolean.TRUE.equals(securityHubEnabled)) {
            LOG.info("Security Hub disabled");
            return;
        }

        CfnHub hub = CfnHub.Builder.create(this, "SecurityHub").build();

        String region = Stack.of(this).getRegion();

        if (Boolean.TRUE.equals(securityHubCisEnabled)) {
            addStandard("CisStandard", String.format(CIS_STANDARD_ARN_TEMPLATE, securityHubCisVersion), hub);
        }
        if (Boolean.TRUE.equals(securityHubAwsFoundationalEnabled)) {
            addStandard("FsbpStandard",
                String.format(FSBP_STANDARD_ARN_TEMPLATE, region, securityHubFsbpVersion), hub);
        }
        if (Boolean.TRUE.equals(securityHubPciDssEnabled)) {
            addStandard("PciDssStandard",
                String.format(PCI_DSS_STANDARD_ARN_TEMPLATE, region, securityHubPciDssVersion), hub);
        }

        LOG.info("Security Hub enabled (hub + standards subscriptions)");
    }

    private void addStandard(String id, String standardsArn, CfnHub hub) {
        CfnStandard standard = CfnStandard.Builder.create(this, id)
            .standardsArn(standardsArn)
            .build();
        standard.addDependency(hub);
    }

    /**
     * Security Hub is required for SOC2 (CC7.2) and PCI-DSS (Req 11.4), same frameworks
     * GuardDutyFactory auto-enables for.
     */
    private boolean shouldAutoEnableForCompliance() {
        if (complianceFrameworks == null || complianceFrameworks.isEmpty()) {
            return false;
        }
        String frameworks = complianceFrameworks.toUpperCase();
        return frameworks.contains("SOC2") || frameworks.contains("PCI-DSS") || frameworks.contains("PCIDSS");
    }
}
