package com.cloudforgeci.api.integration.runtime;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests validating full CDK infrastructure synthesis for runtime configurations.
 *
 * <p>Synthesizes complete CloudFormation templates for EC2 and Fargate runtimes across
 * all security profiles. Verifies core infrastructure (VPC, ALB, EFS, compute), security
 * groups, target groups, listeners, EFS encryption, and IAM roles are correctly configured.
 */
class RuntimeInfrastructureSynthesisTest {

    @Test
    void testEc2MinimalInfrastructureSynthesis() {
        // Given: EC2 deployment with minimal configuration
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "Ec2MinimalStack",
            SecurityProfile.DEV,
            RuntimeType.EC2
        );

        // When: Creating minimal infrastructure and synthesizing CDK template
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify core infrastructure components are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::TargetGroup", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
        template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.resourceCountIs("AWS::EC2::SecurityGroup", 3); // ALB, Instance, EFS

        // Verify EFS is encrypted
        template.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
            "Encrypted", true
        ));

        // Verify HTTP listener
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
            "Protocol", "HTTP",
            "Port", 80
        ));
    }

    @Test
    void testFargateMinimalInfrastructureSynthesis() {
        // Given: Fargate deployment with minimal configuration
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FargateMinimalStack",
            SecurityProfile.DEV,
            RuntimeType.FARGATE
        );

        // When: Creating minimal infrastructure and synthesizing CDK template
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify core infrastructure components are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
        template.resourceCountIs("AWS::ECS::Service", 1);
        template.resourceCountIs("AWS::ECS::TaskDefinition", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // Verify EFS is encrypted
        template.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
            "Encrypted", true
        ));

        // Verify HTTP listener
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
            "Protocol", "HTTP",
            "Port", 80
        ));

        // Verify Fargate task definition
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
            "RequiresCompatibilities", java.util.List.of("FARGATE"),
            "NetworkMode", "awsvpc"
        ));
    }

    @Test
    void testEc2AllSecurityProfiles() {
        // Test that all security profiles can synthesize successfully
        for (SecurityProfile profile : SecurityProfile.values()) {
            // Given: EC2 deployment with specific security profile
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "Ec2Profile" + profile.name(),
                profile,
                RuntimeType.EC2
            );

            // When: Creating infrastructure
            assertDoesNotThrow(() -> builder.createMinimalInfrastructure(),
                "EC2 infrastructure should synthesize for profile: " + profile);

            // Then: Verify CDK template can be generated
            Template template = Template.fromStack(builder.getStack());
            template.resourceCountIs("AWS::EC2::VPC", 1);
            template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
            template.resourceCountIs("AWS::EFS::FileSystem", 1);

            // All profiles require EFS encryption
            template.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
                "Encrypted", true
            ));
        }
    }

    @Test
    void testFargateAllSecurityProfiles() {
        // Test that all security profiles can synthesize successfully
        for (SecurityProfile profile : SecurityProfile.values()) {
            // Given: Fargate deployment with specific security profile
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "FargateProfile" + profile.name(),
                profile,
                RuntimeType.FARGATE
            );

            // When: Creating infrastructure
            assertDoesNotThrow(() -> builder.createMinimalInfrastructure(),
                "Fargate infrastructure should synthesize for profile: " + profile);

            // Then: Verify CDK template can be generated
            Template template = Template.fromStack(builder.getStack());
            template.resourceCountIs("AWS::EC2::VPC", 1);
            template.resourceCountIs("AWS::ECS::Service", 1);
            template.resourceCountIs("AWS::ECS::TaskDefinition", 1);
            template.resourceCountIs("AWS::EFS::FileSystem", 1);

            // All profiles require EFS encryption
            template.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
                "Encrypted", true
            ));
        }
    }

    @Test
    void testEc2ProductionProfileWithAutoscaling() {
        // Given: PRODUCTION profile with autoscaling
        Map<String, Object> context = new HashMap<>();
        context.put("minInstanceCapacity", 2);
        context.put("maxInstanceCapacity", 5);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "Ec2ProductionAutoscaling",
            SecurityProfile.PRODUCTION,
            RuntimeType.EC2,
            context
        );

        // When: Creating infrastructure
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify Auto Scaling Group with proper capacity
        template.hasResourceProperties("AWS::AutoScaling::AutoScalingGroup", Map.of(
            "MinSize", "2",
            "MaxSize", "5"
        ));
    }

    @Test
    void testEc2HealthCheckConfiguration() {
        // Given: Custom health check settings
        Map<String, Object> context = new HashMap<>();
        context.put("healthCheckInterval", 60);
        context.put("healthCheckTimeout", 10);
        context.put("healthyThreshold", 3);
        context.put("unhealthyThreshold", 5);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "Ec2HealthCheck",
            SecurityProfile.DEV,
            RuntimeType.EC2,
            context
        );

        // When: Creating infrastructure (including EFS for validation)
        builder.createMinimalInfrastructure();

        Template template = Template.fromStack(builder.getStack());

        // Then: Verify health check settings
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::TargetGroup", Map.of(
            "HealthCheckIntervalSeconds", 60,
            "HealthCheckTimeoutSeconds", 10,
            "HealthyThresholdCount", 3,
            "UnhealthyThresholdCount", 5
        ));
    }

    @Test
    void testEc2IamRolesAndPolicies() {
        // Given: EC2 deployment
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "Ec2IamRoles",
            SecurityProfile.DEV,
            RuntimeType.EC2
        );

        // When: Creating infrastructure
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify IAM instance profile is created
        template.resourceCountIs("AWS::IAM::InstanceProfile", 1);
        template.resourceCountIs("AWS::IAM::Role", 1);

        // Verify ec2.amazonaws.com trust policy
        template.hasResourceProperties("AWS::IAM::Role", Map.of(
            "AssumeRolePolicyDocument", Map.of(
                "Statement", java.util.List.of(Map.of(
                    "Action", "sts:AssumeRole",
                    "Effect", "Allow",
                    "Principal", Map.of("Service", "ec2.amazonaws.com")
                ))
            )
        ));
    }

    @Test
    void testFargateTaskDefinitionConfiguration() {
        // Given: Fargate deployment
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FargateTaskDef",
            SecurityProfile.DEV,
            RuntimeType.FARGATE
        );

        // When: Creating infrastructure
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify task definition with correct configuration
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
            "RequiresCompatibilities", java.util.List.of("FARGATE"),
            "NetworkMode", "awsvpc",
            "Cpu", "1024",
            "Memory", "2048"
        ));

        // Verify EFS volume with transit encryption
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
            "Volumes", java.util.List.of(Map.of(
                "EFSVolumeConfiguration", Map.of(
                    "TransitEncryption", "ENABLED"
                )
            ))
        ));
    }

    @Test
    void testFargateIamRoles() {
        // Given: Fargate deployment
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FargateIamRoles",
            SecurityProfile.DEV,
            RuntimeType.FARGATE
        );

        // When: Creating infrastructure
        builder.createMinimalInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify IAM roles for ECS tasks
        template.resourceCountIs("AWS::IAM::Role", 2); // Execution role + task role

        // Verify ecs-tasks.amazonaws.com trust policy
        template.hasResourceProperties("AWS::IAM::Role", Map.of(
            "AssumeRolePolicyDocument", Map.of(
                "Statement", java.util.List.of(Map.of(
                    "Action", "sts:AssumeRole",
                    "Effect", "Allow",
                    "Principal", Map.of("Service", "ecs-tasks.amazonaws.com")
                ))
            )
        ));
    }

    @Test
    void testCompleteEc2InfrastructureSynthesis() {
        // Given: EC2 deployment with all components
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "Ec2Complete",
            SecurityProfile.PRODUCTION,
            RuntimeType.EC2
        );

        // When: Creating complete infrastructure
        builder.createCompleteInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify all components exist
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::TargetGroup", 1);
        template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.resourceCountIs("AWS::EFS::AccessPoint", 1);
        template.resourceCountIs("AWS::EC2::SecurityGroup", 3); // ALB, Instance, EFS
    }

    @Test
    void testCompleteFargateInfrastructureSynthesis() {
        // Given: Fargate deployment with all components
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FargateComplete",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE
        );

        // When: Creating complete infrastructure
        builder.createCompleteInfrastructure();
        Template template = Template.fromStack(builder.getStack());

        // Then: Verify all components exist
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ECS::Service", 1);
        template.resourceCountIs("AWS::ECS::TaskDefinition", 1);
        template.resourceCountIs("AWS::ECS::Cluster", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.resourceCountIs("AWS::EFS::AccessPoint", 1);
    }
}
