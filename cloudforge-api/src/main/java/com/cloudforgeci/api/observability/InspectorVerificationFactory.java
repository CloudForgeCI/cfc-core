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
 * Verifies Amazon Inspector is reachable for this account, instead of enabling it from the
 * application stack.
 *
 * <p>Same reasoning as {@link SecurityHubVerificationFactory}/{@link MacieVerificationFactory}:
 * Inspector is an account-wide setting an org may already own centrally, and an application
 * deploy role enabling it is a real IAM-blast-radius concern on top of the collision risk
 * {@link InspectorFactory} already solves.
 *
 * <p><b>This check is weaker than the Security Hub/Macie ones -- read before relying on it.</b>
 * Unlike {@code securityhub:DescribeHub}/{@code macie2:GetMacieSession}, which throw when the
 * service isn't enabled, {@code inspector2:BatchGetAccountStatus} always succeeds and reports
 * {@code resourceState.ec2.status}/{@code resourceState.ecr.status} as {@code ENABLED},
 * {@code DISABLED}, or {@code SUSPENDED} in its response body -- there's no API that throws on
 * "not enabled" the way Security Hub/Macie's do. An {@link AwsSdkCall}-only custom resource (the
 * pattern every other factory in this package uses) can only fail on the API call itself
 * failing, not on a value inside a successful response, so this cannot yet gate a deploy on
 * Inspector actually being disabled. Closing that gap needs a Lambda-backed custom resource --
 * real added scope (function code, bundling, its own IAM role) beyond what this check justifies
 * on its own. This resource still verifies the account is one Inspector's API recognizes at all
 * (fails on account/permission errors), which is a real, if narrower, guarantee.
 *
 * <p>{@link InspectorFactory} is left in place, unused, for the case a future deployment model
 * wants CFC to own provisioning again.
 */
public class InspectorVerificationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(InspectorVerificationFactory.class.getName());

    @DeploymentContext("inspectorEnabled")
    private Boolean inspectorEnabled;

    /** @param scope parent construct
     *  @param id construct ID */
    public InspectorVerificationFactory(Construct scope, String id) {
        super(scope, id);
    }

    /** Resolves whether Inspector is required, then checks the account is reachable via
     *  Inspector's API -- see the class javadoc for why this can't yet gate on the account's
     *  actual enabled/disabled status. */
    @Override
    public void create() {
        if (inspectorEnabled == null) {
            var securityProfileConfig = ctx.securityProfileConfig.get().orElse(null);
            if (securityProfileConfig != null) {
                inspectorEnabled = securityProfileConfig.isInspectorEnabled();
                LOG.info("Inspector requirement inherited from security profile: " + inspectorEnabled);
            }
        }

        if (!Boolean.TRUE.equals(inspectorEnabled)) {
            LOG.info("Inspector not required for this deployment -- skipping verification");
            return;
        }

        String account = Stack.of(this).getAccount();

        AwsSdkCall batchGetAccountStatus = AwsSdkCall.builder()
            .service("Inspector2")
            .action("batchGetAccountStatus")
            .parameters(Map.of("accountIds", List.of(account)))
            .physicalResourceId(PhysicalResourceId.of("inspector-verify-" + Stack.of(this).getStackName()))
            .build();

        AwsCustomResource.Builder.create(this, "InspectorVerification")
            .onCreate(batchGetAccountStatus)
            .onUpdate(batchGetAccountStatus)
            .policy(AwsCustomResourcePolicy.fromSdkCalls(
                SdkCallsPolicyOptions.builder()
                    .resources(List.of("*"))
                    .build()
            ))
            .build();

        LOG.info("Inspector verification resource created -- see class javadoc for its narrower guarantee");
    }
}
