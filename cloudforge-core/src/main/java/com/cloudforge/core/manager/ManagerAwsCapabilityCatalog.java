package com.cloudforge.core.manager;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Versioned catalog of AWS API capabilities CloudForge Manager uses for per-instance
 * operator actions (RDS snapshot/restore/upgrade, ECS lifecycle, CFN inventory/delete).
 *
 * <p>Panel auth/RBAC is separate — this catalog governs generated task-role IAM only.</p>
 */
public final class ManagerAwsCapabilityCatalog {

    public static final String CATALOG_VERSION = "1.6.1";

    private ManagerAwsCapabilityCatalog() {
    }

    public enum Capability {
        CFN_INVENTORY(
            "cloudformation:DescribeStacks",
            "cloudformation:DescribeStackResources",
            "cloudformation:ListStackResources",
            "cloudformation:DescribeStackEvents",
            "cloudformation:ListStacks",
            "cloudformation:GetTemplate"),
        CFN_DELETE("cloudformation:DeleteStack"),
        ECS_DESCRIBE(
            "ecs:DescribeClusters",
            "ecs:DescribeServices",
            "ecs:DescribeTasks",
            "ecs:ListTasks"),
        ECS_UPDATE_SERVICE(
            "ecs:UpdateService",
            "ecs:RegisterTaskDefinition",
            "ecs:DescribeTaskDefinition"),
        ECS_STOP_TASK("ecs:StopTask"),
        RDS_DESCRIBE(
            "rds:DescribeDBInstances",
            "rds:DescribeDBSnapshots"),
        RDS_SNAPSHOT(
            "rds:CreateDBSnapshot",
            "rds:DeleteDBSnapshot",
            // RDS instances created with CopyTagsToSnapshot enabled make AWS call this
            // transparently on the caller's behalf right after CreateDBSnapshot succeeds --
            // without it, snapshot creation itself succeeds but the automatic tag-copy step
            // gets denied.
            "rds:AddTagsToResource"),
        RDS_RESTORE("rds:RestoreDBInstanceFromDBSnapshot"),
        RDS_ENGINE_UPGRADE("rds:ModifyDBInstance"),
        LOGS_READ(
            "logs:DescribeLogGroups",
            "logs:FilterLogEvents"),
        AUDIT_MANAGER_READ(
            "auditmanager:GetAssessment",
            "auditmanager:GetEvidence"),
        /**
         * "Cognito as the whole Users directory" -- the separate, admin-opted-in feature that
         * lets the Users page and its API manage a Cognito User Pool's users directly instead of
         * the local DB (see {@code CognitoUserManagementService}/{@code CognitoPoolLookupService}
         * and {@code manager_auth_backend.cognito_enabled}). Without this in the operator
         * baseline, even the pool-lookup step fails with {@code cognito-idp:ListUserPools}
         * denied before the feature can do anything.
         * {@code ListUserPools} itself has no per-pool resource to scope by (it's what
         * discovers the pool ID in the first place), so this whole group stays {@code
         * Resource: "*"} like every other operator-baseline capability in this catalog.
         */
        COGNITO_USER_MANAGEMENT(
            "cognito-idp:ListUserPools",
            "cognito-idp:DescribeUserPool",
            "cognito-idp:ListUsers",
            "cognito-idp:AdminCreateUser",
            "cognito-idp:AdminDeleteUser",
            "cognito-idp:AdminUpdateUserAttributes",
            "cognito-idp:AdminEnableUser",
            "cognito-idp:AdminDisableUser",
            "cognito-idp:AdminUserGlobalSignOut",
            "cognito-idp:AdminAddUserToGroup",
            "cognito-idp:AdminRemoveUserFromGroup",
            "cognito-idp:AdminListGroupsForUser",
            "cognito-idp:CreateGroup",
            "cognito-idp:ListGroups"),
        /**
         * Direct-deploy path for creating/updating CloudForge-managed AWS infrastructure —
         * {@code deploy:create} in {@code ManagerPolicyCatalog} (admin-only) routes here.
         * Deliberately NOT part of {@link #operatorBaseline()} — unlike every other capability
         * in this catalog, these actions can create/modify arbitrary infrastructure, not just
         * operate on what already exists, so they must never be silently included in the
         * default operator policy. {@code ManagerOperatorIamSupport} (cloudforge-api) attaches
         * these with {@code aws:RequestTag}/{@code aws:ResourceTag}/{@code iam:ResourceTag}
         * conditions scoping them to CloudForge-managed resources — this catalog only lists the
         * actions; the conditions live where the CDK {@code PolicyStatement} actually gets
         * built, since this module has no CDK dependency.
         */
        CFN_DEPLOY(
            "cloudformation:CreateStack",
            "cloudformation:UpdateStack",
            "cloudformation:CreateChangeSet",
            "cloudformation:ExecuteChangeSet",
            "cloudformation:DescribeChangeSet",
            "cloudformation:DeleteChangeSet",
            "iam:PassRole"),
        /**
         * {@code deploy:catalog} (constrained, manager+admin) routes here — Service Catalog
         * provisioning against pre-published products only; no CFN/IAM/EC2 permissions on
         * Manager's own role for this path at all. Also not part of {@link #operatorBaseline()}.
         */
        // The last three actions manage a connected account's own
        // AWS::ServiceCatalog::PortfolioPrincipalAssociation (see CrossAccountRoleTemplateFactory's
        // own ServiceCatalogPortfolioId parameter) -- a placeholder, opt-in resource: CloudFormation
        // needs these to create/update/delete/read that association at all, but the association
        // itself only ever gets created once a caller fills in a real portfolio ID (Manager sharing
        // a portfolio with a connected account is not automated yet, see that class's own javadoc).
        SC_PROVISION(
            "servicecatalog:ListPortfolios",
            "servicecatalog:ProvisionProduct",
            "servicecatalog:UpdateProvisionedProduct",
            "servicecatalog:TerminateProvisionedProduct",
            "servicecatalog:DescribeProvisionedProduct",
            "servicecatalog:DescribeRecord",
            "servicecatalog:SearchProvisionedProducts",
            "servicecatalog:DescribeProduct",
            "servicecatalog:DescribeProductView",
            "servicecatalog:ListLaunchPaths",
            "servicecatalog:AssociatePrincipalWithPortfolio",
            "servicecatalog:DisassociatePrincipalFromPortfolio",
            "servicecatalog:ListPrincipalsForPortfolio"),
        /**
         * {@code deploy:catalog-publish} (admin-only, {@code CATALOG_PUBLISH}) — authoring
         * Service Catalog portfolios/products themselves, backing {@code
         * ServiceCatalogProductPublisher}. Distinct from {@link #SC_PROVISION}: that capability
         * only lets a caller provision an already-published product, this one lets Manager
         * publish new ones. Deliberately separate rather than folded together, matching {@code
         * CatalogPublishController}'s own javadoc on why {@code CATALOG_PUBLISH} is a materially
         * more consequential policy than {@code DEPLOY_CATALOG}.
         */
        SC_PUBLISH(
            "servicecatalog:ListPortfolios",
            "servicecatalog:CreatePortfolio",
            "servicecatalog:CreateProduct",
            "servicecatalog:CreateProvisioningArtifact",
            "servicecatalog:AssociateProductWithPortfolio",
            "servicecatalog:CreateConstraint",
            "servicecatalog:SearchProductsAsAdmin",
            "servicecatalog:DescribeProductAsAdmin",
            // A freshly created provisioning artifact starts CREATING, not AVAILABLE --
            // ServiceCatalogProductPublisher polls this action to wait for Service Catalog's own
            // template fetch-and-validate step to finish before handing the artifact back to a
            // caller that may provision it immediately (see CatalogDeployService#runProvision).
            "servicecatalog:DescribeProvisioningArtifact",
            // Not called directly anywhere in this codebase -- Service Catalog's own
            // CreateProduct/CreateProvisioningArtifact validates the submitted CloudFormation
            // template server-side, and that internal AWS-side call runs as the same caller
            // identity that invoked CreateProduct, not a service-linked role. Without this
            // grant, publishing fails AccessDenied on cloudformation:ValidateTemplate even with
            // every other SC_PUBLISH action already granted.
            "cloudformation:ValidateTemplate"),
        /**
         * Lets a cross-account connection's role verify its own effective permissions via {@code
         * iam:SimulatePrincipalPolicy} — this is how {@code AccountsController}'s "Validate
         * connection" surfaces a real least-privilege report (which of
         * {@code CrossAccountRoleTemplateFactory}'s granted actions
         * actually evaluate to Allow) instead of just proving {@code sts:AssumeRole} works.
         * Simulate-only — never executes anything, so this is safe to grant broadly. Connections
         * whose role predates this capability simply report "unable to verify" rather than
         * failing validation outright; see {@code StsAssumeRoleService#checkPermissions}.
         */
        SELF_PERMISSION_CHECK("iam:SimulatePrincipalPolicy"),
        /**
         * Backs {@code StackEventObservabilityService#enable}/{@code #disable} and the poller
         * that drains captured events ({@code StackEventPoller}) -- the real EventBridge rule +
         * SQS queue this feature provisions per pipeline (see that class's own javadoc). Manager-
         * self-only like {@link #COGNITO_USER_MANAGEMENT}, so it belongs in {@link
         * #operatorBaseline()} rather than a separately-gated capability: the feature's own
         * license entitlement check (not IAM) is what actually gates who can call it.
         */
        STACK_EVENT_OBSERVABILITY(
            "events:PutRule",
            "events:PutTargets",
            "events:RemoveTargets",
            "events:DeleteRule",
            "sqs:CreateQueue",
            "sqs:DeleteQueue",
            "sqs:GetQueueAttributes",
            "sqs:SetQueueAttributes",
            "sqs:ReceiveMessage",
            "sqs:DeleteMessage");

