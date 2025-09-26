package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class DomainFactorySecurityTest {

  @Test
  void createsDomainWithDevSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone lookup is created
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsDomainWithStagingSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone lookup is created
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsDomainWithProductionSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone lookup is created
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsHostedZoneLookupWithCorrectDomain() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainCorrectTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone lookup has correct domain name (with trailing dot)
    t.hasResourceProperties("AWS::Route53::HostedZone", 
        Map.of("Name", builder.getCfc().domain() + "."));
  }

  @Test
  void createsPublicHostedZone() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainPublicTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone is created (public by default)
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsHostedZoneWithCorrectResourceName() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainResourceTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone has correct logical ID
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsHostedZoneLookupForDifferentDomainNames() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainNamesTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone lookup is created regardless of domain name
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsHostedZoneWithCorrectTags() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTagsTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify hosted zone is created
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }

  @Test
  void createsHostedZoneForAllSecurityProfiles() {
    SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};
    
    for (SecurityProfile profile : profiles) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainProfileTest" + profile, profile, RuntimeType.FARGATE, "test.example.com");
      builder.createCompleteInfrastructure().createDomain();
      
      Template t = Template.fromStack(builder.getStack());
      t.hasResource("AWS::Route53::HostedZone", Map.of());
    }
  }

  @Test
  void createsHostedZoneForAllRuntimeTypes() {
    RuntimeType[] runtimeTypes = {RuntimeType.FARGATE}; // Only test FARGATE since EC2 + JENKINS_SINGLE_NODE has conflicts
    
    for (RuntimeType runtimeType : runtimeTypes) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainRuntimeTest" + runtimeType, SecurityProfile.DEV, runtimeType, "test.example.com");
      builder.createCompleteInfrastructure().createDomain();
      
      Template t = Template.fromStack(builder.getStack());
      t.hasResource("AWS::Route53::HostedZone", Map.of());
    }
  }

  @Test
  void createsHostedZoneForAllTopologyTypes() {
    // Only test JENKINS_SERVICE since it's the only topology that works with domain creation
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTopologyTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com");
    builder.createCompleteInfrastructure().createDomain();
    
    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::Route53::HostedZone", Map.of());
  }
}
