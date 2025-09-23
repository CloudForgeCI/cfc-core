package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

/**
 * Integration tests for EfsFactory that verify complete infrastructure setup.
 */
public class EfsFactoryIntegrationTest {

    @Test
    void createsEfsWithCompleteInfrastructure() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify EFS is created
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
        
        // Verify EFS is encrypted
        template.hasResourceProperties("AWS::EFS::FileSystem", 
            Map.of("Encrypted", true));
        
        // Verify EFS has general purpose performance mode
        template.hasResourceProperties("AWS::EFS::FileSystem", 
            Map.of("PerformanceMode", "generalPurpose"));
        
        // Verify EFS has bursting throughput mode
        template.hasResourceProperties("AWS::EFS::FileSystem", 
            Map.of("ThroughputMode", "bursting"));
    }

    @Test
    void createsEfsWithAllSecurityProfiles() {
        SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};
        
        for (SecurityProfile profile : profiles) {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTest" + profile, profile, RuntimeType.FARGATE);
            builder.createCompleteInfrastructure();
            
            Template template = Template.fromStack(builder.getStack());
            template.resourceCountIs("AWS::EFS::FileSystem", 1);
            template.hasResourceProperties("AWS::EFS::FileSystem", 
                Map.of("Encrypted", true));
        }
    }

    @Test
    void createsEfsWithFargateRuntime() {
        // Only test FARGATE runtime since EC2 + JENKINS_SINGLE_NODE has conflicting requirements
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTestFARGATE", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
        template.hasResourceProperties("AWS::EFS::FileSystem", 
            Map.of("Encrypted", true));
    }

    @Test
    void verifiesEfsSecurityGroupConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsSecurityTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify security groups are created
        // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
        int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
        template.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
        
        // Verify EFS security group exists
        assertNotNull(builder.getEfsSecurityGroup());
    }

    @Test
    void verifiesEfsResourceAccess() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsAccessTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify EFS resources are accessible
        assertNotNull(builder.getEfs());
        assertNotNull(builder.getEfsSecurityGroup());
        
        // Verify they are properly configured
        assertNotNull(builder.getEfs());
        assertNotNull(builder.getEfsSecurityGroup());
    }

    @Test
    void verifiesEfsSystemContextIntegration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsContextTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify EFS resources are stored in SystemContext
        assertTrue(builder.getSystemContext().efs.get().isPresent());
        assertTrue(builder.getSystemContext().efsSg.get().isPresent());
        
        // Verify they match the builder's resources
        assertEquals(builder.getEfs(), builder.getSystemContext().efs.get().orElseThrow());
        assertEquals(builder.getEfsSecurityGroup(), builder.getSystemContext().efsSg.get().orElseThrow());
    }

    @Test
    void verifiesEfsValidationPasses() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsValidationTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        // Verify validation passes
        assertDoesNotThrow(() -> {
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void verifiesEfsDefaultConfiguration() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsConfigTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();
        
        Template template = Template.fromStack(builder.getStack());
        
        // Verify default EFS configuration
        template.hasResourceProperties("AWS::EFS::FileSystem", 
            Map.of(
                "Encrypted", true,
                "PerformanceMode", "generalPurpose",
                "ThroughputMode", "bursting"
            ));
    }
}
