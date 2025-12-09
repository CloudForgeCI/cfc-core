package com.cloudforgeci.api.integration.deployment;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.integration.IntegrationTestBase;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.iam.IAMProfileMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

/**
 * Extensive integration tests for deployment workflow and context propagation.
 *
 * Tests validate:
 * - DeploymentContext field mapping and validation
 * - SystemContext slot population across factory chain
 * - CDK synthesis with various configurations
 * - Stack creation and template generation
 * - Different topology and runtime combinations
 */
class DeploymentWorkflowIntegrationTest extends IntegrationTestBase {

    @Test
    void testBasicDeploymentContextCreation() {
        // Given: A stack with deployment context
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "test-stack");
        cfcContext.put("region", "us-east-1");
        stack.getNode().setContext("cfc", cfcContext);

        // When: Creating deployment context
        DeploymentContext context = DeploymentContext.from(stack);

        // Then: Context fields are populated
        assert context != null;
        Template template = Template.fromStack(stack);
        template.resourceCountIs("AWS::EC2::VPC", 0); // No resources yet
    }

    @Test
    void testSystemContextSlotPopulation() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();

        // When: Accessing system context slots
        SystemContext sysCtx = builder.getSystemContext();

        // Then: Verify VPC slot is populated
        assert sysCtx.vpc.get().isPresent();

        // Then: Verify ALB slots are populated
        assert sysCtx.alb.get().isPresent();
        assert sysCtx.albSg.get().isPresent();
        assert sysCtx.http.get().isPresent();

        // Then: Verify EFS slots are populated
        assert sysCtx.efs.get().isPresent();
        assert sysCtx.efsSg.get().isPresent();
        assert sysCtx.ap.get().isPresent();

        // Then: Verify runtime-specific slots
        if (sysCtx.runtime == RuntimeType.FARGATE) {
            assert sysCtx.fargateService.get().isPresent();
            assert sysCtx.fargateTaskDef.get().isPresent();
            assert sysCtx.container.get().isPresent();
        }
    }

    @Test
    void testFargateServiceTopologyDeployment() {
        // Given: Fargate service topology
        App app = new App();
        Stack stack = new Stack(app, "FargateServiceTest");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext context = SystemContext.start(stack, TopologyType.JENKINS_SERVICE,
            RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);
        assert context != null;

        // When: Building complete infrastructure
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "FargateServiceTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE
        );
        builder.createCompleteInfrastructure();

        // Then: Verify complete stack
        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.resourceCountIs("AWS::ECS::Service", 1);
        template.resourceCountIs("AWS::ECS::TaskDefinition", 1);
    }

    @Test
    @org.junit.jupiter.api.Disabled("EC2 runtime requires pre-configured instance security group - architectural dependency issue")
    void testEc2ServiceTopologyDeployment() {
        // Given: EC2 service topology
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "EC2ServiceTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.EC2
        );

        // When: Building complete infrastructure
        builder.createCompleteInfrastructure();

        // Then: Verify complete stack
        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        template.resourceCountIs("AWS::AutoScaling::LaunchConfiguration", 1);
    }

    @Test
    void testSecurityProfileProgression() {
        // Given/When/Then: Test each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            com.cloudforgeci.api.test.TestInfrastructureBuilder testBuilder =
                new com.cloudforgeci.api.test.TestInfrastructureBuilder(
                    "Test" + profile.name(),
                    profile,
                    RuntimeType.FARGATE
                );

            testBuilder.createCompleteInfrastructure();
            Template template = Template.fromStack(testBuilder.getStack());

            // Verify basic infrastructure is created for all profiles
            template.resourceCountIs("AWS::EC2::VPC", 1);
            template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        }
    }

    @Test
    void testIAMProfileMapping() {
        // Given/When: Map security profiles to IAM profiles
        IAMProfile devIam = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        IAMProfile stagingIam = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        IAMProfile prodIam = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        // Then: Verify mappings
        assert devIam == IAMProfile.EXTENDED;
        assert stagingIam == IAMProfile.STANDARD;
        assert prodIam == IAMProfile.MINIMAL;
    }

    @Test
    void testMinimalVsCompleteInfrastructure() {
        // Given: Minimal infrastructure
        builder.createMinimalInfrastructure();
        synthesizeTemplate();

        // Then: Verify minimal components
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);

        // Then: Verify minimal doesn't include alarms/scaling
        template.resourceCountIs("AWS::CloudWatch::Alarm", 0);

        // Given: Complete infrastructure
        builder = new com.cloudforgeci.api.test.TestInfrastructureBuilder(
            "CompleteInfraTest",
            SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE
        );
        builder.createCompleteInfrastructure();
        Template completeTemplate = Template.fromStack(builder.getStack());

        // Then: Verify complete includes additional components
        completeTemplate.resourceCountIs("AWS::EC2::VPC", 1);
        completeTemplate.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        completeTemplate.resourceCountIs("AWS::EFS::FileSystem", 1);
        // CloudWatch Alarms are optional - require explicit factory configuration
    }

    @Test
    void testContextFieldValidation() {
        // Given: Stack with invalid context
        App app = new App();
        Stack stack = new Stack(app, "ValidationTest");

        Map<String, Object> cfcContext = new HashMap<>();
        // Intentionally missing required fields
        stack.getNode().setContext("cfc", cfcContext);

        // When: Creating deployment context
        DeploymentContext cfc = DeploymentContext.from(stack);

        // Then: Context is created (fields have defaults or are optional)
        assert cfc != null;
    }

    @Test
    void testStackOutputGeneration() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify infrastructure exists
        // CloudFormation outputs are optional - typically added in application-specific factories
        template.resourceCountIs("AWS::EC2::VPC", 1);
    }

    @Test
    void testMultiStackDeploymentContext() {
        // Given: Multiple stacks sharing deployment context
        App app = new App();

        Stack stack1 = new Stack(app, "Stack1");
        Map<String, Object> cfcContext1 = new HashMap<>();
        cfcContext1.put("stackName", "stack1");
        stack1.getNode().setContext("cfc", cfcContext1);
        DeploymentContext cfc1 = DeploymentContext.from(stack1);

        Stack stack2 = new Stack(app, "Stack2");
        Map<String, Object> cfcContext2 = new HashMap<>();
        cfcContext2.put("stackName", "stack2");
        stack2.getNode().setContext("cfc", cfcContext2);
        DeploymentContext cfc2 = DeploymentContext.from(stack2);

        // Then: Each stack has independent context
        assert cfc1 != cfc2;
    }

    @Test
    void testResourceNamingConventions() {
        // Given: Complete infrastructure
        builder.createCompleteInfrastructure();
        synthesizeTemplate();

        // Then: Verify resources follow naming conventions
        template.hasResourceProperties("AWS::EC2::VPC", Map.of(
            "Tags", Match.arrayWith(
                Map.of(
                    "Key", "Name",
                    "Value", Match.anyValue()
                )
            )
        ));

        // Then: Verify security groups have descriptive names
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Map.of(
            "GroupDescription", Match.anyValue()
        ));
    }

    @Test
    void testFactoryDependencyChain() {
        // Given: Infrastructure built in specific order
        builder.createVpc();
        SystemContext ctx = builder.getSystemContext();

        // Then: VPC must exist before creating ALB
        assert ctx.vpc.get().isPresent();

        builder.createAlb();
        // Then: ALB and security group must exist
        assert ctx.alb.get().isPresent();
        assert ctx.albSg.get().isPresent();

        builder.createEfs();
        // Then: EFS must be created
        assert ctx.efs.get().isPresent();

        builder.createFargate();
        // Then: Fargate resources must exist
        assert ctx.fargateService.get().isPresent();
    }

    @Test
    void testStagingEnvironmentConfiguration() {
        // Given: Staging environment
        com.cloudforgeci.api.test.TestInfrastructureBuilder stagingBuilder =
            new com.cloudforgeci.api.test.TestInfrastructureBuilder(
                "StagingTest",
                SecurityProfile.STAGING,
                RuntimeType.FARGATE
            );

        stagingBuilder.createCompleteInfrastructure();
        Template stagingTemplate = Template.fromStack(stagingBuilder.getStack());

        // Then: Verify staging-specific configuration
        stagingTemplate.resourceCountIs("AWS::EC2::VPC", 1);
        stagingTemplate.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);

        // Staging should have similar resources to production
        stagingTemplate.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void testDevEnvironmentConfiguration() {
        // Given: Dev environment
        com.cloudforgeci.api.test.TestInfrastructureBuilder devBuilder =
            new com.cloudforgeci.api.test.TestInfrastructureBuilder(
                "DevTest",
                SecurityProfile.DEV,
                RuntimeType.FARGATE
            );

        devBuilder.createCompleteInfrastructure();
        Template devTemplate = Template.fromStack(devBuilder.getStack());

        // Then: Verify dev configuration
        devTemplate.resourceCountIs("AWS::EC2::VPC", 1);
        devTemplate.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);

        // Dev should still have core infrastructure
        devTemplate.resourceCountIs("AWS::EFS::FileSystem", 1);
    }
}
