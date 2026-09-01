package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.IAMProfile;

import java.util.List;
import java.util.Map;

/**
 * Permission matrix for the <b>operator provisioning</b> layer — the AWS actions CloudForge
 * Manager's own task role needs, acting as the calling principal, to actually create/manage the
 * infrastructure a {@code deploy:create} target application's synthesized CloudFormation template
 * describes (VPC, EFS, ALB, ECS cluster/service/task definition).
 *
 * <p><b>This is a different layer than {@link PermissionMatrix}.</b> {@code PermissionMatrix}
 * defines what a <i>deployed app's own task role</i> can do once it's running (pull its image,
 * read SSM params, put CloudWatch metrics). This class defines what <i>Manager's own role</i>
 * needs to bring that app's infrastructure into existence and tear it back down in the first
 * place — CloudFormation issues every one of these calls under Manager's identity, not the
 * deployed app's. Confirmed live: a stack's own workload permissions being perfectly correct
 * (which {@link PermissionMatrix} already ensures) says nothing about whether Manager was ever
 * allowed to create that stack's VPC/EFS/ALB/ECS resources at all.</p>
 *
 * <p>Tiered by the same {@link IAMProfile} enum {@link PermissionMatrix} uses, not a parallel
 * concept — MINIMAL is read-only (inventory/troubleshooting, no condition since Describe- and
 * List-family actions don't accept a Tags parameter to condition on), STANDARD is full lifecycle
 * for the
 * single Fargate+ALB+EFS shape every catalog app in this platform actually deploys today, and
 * EXTENDED adds the NAT/EIP/flow-log surface a private-with-egress network topology needs.
 * {@link ManagerOperatorIamSupport#deployStatements} currently bakes in EXTENDED unconditionally
 * for Manager's own task role, since there is no per-user AWS-level distinction yet — see the
 * design note below for where that's headed.</p>
 *
 * <p><b>Future direction, not built yet:</b> Manager already has a real, working per-user RBAC
 * policy catalog ({@code ManagerPolicyCatalog}, {@code manager_user_policy}) with a {@code
 * deploy:create} capability gating this exact feature today, and a real {@code sts:AssumeRole}
 * pathway ({@code AssumeRoleOperations}/{@code StsAssumeRoleService}) already built for
 * cross-account connections. The intended seam: instead of a {@code deploy:create} request
 * running directly under Manager's task role credentials, Manager assumes its own (or a
 * dedicated operator) role with a session policy scoped to the {@link IAMProfile} tier this
 * matrix says that request needs — so a Manager user without the {@code deploy:create} RBAC
 * capability can never reach AWS-level infrastructure-creation capability even if something
 * upstream misbehaves, because the session policy would never carry those actions to begin with.
 * This class is written so that seam only ever needs one source of truth on the AWS side — never
 * two independently-maintained action lists drifting apart.</p>
 */
public final class OperatorProvisioningPermissionMatrix {
    private OperatorProvisioningPermissionMatrix() {
    }

