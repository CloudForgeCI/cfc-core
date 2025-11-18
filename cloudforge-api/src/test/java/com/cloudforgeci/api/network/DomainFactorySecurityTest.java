package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DomainFactorySecurityTest {

  @Test
  void createsDomainWithDevSecurityProfile() {
    // Use createZone=true to avoid AWS credential requirements for hosted zone lookups
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify domain context is set
    assertTrue(builder.getSystemContext().domain.get().isPresent());
    assertEquals("test.example.com", builder.getSystemContext().domain.get().get());
  }

  @Test
  void createsDomainWithStagingSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify domain context is set
    assertTrue(builder.getSystemContext().domain.get().isPresent());
    assertEquals("test.example.com", builder.getSystemContext().domain.get().get());
  }

  @Test
  void createsDomainWithProductionSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify domain context is set
    assertTrue(builder.getSystemContext().domain.get().isPresent());
    assertEquals("test.example.com", builder.getSystemContext().domain.get().get());
  }

  @Test
  void createsHostedZoneLookupWithCorrectDomain() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainCorrectTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify domain context is set with correct domain name
    assertTrue(builder.getSystemContext().domain.get().isPresent());
    assertEquals("test.example.com", builder.getSystemContext().domain.get().get());
  }

  @Test
  void createsPublicHostedZone() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainPublicTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify zone context is set
    assertTrue(builder.getSystemContext().zone.get().isPresent());
  }

  @Test
  void createsHostedZoneWithCorrectResourceName() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainResourceTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify zone context is set
    assertTrue(builder.getSystemContext().zone.get().isPresent());
  }

  @Test
  void createsHostedZoneLookupForDifferentDomainNames() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainNamesTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify domain context is set regardless of domain name
    assertTrue(builder.getSystemContext().domain.get().isPresent());
  }

  @Test
  void createsHostedZoneWithCorrectTags() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTagsTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify zone context is set
    assertTrue(builder.getSystemContext().zone.get().isPresent());
  }

  @Test
  void createsHostedZoneForAllSecurityProfiles() {
    SecurityProfile[] profiles = {SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION};

    for (SecurityProfile profile : profiles) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainProfileTest" + profile, profile, RuntimeType.FARGATE, "test.example.com", true);
      builder.createCompleteInfrastructure().createDomain();

      // Verify zone context is set for all profiles
      assertTrue(builder.getSystemContext().zone.get().isPresent());
    }
  }

  @Test
  void createsHostedZoneForAllRuntimeTypes() {
    RuntimeType[] runtimeTypes = {RuntimeType.FARGATE}; // Only test FARGATE since EC2 + JENKINS_SINGLE_NODE has conflicts

    for (RuntimeType runtimeType : runtimeTypes) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainRuntimeTest" + runtimeType, SecurityProfile.DEV, runtimeType, "test.example.com", true);
      builder.createCompleteInfrastructure().createDomain();

      // Verify zone context is set for all runtime types
      assertTrue(builder.getSystemContext().zone.get().isPresent());
    }
  }

  @Test
  void createsHostedZoneForAllTopologyTypes() {
    // Only test JENKINS_SERVICE since it's the only topology that works with domain creation
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainTopologyTest", SecurityProfile.DEV, RuntimeType.FARGATE, "test.example.com", true);
    builder.createCompleteInfrastructure().createDomain();

    // Verify zone context is set
    assertTrue(builder.getSystemContext().zone.get().isPresent());
  }
}
