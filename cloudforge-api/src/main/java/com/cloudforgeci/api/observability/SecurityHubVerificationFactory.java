package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * Verifies Security Hub is already enabled in this account/Region, instead of enabling it from
 * the application stack.
 *
 * <p>Security Hub (and its standards subscriptions) is an account/Region singleton, and the org
 * consuming CFC may already own its enablement centrally -- via AWS Control Tower, an
 * Organizations delegated administrator, Landing Zone Accelerator, or a hand-rolled baseline
 * stack. An application deploy role enabling and configuring an account-wide security service
 * is a real IAM-blast-radius concern for that setup, on top of the multi-stack collision risk
 * {@link SecurityHubFactory} already solves with idempotent adoption. This factory instead makes
 * the compliance claim honest without taking on either: it calls {@code securityhub:DescribeHub},
 * which throws (failing this stack's deploy) when the account/Region isn't subscribed, and
 * succeeds silently otherwise. Nothing here enables Security Hub or manages standards
 * subscriptions.
 *
 * <p><b>Scope</b>: this only confirms the hub itself is enabled, not that a specific standard
 * (e.g. PCI DSS v4.0.1) is subscribed -- {@code DescribeHub}'s response doesn't include standards,
 * and asserting a specific standard from {@code GetEnabledStandards}' response list isn't
 * expressible with this SDK-call-only custom resource (no conditional-fail-on-value primitive
 * without a Lambda-backed custom resource, which is a bigger lift than this check justifies on
 * its own). Revisit if per-standard verification becomes a real requirement.
 *
 * <p>{@link SecurityHubFactory} is left in place, unused, for the case a future deployment model
 * wants CFC to own provisioning again.
 */
public class SecurityHubVerificationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(SecurityHubVerificationFactory.class.getName());

    @DeploymentContext("securityHubEnabled")
    private Boolean securityHubEnabled;

    /** @param scope parent construct
     *  @param id construct ID */
    public SecurityHubVerificationFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether Security Hub is required, then fails this stack's deploy if the
     *  account/Region hub isn't already enabled centrally. */
    @Override
    public void create() {
        if (securityHubEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                securityHubEnabled = securityProfileConfig.isSecurityHubEnabled();
                LOG.info("Security Hub requirement inherited from security profile: " + securityHubEnabled);
            }
        }

        if (!Boolean.TRUE.equals(securityHubEnabled)) {
            LOG.info("Security Hub not required for this deployment -- skipping verification");
            return;
        }

        AwsSdkCall describeHub = AwsSdkCall.builder()
            .service("SecurityHub")
            .action("describeHub")
            .physicalResourceId(PhysicalResourceId.of("securityhub-verify-" + Stack.of(this).getStackName()))
            .build();

        AwsCustomResource.Builder.create(this, "SecurityHubVerification")
            .onCreate(describeHub)
            .onUpdate(describeHub)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        LOG.info("Security Hub verification resource created -- deploy fails if the hub isn't already enabled");
    }
}