    /**
     * {@link com.cloudforgeci.api.network.VpcFactory}'s {@code Vpc} L2 construct — every
     * underlying EC2 networking resource type it can synthesize depending on subnet
     * configuration (VpcFactory always creates public+private-with-egress subnet pairs across
     * 2 AZs for every app in this catalog, hence NAT/EIP/route-table actions live at STANDARD,
     * not EXTENDED, despite the class javadoc's general EXTENDED-adds-NAT framing above -- see
     * {@link #EXTENDED_ONLY_VPC_PERMISSIONS} for what genuinely is EXTENDED-only: flow logs and
     * custom network ACLs, neither of which VpcFactory enables by default).
     */
    public static final Map<IAMProfile, List<String>> VPC_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "ec2:DescribeVpcs",
            "ec2:DescribeSubnets",
            "ec2:DescribeRouteTables",
            "ec2:DescribeInternetGateways",
            "ec2:DescribeNatGateways",
            "ec2:DescribeSecurityGroups",
            "ec2:DescribeAvailabilityZones",
            "ec2:DescribeAddresses",
            "ec2:DescribeTags"
        ),
        IAMProfile.STANDARD, List.of(
            "ec2:CreateVpc",
            "ec2:DeleteVpc",
            "ec2:ModifyVpcAttribute",
            "ec2:CreateSubnet",
            "ec2:DeleteSubnet",
            "ec2:ModifySubnetAttribute",
            "ec2:CreateInternetGateway",
            "ec2:DeleteInternetGateway",
            "ec2:AttachInternetGateway",
            "ec2:DetachInternetGateway",
            "ec2:CreateRouteTable",
            "ec2:DeleteRouteTable",
            "ec2:CreateRoute",
            "ec2:DeleteRoute",
            "ec2:AssociateRouteTable",
            "ec2:DisassociateRouteTable",
            "ec2:CreateNatGateway",
            "ec2:DeleteNatGateway",
            "ec2:AllocateAddress",
            "ec2:ReleaseAddress",
            "ec2:AssociateAddress",
            "ec2:DisassociateAddress",
            "ec2:CreateSecurityGroup",
            "ec2:DeleteSecurityGroup",
            "ec2:AuthorizeSecurityGroupIngress",
            "ec2:AuthorizeSecurityGroupEgress",
            "ec2:RevokeSecurityGroupIngress",
            "ec2:RevokeSecurityGroupEgress",
            "ec2:CreateTags",
            "ec2:DeleteTags"
        )
    );

    /** Flow logs and custom network ACLs -- not part of VpcFactory's default topology, only
     *  relevant for a compliance-driven or hardened network profile. */
    public static final List<String> EXTENDED_ONLY_VPC_PERMISSIONS = List.of(
        "ec2:CreateFlowLogs",
        "ec2:DeleteFlowLogs",
        "ec2:DescribeFlowLogs",
        "ec2:CreateNetworkAcl",
        "ec2:DeleteNetworkAcl",
        "ec2:CreateNetworkAclEntry",
        "ec2:DeleteNetworkAclEntry",
        "ec2:ReplaceNetworkAclAssociation",
        "ec2:DescribeNetworkAcls"
    );

    /** {@link com.cloudforgeci.api.storage.EfsFactory}'s {@code FileSystem}/{@code AccessPoint}
     *  L2 constructs. */
    public static final Map<IAMProfile, List<String>> EFS_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "elasticfilesystem:DescribeFileSystems",
            "elasticfilesystem:DescribeAccessPoints",
            "elasticfilesystem:DescribeMountTargets",
            "elasticfilesystem:DescribeMountTargetSecurityGroups",
            "elasticfilesystem:DescribeLifecycleConfiguration",
            "elasticfilesystem:DescribeBackupPolicy",
            // Confirmed live: CloudFormation checks for an existing replication configuration
            // as part of DeleteFileSystem's own preconditions, even on a file system that was
            // never replicated -- without this, deleting an EFS file system fails outright.
            "elasticfilesystem:DescribeReplicationConfigurations"
        ),
        IAMProfile.STANDARD, List.of(
            "elasticfilesystem:CreateFileSystem",
            "elasticfilesystem:DeleteFileSystem",
            "elasticfilesystem:UpdateFileSystem",
            "elasticfilesystem:CreateAccessPoint",
            "elasticfilesystem:DeleteAccessPoint",
            "elasticfilesystem:CreateMountTarget",
            "elasticfilesystem:DeleteMountTarget",
            "elasticfilesystem:ModifyMountTargetSecurityGroups",
            "elasticfilesystem:PutLifecycleConfiguration",
            "elasticfilesystem:PutBackupPolicy",
            "elasticfilesystem:TagResource",
            "elasticfilesystem:UntagResource",
            "elasticfilesystem:ListTagsForResource"
        )
    );

    /** {@link com.cloudforgeci.api.ingress.AlbFactory}'s {@code ApplicationLoadBalancer}/
     *  {@code ApplicationTargetGroup} L2 constructs. */
    public static final Map<IAMProfile, List<String>> ALB_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "elasticloadbalancing:DescribeLoadBalancers",
            "elasticloadbalancing:DescribeLoadBalancerAttributes",
            "elasticloadbalancing:DescribeTargetGroups",
            "elasticloadbalancing:DescribeTargetGroupAttributes",
            "elasticloadbalancing:DescribeTargetHealth",
            "elasticloadbalancing:DescribeListeners",
            "elasticloadbalancing:DescribeRules",
            "elasticloadbalancing:DescribeTags"
        ),
        IAMProfile.STANDARD, List.of(
            "elasticloadbalancing:CreateLoadBalancer",
            "elasticloadbalancing:DeleteLoadBalancer",
            "elasticloadbalancing:ModifyLoadBalancerAttributes",
            "elasticloadbalancing:SetSecurityGroups",
            "elasticloadbalancing:SetSubnets",
            "elasticloadbalancing:SetIpAddressType",
            "elasticloadbalancing:CreateTargetGroup",
            "elasticloadbalancing:DeleteTargetGroup",
            "elasticloadbalancing:ModifyTargetGroup",
            "elasticloadbalancing:ModifyTargetGroupAttributes",
            "elasticloadbalancing:RegisterTargets",
            "elasticloadbalancing:DeregisterTargets",
            "elasticloadbalancing:CreateListener",
            "elasticloadbalancing:DeleteListener",
            "elasticloadbalancing:ModifyListener",
            "elasticloadbalancing:CreateRule",
            "elasticloadbalancing:DeleteRule",
            "elasticloadbalancing:ModifyRule",
            "elasticloadbalancing:AddTags",
            "elasticloadbalancing:RemoveTags"
        )
    );

    /** {@link com.cloudforgeci.api.compute.FargateFactory}'s {@code Cluster}/
     *  {@code FargateService}/{@code FargateTaskDefinition} L2 constructs -- registering and
     *  running the task definition, not the workload permissions the running task itself needs
     *  (that's {@link PermissionMatrix}). */
    public static final Map<IAMProfile, List<String>> ECS_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "ecs:DescribeClusters",
            "ecs:DescribeServices",
            "ecs:DescribeTaskDefinition",
            "ecs:ListClusters",
            "ecs:ListServices",
            "ecs:ListTagsForResource"
        ),
        IAMProfile.STANDARD, List.of(
            "ecs:CreateCluster",
            "ecs:DeleteCluster",
            "ecs:PutClusterCapacityProviders",
            "ecs:CreateService",
            "ecs:DeleteService",
            "ecs:UpdateService",
            "ecs:RegisterTaskDefinition",
            "ecs:DeregisterTaskDefinition",
            "ecs:TagResource",
            "ecs:UntagResource"
        )
    );

    /** Manager creating the target app's own {@code AWS::Logs::LogGroup} as part of its
     *  infrastructure -- separate from {@link PermissionMatrix#CORE_PERMISSIONS}, which is what
     *  the deployed app's own task role needs to write into that log group at runtime. */
    public static final Map<IAMProfile, List<String>> LOGS_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "logs:DescribeLogGroups",
            "logs:ListTagsForResource"
        ),
        IAMProfile.STANDARD, List.of(
            "logs:CreateLogGroup",
            "logs:DeleteLogGroup",
            "logs:PutRetentionPolicy",
            "logs:TagResource",
            "logs:UntagResource"
        )
    );

    /**
     * {@link com.cloudforgeci.api.database.RdsFactory}'s {@code DatabaseInstance}/{@code
     * ParameterGroup}/{@code SubnetGroup} L2 constructs, plus the KMS key and Secrets Manager
     * secret every encrypted instance provisions alongside it -- grouped together, not split into
     * three separate maps, since {@code RdsFactory} always creates the three together for any app
     * whose {@code DatabaseSpec} requests a database (there is no "RDS without its own secret and
     * key" shape in this codebase to scope more narrowly than that). Confirmed live on a genuinely
     * fresh database-backed app deploy: {@code kms:CreateKey}'s own tagging step failed the same
     * "UnauthorizedTaggingOperation" way {@code iamRoleCreate} did (see that class's own comment --
     * this is CloudFormation's error-classification label for <i>any</i> denied create-with-tags
     * call, not proof of a tag-condition mismatch specifically), and
     * {@code rds:DescribeDBParameterGroups}/{@code secretsmanager:GetRandomPassword} were plain,
     * simple missing grants -- this whole category simply didn't exist before that deploy.
     */
    public static final Map<IAMProfile, List<String>> DATABASE_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "rds:DescribeDBInstances",
            "rds:DescribeDBSubnetGroups",
            "rds:DescribeDBParameterGroups",
            "rds:DescribeDBParameters",
            // Confirmed live: CloudFormation resolves the engine's own default parameter values
            // before applying ParameterGroup's custom overrides, even when every override is
            // explicit -- without this, DBParameterGroup creation fails outright.
            "rds:DescribeEngineDefaultParameters",
            "rds:ListTagsForResource",
            "kms:DescribeKey",
            "kms:ListAliases",
            "kms:GetKeyPolicy",
            "kms:GetKeyRotationStatus",
            "secretsmanager:DescribeSecret",
            "secretsmanager:ListSecrets"
        ),
        IAMProfile.STANDARD, List.of(
            "rds:CreateDBInstance",
            "rds:DeleteDBInstance",
            "rds:ModifyDBInstance",
            "rds:AddTagsToResource",
            "rds:RemoveTagsFromResource",
            "rds:CreateDBSubnetGroup",
            "rds:DeleteDBSubnetGroup",
            "rds:ModifyDBSubnetGroup",
            "rds:CreateDBParameterGroup",
            "rds:DeleteDBParameterGroup",
            "rds:ModifyDBParameterGroup",
            "rds:ResetDBParameterGroup",
            // KMS key lifecycle -- Key.Builder always enables key rotation and a DESTROY removal
            // policy in RdsFactory, so rotation/deletion/policy actions are needed alongside
            // create, not just CreateKey itself.
            "kms:CreateKey",
            "kms:CreateAlias",
            "kms:DeleteAlias",
            "kms:EnableKeyRotation",
            "kms:PutKeyPolicy",
            "kms:TagResource",
            "kms:UntagResource",
            "kms:ScheduleKeyDeletion",
            "kms:CancelKeyDeletion",
            // Secrets Manager -- Secret.Builder's generateSecretString is what actually calls
            // GetRandomPassword server-side; RemovalPolicy.DESTROY means a real DeleteSecret (not
            // just a scheduled deletion) has to work too.
            "secretsmanager:CreateSecret",
            "secretsmanager:DeleteSecret",
            "secretsmanager:GetRandomPassword",
            "secretsmanager:GetSecretValue",
            "secretsmanager:PutSecretValue",
            "secretsmanager:UpdateSecret",
            "secretsmanager:TagResource",
            "secretsmanager:UntagResource"
        )
    );

    /**
     * {@link com.cloudforgeci.api.observability.ComplianceFactory}/{@link
     * com.cloudforgeci.api.observability.GuardDutyFactory}/{@link
     * com.cloudforgeci.api.observability.WafFactory} -- deliberately its own dimension, not a
     * fourth {@link IAMProfile} tier, since compliance mode is an independent boolean toggle on
     * {@code DeploymentConfig} ({@code complianceMode}/{@code awsConfigEnabled}/{@code
     * guardDutyEnabled}), orthogonal to which IAMProfile tier an app's own workload role runs
     * under. Confirmed live in this project before (production + compliance enforcement is one
     * of the most fragile combinations here): AWS Config and GuardDuty are both account-level
     * <i>singletons</i>, and enabling either for the very first time in an account requires
     * {@code iam:CreateServiceLinkedRole} for that service's own service-linked role -- a step
     * with no equivalent in the VPC/EFS/ALB/ECS categories above, easy to miss because it's only
     * needed exactly once per account, not once per deployment.
     */
    public static final Map<IAMProfile, List<String>> COMPLIANCE_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "config:DescribeConfigurationRecorders",
            "config:DescribeConfigurationRecorderStatus",
            "config:DescribeConfigRules",
            "config:DescribeConformancePacks",
            "config:DescribeDeliveryChannels",
            "config:DescribeRemediationConfigurations",
            "config:GetComplianceDetailsByConfigRule",
            "guardduty:GetDetector",
            "guardduty:ListDetectors",
            "wafv2:GetWebACL",
            "wafv2:ListWebACLs",
            "wafv2:GetLoggingConfiguration",
            "ssm:DescribeDocument",
            "ssm:GetDocument",
            "ssm:ListDocuments",
            "auditmanager:GetAssessment"
        ),
        IAMProfile.STANDARD, List.of(
            "config:PutConfigurationRecorder",
            "config:DeleteConfigurationRecorder",
            "config:StartConfigurationRecorder",
            "config:StopConfigurationRecorder",
            "config:PutDeliveryChannel",
            "config:DeleteDeliveryChannel",
            "config:PutConfigRule",
            "config:DeleteConfigRule",
            "config:PutConformancePack",
            "config:DeleteConformancePack",
            "config:PutRemediationConfigurations",
            "config:DeleteRemediationConfiguration",
            "config:TagResource",
            "config:UntagResource",
            "guardduty:CreateDetector",
            "guardduty:DeleteDetector",
            "guardduty:UpdateDetector",
            "guardduty:TagResource",
            "guardduty:UntagResource",
            "wafv2:CreateWebACL",
            "wafv2:DeleteWebACL",
            "wafv2:UpdateWebACL",
            "wafv2:PutLoggingConfiguration",
            "wafv2:DeleteLoggingConfiguration",
            "wafv2:AssociateWebACL",
            "wafv2:DisassociateWebACL",
            "wafv2:TagResource",
            "wafv2:UntagResource",
            "ssm:CreateDocument",
            "ssm:DeleteDocument",
            "ssm:AddTagsToResource",
            "auditmanager:CreateAssessment",
            "auditmanager:DeleteAssessment",
            "auditmanager:UpdateAssessment",
            "auditmanager:TagResource",
            // Account-level singleton services (Config, GuardDuty) need their own service-linked
            // role created the first time either is ever enabled in the account -- restricted to
            // exactly those two AWS service names, not a bare iam:CreateServiceLinkedRole grant.
            "iam:CreateServiceLinkedRole",
            "iam:GetServiceLinkedRoleDeletionStatus"
        )
    );

    /**
     * All actions needed at or below the given tier, across every provisioning category, for a
     * single flat action list -- mirrors {@link PermissionMatrix#getRequiredPermissions}'
     * additive-tier shape (STANDARD includes MINIMAL, EXTENDED includes STANDARD). {@code
     * includeCompliance} pulls in {@link #COMPLIANCE_PERMISSIONS} at the same tier, kept as a
     * separate parameter rather than a fourth tier value for the reason documented on that map.
     */
    public static List<String> getRequiredPermissions(IAMProfile tier, boolean includeCompliance) {
        List<String> actions = new java.util.ArrayList<>();
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.MINIMAL));
        }
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.STANDARD));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.STANDARD));
        }
        if (tier == IAMProfile.STANDARD) {
            return List.copyOf(actions);
        }
        actions.addAll(EXTENDED_ONLY_VPC_PERMISSIONS);
        return List.copyOf(actions);
    }

    /**
     * {@link #getRequiredPermissions}'s full action list, split roughly in half by measured JSON
     * byte size rather than by category count -- {@link #VPC_PERMISSIONS}/{@link #ALB_PERMISSIONS}/
     * {@link #EFS_PERMISSIONS} ("network") on one side, {@link #ECS_PERMISSIONS}/{@link
     * #LOGS_PERMISSIONS}/{@link #DATABASE_PERMISSIONS}/{@link #COMPLIANCE_PERMISSIONS}
     * ("compute/data") on the other. Exists because of a real, hard AWS ceiling this class's
     * single flat list ran into: an IAM role's <i>combined</i> inline-policy size across every
     * inline policy document it carries is capped at 10,240 bytes total -- not a separate budget
     * per document, confirmed live the first time this class's flat list (212 actions, ~6.3KB on
     * its own) was moved into its own named inline policy and the role's default policy plus this
     * one together still exceeded the combined cap. {@link ManagerOperatorIamSupport} attaches
     * each half as its own customer-managed policy instead of inline specifically to sidestep
     * that combined-inline ceiling (a managed policy's size budget is independent of it), so the
     * split point here only needs to keep each half comfortably under a managed policy's own
     * (smaller, ~6,144-byte default) size limit -- confirmed live via the real synthesized byte
     * counts per AWS service prefix, not guessed.
     */
    public static List<String> getNetworkPermissions(IAMProfile tier) {
        List<String> actions = new java.util.ArrayList<>();
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.STANDARD));
        if (tier == IAMProfile.STANDARD) {
            return List.copyOf(actions);
        }
        actions.addAll(EXTENDED_ONLY_VPC_PERMISSIONS);
        return List.copyOf(actions);
    }

    /** See {@link #getNetworkPermissions} -- the other half of the same split. */
    public static List<String> getComputeAndDataPermissions(IAMProfile tier, boolean includeCompliance) {
        List<String> actions = new java.util.ArrayList<>();
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.MINIMAL));
        }
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.STANDARD));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.STANDARD));
        }
        return List.copyOf(actions);
    }
}
