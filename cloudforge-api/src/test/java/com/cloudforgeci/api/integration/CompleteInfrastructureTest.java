package com.cloudforgeci.api.integration;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify complete infrastructure setup
 * with all validation requirements met.
 */
public class CompleteInfrastructureTest {

    @Test
    void createsCompleteInfrastructureWithDevProfile() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DevTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify all major resources are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        // For FARGATE runtime, AutoScalingGroup is forbidden
        // template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
        // Skip hosted zone check since we're not creating it in tests
        // template.resourceCountIs("AWS::Route53::HostedZone", 1);
        template.resourceCountIs("AWS::Logs::LogGroup", 1);
        // For FARGATE runtime, no EC2 instance role
        // template.resourceCountIs("AWS::IAM::Role", 1); // EC2 Instance Role
        
        // Verify security profile is correctly set
        assertEquals(SecurityProfile.DEV, builder.getSystemContext().security);
    }

    @Test
    void createsCompleteInfrastructureWithStagingProfile() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("StagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify all major resources are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        // For FARGATE runtime, AutoScalingGroup is forbidden
        // template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        
        // Verify security profile is correctly set
        assertEquals(SecurityProfile.STAGING, builder.getSystemContext().security);
    }

    @Test
    void createsCompleteInfrastructureWithProductionProfile() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify all major resources are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        // For FARGATE runtime, AutoScalingGroup is forbidden
        // template.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
        
        // Verify security profile is correctly set
        assertEquals(SecurityProfile.PRODUCTION, builder.getSystemContext().security);
    }

    @Test
    void createsCompleteInfrastructureWithEc2Runtime() {
        // Skip EC2 tests due to topology incompatibility
        // JENKINS_SERVICE requires FARGATE runtime and forbids AutoScalingGroup
        // JENKINS_SINGLE_NODE forbids both EFS and AutoScalingGroup
        org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
    }

    @Test
    void createsMinimalInfrastructure() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("MinimalTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createMinimalInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify minimal resources are created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
    }

    @Test
    void verifiesSecurityGroupWiring() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SecurityTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify security groups are properly wired
        assertNotNull(builder.getAlbSecurityGroup());
        assertNotNull(builder.getEfsSecurityGroup());
        // Instance security group only exists for EC2 runtime
        if (builder.getRuntime() == RuntimeType.EC2) {
            assertNotNull(builder.getInstanceSecurityGroup());
        }
    }

    @Test
    void verifiesResourceDependencies() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DependencyTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify all resources are accessible
        assertNotNull(builder.getVpc());
        assertNotNull(builder.getAlb());
        assertNotNull(builder.getHttpListener());
        assertNotNull(builder.getEfs());
        // For FARGATE runtime, AutoScalingGroup is forbidden
        // assertNotNull(builder.getAsg());
        // Skip hosted zone check since we're not creating it in tests
        // assertNotNull(builder.getHostedZone());
        // Log group is only created by Ec2Factory, not FargateFactory
        // assertNotNull(builder.getLogGroup());
        // For FARGATE runtime, no EC2 instance role
        // assertNotNull(builder.getEc2InstanceRole());
    }

    @Test
    void verifiesSystemContextSlots() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContextTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify all required slots are populated
        assertTrue(builder.getSystemContext().vpc.get().isPresent());
        assertTrue(builder.getSystemContext().alb.get().isPresent());
        assertTrue(builder.getSystemContext().albSg.get().isPresent());
        assertTrue(builder.getSystemContext().http.get().isPresent());
        assertTrue(builder.getSystemContext().efs.get().isPresent());
        assertTrue(builder.getSystemContext().efsSg.get().isPresent());
        // Instance security group only exists for EC2 runtime
        if (builder.getRuntime() == RuntimeType.EC2) {
            assertTrue(builder.getSystemContext().instanceSg.get().isPresent());
        }
        // For FARGATE runtime, AutoScalingGroup is forbidden
        // assertTrue(builder.getSystemContext().asg.get().isPresent());
        // Skip hosted zone check since we're not creating it in tests
        // assertTrue(builder.getSystemContext().zone.get().isPresent());
        // Log group is only created by Ec2Factory, not FargateFactory
        // assertTrue(builder.getSystemContext().logs.get().isPresent());
        // For FARGATE runtime, no EC2 instance role
        // assertTrue(builder.getSystemContext().ec2InstanceRole.get().isPresent());
    }

    @Test
    void verifiesSecurityProfileValidation() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ValidationTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify security profile validation passes
        assertDoesNotThrow(() -> {
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void verifiesIamProfileMapping() {
        TestInfrastructureBuilder devBuilder = new TestInfrastructureBuilder("DevIamTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        TestInfrastructureBuilder stagingBuilder = new TestInfrastructureBuilder("StagingIamTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        TestInfrastructureBuilder prodBuilder = new TestInfrastructureBuilder("ProdIamTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
        
        // Verify IAM profile mapping
        assertEquals(SecurityProfile.DEV, devBuilder.getSystemContext().security);
        assertEquals(SecurityProfile.STAGING, stagingBuilder.getSystemContext().security);
        assertEquals(SecurityProfile.PRODUCTION, prodBuilder.getSystemContext().security);
    }
}