        private final List<String> iamActions;

        Capability(String... iamActions) {
            this.iamActions = List.of(iamActions);
        }

        public List<String> iamActions() {
            return iamActions;
        }
    }

    /** Baseline operator capabilities — inventory/delete + RDS/ECS operator paths. */
    public static Set<Capability> operatorBaseline() {
        return EnumSet.of(
            Capability.CFN_INVENTORY,
            Capability.CFN_DELETE,
            Capability.ECS_DESCRIBE,
            Capability.ECS_UPDATE_SERVICE,
            Capability.RDS_DESCRIBE,
            Capability.RDS_SNAPSHOT,
            Capability.RDS_RESTORE,
            Capability.RDS_ENGINE_UPGRADE,
            Capability.COGNITO_USER_MANAGEMENT,
            Capability.LOGS_READ,
            Capability.STACK_EVENT_OBSERVABILITY);
    }

    /**
     * Direct-deploy capabilities — never included in {@link #operatorBaseline()}, always
     * attached separately (and conditionally, per-capability) by {@code ManagerOperatorIamSupport}
     * only when the deploying caller actually holds the matching {@code deploy:*}
     * {@code ManagerPolicyCatalog} policy.
     */
    public static Set<Capability> deployCapabilities() {
        return EnumSet.of(Capability.CFN_DEPLOY, Capability.SC_PROVISION, Capability.SC_PUBLISH);
    }

    public static Set<String> iamActions(Iterable<Capability> capabilities) {
        Set<String> actions = new LinkedHashSet<>();
        for (Capability capability : capabilities) {
            actions.addAll(capability.iamActions());
        }
        return Set.copyOf(actions);
    }

    public static Set<String> operatorBaselineIamActions() {
        return iamActions(operatorBaseline());
    }

    public static Capability parse(String id) {
        return Capability.valueOf(id.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isKnownIamAction(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(Capability.values())
            .flatMap(cap -> cap.iamActions().stream())
            .map(a -> a.toLowerCase(Locale.ROOT))
            .anyMatch(a -> a.equals(normalized));
    }
}
