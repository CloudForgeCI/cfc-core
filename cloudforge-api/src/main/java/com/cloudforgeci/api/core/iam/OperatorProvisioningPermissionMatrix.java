package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.IAMProfile;

import java.util.List;
import java.util.Map;

/**
 * Permission matrix for the <b>operator provisioning</b> layer: the AWS actions CloudForge
 * Manager's task role needs, as the calling principal, to create and manage the infrastructure a
 * {@code deploy:create} target application's CloudFormation template describes (VPC, EFS, ALB,
 * ECS cluster/service/task definition).
 *
 * <p><b>This is a different layer than {@link PermissionMatrix}.</b> {@code PermissionMatrix}
 * defines what a <i>deployed app's task role</i> can do at runtime (pull its image, read SSM
 * parameters, put CloudWatch metrics). This class defines what <i>Manager's role</i> needs to
 * create and delete that app's infrastructure, because CloudFormation issues those calls under
 * Manager's identity.</p>
 *
 * <p>Tiered by the same {@link IAMProfile} enum as {@link PermissionMatrix}: MINIMAL is
 * read-only (inventory and troubleshooting; Describe/List actions accept no Tags parameter to
 * condition on), STANDARD is the full lifecycle for the Fargate + ALB + EFS shape catalog apps
 * deploy, and EXTENDED adds flow logs and custom network ACLs.
 * {@link ManagerOperatorIamSupport#deployStatements} currently uses EXTENDED for Manager's task
 * role because there is no per-user AWS-level distinction yet.</p>
 *
 * <p><b>Planned direction:</b> rather than running {@code deploy:create} requests directly under
 * Manager's task role, Manager would assume an operator role with a session policy scoped to the
 * {@link IAMProfile} tier a request needs, using its existing RBAC catalog
 * ({@code ManagerPolicyCatalog}) and {@code sts:AssumeRole} support. Users without the
 * {@code deploy:create} capability could then never obtain infrastructure-creation permissions.
 * This class is intended to remain the single source of truth for those action lists.</p>
 */
public final class OperatorProvisioningPermissionMatrix {
    private OperatorProvisioningPermissionMatrix() {
    }

