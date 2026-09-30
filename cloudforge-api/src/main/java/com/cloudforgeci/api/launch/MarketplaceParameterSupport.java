package com.cloudforgeci.api.launch;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.iam.ManagerOperatorIamSupport;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

/**
 * For an AWS Marketplace CloudFormation listing of CloudForge Manager (the only application this
 * applies to -- see {@link ManagerOperatorIamSupport#APPLICATION_ID}), replaces the two values a
 * buyer must supply at launch -- {@code cognitoInitialAdminEmail}, {@code managerLicenseKey} --
 * with real {@link CfnParameter}s the AWS Console's CloudFormation launch form renders, instead of
 * requiring them via CDK {@code --context}/cdk.json (meaningless to a buyer who never runs the CDK
 * CLI at all).
 *
 * <p>Deliberately value-only, not structural: every existing {@code @DeploymentContext(
 * "cognitoInitialAdminEmail")}/{@code "managerLicenseKey")} consumer already gates resource
 * creation on "is this non-null/non-empty" ({@code CognitoAuthenticationFactory},
 * {@code ContainerFactory}, {@code ApplicationFactory}) -- a CDK Token string (what {@code
 * CfnParameter#getValueAsString()} returns at synth time, before the buyer's real value exists)
 * is always non-null and non-empty, so those synth-time structural decisions are completely
 * unaffected; only the literal value each resource ends up holding changes, resolved by
 * CloudFormation at deploy time from what the buyer actually typed in.</p>
 *
 * <p>Can't override the stack's own node context: CDK's {@code Node.setContext} refuses once the
 * node already has children, and the two {@link CfnParameter}s this creates are themselves
 * children of the stack the moment they're built. Returns a small nested scope instead -- created
 * fresh, so it has no children yet when its context is set -- for the caller to build the
 * application's factories under in place of the stack directly. Each factory resolves its own
 * {@link DeploymentContext} independently via {@code DeploymentContext.from(this)} in {@code
 * BaseFactory}'s constructor (not a shared instance), so building them under this scope is enough
 * for the existing {@code @DeploymentContext} injection to pick up the overridden values for
 * free, with no change needed in any of those factories. This does shift every resource's logical
 * id by one construct-path segment relative to a non-Marketplace deploy of the same app -- fine
 * for a brand new feature with no prior deployment to preserve continuity with, but would be a
 * breaking change (CloudFormation replaces rather than updates) for anything already deployed, so
 * never make this unconditional.</p>
 *
 * <p>Never validates the key's content -- this library has no way to check a LicenseSeat key is
 * real, only that some value was supplied, identical to how {@code --context
 * managerLicenseKey=...} already works today. Actual license verification happens at runtime
 * inside cloudforge-manager's own compiled binary against LicenseSeat's servers, not here.</p>
 */
final class MarketplaceParameterSupport {

    private MarketplaceParameterSupport() {
    }

    /**
     * Returns {@code stack} unchanged unless {@code applicationSpec} is CloudForge Manager and
     * {@code marketplaceDeploymentEnabled} is set -- every other application, and every
     * non-Marketplace CloudForge Manager deployment, keeps sourcing these two values from CDK
     * context exactly as before, with the caller building the application directly under the
     * stack as usual.
     */
    static Construct applyIfApplicable(Stack stack, DeploymentContext cfc, ApplicationSpec applicationSpec) {
        if (!Boolean.TRUE.equals(cfc.marketplaceDeploymentEnabled())) {
            return stack;
        }
        if (!ManagerOperatorIamSupport.APPLICATION_ID.equals(applicationSpec.applicationId())) {
            return stack;
        }

        CfnParameter adminEmail = CfnParameter.Builder.create(stack, "AdminEmail")
            .type("String")
            .description("Email address for the initial CloudForge Manager admin user")
            .allowedPattern(".+@.+")
            .constraintDescription("Must be a valid email address")
            .build();

        CfnParameter licenseKey = CfnParameter.Builder.create(stack, "LicenseKey")
            .type("String")
            .description("LicenseSeat license key (LS-XXXX-XXXX-XXXX-XXXX) from your AWS Marketplace purchase")
            .noEcho(true)
            .build();

        Map<String, Object> overridden = new HashMap<>(cfc.raw());
        overridden.put("cognitoInitialAdminEmail", adminEmail.getValueAsString());
        overridden.put("managerLicenseKey", licenseKey.getValueAsString());

        Construct appScope = new Construct(stack, "App");
        appScope.getNode().setContext("cfc", overridden);
        return appScope;
    }
}
