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
 * Extended IAM configuration with broader permissions for development.
 * Suitable for development environments only.
 *
 * Key features:
 * - Additional debugging permissions
 * - Extended monitoring capabilities
 * - Development tools access
 * - Administrative permissions for troubleshooting
 *
 * WARNING: This IAM profile is NOT recommended for PRODUCTION security profile.
 * Use MINIMAL or STANDARD profiles for production deployments with compliance requirements.
 */
public final class ExtendedIAMConfiguration extends AbstractIAMConfiguration {

    @Override
    public IAMProfile kind() {
        return IAMProfile.EXTENDED;
    }

    @Override
    protected void createEc2Role(SystemContext c) {
        // Extended EC2 instance role with development permissions
        Role ec2Role = Role.Builder.create(c, "ExtendedEc2Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy"),
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonS3ReadOnlyAccess")
                ))
                .build();

        // Suppress IAM4 for AWS managed policies - these are AWS-recommended for EC2 operations
        NagSuppressions.addResourceSuppressions(
            ec2Role,
            List.of(
                NagSuppress.of("AwsSolutions-IAM4",
                    "AWS managed policies (AmazonSSMManagedInstanceCore, CloudWatchAgentServerPolicy, " +
                            "AmazonS3ReadOnlyAccess) are AWS-recommended for EC2 instances. These provide " +
                            "minimal permissions for SSM connectivity, CloudWatch monitoring, and S3 read access."),
                NagSuppress.of("AwsSolutions-IAM5",
                    "Extended IAM profile is designed for DEV environments with broad permissions " +
                            "for debugging and development. Wildcard permissions enable full CloudWatch, S3, " +
                            "and EFS access for development flexibility. Production uses MinimalIAMConfiguration.")
            ),
            Boolean.TRUE
        );

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
        // Use stackName for application-aware S3 bucket patterns
        String devBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-dev-*"
                : "*-dev-*";
        String backupBucketPattern = backupBucketPattern(c);
        ec2Role.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedS3Access")
                .actions(List.of(
                        "s3:*"
                ))
                .resources(List.of(
                        "arn:aws:s3:::" + devBucketPattern,
                        "arn:aws:s3:::" + devBucketPattern + "/*",
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
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

        ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, ec2Role);
        ManagerOperatorIamSupport.attachDeployCapabilities(c, ec2Role);
        c.ec2InstanceRole.set(ec2Role);
    }

    @Override
    protected void createFargateRoles(SystemContext c) {
        // Extended ECS Task Execution Role
        Role executionRole = Role.Builder.create(c, "ExtendedTaskExecutionRole")
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
        // Use stackName for application-aware S3 bucket patterns
        String devBucketPattern = c.stackName != null && !c.stackName.isEmpty()
                ? c.stackName.toLowerCase() + "-dev-*"
                : "*-dev-*";
        String backupBucketPattern = backupBucketPattern(c);
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("ExtendedS3Access")
                .actions(List.of(
                        "s3:*"
                ))
                .resources(List.of(
                        "arn:aws:s3:::" + devBucketPattern,
                        "arn:aws:s3:::" + devBucketPattern + "/*",
                        "arn:aws:s3:::" + backupBucketPattern,
                        "arn:aws:s3:::" + backupBucketPattern + "/*"
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

        ManagerOperatorIamSupport.attachOperatorBaselinePolicies(c, taskRole);
        ManagerOperatorIamSupport.attachDeployCapabilities(c, taskRole);

        c.fargateExecutionRole.set(executionRole);
        c.fargateTaskRole.set(taskRole);
    }
}
