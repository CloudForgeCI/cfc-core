package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.util.NagSuppress;
import com.cloudforge.core.enums.SecurityProfile;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.List;

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
public final class StandardIAMConfiguration extends AbstractIAMConfiguration {

    @Override
    public IAMProfile kind() {
        return IAMProfile.STANDARD;
    }

    @Override
    protected void createEc2Role(SystemContext c) {
        // Get account ID from stack
        String accountId = accountId(c);

        // Use stackName for application-aware log paths and bucket patterns
        String appLogPattern = logGroupPattern(c);
        String backupBucketPattern = backupBucketPattern(c);

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
                    NagSuppress.of("AwsSolutions-IAM5",
                        "SSM service endpoints and CloudWatch metrics require wildcards - AWS service requirements")
                ),
                Boolean.TRUE
            );

            ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, ec2Role);
            ManagerOperatorIamSupport.attachDeployCapabilities(c, ec2Role);
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
                    NagSuppress.of("AwsSolutions-IAM4",
                        "AWS managed policies (AmazonSSMManagedInstanceCore, CloudWatchAgentServerPolicy) " +
                                "are AWS-recommended for EC2 instances. They provide minimal permissions " +
                                "for SSM connectivity and CloudWatch monitoring.")
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

            ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, ec2Role);
            ManagerOperatorIamSupport.attachDeployCapabilities(c, ec2Role);
            c.ec2InstanceRole.set(ec2Role);
        }
    }

    @Override
    protected void createFargateRoles(SystemContext c) {
        // Get account ID from stack
        String accountId = accountId(c);

        // Use stackName for application-aware log paths and bucket patterns
        String appLogPattern = logGroupPattern(c);
        String backupBucketPattern = backupBucketPattern(c);

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

            ManagerOperatorIamSupport.addOperatorBaselineToStatements(c, taskStatements);
            ManagerOperatorIamSupport.addDeployCapabilitiesToStatements(c, taskStatements);

            ManagedPolicy taskPolicy = ManagedPolicy.Builder.create(c, "StandardTaskPolicy")
                    .description("Standard ECS task permissions for application runtime")
                    .statements(taskStatements)
                    .build();

            Role taskRole = Role.Builder.create(c, "StandardTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(taskPolicy))
                    .build();
            ManagerOperatorIamSupport.attachDeployTargetInfrastructurePolicies(c, taskRole);

            // Add CDK-NAG suppressions for necessary wildcards
            NagSuppressions.addResourceSuppressions(
                executionPolicy,
                List.of(
                    NagSuppress.of("AwsSolutions-IAM5",
                        "ECR GetAuthorizationToken and CloudWatch Logs patterns require wildcards - AWS service requirements")
                ),
                Boolean.TRUE
            );

            NagSuppressions.addResourceSuppressions(
                taskPolicy,
                List.of(
                    NagSuppress.of("AwsSolutions-IAM5",
                        "CloudWatch Logs patterns and CloudWatch metrics require wildcards - AWS service requirements")
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
                    NagSuppress.of("AwsSolutions-IAM4",
                        "AmazonECSTaskExecutionRolePolicy is an AWS-managed policy specifically " +
                                "designed for ECS task execution. It provides minimal required permissions " +
                                "for pulling container images from ECR and writing logs to CloudWatch.")
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

            ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, taskRole);
            ManagerOperatorIamSupport.attachDeployCapabilities(c, taskRole);

            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);
        }
    }
}
