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

    /** Every cross-account-connect role this app itself ever generates carries this exact name
     *  prefix regardless of which AWS account it lives in (see {@code
     *  CrossAccountRoleTemplateFactory#ROLE_NAME_PREFIX} in cloudforge-manager — duplicated here
     *  rather than shared, since cloudforge-api can't depend on cloudforge-manager; keep the two
     *  in sync by hand). Scoping {@link #crossAccountAssumeRoleStatement}'s resource to this
     *  prefix instead of {@code Resource: "*"} is exactly as narrow as the actual capability
     *  needs to be. */
    private static final String CROSS_ACCOUNT_ROLE_NAME_PREFIX = "CloudForgeManagerAccess-";

    /**
     * Lets Manager's own task role actually CALL {@code sts:AssumeRole} against a connected
     * account's {@code CloudForgeManagerAccess-*} role — the other half of the cross-account
     * connect feature ({@code StsAssumeRoleService}/{@code CrossAccountRoleTemplateFactory}): a
     * correct trust policy on the TARGET role means nothing if Manager's own IAM policy never
     * grants it permission to make the call in the first place. A connected account's deployed
     * trust policy can match byte-for-byte what Manager itself generated and {@code
     * sts:AssumeRole} still come back {@code AccessDenied} without this — the grant had never
     * actually existed anywhere in either repo, despite {@code CrossAccountRoleTemplateFactory}'s
     * own javadoc claiming this class already provided it.
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
    /**
     * Resource-name patterns for the IAM roles a deployed app's own stack creates that Manager's
     * operator role must be able to create/manage/tear down (see {@code iamRoleCreate}/{@code
     * iamRoleManage} below for why this can't be a tag condition instead). Each pattern targets a
     * name segment that survives CloudFormation's physical-role-name truncation (IAM's
     * 64-character role-name limit forces CloudFormation to keep only a prefix of the full
     * logical id plus a random hash suffix) — not every role this platform's own constructs
     * create can be covered this way, see the caveat below.
     *
     * <p><b>Known gap, not coverable by this mechanism</b>: {@code VpcFlowlogIAMRole}, {@code
     * BackupSelectionRole}, and {@code ComplianceCloudTrailLogsRole}. Each is nested deep enough
     * under an app's own construct path (e.g. {@code <App>Application/<App>Vpc/VpcFlowlogIAMRole})
     * that for a long-enough stack/app name, CloudFormation's truncation cuts the physical name
     * off before any of these three ever appear — {@code VpcFlowlogIAMRole} and {@code
     * BackupSelectionRole} can truncate to the exact same physical-name prefix on the same stack,
     * so no name pattern can even distinguish between them, let alone reliably match either.
     * {@code ComplianceCloudTrailLogsRole} truncates to an app-name-derived fragment that isn't a
     * fixed platform string at all, so no pattern written today can match it for a
     * differently-named app either. Adding more name patterns can't close this gap — the
     * identifying suffix is destroyed before it reaches the physical name, not merely unmatched.
     * The real fix is a stable identifier that survives truncation entirely, e.g. a shared IAM
     * path prefix (a role's {@code path} isn't subject to the same length-driven truncation as
     * its name) assigned to every role this platform's own factories create, with Manager's
     * operator policy scoped to that path instead of a name substring — a broader change across
     * every factory that creates a {@code Role} (VpcFactory, BackupFactory, compliance CloudTrail
     * logging, ...), out of scope here. Until then, a deploy whose rollback needs to delete one of
     * these three roles gets stuck in {@code ROLLBACK_FAILED}, needing manual cleanup with
     * elevated (non-Manager) credentials.</p>
     */
    private static final List<String> IAM_ROLE_MANAGE_RESOURCES = List.of(
        // Every IAMProfile variant's task/task-execution role (ExtendedIAMConfiguration/
        // StandardIAMConfiguration/MinimalIAMConfiguration). All three are emitted as a flat,
        // stack-top-level construct (never nested under an app's own construct path), so
        // "SystemContext...Task..." reliably survives truncation regardless of stack/app name
        // length.
        "arn:aws:iam::*:role/*SystemContextExtendedTask*",
        "arn:aws:iam::*:role/*SystemContextStandardTask*",
        "arn:aws:iam::*:role/*SystemContextMinimalTask*",
        // CDK's one shared singleton log-retention Lambda's role, identical name shape across
        // every app.
        "arn:aws:iam::*:role/*LogRetention*",
        // CDK's other shared singleton custom-resource-provider framework role, backing (among
        // other built-ins) Custom::S3AutoDeleteObjects. "679f53fac002430cb0da5b7982bd2287" is
        // CDK's own fixed construct-id hash for this framework (not a per-stack random value), so
        // it's as stable a match as "LogRetention" above; matched on a shortened prefix of it
        // since that's the portion that survives truncation.
        "arn:aws:iam::*:role/*AWS679f53fac002430cb0da5b798*",
        "arn:aws:iam::*:role/*CustomS3AutoDeleteObjectsCus*",
        // RDS Enhanced Monitoring role, built with an explicit roleName by RdsFactory precisely
        // so this pattern can rely on it -- see RdsFactory#createMonitoringRole's own javadoc for
        // why the auto-created default (a role CloudFormation names itself, nested deep enough
        // under the database instance's own construct to get truncated away for a long enough
        // stack/app name) isn't safe to pattern-match here the way the other entries in this list
        // are.
        "arn:aws:iam::*:role/*-CfcRdsMonitor"
    );

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
        // DescribeChangeSet/ExecuteChangeSet/DeleteChangeSet can't share a tag condition with
        // CreateChangeSet at all:
        //   1. Those three actions don't accept a Tags parameter, so aws:RequestTag never has a
        //      value to compare against -- an unconditional deny that looks like a scoped one.
        //   2. aws:ResourceTag doesn't work either: these actions authorize against the STACK the
        //      change set belongs to, and for a CREATE-type change set specifically, CloudFormation
        //      only applies a change set's Tags to the stack when it EXECUTES, not when it's
        //      merely created. A brand-new stack sits in REVIEW_IN_PROGRESS with zero tags for as
        //      long as its first change set is still pending, with no tag to match against yet.
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
        // iam:PassRole alone isn't enough -- CloudFormation creates and manages the target
        // application's own IAM roles (task role, task execution role -- the standard ECS/Fargate
        // pattern every deployed app needs) using the DEPLOYING PRINCIPAL's own credentials, the
        // same way it does for every other resource type in the template. CAPABILITY_NAMED_IAM on
        // the API call only acknowledges the template creates IAM resources; it doesn't itself
        // grant the permission to create/manage them. Without the grants below, an automatic
        // rollback triggered by an unrelated resource failure can't tear back down the roles
        // CloudFormation had already created either, for the same missing-permission reason.
        //
        // Resource-name-scoped rather than tag-conditioned on iam:RequestTag, even though
        // CreateRole/TagRole carry the request's own Tags parameter synchronously with no
        // propagation delay to race against in principle: CloudFormation's IAM::Role resource
        // provider doesn't reliably pass the template's tags through to the CreateRole API call's
        // own Tags parameter in a way iam:RequestTag can evaluate against, surfacing as a
        // HandlerErrorCode: UnauthorizedTaggingOperation on iam:CreateRole even when the
        // synthesized template carries the correct inline cloudforge:managed tag in the role's
        // own Properties.Tags -- a real, documented CloudFormation/IAM interaction gap, not a
        // config mistake on this class's part. Same fix as iamRoleManage below, for the same
        // reason: resource-name-pattern matching is evaluated synchronously against the request
        // and doesn't depend on any tag mechanism at all.
        PolicyStatement iamRoleCreate = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployIamRoleCreate")
            .actions(List.of("iam:CreateRole", "iam:TagRole"))
            .resources(IAM_ROLE_MANAGE_RESOURCES)
            .build();
        // Deliberately resource-name-scoped, not iam:ResourceTag-conditioned: CloudFormation's
        // automatic rollback can issue DeleteRole/DetachRolePolicy within seconds of CreateRole
        // succeeding (a role created just before some unrelated resource in the same stack
        // failed) -- faster than the role's own just-applied tags reliably propagate into IAM's
        // tag-based condition evaluation, a real eventual-consistency race on iam:ResourceTag
        // specifically, not a missing/wrong tag. A resource ARN pattern match is evaluated
        // synchronously against the request (same reasoning as templateBucket's fixed prefix
        // below), so it can't race. See IAM_ROLE_MANAGE_RESOURCES's own javadoc for what's
        // covered, what isn't, and why.
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
        // The LogRetention custom resource is a Lambda *function*, not just the IAM role above --
        // without these, lambda:CreateFunction is denied outright (nothing else here grants any
        // lambda:* action), and the same gap then blocks the stack's own automatic rollback on
        // lambda:DeleteFunction, leaving it stuck in ROLLBACK_FAILED. Same resource-name-pattern
        // scoping as the role above, for the same reason -- this is CDK's one shared singleton
        // log-retention Lambda, identical name shape across every app.
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
        // The very gap this class's own "Caveat, stated plainly" javadoc above warns about:
        // AwsDirectDeployer uploads the template to a bucket before CreateStack/CreateChangeSet
        // ever runs, which needs its own S3 grant -- CreateChangeSet's tag condition alone doesn't
        // cover it. S3 bucket ARNs carry no account segment, so a fixed prefix match is exactly as
        // scoped as the tag-conditioned statements above, without needing a matching condition key.
        PolicyStatement templateBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployTemplateBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*",
                "arn:aws:s3:::" + AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX + "*/*"))
            .build();
        // The template bucket above isn't the only S3 grant needed: AwsDirectDeployer.deploy()
        // always calls LocalStackCdkAssetPublisher.publish() -- for real AWS as much as a local
        // emulator, that class's own javadoc says -- which uploads to whatever bucket the
        // synthesized CDK asset manifest names, the standard CDK bootstrap convention
        // (cdk-<qualifier>-assets-<account>-<region>). This app's deploy path never runs `cdk
        // bootstrap` and doesn't support a custom qualifier, so the default ("hnb659fds") is the
        // only value ever used here.
        PolicyStatement cdkAssetBucket = PolicyStatement.Builder.create()
            .sid("CloudForgeManagerDeployCdkAssetBucket")
            .actions(List.of("s3:*"))
            .resources(List.of(
                "arn:aws:s3:::cdk-hnb659fds-assets-*",
                "arn:aws:s3:::cdk-hnb659fds-assets-*/*"))
            .build();
        // CloudFormation resolves a template's BootstrapVersion AWS::SSM::Parameter::Value<String>
        // dynamic reference using the caller's own credentials, not a service-linked role -- so
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
        // Even with every statement above, a genuinely new stack's own VPC/EFS/ALB/ECS-cluster
        // resources still can't be created without this: CloudFormation issues every
        // ec2:CreateVpc/elasticfilesystem:CreateFileSystem/ecs:CreateCluster/etc. call under
        // Manager's own identity, a layer distinct from the CloudFormation-orchestration and
        // IAM-role-lifecycle grants above, and from PermissionMatrix, which governs a *deployed
        // app's own* task role, not Manager's.
        // See OperatorProvisioningPermissionMatrix's own javadoc for why this stays unconditioned
        // like changeSetLifecycle above rather than tag-conditioned: individually splitting ~150
        // create-vs-manage-vs-describe actions across six AWS services by RequestTag/ResourceTag
        // would reproduce the exact same tag-propagation race iamRoleManage above was fixed for,
        // and these resources have no stable name pattern the way IAM roles do to scope by
        // instead. The cloudforge:managed condition on create/update above already bounds this:
        // every one of these calls only ever fires as part of a stack CloudFormation was told to
        // manage under that tag.
        //
        // Split into two statements, not one: a role's combined inline-policy size across every
        // inline policy document it carries is capped at 10,240 bytes total, not a separate
        // budget per document. The database/KMS/secrets permissions push the single flat action
        // list (212 actions, ~6.3KB alone) over what fits alongside this
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
        for (PolicyStatement statement : statements) {
            if (isTargetInfrastructureStatement(statement)) {
                attachAsOwnManagedPolicy(role, statement);
            } else {
                role.addToPolicy(statement);
            }
        }
        catalogProvisionStatement(ctx).ifPresent(role::addToPolicy);
        addDeployCapabilitiesNagSuppressions(role);
    }

    /**
     * Deploy-capability statements safe to fold into a CALLER-assembled statements list — every
     * one of {@link #deployStatements} except the two target-infrastructure statements (see
     * {@link #attachDeployTargetInfrastructurePolicies}'s javadoc for why those can't share a
     * document with anything else). Needed alongside {@link #attachDeployCapabilities} rather
     * than instead of it: a Fargate task role built PRODUCTION-profile-style (one hand-assembled
     * {@code ManagedPolicy} built from a statements list, then attached at role construction —
     * see {@code MinimalIAMConfiguration}'s two Fargate task-role branches) can't retrofit
     * {@code role.addToPolicy(...)} onto a role that doesn't exist yet the way {@code
     * attachDeployCapabilities} does for the DEV/STAGING branch's incrementally-built role. Same
     * opt-in check as every other method here — a no-op call when {@code
     * managerDirectDeployEnabled} isn't set is always safe.
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
        catalogProvisionStatement(ctx).ifPresent(statements::add);
    }

    /**
     * The two target-infrastructure statements from {@link #deployStatements}, each attached to
     * {@code role} as its own customer-managed policy — the companion {@code role} needs once
     * its other deploy-capability statements arrived via {@link #addDeployCapabilitiesToStatements}
     * instead of {@link #attachDeployCapabilities}. Split out from the rest for the same reason
     * {@code attachDeployCapabilities} itself splits them: IAM caps a role's combined inline-
     * policy size, across every inline document it carries, at 10,240 bytes total — not a
     * per-document budget — so the database/KMS/secrets permissions already push a Fargate task
     * role's own policy near that ceiling before these two (~150 actions across two AWS services)
     * are even added. A managed policy's size budget is independent of a role's inline-policy
     * total, which is why these two specifically get their own rather than joining any inline
     * document (this one included, despite it being a customer-managed policy built from a
     * statements list rather than {@code role.addToPolicy} — its own budget is still shared
     * across everything in that SAME list, unlike a freshly attached policy's).
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
