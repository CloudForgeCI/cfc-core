package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforge.core.enums.RuntimeType;
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
        // Standard EC2 instance role with additional permissions
        Role ec2Role = Role.Builder.create(c, "StandardEc2Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
                ))
                .build();

        // Add standard CloudWatch permissions
        // Use stackName for application-aware log paths
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";
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
                        "arn:aws:logs:" + c.cfc.region() + ":*:log-group:" + appLogPattern
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

        // Add S3 permissions for backup/restore (if needed)
        // Use stackName for application-aware S3 bucket patterns
        String backupBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-backup-*"
                : "*-backup-*";
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

    private void createStandardFargateRoles(SystemContext c) {
        // Standard ECS Task Execution Role
        Role executionRole = Role.Builder.create(c, "StandardTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();

        // Standard ECS Task Role
        Role taskRole = Role.Builder.create(c, "StandardTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .build();

        // Add standard EFS permissions
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

        // Add standard CloudWatch permissions
        // Use stackName for application-aware log paths
        String appLogPattern = c.stackName != null && !c.stackName.isEmpty()
                ? "/aws/*/" + c.stackName + "*"
                : "/aws/*";
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
                        "arn:aws:logs:" + c.cfc.region() + ":*:log-group:" + appLogPattern
                ))
                .build());

        // Add CloudWatch metrics permissions
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("StandardCloudWatchMetrics")
                .actions(List.of(
                        "cloudwatch:PutMetricData",
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics"
                ))
                .resources(List.of("*"))
                .build());

        // Add S3 permissions for backup/restore
        // Use stackName for application-aware S3 bucket patterns
        String backupBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-backup-*"
                : "*-backup-*";
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
