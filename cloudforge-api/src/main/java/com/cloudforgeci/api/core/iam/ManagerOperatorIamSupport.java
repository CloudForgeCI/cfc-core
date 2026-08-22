package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.manager.ManagerAwsCapabilityCatalog;
import com.cloudforgeci.api.core.SystemContext;
import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attaches {@link ManagerAwsCapabilityCatalog} baseline operator IAM to CloudForge Manager
 * task/instance roles at CDK synthesis time.
 */
public final class ManagerOperatorIamSupport {

    public static final String APPLICATION_ID = "cloudforge-manager";
    public static final String OPERATOR_POLICY_SID = "CloudForgeManagerOperatorBaseline";

    private ManagerOperatorIamSupport() {
    }

    public static boolean isCloudForgeManager(SystemContext ctx) {
        if (ctx.applicationSpec.get()
                .map(spec -> APPLICATION_ID.equals(spec.applicationId()))
                .orElse(false)) {
            return true;
        }
        return APPLICATION_ID.equals(ctx.cfc.applicationId());
    }

    public static Optional<PolicyStatement> operatorBaselineStatement(SystemContext ctx) {
        if (!isCloudForgeManager(ctx)) {
            return Optional.empty();
        }
        List<String> actions = new ArrayList<>(ManagerAwsCapabilityCatalog.operatorBaselineIamActions());
        return Optional.of(PolicyStatement.Builder.create()
            .sid(OPERATOR_POLICY_SID)
            .actions(actions)
            .resources(List.of("*"))
            .build());
    }

    public static void addOperatorBaselineToStatements(SystemContext ctx, List<PolicyStatement> statements) {
        operatorBaselineStatement(ctx).ifPresent(statements::add);
    }

