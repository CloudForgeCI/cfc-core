package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Integration tests for VpcFactory that verify complete infrastructure setup.
 */
public class VpcFactoryIntegrationTest {

    @Test
    void createsVpcWithCompleteInfrastructure() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify VPC is created
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::EC2::NatGateway", 0);
        template.resourceCountIs("AWS::EC2::Subnet", 4); // 2 public + 2 private
        template.resourceCountIs("AWS::EC2::InternetGateway", 1);
        template.resourceCountIs("AWS::EC2::RouteTable", 4); // main + public + private
    }

    @Test
    void createsVpcWithAllSecurityProfiles() {
        SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};
        int[] expectedNatGateways = {0, 0, 2}; // DEV=0, STAGING=0 (public-no-nat), PRODUCTION=2 (always)
        
        for (int i = 0; i < profiles.length; i++) {
            SecurityProfile profile = profiles[i];
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcTest" + profile, profile, RuntimeType.FARGATE);
            builder.createCompleteInfrastructure();
            
            Template template = Template.fromStack(builder.getStack());
            template.resourceCountIs("AWS::EC2::VPC", 1);
            template.resourceCountIs("AWS::EC2::NatGateway", expectedNatGateways[i]);
            template.resourceCountIs("AWS::EC2::Subnet", 4);
        }
    }

    @Test
    void createsVpcWithFargateRuntime() {
        // Only test FARGATE runtime since EC2 + JENKINS_SINGLE_NODE has conflicting requirements
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcTestFARGATE", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::EC2::NatGateway", 0);
        template.resourceCountIs("AWS::EC2::Subnet", 4);
    }

    @Test
    void verifiesVpcConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcConfigTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify VPC has correct number of subnets (2 AZs * 2 subnet types = 4 subnets)
        template.resourceCountIs("AWS::EC2::Subnet", 4);
        
        // Verify NAT Gateway is not created (set to 0 by default)
        template.resourceCountIs("AWS::EC2::NatGateway", 0);
        
        // Verify Internet Gateway is created
        template.resourceCountIs("AWS::EC2::InternetGateway", 1);
    }

    @Test
    void verifiesVpcResourceAccess() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcAccessTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify VPC resource is accessible
        assertNotNull(builder.getVpc());
        
        // Verify VPC has correct configuration
        assertNotNull(builder.getVpc().getVpcId());
        assertNotNull(builder.getVpc().getVpcArn());
    }

    @Test
    void verifiesVpcSystemContextIntegration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcContextTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify VPC resource is stored in SystemContext
        assertTrue(builder.getSystemContext().vpc.get().isPresent());
        
        // Verify it matches the builder's resource
        assertEquals(builder.getVpc(), builder.getSystemContext().vpc.get().orElseThrow());
    }

    @Test
    void verifiesVpcValidationPasses() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcValidationTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify validation passes
        assertDoesNotThrow(() -> {
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void verifiesVpcDefaultConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcDefaultTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify default VPC configuration
        template.resourceCountIs("AWS::EC2::VPC", 1);
        template.resourceCountIs("AWS::EC2::NatGateway", 0);
        template.resourceCountIs("AWS::EC2::Subnet", 4);
        template.resourceCountIs("AWS::EC2::InternetGateway", 1);
        template.resourceCountIs("AWS::EC2::RouteTable", 4);
    }

    @Test
    void verifiesVpcSubnetConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcSubnetTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify subnets have correct CIDR mask (24)
        template.hasResourceProperties("AWS::EC2::Subnet", 
            Map.of("CidrBlock", "10.0.0.0/24"));
    }
}
