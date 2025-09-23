package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Integration tests for AlbFactory that verify complete infrastructure setup.
 */
public class AlbFactoryIntegrationTest {

    @Test
    void createsAlbWithCompleteInfrastructure() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify ALB is created
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
        
        // Verify ALB is internet-facing
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", 
            Map.of("Scheme", "internet-facing"));
        
        // Verify HTTP listener is created on port 80
        template.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", 
            Map.of("Port", 80, "Protocol", "HTTP"));
    }

    @Test
    void createsAlbWithAllSecurityProfiles() {
        SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};
        
        for (SecurityProfile profile : profiles) {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbTest" + profile, profile, RuntimeType.FARGATE);
            builder.createCompleteInfrastructure();
            
            Template template = Template.fromStack(builder.getStack());
            template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
            template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
        }
    }

    @Test
    void createsAlbWithFargateRuntime() {
        // Only test FARGATE runtime since EC2 + JENKINS_SINGLE_NODE has conflicting requirements
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbTestFARGATE", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
        template.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
    }

    @Test
    void verifiesAlbSecurityGroupConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbSecurityTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify security groups are created
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
        
        // Verify ALB security group exists
        assertNotNull(builder.getAlbSecurityGroup());
    }

    @Test
    void verifiesAlbResourceAccess() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbAccessTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify ALB resources are accessible
        assertNotNull(builder.getAlb());
        assertNotNull(builder.getHttpListener());
        assertNotNull(builder.getAlbSecurityGroup());
        
        // Verify they are properly configured
        assertNotNull(builder.getAlb());
        assertNotNull(builder.getAlbSecurityGroup());
    }

    @Test
    void verifiesAlbSystemContextIntegration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbContextTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify ALB resources are stored in SystemContext
        assertTrue(builder.getSystemContext().alb.get().isPresent());
        assertTrue(builder.getSystemContext().albSg.get().isPresent());
        assertTrue(builder.getSystemContext().http.get().isPresent());
        
        // Verify they match the builder's resources
        assertEquals(builder.getAlb(), builder.getSystemContext().alb.get().orElseThrow());
        assertEquals(builder.getAlbSecurityGroup(), builder.getSystemContext().albSg.get().orElseThrow());
        assertEquals(builder.getHttpListener(), builder.getSystemContext().http.get().orElseThrow());
    }

    @Test
    void verifiesAlbValidationPasses() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbValidationTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify validation passes
        assertDoesNotThrow(() -> {
            Template.fromStack(builder.getStack());
        });
    }
}