    public static void attachOperatorBaselinePolicies(SystemContext ctx, Role role) {
        operatorBaselineStatement(ctx).ifPresent(statement -> {
            role.addToPolicy(statement);
            NagSuppressions.addResourceSuppressions(
                role,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("CloudForge Manager operator APIs (CFN inventory/delete, ECS lifecycle, "
                            + "RDS snapshot/restore) require account-scoped resources per "
                            + ManagerAwsCapabilityCatalog.CATALOG_VERSION
                            + ". Application-layer RBAC governs panel access.")
                        .build()
                ),
                Boolean.TRUE);
        });
    }

    /** Same tag key {@code ApplicationFargateStack}/{@code ApplicationEc2Stack} apply via
     * {@code Tags.of(this).add(...)}, and {@code StackListingPolicy.TAG_MANAGED} reads on the
     * Manager-inventory side — one convention, three independent read/write sites (CDK doesn't
     * offer a shared constants module below cloudforge-core that all of these could import). */
    private static final String TAG_MANAGED = "cloudforge:managed";

    /**
     * Direct-deploy IAM ({@code CFN_DEPLOY} capability, backing the {@code deploy:create}
     * {@code ManagerPolicyCatalog} policy) — scoped to CloudForge-tagged resources instead of a
     * flat action allow-list, since {@code CreateStack}/{@code UpdateStack}/{@code PassRole} can
     * create or modify arbitrary infrastructure, unlike every other capability
     * {@link #attachOperatorBaselinePolicies} attaches.
     *
     * <p>Three separate statements, not one, because the relevant condition key differs by
     * lifecycle stage: {@code aws:RequestTag} only evaluates the tags being set on a resource
     * that doesn't exist yet (the create path), {@code aws:ResourceTag} only evaluates an
     * existing resource's current tags (the update path) — a single statement can't express
     * "either," but IAM statements are OR'd at the policy level, so attaching both lets whichever
     * path is actually being taken succeed on its own terms. {@code iam:PassRole} gets its own
     * statement scoped by {@code iam:ResourceTag} on the role being passed, relying on CDK's
     * stack-level tag propagation to also tag IAM roles the stack creates.</p>
     *
     * <p><b>Caveat, stated plainly:</b> not validated against a real AWS account — this
     * environment has no AWS credentials to confirm {@code CreateChangeSet}/{@code
     * ExecuteChangeSet} (which can create <em>or</em> update a stack depending on whether the
     * target already exists) behave exactly as documented under these conditions in practice.
     * Verify with a real deploy before treating this as the sole guardrail.</p>
     */
    public static List<PolicyStatement> deployStatements(SystemContext ctx) {
        if (!isCloudForgeManager(ctx)) {
            return List.of();
        }
        PolicyStatement create = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCreate")
            .actions(List.of(
                "cloudformation:CreateStack",
                "cloudformation:CreateChangeSet",
                "cloudformation:ExecuteChangeSet",
                "cloudformation:DescribeChangeSet",
                "cloudformation:DeleteChangeSet"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("aws:RequestTag/" + TAG_MANAGED, "true")))
            .build();
        PolicyStatement update = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployUpdate")
            .actions(List.of(
                "cloudformation:UpdateStack",
                "cloudformation:CreateChangeSet",
                "cloudformation:ExecuteChangeSet",
                "cloudformation:DescribeChangeSet",
                "cloudformation:DeleteChangeSet"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("aws:ResourceTag/" + TAG_MANAGED, "true")))
            .build();
        PolicyStatement passRole = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployPassRole")
            .actions(List.of("iam:PassRole"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("iam:ResourceTag/" + TAG_MANAGED, "true")))
            .build();
        return List.of(create, update, passRole);
    }

    /**
     * {@code SC_PROVISION} capability backing {@code deploy:catalog} — deliberately no
     * conditions: Service Catalog's own portfolio/product/launch-constraint model is the
     * guardrail here (a caller can only provision products actually shared with them), not a
     * tag condition on Manager's role. No CFN/IAM permissions appear in this statement at all.
     */
    public static Optional<PolicyStatement> catalogProvisionStatement(SystemContext ctx) {
        if (!isCloudForgeManager(ctx)) {
            return Optional.empty();
        }
        List<String> actions = new ArrayList<>(ManagerAwsCapabilityCatalog.iamActions(
            List.of(ManagerAwsCapabilityCatalog.Capability.SC_PROVISION)));
        return Optional.of(PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCatalog")
            .actions(actions)
            .resources(List.of("*"))
            .build());
    }

    /**
     * Attaches {@link #deployStatements} and {@link #catalogProvisionStatement} to the role.
     * Unlike {@link #attachOperatorBaselinePolicies} (always attached as the operator baseline),
     * this is gated behind {@link com.cloudforge.core.config.DeploymentConfig#managerDirectDeployEnabled}
     * — it is a materially higher-privilege tier and must be explicitly requested per deployment,
     * not inherited automatically from IAM profile or {@link #isCloudForgeManager}. Every Standard/
     * Extended/Minimal IAM configuration calls this unconditionally after
     * {@link #attachOperatorBaselinePolicies} on the Manager's task/instance role; this method,
     * not the call sites, owns the opt-in check so there is exactly one place to get it right.
     */
    public static void attachDeployCapabilities(SystemContext ctx, Role role) {
        if (!Boolean.TRUE.equals(ctx.cfc.managerDirectDeployEnabled())) {
            return;
        }
        List<PolicyStatement> statements = deployStatements(ctx);
        if (statements.isEmpty()) {
            return;
        }
        statements.forEach(role::addToPolicy);
        catalogProvisionStatement(ctx).ifPresent(role::addToPolicy);
        NagSuppressions.addResourceSuppressions(
            role,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-IAM5")
                    .reason("CloudForge Manager direct-deploy actions (CreateStack/UpdateStack/PassRole) "
                        + "are scoped by aws:RequestTag/aws:ResourceTag/iam:ResourceTag conditions requiring "
                        + "the CloudForge-managed tag convention, not by resource ARN — CDK synthesizes "
                        + "resource names dynamically, so a wildcard resource with a tag condition is the "
                        + "narrowest expressible scope. Service Catalog provisioning carries no CFN/IAM "
                        + "permissions on this role at all.")
                    .build()
            ),
            Boolean.TRUE);
    }
}
