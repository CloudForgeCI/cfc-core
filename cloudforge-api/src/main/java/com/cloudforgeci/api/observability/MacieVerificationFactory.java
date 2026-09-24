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
 * Verifies Amazon Macie is already enabled in this account/Region, instead of enabling it from
 * the application stack.
 *
 * <p>Same reasoning as {@link SecurityHubVerificationFactory}: Macie is an account/Region
 * singleton an org may already own centrally, and an application deploy role enabling it
 * account-wide is a real IAM-blast-radius concern on top of the collision risk
 * {@link MacieFactory} already solves with idempotent adoption. This calls
 * {@code macie2:GetMacieSession}, which throws (failing this stack's deploy) when no session
 * exists for the account/Region, and succeeds otherwise -- nothing here enables Macie.
 *
 * <p>{@link MacieFactory} is left in place, unused, for the case a future deployment model wants
 * CFC to own provisioning again.
 */
public class MacieVerificationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(MacieVerificationFactory.class.getName());

    @DeploymentContext("macieEnabled")
    private Boolean macieEnabled;

    /** @param scope parent construct
     *  @param id construct ID */
    public MacieVerificationFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether Macie is required, then fails this stack's deploy if the account/Region
     *  session isn't already enabled centrally. */
    @Override
    public void create() {
        if (macieEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                macieEnabled = securityProfileConfig.isMacieEnabled();
                LOG.info("Macie requirement inherited from security profile: " + macieEnabled);
            }
        }

        if (!Boolean.TRUE.equals(macieEnabled)) {
            LOG.info("Macie not required for this deployment -- skipping verification");
            return;
        }

        AwsSdkCall getMacieSession = AwsSdkCall.builder()
            .service("Macie2")
            .action("getMacieSession")
            .physicalResourceId(PhysicalResourceId.of("macie-verify-" + Stack.of(this).getStackName()))
            .build();

        AwsCustomResource.Builder.create(this, "MacieVerification")
            .onCreate(getMacieSession)
            .onUpdate(getMacieSession)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        LOG.info("Macie verification resource created -- deploy fails if the session isn't already enabled");
    }
}
