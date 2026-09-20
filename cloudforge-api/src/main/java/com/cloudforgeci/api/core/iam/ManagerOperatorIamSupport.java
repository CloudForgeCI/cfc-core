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

    /** Name prefix of every cross-account access role Manager generates, in any account. Mirrors
     *  {@code CrossAccountRoleTemplateFactory#ROLE_NAME_PREFIX} in cloudforge-manager (duplicated
     *  because cloudforge-api cannot depend on it; keep the two in sync). Scopes
     *  {@link #crossAccountAssumeRoleStatement} to these roles instead of {@code "*"}. */
    private static final String CROSS_ACCOUNT_ROLE_NAME_PREFIX = "CloudForgeManagerAccess-";

    /**
     * Allows Manager's task role to call {@code sts:AssumeRole} on a connected account's
     * {@code CloudForgeManagerAccess-*} role. Cross-account access needs both this grant on the
     * caller and a matching trust policy on the target role ({@code CrossAccountRoleTemplateFactory});
     * without this statement {@code sts:AssumeRole} returns {@code AccessDenied} even when the
     * target's trust policy is correct.
     */
    public static Optional<PolicyStatement> crossAccountAssumeRoleStatement(SystemContext ctx) {
        if (!isCloudForgeManager(ctx)) {
            return Optional.empty();
        }
        return Optional.of(PolicyStatement.Builder.create()
            .sid("CloudForgeManagerCrossAccountAssumeRole")
            .actions(List.of("sts:AssumeRole"))
            .resources(List.of("arn:aws:iam::*:role/" + CROSS_ACCOUNT_ROLE_NAME_PREFIX + "*"))
            .build());
    }

    public static void addOperatorBaselineToStatements(SystemContext ctx, List<PolicyStatement> statements) {
        operatorBaselineStatement(ctx).ifPresent(statements::add);
        crossAccountAssumeRoleStatement(ctx).ifPresent(statements::add);
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
        crossAccountAssumeRoleStatement(ctx).ifPresent(role::addToPolicy);
    }

    /** Tag key applied by {@code ApplicationFargateStack}/{@code ApplicationEc2Stack} via
     * {@code Tags.of(this).add(...)} and read by Manager's {@code StackListingPolicy.TAG_MANAGED}.
     * The three sites share no common module, so keep them in sync. */
    private static final String TAG_MANAGED = "cloudforge:managed";

    /**
     * Resource-name patterns for the IAM roles a deployed app's stack creates that Manager's
     * operator role must be able to create, manage, and delete (see {@code iamRoleCreate}/{@code
     * iamRoleManage} below for why this is not a tag condition). Each pattern targets a name
     * segment that survives CloudFormation's physical-name truncation (IAM's 64-character limit
     * keeps only a prefix of the logical id plus a random suffix).
     *
     * <p><b>Known gap:</b> {@code VpcFlowlogIAMRole}, {@code BackupSelectionRole}, and
     * {@code ComplianceCloudTrailLogsRole} are nested deeply enough (e.g.
     * {@code <App>Application/<App>Vpc/VpcFlowlogIAMRole}) that, for long stack/app names,
     * truncation removes the identifying segment entirely, so no name pattern can match them.
     * TODO: assign a shared IAM {@code path} to every role CloudForge factories create (paths are
     * not truncated) and scope this policy by path instead. Until then, a rollback that must delete
     * one of these roles ends in {@code ROLLBACK_FAILED} and needs manual cleanup with elevated
     * credentials.</p>
     */
    private static final List<String> IAM_ROLE_MANAGE_RESOURCES = List.of(
        // Task/task-execution roles for each IAMProfile (Extended/Standard/MinimalIAMConfiguration).
        // These are stack-top-level constructs, so "SystemContext...Task..." survives truncation.
        "arn:aws:iam::*:role/*SystemContextExtendedTask*",
        "arn:aws:iam::*:role/*SystemContextStandardTask*",
        "arn:aws:iam::*:role/*SystemContextMinimalTask*",
        // Role of CDK's singleton log-retention Lambda; same name shape in every app.
        "arn:aws:iam::*:role/*LogRetention*",
        // Role of CDK's singleton custom-resource provider (backs Custom::S3AutoDeleteObjects,
        // among others). "679f53fac002430cb0da5b7982bd2287" is CDK's fixed construct-id hash for
        // this provider, so it is stable; only the prefix that survives truncation is matched.
        "arn:aws:iam::*:role/*AWS679f53fac002430cb0da5b798*",
        "arn:aws:iam::*:role/*CustomS3AutoDeleteObjectsCus*",
        // RDS Enhanced Monitoring role. RdsFactory gives it an explicit roleName so this pattern
        // can match it; see RdsFactory#createMonitoringRole.
        "arn:aws:iam::*:role/*-CfcRdsMonitor",
        // AWS Backup selection role. BackupFactory gives it an explicit roleName for the same
        // reason as the RDS monitoring role above; see BackupFactory#createSelectionRole -- its
        // auto-generated physical name otherwise truncates to a generic tail shared with unrelated
        // roles in the same stack, leaving nothing stable to match, and a rollback that needs to
        // delete it fails with AccessDenied and lands the stack in ROLLBACK_FAILED.
        "arn:aws:iam::*:role/*-CfcBackupSelection",
        // VPC Flow Log delivery role. FlowLogFactory gives it an explicit roleName for the same
        // reason; see FlowLogFactory#createFlowLogRole.
        "arn:aws:iam::*:role/*-CfcFlowLog"
    );

    // KNOWN GAP: CloudTrail's CloudWatch Logs delivery role, auto-created by ComplianceFactory's
    // use of the high-level `Trail` L2 construct (`Trail.Builder...sendToCloudWatchLogs(true)`),
    // is NOT covered by any pattern above and remains unmatchable the same way the RDS/Backup/
    // FlowLog roles were before their fixes. Unlike those three, `Trail.Builder` has no
    // `cloudWatchLogsRole`-style override -- there is no supported way to give this auto-created
    // role an explicit, stable name from the L2 API. Closing this gap would mean dropping to the
    // L1 `CfnTrail` construct (or reaching into the L2 Trail's internal child construct via
    // `addPropertyOverride`, an unverified escape hatch) to assign an explicit RoleName, which is
    // a larger, riskier change than the three fixes above and has not been attempted yet. Until
    // then, a stack whose rollback needs to delete this specific role can still land in
    // ROLLBACK_FAILED and need the same manual cleanup the fixes above now avoid for the others.

    /**
     * Direct-deploy IAM ({@code CFN_DEPLOY} capability, backing the {@code deploy:create}
     * {@code ManagerPolicyCatalog} policy), scoped to CloudForge-tagged resources rather than a
     * flat action allow-list, because {@code CreateStack}/{@code UpdateStack}/{@code PassRole} can
     * create or modify arbitrary infrastructure.
     *
     * <p>Create and update use separate statements because the condition key differs by lifecycle
     * stage: {@code aws:RequestTag} evaluates tags on a resource being created, while
     * {@code aws:ResourceTag} evaluates an existing resource's tags. Statements are OR'd, so
     * whichever path applies succeeds. {@code iam:PassRole} is scoped by {@code iam:ResourceTag}
     * on the passed role, relying on CDK's stack-level tag propagation to tag the stack's roles.</p>
     *
     * <p><b>Caveat:</b> these conditions are covered by unit tests only, not an end-to-end
     * deploy against an AWS account. Verify {@code CreateChangeSet}/{@code ExecuteChangeSet}
     * behavior under these conditions before relying on this as the sole guardrail.</p>
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
        // DescribeChangeSet/ExecuteChangeSet/DeleteChangeSet cannot use a tag condition:
        //   1. They accept no Tags parameter, so aws:RequestTag never matches.
        //   2. They authorize against the stack, and for a CREATE change set CloudFormation only
        //      applies tags on execution, so a new stack in REVIEW_IN_PROGRESS has no tags yet.
        // CreateChangeSet's tag condition already gates who can start a managed change set.
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
        // iam:PassRole alone is not enough: CloudFormation creates the application's IAM roles
        // (task and task-execution roles) with the deploying principal's credentials.
        // CAPABILITY_NAMED_IAM only acknowledges IAM resources; it grants nothing. Without these
        // grants, an automatic rollback also cannot delete roles already created.
        //
        // Scoped by resource name rather than iam:RequestTag: CloudFormation's AWS::IAM::Role
        // provider does not reliably pass template tags to CreateRole in a form iam:RequestTag
        // can evaluate, which fails with UnauthorizedTaggingOperation even when the template
        // carries the cloudforge:managed tag. ARN patterns do not depend on tags.
        PolicyStatement iamRoleCreate = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployIamRoleCreate")
            .actions(List.of("iam:CreateRole", "iam:TagRole"))
            .resources(IAM_ROLE_MANAGE_RESOURCES)
            .build();
        // Scoped by resource name rather than iam:ResourceTag: an automatic rollback can issue
        // DeleteRole/DetachRolePolicy seconds after CreateRole, before the new role's tags are
        // visible to IAM condition evaluation (eventual consistency). ARN patterns are evaluated
        // synchronously. See IAM_ROLE_MANAGE_RESOURCES for coverage and known gaps.
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
            .resources(IAM_ROLE_MANAGE_RESOURCES)
            .build();
        // The LogRetention custom resource is also a Lambda function. Without these grants,
        // lambda:CreateFunction is denied and rollback fails on lambda:DeleteFunction
        // (ROLLBACK_FAILED). Scoped by name, like the role above.
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
        // AwsDirectDeployer uploads the template to S3 before CreateStack/CreateChangeSet, which
        // needs its own grant. Scoped to the deployer's fixed bucket-name prefix.
        PolicyStatement templateBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployTemplateBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*",
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*/*"))
            .build();
        // AwsDirectDeployer.deploy() also publishes CDK assets (via LocalStackCdkAssetPublisher,
        // for AWS and emulators alike) to the bucket named in the asset manifest, following the
        // CDK bootstrap convention cdk-<qualifier>-assets-<account>-<region>. Custom qualifiers are
        // not supported, so only the default "hnb659fds" is granted.
        PolicyStatement cdkAssetBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCdkAssetBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::cdk-hnb659fds-assets-*",
                "arn:aws:s3:::cdk-hnb659fds-assets-*/*"))
            .build();
        // CloudFormation resolves the template's BootstrapVersion SSM parameter reference with
        // the caller's credentials, so the task role needs ssm:GetParameters on it.
        // (AwsDirectDeployer.resolveCdkBootstrapParameters removes the reference for local
        // emulator targets only.)
        PolicyStatement cdkBootstrapParameter = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCdkBootstrapParameter")
            .actions(List.of("ssm:GetParameters"))
            .resources(List.of("arn:aws:ssm:*:*:parameter/cdk-bootstrap/hnb659fds/*"))
            .build();
        // CloudFormation creates the stack's VPC/EFS/ALB/ECS-cluster resources under the
        // caller's identity, so Manager's role needs those service actions too. (PermissionMatrix
        // governs the deployed app's task role, not Manager's.)
        // Unconditioned, like changeSetLifecycle: tag-conditioning ~150 actions across six
        // services would hit the same tag-propagation race as iamRoleManage, and these resources
        // have no stable name pattern. The cloudforge:managed condition on stack create/update
        // bounds them. See OperatorProvisioningPermissionMatrix.
        //
        // Split into two statements because a role's inline policies share a single 10,240-byte
        // budget; attachDeployCapabilities attaches each half as its own customer-managed policy.
        // See OperatorProvisioningPermissionMatrix#getNetworkPermissions for the size accounting.
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
     * Attaches {@link #deployStatements} to the role.
     * Unlike {@link #attachOperatorBaselinePolicies}, this is gated behind
     * {@link com.cloudforge.core.config.DeploymentConfig#managerDirectDeployEnabled}: it is a
     * higher-privilege tier that must be requested explicitly per deployment. The Standard,
     * Extended, and Minimal IAM configurations call this unconditionally; the opt-in check lives
     * here so it has a single owner.
     */
    public static void attachDeployCapabilities(SystemContext ctx, Role role) {
        if (!Boolean.TRUE.equals(ctx.cfc.managerDirectDeployEnabled())) {
            return;
        }
        List<PolicyStatement> statements = deployStatements(ctx);
        if (statements.isEmpty()) {
            return;
        }
        for (PolicyStatement statement : statements) {
            if (isTargetInfrastructureStatement(statement)) {
                attachAsOwnManagedPolicy(role, statement);
            } else {
                role.addToPolicy(statement);
            }
        }
        addDeployCapabilitiesNagSuppressions(role);
    }

    /**
     * Adds {@link #deployStatements} to a caller-assembled statements list, except the two
     * target-infrastructure statements (see {@link #attachDeployTargetInfrastructurePolicies}).
     * Used where a role's policy is built from a statements list before the role exists (e.g. the
     * PRODUCTION Fargate task-role branches in {@code MinimalIAMConfiguration}), so
     * {@link #attachDeployCapabilities} cannot be used. No-op unless
     * {@code managerDirectDeployEnabled} is set.
     */
    public static void addDeployCapabilitiesToStatements(SystemContext ctx, List<PolicyStatement> statements) {
        if (!Boolean.TRUE.equals(ctx.cfc.managerDirectDeployEnabled())) {
            return;
        }
        for (PolicyStatement statement : deployStatements(ctx)) {
            if (!isTargetInfrastructureStatement(statement)) {
                statements.add(statement);
            }
        }
    }

    /**
     * Attaches the two target-infrastructure statements from {@link #deployStatements}, each as
     * its own customer-managed policy. Companion to {@link #addDeployCapabilitiesToStatements}.
     * These statements are large (~150 actions), and a role's inline policies share a single
     * 10,240-byte budget, as do all statements in one managed policy, so each gets a separate
     * managed policy with its own size budget.
     */
    public static void attachDeployTargetInfrastructurePolicies(SystemContext ctx, Role role) {
        if (!Boolean.TRUE.equals(ctx.cfc.managerDirectDeployEnabled())) {
            return;
        }
        boolean attachedAny = false;
        for (PolicyStatement statement : deployStatements(ctx)) {
            if (isTargetInfrastructureStatement(statement)) {
                attachAsOwnManagedPolicy(role, statement);
                attachedAny = true;
            }
        }
        if (attachedAny) {
            addDeployCapabilitiesNagSuppressions(role);
        }
    }

    private static boolean isTargetInfrastructureStatement(PolicyStatement statement) {
        String sid = statement.getSid();
        return "CloudForgeManagerDeployTargetInfrastructureNetwork".equals(sid)
            || "CloudForgeManagerDeployTargetInfrastructureComputeData".equals(sid);
    }

    private static void attachAsOwnManagedPolicy(Role role, PolicyStatement statement) {
        role.addManagedPolicy(ManagedPolicy.Builder.create(role, statement.getSid() + "Policy")
            .statements(List.of(statement))
            .build());
    }

    private static void addDeployCapabilitiesNagSuppressions(Role role) {
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