    /**
     * {@link com.cloudforgeci.api.network.VpcFactory}'s {@code Vpc} L2 construct: every EC2
     * networking resource type it can synthesize. VpcFactory always creates public and
     * private-with-egress subnet pairs across two AZs, so NAT/EIP/route-table actions are at
     * STANDARD. {@link #EXTENDED_ONLY_VPC_PERMISSIONS} holds the EXTENDED-only actions (flow logs
     * and custom network ACLs, which VpcFactory does not enable by default).
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
            "ec2:DescribeTags",
            // EFS mount targets are ENIs: CreateMountTarget/DeleteMountTarget call these EC2 APIs
            // with the caller's identity (documented AWS requirement). When missing, EFS returns a
            // generic 403 that does not name the EC2 action.
            "ec2:DescribeNetworkInterfaces"
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
            "ec2:DeleteTags",
            // Create/delete/modify half of the EFS mount-target ENI requirement (see MINIMAL above).
            "ec2:CreateNetworkInterface",
            "ec2:DeleteNetworkInterface",
            "ec2:ModifyNetworkInterfaceAttribute"
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
            // CloudFormation checks for a replication configuration before DeleteFileSystem, even
            // for file systems that were never replicated; deletion fails without this.
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
            "elasticloadbalancing:DescribeTags",
            // ELB calls this EC2 API with the caller's identity during CreateLoadBalancer (a
            // documented AWS requirement). When missing, ALB creation fails with
            // "ec2:DescribeAccountAttributes ... (Service: ElasticLoadBalancingV2 ...)".
            "ec2:DescribeAccountAttributes"
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

    /**
     * {@link com.cloudforgeci.api.core.runtime.FargateRuntimeConfiguration}'s public ACM {@code
     * Certificate} L2 construct (SSL with a custom domain, DNS-validated against the app's Route 53
     * hosted zone). CloudFormation's {@code AWS::CertificateManager::Certificate} handler calls
     * these actions with the caller's identity. Grouped with the network category
     * ({@link #getNetworkPermissions}) because the certificate backs the ALB's HTTPS listener.
     * The private-CA fallback ({@code acm-pca:*}) is a separate category; see
     * {@link #ACM_PCA_PERMISSIONS}.
     */
    public static final Map<IAMProfile, List<String>> ACM_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "acm:DescribeCertificate",
            "acm:ListCertificates",
            "acm:ListTagsForCertificate"
        ),
        IAMProfile.STANDARD, List.of(
            "acm:RequestCertificate",
            "acm:DeleteCertificate",
            "acm:AddTagsToCertificate",
            "acm:RemoveTagsFromCertificate"
        )
    );

    /**
     * {@link com.cloudforgeci.api.network.DomainFactory}'s {@code HostedZone} L2 construct.
     * MINIMAL covers the {@code ListHostedZonesByName} lookup in
     * {@code CloudForgeSynthesizer#seedHostedZoneContext} (which runs under this operator role)
     * and the read-only calls {@code HostedZone.fromLookup} needs; STANDARD covers the hosted-zone
     * lifecycle for {@code createZone=true} and the record sets that point at the app's ALB or
     * CloudFront distribution.
     */
    public static final Map<IAMProfile, List<String>> ROUTE53_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "route53:ListHostedZonesByName",
            "route53:GetHostedZone",
            "route53:ListResourceRecordSets",
            "route53:ListTagsForResource"
        ),
        IAMProfile.STANDARD, List.of(
            "route53:CreateHostedZone",
            "route53:DeleteHostedZone",
            "route53:ChangeTagsForResource",
            "route53:ChangeResourceRecordSets",
            "route53:GetChange"
        )
    );

    /** {@link com.cloudforgeci.api.core.runtime.FargateRuntimeConfiguration}'s private-CA
     *  fallback ({@code CfnCertificateAuthority}/{@code CfnCertificateAuthorityActivation}), used
     *  when SSL is enabled without a custom domain. Separate from {@link #ACM_PERMISSIONS} because
     *  {@code acm-pca:*} is a distinct action prefix. */
    public static final Map<IAMProfile, List<String>> ACM_PCA_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "acm-pca:DescribeCertificateAuthority",
            "acm-pca:GetCertificateAuthorityCertificate",
            "acm-pca:GetCertificate",
            "acm-pca:ListTags"
        ),
        IAMProfile.STANDARD, List.of(
            "acm-pca:CreateCertificateAuthority",
            "acm-pca:DeleteCertificateAuthority",
            "acm-pca:UpdateCertificateAuthority",
            "acm-pca:IssueCertificate",
            "acm-pca:ImportCertificateAuthorityCertificate",
            "acm-pca:TagCertificateAuthority",
            "acm-pca:UntagCertificateAuthority"
        )
    );

    /** {@link com.cloudforgeci.api.core.topology.CmsCdnConfiguration}/{@link
     *  com.cloudforgeci.api.core.topology.S3WebsiteTopologyConfiguration}'s {@code Distribution}
     *  L2 construct — CDN in front of an app's ALB origin or an S3-hosted static site. */
    public static final Map<IAMProfile, List<String>> CLOUDFRONT_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "cloudfront:GetDistribution",
            "cloudfront:GetDistributionConfig",
            "cloudfront:ListDistributions",
            "cloudfront:ListTagsForResource",
            // OriginAccessControl (CachePolicy/origin config needs this to describe an existing
            // S3-origin OAC) — the read half of the create/manage pair below.
            "cloudfront:GetOriginAccessControl",
            "cloudfront:ListOriginAccessControls"
        ),
        IAMProfile.STANDARD, List.of(
            "cloudfront:CreateDistribution",
            "cloudfront:UpdateDistribution",
            "cloudfront:DeleteDistribution",
            "cloudfront:TagResource",
            "cloudfront:UntagResource",
            "cloudfront:CreateOriginAccessControl",
            "cloudfront:DeleteOriginAccessControl",
            "cloudfront:UpdateOriginAccessControl",
            "cloudfront:CreateInvalidation"
        )
    );

    /** {@link com.cloudforgeci.api.core.topology.S3WebsiteTopologyConfiguration}'s {@code Bucket}
     *  L2 construct (a static-site origin) — distinct from {@code ManagerOperatorIamSupport}'s
     *  own S3 grants, which are scoped to Manager's own fixed-prefix template/CDK-asset buckets,
     *  never an arbitrary bucket a deployed app's own template declares. */
    public static final Map<IAMProfile, List<String>> S3_APP_BUCKET_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "s3:GetBucketLocation",
            "s3:GetBucketPolicy",
            "s3:GetEncryptionConfiguration",
            "s3:GetBucketPublicAccessBlock",
            "s3:ListBucket"
        ),
        IAMProfile.STANDARD, List.of(
            "s3:CreateBucket",
            "s3:DeleteBucket",
            "s3:PutBucketPolicy",
            "s3:DeleteBucketPolicy",
            "s3:PutEncryptionConfiguration",
            "s3:PutBucketPublicAccessBlock",
            "s3:PutBucketOwnershipControls",
            "s3:PutBucketAcl",
            "s3:PutObject",
            "s3:DeleteObject",
            "s3:PutBucketTagging"
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

    /** Creating the target app's {@code AWS::Logs::LogGroup}. Separate from
     *  {@link PermissionMatrix#CORE_PERMISSIONS}, which covers the deployed app's task role writing
     *  to that log group at runtime. */
    public static final Map<IAMProfile, List<String>> LOGS_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "logs:DescribeLogGroups",
            "logs:ListTagsForResource",
            // Reads log events for Manager's Logs tab (CloudWatchLogsStackOperations
            // #fetchLogEvents); the other actions here only describe or manage the log group.
            "logs:FilterLogEvents"
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
     * key" shape in this codebase to scope more narrowly than that). {@code kms:CreateKey}'s own
     * tagging step fails with "UnauthorizedTaggingOperation" without {@code kms:TagResource}
     * granted alongside it (see {@code ManagerOperatorIamSupport}'s {@code iamRoleCreate} for the
     * same CloudFormation error-classification label on a different action -- it denotes any
     * denied create-with-tags call, not a tag-condition mismatch specifically).
     */
    public static final Map<IAMProfile, List<String>> DATABASE_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "rds:DescribeDBInstances",
            "rds:DescribeDBSubnetGroups",
            "rds:DescribeDBParameterGroups",
            "rds:DescribeDBParameters",
            // CloudFormation reads the engine's default parameter values before applying
            // ParameterGroup overrides; DBParameterGroup creation fails without this.
            "rds:DescribeEngineDefaultParameters",
            "rds:ListTagsForResource",
            "kms:DescribeKey",
            "kms:ListAliases",
            "kms:GetKeyPolicy",
            "kms:GetKeyRotationStatus",
            "secretsmanager:DescribeSecret",
            "secretsmanager:ListSecrets",
            // RdsFactory's own StringParameter (a distroless app with no shell can't do variable
            // substitution, so its datasource URL -- including dynamic references CloudFormation
            // resolves -- is stored here instead, see ContainerFactory's mattermost-* case) and
            // SharedResourceRegistry's own general-purpose parameter storage. Distinct from
            // COMPLIANCE_PERMISSIONS' ssm:*Document actions (SSM Documents, a different resource
            // type under the same service prefix) and ManagerOperatorIamSupport's own
            // ssm:GetParameters grant (fixed to the CDK bootstrap-version parameter specifically).
            "ssm:GetParameter",
            "ssm:GetParameters",
            "ssm:DescribeParameters",
            "ssm:ListTagsForResource"
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
            // Secrets Manager: generateSecretString calls GetRandomPassword server-side, and
            // RemovalPolicy.DESTROY requires DeleteSecret.
            "secretsmanager:CreateSecret",
            "secretsmanager:DeleteSecret",
            "secretsmanager:GetRandomPassword",
            "secretsmanager:GetSecretValue",
            "secretsmanager:PutSecretValue",
            "secretsmanager:UpdateSecret",
            "secretsmanager:TagResource",
            "secretsmanager:UntagResource",
            "ssm:PutParameter",
            "ssm:DeleteParameter",
            "ssm:AddTagsToResource",
            "ssm:RemoveTagsFromResource"
        )
    );

    /**
     * {@link com.cloudforgeci.api.observability.ComplianceFactory}/{@link
     * com.cloudforgeci.api.observability.GuardDutyFactory}/{@link
     * com.cloudforgeci.api.observability.WafFactory} -- deliberately its own dimension, not a
     * fourth {@link IAMProfile} tier, since compliance mode is an independent boolean toggle on
     * {@code DeploymentConfig} ({@code complianceMode}/{@code awsConfigEnabled}/{@code
     * guardDutyEnabled}), orthogonal to which IAMProfile tier an app's own workload role runs
     * under. AWS Config and GuardDuty are both account-level <i>singletons</i>, and enabling
     * either for the very first time in an account requires
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
            "auditmanager:GetAssessment",
            // ComplianceFactory's own CloudTrail Trail -- same "compliance mode toggle" grouping
            // as Config/GuardDuty/WAF above, not a category of its own.
            "cloudtrail:GetTrailStatus",
            "cloudtrail:GetEventSelectors",
            "cloudtrail:ListTags"
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
            "cloudtrail:CreateTrail",
            "cloudtrail:DeleteTrail",
            "cloudtrail:UpdateTrail",
            "cloudtrail:PutEventSelectors",
            "cloudtrail:StartLogging",
            "cloudtrail:StopLogging",
            "cloudtrail:AddTags",
            "cloudtrail:RemoveTags",
            // Account-level singleton services (Config, GuardDuty) need their own service-linked
            // role created the first time either is ever enabled in the account -- restricted to
            // exactly those two AWS service names, not a bare iam:CreateServiceLinkedRole grant.
            "iam:CreateServiceLinkedRole",
            "iam:GetServiceLinkedRoleDeletionStatus"
        )
    );

    /** {@link com.cloudforgeci.api.scaling.ScalingFactory}'s Fargate service {@code
     *  scaleOnCpuUtilization}/{@code EnableScalingProps} and {@link
     *  com.cloudforgeci.api.compute.Ec2Factory}'s {@code AutoScalingGroup#scaleOnCpuUtilization} --
     *  two distinct AWS services (Application Auto Scaling registers the ECS service as a
     *  scalable target; EC2 Auto Scaling owns the ASG directly), kept as one category rather than
     *  two small ones. */
    public static final Map<IAMProfile, List<String>> SCALING_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "application-autoscaling:DescribeScalableTargets",
            "application-autoscaling:DescribeScalingPolicies",
            "autoscaling:DescribeAutoScalingGroups",
            "autoscaling:DescribeScalingActivities",
            "autoscaling:DescribePolicies"
        ),
        IAMProfile.STANDARD, List.of(
            "application-autoscaling:RegisterScalableTarget",
            "application-autoscaling:DeregisterScalableTarget",
            "application-autoscaling:PutScalingPolicy",
            "application-autoscaling:DeleteScalingPolicy",
            "autoscaling:CreateAutoScalingGroup",
            "autoscaling:DeleteAutoScalingGroup",
            "autoscaling:UpdateAutoScalingGroup",
            "autoscaling:PutScalingPolicy",
            "autoscaling:DeletePolicy",
            "autoscaling:CreateOrUpdateTags",
            "autoscaling:DeleteTags"
        )
    );

    /** {@link com.cloudforgeci.api.security.CognitoAuthenticationFactory}/{@link
     *  com.cloudforgeci.api.security.CognitoSamlFactory}'s {@code UserPool}/{@code
     *  UserPoolClient}/{@code UserPoolDomain} L2 constructs (application-oidc's own identity
     *  provider) -- the {@code AwsCustomResource} calls both classes also make (SAML IdP config,
     *  client-secret retrieval) carry their own dedicated, narrowly-scoped IAM policy per AWS CDK's
     *  own custom-resource convention, so those don't need anything added here. */
    public static final Map<IAMProfile, List<String>> COGNITO_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "cognito-idp:DescribeUserPool",
            "cognito-idp:DescribeUserPoolClient",
            "cognito-idp:DescribeUserPoolDomain",
            "cognito-idp:DescribeIdentityProvider",
            "cognito-idp:GetUserPoolMfaConfig",
            "cognito-idp:ListTagsForResource"
        ),
        IAMProfile.STANDARD, List.of(
            "cognito-idp:CreateUserPool",
            "cognito-idp:DeleteUserPool",
            "cognito-idp:UpdateUserPool",
            "cognito-idp:CreateUserPoolClient",
            "cognito-idp:DeleteUserPoolClient",
            "cognito-idp:UpdateUserPoolClient",
            "cognito-idp:CreateUserPoolDomain",
            "cognito-idp:DeleteUserPoolDomain",
            "cognito-idp:UpdateUserPoolDomain",
            "cognito-idp:CreateIdentityProvider",
            "cognito-idp:DeleteIdentityProvider",
            "cognito-idp:UpdateIdentityProvider",
            "cognito-idp:SetUserPoolMfaConfig",
            "cognito-idp:CreateGroup",
            "cognito-idp:DeleteGroup",
            "cognito-idp:TagResource",
            "cognito-idp:UntagResource"
        )
    );

    /** {@link com.cloudforgeci.api.observability.AlarmFactory}/{@link
     *  com.cloudforgeci.api.observability.SecurityMonitoringFactory}'s {@code Alarm}/{@code
     *  Metric}/{@code Topic} constructs -- CloudWatch alarms and their SNS notification target,
     *  one category since neither is useful without the other here (every alarm this platform
     *  creates wires an {@code SnsAction}). Distinct from {@link #LOGS_PERMISSIONS}: CloudWatch
     *  Logs and CloudWatch metrics/alarms are different action prefixes ({@code logs:*} vs
     *  {@code cloudwatch:*}) despite sharing a console. */
    public static final Map<IAMProfile, List<String>> MONITORING_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "cloudwatch:DescribeAlarms",
            "cloudwatch:GetMetricData",
            "cloudwatch:GetMetricStatistics",
            "cloudwatch:ListTagsForResource",
            "sns:GetTopicAttributes",
            "sns:ListTagsForResource",
            "sns:ListSubscriptionsByTopic"
        ),
        IAMProfile.STANDARD, List.of(
            "cloudwatch:PutMetricAlarm",
            "cloudwatch:DeleteAlarms",
            "cloudwatch:TagResource",
            "cloudwatch:UntagResource",
            "sns:CreateTopic",
            "sns:DeleteTopic",
            "sns:SetTopicAttributes",
            "sns:Subscribe",
            "sns:Unsubscribe",
            "sns:TagResource",
            "sns:UntagResource"
        )
    );

    /** {@link com.cloudforgeci.api.storage.BackupFactory}'s {@code BackupVault}/{@code
     *  BackupPlan}/{@code BackupSelection} L2 constructs -- {@code BackupSelection} also creates
     *  its own IAM role, explicitly named (see {@code BackupFactory#createSelectionRole}) so
     *  {@code ManagerOperatorIamSupport#IAM_ROLE_MANAGE_RESOURCES}'s {@code -CfcBackupSelection}
     *  pattern can match it; that role's own create/manage permissions live there, not here. */
    public static final Map<IAMProfile, List<String>> BACKUP_PERMISSIONS = Map.of(
        IAMProfile.MINIMAL, List.of(
            "backup:DescribeBackupVault",
            "backup:GetBackupPlan",
            "backup:GetBackupSelection",
            "backup:ListTags"
        ),
        IAMProfile.STANDARD, List.of(
            "backup:CreateBackupVault",
            "backup:DeleteBackupVault",
            "backup:PutBackupVaultAccessPolicy",
            "backup:CreateBackupPlan",
            "backup:DeleteBackupPlan",
            "backup:UpdateBackupPlan",
            "backup:CreateBackupSelection",
            "backup:DeleteBackupSelection",
            "backup:TagResource",
            "backup:UntagResource"
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
        actions.addAll(ACM_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ACM_PCA_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ROUTE53_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(CLOUDFRONT_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(S3_APP_BUCKET_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(SCALING_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(COGNITO_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(MONITORING_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(BACKUP_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.MINIMAL));
        }
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ACM_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ACM_PCA_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ROUTE53_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(CLOUDFRONT_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(S3_APP_BUCKET_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(SCALING_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(COGNITO_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(MONITORING_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(BACKUP_PERMISSIONS.get(IAMProfile.STANDARD));
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
     * The "network" half of {@link #getRequiredPermissions}: VPC, EFS, ALB, ACM, ACM PCA,
     * Route 53, CloudFront, and app S3 bucket actions. {@link #getComputeAndDataPermissions}
     * returns the rest. The list is split by JSON size, not category count, because the full list
     * exceeds a role's combined 10,240-byte inline-policy limit alongside its other policies.
     * {@link ManagerOperatorIamSupport} attaches each half as its own customer-managed policy, so
     * each half must stay under the 6,144-byte managed-policy limit.
     */
    public static List<String> getNetworkPermissions(IAMProfile tier) {
        List<String> actions = new java.util.ArrayList<>();
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ACM_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ACM_PCA_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(ROUTE53_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(CLOUDFRONT_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(S3_APP_BUCKET_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(VPC_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(EFS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ALB_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ACM_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ACM_PCA_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(ROUTE53_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(CLOUDFRONT_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(S3_APP_BUCKET_PERMISSIONS.get(IAMProfile.STANDARD));
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
        actions.addAll(SCALING_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(COGNITO_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(MONITORING_PERMISSIONS.get(IAMProfile.MINIMAL));
        actions.addAll(BACKUP_PERMISSIONS.get(IAMProfile.MINIMAL));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.MINIMAL));
        }
        if (tier == IAMProfile.MINIMAL) {
            return List.copyOf(actions);
        }
        actions.addAll(ECS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(LOGS_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(DATABASE_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(SCALING_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(COGNITO_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(MONITORING_PERMISSIONS.get(IAMProfile.STANDARD));
        actions.addAll(BACKUP_PERMISSIONS.get(IAMProfile.STANDARD));
        if (includeCompliance) {
            actions.addAll(COMPLIANCE_PERMISSIONS.get(IAMProfile.STANDARD));
        }
        return List.copyOf(actions);
    }
}
