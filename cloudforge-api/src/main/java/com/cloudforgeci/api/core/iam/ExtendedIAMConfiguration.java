package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.RuntimeType;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;

import java.util.List;

import static com.cloudforgeci.api.core.rules.RuleKit.require;

/**
 * Extended IAM configuration with broader permissions for development.
 * Suitable for development environments only.
 * 
 * Key features:
 * - Additional debugging permissions
 * - Extended monitoring capabilities
 * - Development tools access
 * - Administrative permissions for troubleshooting
 */
public final class ExtendedIAMConfiguration implements IAMConfiguration {

    @Override
    public IAMProfile kind() { 
        return IAMProfile.EXTENDED; 
    }

    @Override
    public String id() { 
        return "iam:EXTENDED"; 
    }

    @Override
    public List<Rule> rules(SystemContext c) {
        var rules = new java.util.ArrayList<Rule>();
        rules.add(require("vpc", x -> x.vpc));
        
        // Instance security group is only required for EC2 runtime
        if (c.runtime == com.cloudforgeci.api.interfaces.RuntimeType.EC2) {
            rules.add(require("instance security group", x -> x.instanceSg));
        }
        
        rules.add(require("alb security group", x -> x.albSg));
        rules.add(require("efs security group", x -> x.efsSg));
        return rules;
    }

    @Override
    public void wire(SystemContext c) {
        // Create extended IAM roles based on runtime type
        if (c.runtime == RuntimeType.EC2) {
            createExtendedEC2Role(c);
        } else if (c.runtime == RuntimeType.FARGATE) {
            createExtendedFargateRoles(c);
        }
    }

    private void createExtendedEC2Role(SystemContext c) {
        // Extended EC2 instance role with development permissions
        Role ec2Role = Role.Builder.create(c, "ExtendedEc2Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy"),
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")
                ))
                .build();

        // Add extended CloudWatch permissions
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedCloudWatchLogs")
                .actions(List.of(
                        "logs:*"
                ))
                .resources(List.of("*"))
                .build());

        // Add extended CloudWatch metrics permissions
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedCloudWatchMetrics")
                .actions(List.of(
                        "cloudwatch:*"
                ))
                .resources(List.of("*"))
                .build());

        // Add EFS permissions
        if (c.efs.get().isPresent()) {
            ec2Role.addToPolicy(PolicyStatement.Builder.create()
                    .sid("ExtendedEfsAccess")
                    .actions(List.of(
                            "elasticfilesystem:*"
                    ))
                    .resources(List.of("*"))
                    .build());
        }

        // Add S3 permissions for development
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedS3Access")
                .actions(List.of(
                        "s3:*"
                ))
                .resources(List.of(
                        "arn:aws:s3:::jenkins-dev-*",
                        "arn:aws:s3:::jenkins-dev-*/*",
                        "arn:aws:s3:::jenkins-backup-*",
                        "arn:aws:s3:::jenkins-backup-*/*"
                ))
                .build());

        // Add EC2 permissions for debugging
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedEc2Debug")
                .actions(List.of(
                        "ec2:DescribeInstances",
                        "ec2:DescribeVolumes",
                        "ec2:DescribeSnapshots",
                        "ec2:DescribeImages",
                        "ec2:DescribeSecurityGroups",
                        "ec2:DescribeVpcs",
                        "ec2:DescribeSubnets"
                ))
                .resources(List.of("*"))
                .build());

        // Add Systems Manager permissions for debugging
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedSSMDebug")
                .actions(List.of(
                        "ssm:DescribeInstanceInformation",
                        "ssm:ListCommandInvocations",
                        "ssm:SendCommand",
                        "ssm:GetCommandInvocation"
                ))
                .resources(List.of("*"))
                .build());

        c.ec2InstanceRole.set(ec2Role);
    }

    private void createExtendedFargateRoles(SystemContext c) {
        // Extended ECS Task Execution Role
        Role executionRole = Role.Builder.create(c, "ExtendedTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();

        // Extended ECS Task Role
        Role taskRole = Role.Builder.create(c, "ExtendedTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .build();

        // Add extended EFS permissions
        if (c.efs.get().isPresent() && c.ap.get().isPresent()) {
            taskRole.addToPolicy(PolicyStatement.Builder.create()
                    .sid("ExtendedEfsAccess")
                    .actions(List.of(
                            "elasticfilesystem:*"
                    ))
                    .resources(List.of("*"))
                    .build());
        }

        // Add extended CloudWatch permissions
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedCloudWatchLogs")
                .actions(List.of(
                        "logs:*"
                ))
                .resources(List.of("*"))
                .build());

        // Add extended CloudWatch metrics permissions
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedCloudWatchMetrics")
                .actions(List.of(
                        "cloudwatch:*"
                ))
                .resources(List.of("*"))
                .build());

        // Add extended S3 permissions
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedS3Access")
                .actions(List.of(
                        "s3:*"
                ))
                .resources(List.of(
                        "arn:aws:s3:::jenkins-dev-*",
                        "arn:aws:s3:::jenkins-dev-*/*",
                        "arn:aws:s3:::jenkins-backup-*",
                        "arn:aws:s3:::jenkins-backup-*/*"
                ))
                .build());

        // Add ECS permissions for debugging
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedEcsDebug")
                .actions(List.of(
                        "ecs:DescribeClusters",
                        "ecs:DescribeServices",
                        "ecs:DescribeTasks",
                        "ecs:DescribeTaskDefinition",
                        "ecs:ListTasks",
                        "ecs:ListServices"
                ))
                .resources(List.of("*"))
                .build());

        c.fargateExecutionRole.set(executionRole);
        c.fargateTaskRole.set(taskRole);
    }
}
