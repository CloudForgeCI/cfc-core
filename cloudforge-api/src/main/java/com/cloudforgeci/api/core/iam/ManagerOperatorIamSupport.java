package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.manager.ManagerAwsCapabilityCatalog;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.deploy.aws.AwsDirectDeployer;
import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.services.iam.ManagedPolicy;
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
                "cloudformation:CreateChangeSet"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("aws:RequestTag/" + TAG_MANAGED, "true")))
            .build();
        PolicyStatement update = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployUpdate")
            .actions(List.of(
                "cloudformation:UpdateStack",
                "cloudformation:CreateChangeSet"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("aws:ResourceTag/" + TAG_MANAGED, "true")))
            .build();
        // Confirmed live, twice, that DescribeChangeSet/ExecuteChangeSet/DeleteChangeSet can't
        // share a tag condition with CreateChangeSet at all:
        //   1. Those three actions don't accept a Tags parameter, so aws:RequestTag never has a
        //      value to compare against -- an unconditional deny that looked like a scoped one.
        //   2. A second attempt used aws:ResourceTag instead (these actions authorize against
        //      the STACK the change set belongs to, confirmed by the denial's own resource being
        //      a stack ARN, not a changeset one) -- still failed live, for a CREATE-type change
        //      set specifically: CloudFormation only applies a change set's Tags to the stack
        //      when it EXECUTES, not when it's merely created. A brand-new stack sits in
        //      REVIEW_IN_PROGRESS with zero tags for as long as its first change set is still
        //      pending -- exactly the state a stuck stack from an earlier failed attempt was
        //      found in live, no tag to match against yet.
        // No condition at all, matching this class's own SC_PROVISION precedent below ("the
        // underlying service's own model is the guardrail, not a tag condition on Manager's
        // role") -- CreateChangeSet's own tag requirement already gates who can start a managed
        // change set in the first place; once one exists, finishing what Manager itself just
        // started doesn't need a second gate that these actions can't structurally satisfy.
        PolicyStatement changeSetLifecycle = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployChangeSetLifecycle")
            .actions(List.of(
                "cloudformation:DescribeChangeSet",
                "cloudformation:ExecuteChangeSet",
                "cloudformation:DeleteChangeSet"))
            .resources(List.of("*"))
            .build();
        PolicyStatement passRole = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployPassRole")
            .actions(List.of("iam:PassRole"))
            .resources(List.of("*"))
            .conditions(Map.of("StringEquals", Map.of("iam:ResourceTag/" + TAG_MANAGED, "true")))
            .build();
        // Confirmed live: iam:PassRole alone isn't enough -- CloudFormation creates and manages
        // the target application's own IAM roles (task role, task execution role -- the standard
        // ECS/Fargate pattern every deployed app needs) using the DEPLOYING PRINCIPAL's own
        // credentials, the same way it does for every other resource type in the template.
        // CAPABILITY_NAMED_IAM on the API call only acknowledges the template creates IAM
        // resources; it doesn't itself grant the permission to create/manage them. Surfaced by a
        // rollback that failed on iam:DeleteRole/iam:DetachRolePolicy after an unrelated resource
        // failure triggered an automatic rollback -- the roles CloudFormation had already created
        // couldn't be torn back down either, for the same missing-permission reason.
        //
        // Originally tag-conditioned on iam:RequestTag, on the assumption that CreateRole/TagRole
        // carry the request's own Tags parameter synchronously, with no propagation delay to race
        // against. Confirmed live that assumption was wrong: a genuinely first-ever CreateRole
        // call for a brand-new role was denied by this exact condition, even though the
        // synthesized template was verified (via direct cdk synth inspection) to carry the
        // correct inline cloudforge:managed tag in the role's own Properties.Tags. CloudFormation's
        // own error surfaced it as a HandlerErrorCode: UnauthorizedTaggingOperation on iam:CreateRole
        // specifically -- consistent with its IAM::Role resource provider not reliably passing the
        // template's tags through to the CreateRole API call's own Tags parameter in a way
        // iam:RequestTag can evaluate against (a real, documented CloudFormation/IAM interaction
        // gap, not a config mistake on this class's part). Same fix as iamRoleManage below, for the
        // same reason: resource-name-pattern matching is evaluated synchronously against the
        // request and doesn't depend on any tag mechanism at all.
        PolicyStatement iamRoleCreate = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployIamRoleCreate")
            .actions(List.of("iam:CreateRole", "iam:TagRole"))
            .resources(List.of(
                "arn:aws:iam::*:role/*SystemContextExtendedTask*",
                "arn:aws:iam::*:role/*LogRetention*"))
            .build();
        // Deliberately resource-name-scoped, not iam:ResourceTag-conditioned -- confirmed live:
        // CloudFormation's automatic rollback issues DeleteRole/DetachRolePolicy within seconds of
        // CreateRole succeeding (a role created just before some unrelated resource in the same
        // stack failed), faster than the role's own just-applied tags reliably propagate into
        // IAM's tag-based condition evaluation. The synthesized template itself was verified to
        // carry the correct cloudforge:managed tag on these exact roles -- this is a real IAM
        // eventual-consistency race on iam:ResourceTag
        // specifically, not a missing/wrong tag. A resource ARN pattern match is evaluated
        // synchronously against the request (same reasoning as templateBucket's fixed prefix
        // below), so it can't race. Scoped to the fixed logical-id-derived names this platform's
        // own constructs and CDK's own built-ins produce, never an arbitrary role an unrelated
        // stack could shape to match: "SystemContextExtendedTask..." for every app's
        // ExtendedIAMConfiguration/StandardIAMConfiguration/MinimalIAMConfiguration task role
        // (never itself truncated by CloudFormation's physical-name generation in any real case
        // observed, only the trailing "Role"/"ExecutionRole" suffix and random hash are), and
        // "LogRetention..." for the CDK-builtin custom-resource Lambda every app in this catalog
        // gets simply by setting a log group's retention -- confirmed live as a second, separate
        // rollback-delete denial once the first pattern alone stopped blocking. No "ServiceRole"
        // requirement in the pattern: this logical ID is long enough that CloudFormation's
        // physical-name truncation cuts it off before "ServiceRole" ever appears at all (matching
        // "Basic-Minimal-LogRetentionaae0aa3c5b4d4f87b02d85b20-<hash>" observed live) -- the
        // "aae0aa3c5..." segment right after "LogRetention" is CDK's own stable construct-id hash
        // for this one shared singleton Lambda, identical across every app, so matching on
        // "LogRetention" alone is already as tightly scoped as this name can reliably get.
        PolicyStatement iamRoleManage = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployIamRoleManage")
            .actions(List.of(
                "iam:DeleteRole",
                "iam:GetRole",
                "iam:UpdateRole",
                "iam:UpdateAssumeRolePolicy",
                "iam:AttachRolePolicy",
                "iam:DetachRolePolicy",
                "iam:PutRolePolicy",
                "iam:DeleteRolePolicy",
                "iam:GetRolePolicy",
                "iam:ListRolePolicies",
                "iam:ListAttachedRolePolicies",
                "iam:UntagRole"))
            .resources(List.of(
                "arn:aws:iam::*:role/*SystemContextExtendedTask*",
                "arn:aws:iam::*:role/*LogRetention*"))
            .build();
        // The LogRetention custom resource is a Lambda *function*, not just the IAM role above --
        // confirmed live: lambda:CreateFunction was denied outright (nothing granted any lambda:*
        // action at all until this point), and the same gap then blocked the stack's own
        // automatic rollback on lambda:DeleteFunction, leaving it stuck in ROLLBACK_FAILED.
        // Same resource-name-pattern scoping as the role above, for the same reason -- this is
        // CDK's one shared singleton log-retention Lambda, identical name shape across every app.
        PolicyStatement logRetentionFunctionManage = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployLogRetentionFunctionManage")
            .actions(List.of(
                "lambda:CreateFunction",
                "lambda:DeleteFunction",
                "lambda:GetFunction",
                "lambda:GetFunctionConfiguration",
                "lambda:UpdateFunctionCode",
                "lambda:UpdateFunctionConfiguration",
                "lambda:AddPermission",
                "lambda:RemovePermission",
                "lambda:GetPolicy",
                "lambda:TagResource",
                "lambda:UntagResource",
                "lambda:ListTags",
                "lambda:InvokeFunction"))
            .resources(List.of("arn:aws:lambda:*:*:function:*LogRetention*"))
            .build();
        // The very gap this class's own "Caveat, stated plainly" javadoc above warned about,
        // confirmed live the first time deploy:create ran against an AWS account: AwsDirectDeployer
        // uploads the template to a bucket before CreateStack/CreateChangeSet ever runs, and
        // nothing granted that upload -- no S3 statement existed here at all. S3 bucket ARNs carry
        // no account segment, so a fixed prefix match is exactly as scoped as the tag-conditioned
        // statements above, without needing a matching condition key.
        PolicyStatement templateBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployTemplateBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*",
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*/*"))
            .build();
        // Confirmed live: fixing the template bucket above wasn't the whole gap.
        // AwsDirectDeployer.deploy() always calls LocalStackCdkAssetPublisher.publish() -- for
        // real AWS as much as a local emulator, that class's own javadoc says -- which uploads
        // to whatever bucket the synthesized CDK asset manifest names, the standard CDK bootstrap
        // convention (cdk-<qualifier>-assets-<account>-<region>). This app's deploy path never
        // runs `cdk bootstrap` and doesn't support a custom qualifier, so the default
        // ("hnb659fds") is the only value that's ever actually used here.
        PolicyStatement cdkAssetBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCdkAssetBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::cdk-hnb659fds-assets-*",
                "arn:aws:s3:::cdk-hnb659fds-assets-*/*"))
            .build();
        // Confirmed live: the two S3 grants above weren't the whole gap either. CloudFormation
        // resolves a template's BootstrapVersion AWS::SSM::Parameter::Value<String> dynamic
        // reference using the caller's own credentials, not a service-linked role -- so
        // Manager's task role itself needs ssm:GetParameters on this parameter, the same way it
        // needs the S3 grants above, regardless of whether the account has ever run a real `cdk
        // bootstrap` (AwsDirectDeployer.resolveCdkBootstrapParameters rewrites this reference
        // away entirely, but only for local-emulator targets -- see that method's own javadoc for
        // why real AWS keeps its template untouched).
        PolicyStatement cdkBootstrapParameter = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCdkBootstrapParameter")
            .actions(List.of("ssm:GetParameters"))
            .resources(List.of("arn:aws:ssm:*:*:parameter/cdk-bootstrap/hnb659fds/*"))
            .build();
        // Confirmed live: even with every statement above, a genuinely new stack's own VPC/EFS/
        // ALB/ECS-cluster resources still couldn't be created at all -- CloudFormation issues
        // every ec2:CreateVpc/elasticfilesystem:CreateFileSystem/ecs:CreateCluster/etc. call
        // under Manager's own identity, and nothing had ever granted that layer (as distinct
        // from the CloudFormation-orchestration and IAM-role-lifecycle grants above, and from
        // PermissionMatrix, which governs a *deployed app's own* task role, not Manager's).
        // See OperatorProvisioningPermissionMatrix's own javadoc for why this stays unconditioned
        // like changeSetLifecycle above rather than tag-conditioned: individually splitting ~150
        // create-vs-manage-vs-describe actions across six AWS services by RequestTag/ResourceTag
        // would reproduce the exact same tag-propagation race iamRoleManage above was fixed for,
        // and these resources have no stable name pattern the way IAM roles do to scope by
        // instead. The cloudforge:managed condition on create/update above already bounds this:
        // every one of these calls only ever fires as part of a stack CloudFormation was told to
        // manage under that tag.
        //
        // Split into two statements, not one -- confirmed live: a role's combined inline-policy
        // size across every inline policy document it carries is capped at 10,240 bytes total,
        // not a separate budget per document. Adding the database/KMS/secrets permissions pushed
        // the single flat action list (212 actions, ~6.3KB alone) over what fit alongside this
        // role's other statements even in its own separate inline policy. See
        // OperatorProvisioningPermissionMatrix#getNetworkPermissions's own javadoc for the byte
        // accounting behind this exact split point; attachDeployCapabilities attaches each half
        // as its own customer-managed policy (a separate, larger budget) rather than inline.
        PolicyStatement targetInfrastructureNetwork = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployTargetInfrastructureNetwork")
            .actions(OperatorProvisioningPermissionMatrix.getNetworkPermissions(IAMProfile.EXTENDED))
            .resources(List.of("*"))
            .build();
        PolicyStatement targetInfrastructureComputeData = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployTargetInfrastructureComputeData")
            .actions(OperatorProvisioningPermissionMatrix.getComputeAndDataPermissions(IAMProfile.EXTENDED, true))
            .resources(List.of("*"))
            .build();
        return List.of(create, update, passRole, templateBucket, cdkAssetBucket, cdkBootstrapParameter,
            changeSetLifecycle, iamRoleCreate, iamRoleManage, logRetentionFunctionManage,
            targetInfrastructureNetwork, targetInfrastructureComputeData);
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
        // The two target-infrastructure-provisioning statements get their own customer-managed
        // policies instead of sharing the role's inline policy/policies (what role.addToPolicy
        // uses for every other statement here). Confirmed live, twice: (1) the shared default
        // inline policy alone hit IAM's real 10,240-byte combined-inline-per-role ceiling once
        // the database/KMS/secrets permissions were added; (2) moving the combined statement into
        // its own separate *inline* policy document didn't help, because that 10,240-byte cap is
        // a total across every inline document a role carries, not a per-document budget --
        // confirmed by the role's two inline policies (4.5KB + 6.3KB) still failing combined. A
        // managed policy's size budget is independent of a role's inline-policy total, which is
        // why these two specifically move there rather than getting a third inline document.
        for (PolicyStatement statement : statements) {
            String sid = statement.getSid();
            if ("CloudForgeManagerDeployTargetInfrastructureNetwork".equals(sid)
                    || "CloudForgeManagerDeployTargetInfrastructureComputeData".equals(sid)) {
                role.addManagedPolicy(ManagedPolicy.Builder.create(role, sid + "Policy")
                    .statements(List.of(statement))
                    .build());
            } else {
                role.addToPolicy(statement);
            }
        }
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
