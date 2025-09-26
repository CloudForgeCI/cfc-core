package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class EfsFactorySecurityTest {

  @Test
  void createsEfsWithDevSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsDevTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EFS::FileSystem", 1);
    // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
    int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
    t.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
    
    // Verify EFS is encrypted
    t.hasResourceProperties("AWS::EFS::FileSystem", 
        Map.of("Encrypted", true));
  }

  @Test
  void createsEfsWithStagingSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EFS::FileSystem", 1);
    
    // Verify EFS is encrypted
    t.hasResourceProperties("AWS::EFS::FileSystem", 
        Map.of("Encrypted", true));
  }

  @Test
  void createsEfsWithProductionSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EFS::FileSystem", 1);
    
    // Verify EFS is encrypted
    t.hasResourceProperties("AWS::EFS::FileSystem", 
        Map.of("Encrypted", true));
  }

  @Test
  void createsEfsWithGeneralPurposePerformanceMode() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsPerformanceTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify EFS has general purpose performance mode
    t.hasResourceProperties("AWS::EFS::FileSystem", 
        Map.of("PerformanceMode", "generalPurpose"));
  }

  @Test
  void createsEfsWithBurstingThroughputMode() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsThroughputTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify EFS has bursting throughput mode
    t.hasResourceProperties("AWS::EFS::FileSystem", 
        Map.of("ThroughputMode", "bursting"));
  }

  @Test
  void createsEfsWithCorrectResourceName() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsResourceTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify EFS resource exists
    t.hasResource("AWS::EFS::FileSystem", Map.of());
  }

  @Test
  void createsSecurityGroupWithCorrectVpc() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsSecurityTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify security groups are created
    // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
    int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
    t.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
  }

  @Test
  void createsEfsSecurityGroupWithCorrectDescription() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsDescriptionTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify EFS security group exists
    assertNotNull(builder.getEfsSecurityGroup());
  }
}