package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.require;

/**
 * Standard IAM configuration with balanced permissions.
 * Suitable for staging and development environments.
 *
 * Key features:
 * - Standard operational permissions
 * - Limited administrative access
 * - Enhanced monitoring and logging
 * - Backup and recovery permissions
 */
public final class StandardIAMConfiguration implements IAMConfiguration {

    @Override
    public IAMProfile kind() {
        return IAMProfile.STANDARD;
    }

    @Override
    public String id() {
        return "iam:STANDARD";
    }

    @Override
    public List<Rule> rules(SystemContext c) {
        var rules = new java.util.ArrayList<Rule>();
        rules.add(require("vpc", x -> x.vpc));

        // Instance security group is only required for EC2 runtime
        if (c.runtime == RuntimeType.EC2) {
            rules.add(require("instance security group", x -> x.instanceSg));
        }

        rules.add(require("alb security group", x -> x.albSg));
        rules.add(require("efs security group", x -> x.efsSg));
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        // Create standard IAM roles based on runtime type
        if (c.runtime == RuntimeType.EC2) {
            createStandardEC2Role(c);
        } else if (c.runtime == RuntimeType.FARGATE) {
            createStandardFargateRoles(c);
        }
    }

    private void createStandardEC2Role(SystemContext c) {
        // Get account ID from stack
        String accountId = software.amazon.awscdk.Stack.of(c).getAccount();

        // Use stackName for application-aware log paths and bucket patterns
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";
        String backupBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-backup-*"
                : "*-backup-*";

        // For PRODUCTION, use Customer Managed Policies
        if (c.security == SecurityProfile.PRODUCTION) {
            var policyStatements = new java.util.ArrayList<PolicyStatement>();

            // SSM Core permissions
            policyStatements.add(PolicyStatement.Builder.create()
                    .sid("SSMCore")
                    .actions(List.of(
                        "ssm:UpdateInstanceInformation",
                        "ssmmessages:CreateControlChannel",
                        "ssmmessages:CreateDataChannel",
                        "ssmmessages:OpenControlChannel",
                        "ssmmessages:OpenDataChannel"
                    ))
                    .resources(List.of("*")) // SSM requires wildcard
                    .build());

            // SSM S3 access for agent downloads
            policyStatements.add(PolicyStatement.Builder.create()
                    .sid("SSMS3Access")
                    .actions(List.of("s3:GetObject"))
                    .resources(List.of(
                        "arn:aws:s3:::aws-ssm-" + c.cfc.region() + "/*",
                        "arn:aws:s3:::aws-windows-downloads-" + c.cfc.region() + "/*",
                        "arn:aws:s3:::amazon-ssm-" + c.cfc.region() + "/*",
                        "arn:aws:s3:::amazon-ssm-packages-" + c.cfc.region() + "/*"
                    ))
                    .build());

            // CloudWatch Logs permissions
            policyStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchLogs")
                    .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogGroups",
                        "logs:DescribeLogStreams",
                        "logs:PutRetentionPolicy"
                    ))
                    .resources(List.of(
                        "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            // CloudWatch Metrics permissions (service-level, requires wildcard)
            policyStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchMetrics")
                    .actions(List.of(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                    ))
                    .resources(List.of("*")) // CloudWatch metrics are service-level
                    .build());

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent()) {
                policyStatements.add(PolicyStatement.Builder.create()
                        .sid("StandardEfsAccess")
                        .actions(List.of(
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:DescribeMountTargets",
                            "elasticfilesystem:DescribeFileSystems"
                        ))
                        .resources(List.of(c.efs.get().orElseThrow().getFileSystemArn()))
                        .build());
            }

