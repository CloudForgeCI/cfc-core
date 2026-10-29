package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforgeci.api.util.NagSuppress;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import io.github.cdklabs.cdknag.NagSuppressions;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.List;

/**
 * Minimal IAM configuration with least privilege permissions.
 * Suitable for production environments with strict compliance requirements.
 *
 * Key principles:
 * - No administrative permissions
 * - Only essential read/write permissions
 * - Resource-specific permissions (no wildcards)
 * - Minimal CloudWatch permissions
 */
public final class MinimalIAMConfiguration extends AbstractIAMConfiguration {

    @Override
    public IAMProfile kind() {
        return IAMProfile.MINIMAL;
    }

    @Override
    protected boolean requiresEfsSecurityGroup() {
        return false;
    }

    @Override
    protected void createEc2Role(SystemContext c) {
        // Get account ID from stack
        String accountId = accountId(c);

        // Use stackName for application-aware log paths
        String appLogPattern = logGroupPattern(c);

        // For PRODUCTION, use Customer Managed Policy (not inline, not AWS managed)
        if (c.security == SecurityProfile.PRODUCTION) {
            // Create Customer Managed SSM Policy (standalone, not inline)
            ManagedPolicy ssmPolicy = ManagedPolicy.Builder.create(c, "MinimalEc2SSMPolicy")
                    .description("Minimal SSM permissions for EC2 instances (HIPAA/PCI-DSS compliant)")
                    .statements(List.of(
                        PolicyStatement.Builder.create()
                            .sid("SSMCore")
                            .actions(List.of(
                                "ssm:UpdateInstanceInformation",
                                "ssmmessages:CreateControlChannel",
                                "ssmmessages:CreateDataChannel",
                                "ssmmessages:OpenControlChannel",
                                "ssmmessages:OpenDataChannel"
                            ))
                            .resources(List.of("*")) // SSM requires wildcard for these service endpoints
                            .build(),
                        PolicyStatement.Builder.create()
                            .sid("S3GetObject")
                            .actions(List.of("s3:GetObject"))
                            .resources(List.of(
                                "arn:aws:s3:::aws-ssm-" + c.cfc.region() + "/*",
                                "arn:aws:s3:::aws-windows-downloads-" + c.cfc.region() + "/*",
                                "arn:aws:s3:::amazon-ssm-" + c.cfc.region() + "/*",
                                "arn:aws:s3:::amazon-ssm-packages-" + c.cfc.region() + "/*"
                            ))
                            .build()
                    ))
                    .build();

            // Add CDK-NAG suppression for SSM S3 bucket wildcards
            NagSuppressions.addResourceSuppressions(
                ssmPolicy,
                List.of(
                    NagSuppress.of("AwsSolutions-IAM5",
                        "SSM requires s3:GetObject on AWS-managed SSM buckets (aws-ssm-*, amazon-ssm-*, aws-windows-downloads-*) - these are AWS service requirements, not application data")
                ),
                Boolean.TRUE
            );

            // Create Customer Managed CloudWatch Policy (standalone, not inline)
            ManagedPolicy cloudwatchPolicy = ManagedPolicy.Builder.create(c, "MinimalEc2CloudWatchPolicy")
                    .description("Minimal CloudWatch Logs permissions for EC2 instances")
                    .statements(List.of(
                        PolicyStatement.Builder.create()
                            .sid("MinimalCloudWatchLogs")
                            .actions(List.of(
                                "logs:CreateLogGroup",
                                "logs:CreateLogStream",
                                "logs:PutLogEvents",
                                "logs:DescribeLogGroups",
                                "logs:DescribeLogStreams"
                            ))
                            .resources(List.of(
                                // Log group ARN for CreateLogGroup/DescribeLogGroups (requires :* suffix)
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*",
                                // Log stream ARN for CreateLogStream/PutLogEvents/DescribeLogStreams
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                            ))
                            .build()
                    ))
                    .build();

            // Add CDK-NAG suppression for CloudWatch Logs wildcard pattern
            NagSuppressions.addResourceSuppressions(
                cloudwatchPolicy,
                List.of(
                    NagSuppress.of("AwsSolutions-IAM5",
                        "CloudWatch Logs requires wildcard in log group path (/aws/*/) to allow logging to application-specific log groups created at runtime")
                ),
                Boolean.TRUE
            );

            // Create role and attach Customer Managed Policies
            Role ec2Role = Role.Builder.create(c, "MinimalEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(ssmPolicy, cloudwatchPolicy))
                    .build();

            ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, ec2Role);
            ManagerOperatorIamSupport.attachDeployCapabilities(c, ec2Role);
            c.ec2InstanceRole.set(ec2Role);

        } else {
            // For DEV/STAGING, use AWS managed policy (simpler, less strict)
            Role ec2Role = Role.Builder.create(c, "MinimalEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                    ))
                    .build();

            NagSuppressions.addResourceSuppressions(
                ec2Role,
                List.of(
                    NagSuppress.of("AwsSolutions-IAM4",
                        "AmazonSSMManagedInstanceCore is an AWS-managed policy required for " +
                                "EC2 instances to use Systems Manager. It provides minimal permissions " +
                                "for SSM connectivity and is AWS-recommended for all EC2 deployments.")
                ),
                Boolean.TRUE
            );

            // Add CloudWatch permissions as inline policy for DEV/STAGING
            ec2Role.addToPolicy(PolicyStatement.Builder.create()
                    .sid("MinimalCloudWatchLogs")
                    .actions(List.of(
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "logs:DescribeLogGroups",
                            "logs:DescribeLogStreams"
                    ))
                    .resources(List.of(
                            // Log group ARN for CreateLogGroup/DescribeLogGroups (requires :* suffix)
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*",
                            // Log stream ARN for CreateLogStream/PutLogEvents/DescribeLogStreams
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
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

        // Use stackName for application-aware log paths
        String appLogPattern = logGroupPattern(c);

        // For PRODUCTION, use Customer Managed Policies (not inline, not AWS managed)
        if (c.security == SecurityProfile.PRODUCTION) {
            // Create Customer Managed ECS Task Execution Policy
            ManagedPolicy executionPolicy = ManagedPolicy.Builder.create(c, "MinimalTaskExecutionPolicy")
                    .description("Minimal ECS task execution permissions (HIPAA/PCI-DSS compliant)")
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
                                // Log group ARN (requires :* suffix)
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:/aws/ecs/" + c.stackName + "*:*",
                                // Log stream ARN for CreateLogStream/PutLogEvents
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:/aws/ecs/" + c.stackName + "*:*"
                            ))
                            .build()
                    ))
                    .build();

            // Create execution role with Customer Managed Policy
            Role executionRole = Role.Builder.create(c, "MinimalTaskExecutionRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(executionPolicy))
                    .build();

            // Create Customer Managed Task Policy for application permissions
            ManagedPolicy.Builder taskPolicyBuilder = ManagedPolicy.Builder.create(c, "MinimalTaskPolicy")
                    .description("Minimal ECS task permissions for application runtime");

            // Build task policy statements
            var taskStatements = new java.util.ArrayList<PolicyStatement>();

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent() && c.ap.get().isPresent()) {
                taskStatements.add(PolicyStatement.Builder.create()
                        .sid("MinimalEfsAccess")
                        .actions(List.of(
                                "elasticfilesystem:ClientMount",
                                "elasticfilesystem:ClientWrite"
                        ))
                        .resources(List.of(
                                c.efs.get().orElseThrow().getFileSystemArn(),
                                c.ap.get().orElseThrow().getAccessPointArn()
                        ))
                        .build());
            }

            // Add CloudWatch Logs permissions
            taskStatements.add(PolicyStatement.Builder.create()
                    .sid("MinimalCloudWatchLogs")
                    .actions(List.of(
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents"
                    ))
                    .resources(List.of(
                            // Log group ARN for CreateLogGroup
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern,
                            // Log stream ARN for CreateLogStream/PutLogEvents
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            ManagerOperatorIamSupport.addOperatorBaselineToStatements(c, taskStatements);
            ManagerOperatorIamSupport.addDeployCapabilitiesToStatements(c, taskStatements);

            ManagedPolicy taskPolicy = taskPolicyBuilder.statements(taskStatements).build();

            // Create task role with Customer Managed Policy
            Role taskRole = Role.Builder.create(c, "MinimalTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(taskPolicy))
                    .build();
            ManagerOperatorIamSupport.attachDeployTargetInfrastructurePolicies(c, taskRole);

            // Add CDK-NAG suppressions for wildcards (PRODUCTION)
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
                        "CloudWatch Logs requires wildcard in log group path for application logging")
                ),
                Boolean.TRUE
            );

            // Store roles in SystemContext
            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);

        } else {
            // For DEV/STAGING, use AWS managed policy and inline policies (simpler, less strict)
            Role executionRole = Role.Builder.create(c, "MinimalTaskExecutionRole")
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

            Role taskRole = Role.Builder.create(c, "MinimalTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .build();

            // Add EFS permissions if EFS is used
            if (c.efs.get().isPresent() && c.ap.get().isPresent()) {
                taskRole.addToPolicy(PolicyStatement.Builder.create()
                        .sid("MinimalEfsAccess")
                        .actions(List.of(
                                "elasticfilesystem:ClientMount",
                                "elasticfilesystem:ClientWrite"
                        ))
                        .resources(List.of(
                                c.efs.get().orElseThrow().getFileSystemArn(),
                                c.ap.get().orElseThrow().getAccessPointArn()
                        ))
                        .build());
            }

            // Add CloudWatch Logs permissions
            taskRole.addToPolicy(PolicyStatement.Builder.create()
                    .sid("MinimalCloudWatchLogs")
                    .actions(List.of(
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents"
                    ))
                    .resources(List.of(
                            // Log group ARN for CreateLogGroup
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern,
                            // Log stream ARN for CreateLogStream/PutLogEvents
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern + ":*"
                    ))
                    .build());

            ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, taskRole);
            ManagerOperatorIamSupport.attachDeployCapabilities(c, taskRole);

            // Store roles in SystemContext
            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);
        }
    }
}
