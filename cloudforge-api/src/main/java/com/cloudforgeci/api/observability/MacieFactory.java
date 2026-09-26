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
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for Amazon Macie (sensitive-data discovery), account/region-scoped like GuardDuty.
 *
 * <p>An AWS account allows only one Macie session per Region, and {@code macie2:EnableMacie} is
 * not idempotent -- it throws {@code ConflictException} when a session already exists. Multiple
 * CloudForgeCI stacks in the same account/Region (e.g. two PRODUCTION applications) would
 * otherwise race to create {@code AWS::Macie::Session} and the second deploy would fail outright.
 * This uses an {@link AwsCustomResource} calling {@code macie2:EnableMacie} directly and ignores
 * that specific conflict, so a second stack's deploy adopts the existing session instead of
 * failing. No {@code onDelete}: Macie is an account/Region singleton another stack may still
 * depend on, so deleting this stack must not disable it for everyone else.
 *
 * <p>Automated discovery jobs ({@code macieAutomatedDiscoveryEnabled}) are a separate concern from
 * the session itself and aren't modeled here yet -- there's no {@code CfnClassificationJob}
 * equivalent for "continuous discovery" wired up; this factory only turns Macie on.
 */
public class MacieFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(MacieFactory.class.getName());

    @DeploymentContext("macieEnabled")
    private Boolean macieEnabled;

    /** @param scope parent construct
     *  @param id construct ID */
    public MacieFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether Macie is required, then enables the account/Region session, adopting an
     *  existing one if another stack already created it. */
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

        String account = Stack.of(this).getAccount();
        String region = Stack.of(this).getRegion();

        AwsSdkCall enableCall = AwsSdkCall.builder()
            .service("Macie2")
            .action("enableMacie")
            .parameters(Map.of(
                "findingPublishingFrequency", "FIFTEEN_MINUTES",
                "status", "ENABLED"
            ))
            .physicalResourceId(PhysicalResourceId.of("macie-enable-" + account + "-" + region))
            .ignoreErrorCodesMatching("ConflictException")
            .build();

        AwsCustomResource.Builder.create(this, "MacieSession")
            .onCreate(enableCall)
            .onUpdate(enableCall)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        LOG.info("Macie enabled (account-region session, adopts an existing session if present)");
    }
}
