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
        if (c.runtime == com.cloudforgeci.api.interfaces.RuntimeType.EC2) {
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
        // Minimal EC2 instance role - only SSM and essential permissions
        Role ec2Role = Role.Builder.create(c, "MinimalEc2Role")
                .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
                ))
                .build();

        // Add minimal CloudWatch permissions for basic monitoring
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
                        "arn:aws:logs:" + c.cfc.region() + ":*:log-group:/aws/jenkins*"
                ))
                .build());

        // Store the role in SystemContext for use by factories
        c.ec2InstanceRole.set(ec2Role);
    }

    private void createMinimalFargateRoles(SystemContext c) {
        // Minimal ECS Task Execution Role
        Role executionRole = Role.Builder.create(c, "MinimalTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();

        // Minimal ECS Task Role (for application permissions)
        Role taskRole = Role.Builder.create(c, "MinimalTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .build();

        // Add minimal EFS permissions if EFS is used
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

        // Add minimal CloudWatch permissions
        taskRole.addToPolicy(PolicyStatement.Builder.create()
                .sid("MinimalCloudWatchLogs")
                .actions(List.of(
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents"
                ))
                .resources(List.of(
                        "arn:aws:logs:" + c.cfc.region() + ":*:log-group:/aws/ecs/jenkins*"
                ))
                .build());

        // Store roles in SystemContext
        c.fargateExecutionRole.set(executionRole);
        c.fargateTaskRole.set(taskRole);
    }
}
