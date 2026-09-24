package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.amazon.awscdk.services.securityhub.CfnStandard;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for AWS Security Hub and its standards subscriptions.
 *
 * <p>Security Hub is an account/Region singleton, and {@code securityhub:EnableSecurityHub} is
 * not idempotent -- it throws {@code ResourceConflictException} when the account is already
 * subscribed. The declarative {@code AWS::SecurityHub::Hub} CloudFormation resource has the same
 * failure mode: a second CloudForgeCI stack in the same account/Region would fail its deploy
 * trying to create a second hub. This enables the hub via an {@link AwsCustomResource} calling
 * {@code securityhub:EnableSecurityHub} directly and ignores that specific conflict, so a second
 * stack's deploy adopts the existing hub instead of failing. No {@code onDelete}: another stack
 * may still depend on Security Hub, so deleting this stack must not disable it account-wide.
 *
 * <p>Standards subscriptions ({@code CfnStandard}) stay declarative CloudFormation resources --
 * each standard's ARN is stack-specific, so redundant subscriptions across stacks are a much
 * narrower, lower-frequency risk than the hub itself, and CloudFormation's native resource
 * lifecycle (proper update/delete, drift detection) is worth keeping here where it's available.
 *
 * <p><b>Standard versions are config, not hardcoded</b> -- AWS periodically ships new standard
 * versions (CIS has shipped 1.2.0, 1.4.0, 3.0.0, and 5.0.0; PCI DSS moved to 4.0.1 after the PCI
 * Council retired the prior version), so a version bump here is a {@code securityHub*Version}
 * config change, not a code change. Defaults are current as of this writing -- verify against
 * {@code securityhub:DescribeStandards} if in doubt.
 */
public class SecurityHubFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(SecurityHubFactory.class.getName());

    // CIS AWS Foundations Benchmark: only v1.2.0 uses the legacy global (no region) ruleset ARN.
    // v1.4.0/3.0.0/5.0.0 are region-scoped standards ARNs, like FSBP and PCI-DSS below.
    private static final String CIS_LEGACY_STANDARD_ARN =
        "arn:aws:securityhub:::ruleset/cis-aws-foundations-benchmark/v/1.2.0";
    private static final String CIS_STANDARD_ARN_TEMPLATE =
        "arn:aws:securityhub:%s::standards/cis-aws-foundations-benchmark/v/%s";
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

    public SecurityHubFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        if (securityHubEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                // isSecurityHubEnabled() already applies the compliance-matrix requirement
                // (SOC2/PCI-DSS) and then the profile default -- no need to duplicate that logic
                // here.
                securityHubEnabled = securityProfileConfig.isSecurityHubEnabled();
                LOG.info("Security Hub inherited from security profile: " + securityHubEnabled);
            }
        }

        if (!Boolean.TRUE.equals(securityHubEnabled)) {
            LOG.info("Security Hub disabled");
            return;
        }

        String account = Stack.of(this).getAccount();
        String region = Stack.of(this).getRegion();

        AwsSdkCall enableCall = AwsSdkCall.builder()
            .service("SecurityHub")
            .action("enableSecurityHub")
            .parameters(Map.of("EnableDefaultStandards", false))
            .physicalResourceId(PhysicalResourceId.of("securityhub-enable-" + account + "-" + region))
            .ignoreErrorCodesMatching("ResourceConflictException")
            .build();

        AwsCustomResource hub = AwsCustomResource.Builder.create(this, "SecurityHub")
            .onCreate(enableCall)
            .onUpdate(enableCall)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        if (Boolean.TRUE.equals(securityHubCisEnabled)) {
            String cisArn = "1.2.0".equals(securityHubCisVersion)
                ? CIS_LEGACY_STANDARD_ARN
                : String.format(CIS_STANDARD_ARN_TEMPLATE, region, securityHubCisVersion);
            addStandard("CisStandard", cisArn, hub);
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

    private void addStandard(String id, String standardsArn, AwsCustomResource hub) {
        CfnStandard standard = CfnStandard.Builder.create(this, id)
            .standardsArn(standardsArn)
            .build();
        standard.getNode().addDependency(hub);
    }
}