            // S3 backup permissions
            policyStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardS3Backup")
                    .actions(List.of(
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject",
                        "s3:ListBucket"
                    ))
                    .resources(List.of(
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
                    ))
                    .build());

            // Create Customer Managed Policy
            ManagedPolicy ec2Policy = ManagedPolicy.Builder.create(c, "StandardEc2Policy")
                    .description("Standard EC2 permissions (HIPAA/PCI-DSS compliant)")
                    .statements(policyStatements)
                    .build();

            // Create role with Customer Managed Policy
            Role ec2Role = Role.Builder.create(c, "StandardEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(ec2Policy))
                    .build();

            // Add CDK-NAG suppressions for necessary wildcards
            NagSuppressions.addResourceSuppressions(
                ec2Policy,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("SSM service endpoints and CloudWatch metrics require wildcards - AWS service requirements")
                        .build()
                ),
                Boolean.TRUE
            );

            c.ec2InstanceRole.set(ec2Role);

        } else {
            // For DEV/STAGING, use AWS managed policies and inline policies
            Role ec2Role = Role.Builder.create(c, "StandardEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
                    ))
                    .build();

            NagSuppressions.addResourceSuppressions(
                ec2Role,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM4")
                        .reason("AWS managed policies (AmazonSSMManagedInstanceCore, CloudWatchAgentServerPolicy) " +
                                "are AWS-recommended for EC2 instances. They provide minimal permissions " +
                                "for SSM connectivity and CloudWatch monitoring.")
                        .build()
                ),
                Boolean.TRUE
            );

            // Add CloudWatch Logs permissions
            ec2Role.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchLogs")
                    .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogGroups",
                        "logs:DescribeLogStreams",
                        "logs:PutRetentionPolicy"
                    ))
                    .resources(List.of(
                        "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            // Add CloudWatch metrics permissions
            ec2Role.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchMetrics")
                    .actions(List.of(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                    ))
                    .resources(List.of("*"))
                    .build());

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent()) {
                ec2Role.addToPolicy(PolicyStatement.Builder.create()
                        .sid("StandardEfsAccess")
                        .actions(List.of(
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:DescribeMountTargets",
                            "elasticfilesystem:DescribeFileSystems"
                        ))
                        .resources(List.of(c.efs.get().orElseThrow().getFileSystemArn()))
                        .build());
            }

            // Add S3 backup permissions
            ec2Role.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardS3Backup")
                    .actions(List.of(
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject",
                        "s3:ListBucket"
                    ))
                    .resources(List.of(
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
                    ))
                    .build());

            c.ec2InstanceRole.set(ec2Role);
        }
    }

    private void createStandardFargateRoles(SystemContext c) {
        // Get account ID from stack
        String accountId = software.amazon.awscdk.Stack.of(c).getAccount();

        // Use stackName for application-aware log paths and bucket patterns
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";
        String backupBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-backup-*"
                : "*-backup-*";

        // For PRODUCTION, use Customer Managed Policies
        if (c.security == SecurityProfile.PRODUCTION) {
            // Create Customer Managed Execution Policy
            ManagedPolicy executionPolicy = ManagedPolicy.Builder.create(c, "StandardTaskExecutionPolicy")
                    .description("Standard ECS task execution permissions (HIPAA/PCI-DSS compliant)")
                    .statements(List.of(
                        PolicyStatement.Builder.create()
                            .sid("ECRImagePull")
                            .actions(List.of(
                                "ecr:GetAuthorizationToken",
                                "ecr:BatchCheckLayerAvailability",
                                "ecr:GetDownloadUrlForLayer",
                                "ecr:BatchGetImage"
                            ))
                            .resources(List.of("*")) // ECR GetAuthorizationToken requires wildcard
                            .build(),
                        PolicyStatement.Builder.create()
                            .sid("CloudWatchLogs")
                            .actions(List.of(
                                "logs:CreateLogStream",
                                "logs:PutLogEvents"
                            ))
                            .resources(List.of(
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:/aws/ecs/" + c.stackName + "*:*"
                            ))
                            .build()
                    ))
                    .build();

            Role executionRole = Role.Builder.create(c, "StandardTaskExecutionRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(executionPolicy))
                    .build();

            // Create Customer Managed Task Policy
            var taskStatements = new java.util.ArrayList<PolicyStatement>();

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent() && c.ap.get().isPresent()) {
                taskStatements.add(PolicyStatement.Builder.create()
                        .sid("StandardEfsAccess")
                        .actions(List.of(
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets"
                        ))
                        .resources(List.of(
                            c.efs.get().orElseThrow().getFileSystemArn(),
                            c.ap.get().orElseThrow().getAccessPointArn()
                        ))
                        .build());
            }

            // CloudWatch Logs permissions
            taskStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchLogs")
                    .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogGroups",
                        "logs:DescribeLogStreams",
                        "logs:PutRetentionPolicy"
                    ))
                    .resources(List.of(
                        "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            // CloudWatch Metrics permissions (service-level)
            taskStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchMetrics")
                    .actions(List.of(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                    ))
                    .resources(List.of("*")) // CloudWatch metrics are service-level
                    .build());

            // S3 backup permissions
            taskStatements.add(PolicyStatement.Builder.create()
                    .sid("StandardS3Backup")
                    .actions(List.of(
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject",
                        "s3:ListBucket"
                    ))
                    .resources(List.of(
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
                    ))
                    .build());

            ManagedPolicy taskPolicy = ManagedPolicy.Builder.create(c, "StandardTaskPolicy")
                    .description("Standard ECS task permissions for application runtime")
                    .statements(taskStatements)
                    .build();

            Role taskRole = Role.Builder.create(c, "StandardTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(taskPolicy))
                    .build();

            // Add CDK-NAG suppressions for necessary wildcards
            NagSuppressions.addResourceSuppressions(
                executionPolicy,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("ECR GetAuthorizationToken and CloudWatch Logs patterns require wildcards - AWS service requirements")
                        .build()
                ),
                Boolean.TRUE
            );

            NagSuppressions.addResourceSuppressions(
                taskPolicy,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("CloudWatch Logs patterns and CloudWatch metrics require wildcards - AWS service requirements")
                        .build()
                ),
                Boolean.TRUE
            );

            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);

        } else {
            // For DEV/STAGING, use AWS managed policies and inline policies
            Role executionRole = Role.Builder.create(c, "StandardTaskExecutionRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                    ))
                    .build();

            NagSuppressions.addResourceSuppressions(
                executionRole,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM4")
                        .reason("AmazonECSTaskExecutionRolePolicy is an AWS-managed policy specifically " +
                                "designed for ECS task execution. It provides minimal required permissions " +
                                "for pulling container images from ECR and writing logs to CloudWatch.")
                        .build()
                ),
                Boolean.TRUE
            );

            Role taskRole = Role.Builder.create(c, "StandardTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .build();

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent() && c.ap.get().isPresent()) {
                taskRole.addToPolicy(PolicyStatement.Builder.create()
                        .sid("StandardEfsAccess")
                        .actions(List.of(
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets"
                        ))
                        .resources(List.of(
                            c.efs.get().orElseThrow().getFileSystemArn(),
                            c.ap.get().orElseThrow().getAccessPointArn()
                        ))
                        .build());
            }

            // CloudWatch Logs permissions
            taskRole.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchLogs")
                    .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents",
                        "logs:DescribeLogGroups",
                        "logs:DescribeLogStreams",
                        "logs:PutRetentionPolicy"
                    ))
                    .resources(List.of(
                        "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            // CloudWatch Metrics permissions
            taskRole.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardCloudWatchMetrics")
                    .actions(List.of(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                    ))
                    .resources(List.of("*"))
                    .build());

            // S3 backup permissions
            taskRole.addToPolicy(PolicyStatement.Builder.create()
                    .sid("StandardS3Backup")
                    .actions(List.of(
                        "s3:GetObject",
                        "s3:PutObject",
                        "s3:DeleteObject",
                        "s3:ListBucket"
                    ))
                    .resources(List.of(
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
                    ))
                    .build());

            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);
        }
    }
}
