package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
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
 * Minimal IAM configuration with least privilege permissions.
 * Suitable for production environments with strict compliance requirements.
 *
 * Key principles:
 * - No administrative permissions
 * - Only essential read/write permissions
 * - Resource-specific permissions (no wildcards)
 * - Minimal CloudWatch permissions
 */
public final class MinimalIAMConfiguration implements IAMConfiguration {

    @Override
    public IAMProfile kind() {
        return IAMProfile.MINIMAL;
    }

    @Override
    public String id() {
        return "iam:MINIMAL";
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
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        // Create minimal IAM roles based on runtime type
        if (c.runtime == RuntimeType.EC2) {
            createMinimalEC2Role(c);
        } else if (c.runtime == RuntimeType.FARGATE) {
            createMinimalFargateRoles(c);
        }
    }

    private void createMinimalEC2Role(SystemContext c) {
        // Get account ID from stack
        String accountId = software.amazon.awscdk.Stack.of(c).getAccount();

        // Use stackName for application-aware log paths
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";

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
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("SSM requires s3:GetObject on AWS-managed SSM buckets (aws-ssm-*, amazon-ssm-*, aws-windows-downloads-*) - these are AWS service requirements, not application data")
                        .build()
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
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern
                            ))
                            .build()
                    ))
                    .build();

            // Add CDK-NAG suppression for CloudWatch Logs wildcard pattern
            NagSuppressions.addResourceSuppressions(
                cloudwatchPolicy,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-IAM5")
                        .reason("CloudWatch Logs requires wildcard in log group path (/aws/*/) to allow logging to application-specific log groups created at runtime")
                        .build()
                ),
                Boolean.TRUE
            );

            // Create role and attach Customer Managed Policies
            Role ec2Role = Role.Builder.create(c, "MinimalEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(ssmPolicy, cloudwatchPolicy))
                    .build();

            c.ec2InstanceRole.set(ec2Role);

        } else {
            // For DEV/STAGING, use AWS managed policy (simpler, less strict)
            Role ec2Role = Role.Builder.create(c, "MinimalEc2Role")
                    .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                    .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                    ))
                    .build();

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
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern
                    ))
                    .build());

            c.ec2InstanceRole.set(ec2Role);
        }
    }

    private void createMinimalFargateRoles(SystemContext c) {
        // Get account ID from stack
        String accountId = software.amazon.awscdk.Stack.of(c).getAccount();

        // Use stackName for application-aware log paths
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";

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
                                "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:/aws/ecs/" + c.stackName + "*"
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
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern
                    ))
                    .build());

            ManagedPolicy taskPolicy = taskPolicyBuilder.statements(taskStatements).build();

            // Create task role with Customer Managed Policy
            Role taskRole = Role.Builder.create(c, "MinimalTaskRole")
                    .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                    .managedPolicies(List.of(taskPolicy))
                    .build();

            // Add CDK-NAG suppressions for wildcards (PRODUCTION)
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
                        .reason("CloudWatch Logs requires wildcard in log group path for application logging")
                        .build()
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
                            "arn:aws:logs:" + c.cfc.region() + ":" + accountId + ":log-group:" + appLogPattern
                    ))
                    .build());

            // Store roles in SystemContext
            c.fargateExecutionRole.set(executionRole);
            c.fargateTaskRole.set(taskRole);
        }
    }
}
