package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class DomainFactoryTest {

  @Test
  void createsHostedZoneWhenCreateZoneIsTrue() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "test.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainCreateTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc()
           .createAlb()
           .createEfs()
           .createFargate()
           .createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::Route53::HostedZone", 1);
    t.hasResourceProperties("AWS::Route53::HostedZone", Map.of(
      "Name", "test.example.com."
    ));
  }

  @Test
  void createsHostedZoneWithDestroyPolicyForDev() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "dev.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainDevTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::Route53::HostedZone", Map.of(
      "DeletionPolicy", "Delete"
    ));
  }

  @Test
  void createsHostedZoneWithRetainPolicyForProduction() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "prod.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainProdTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::Route53::HostedZone", Map.of(
      "DeletionPolicy", "Retain"
    ));
  }

  @Test
  void createsHostedZoneWithDomainNameEndsWithDot() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainDotTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::Route53::HostedZone", Map.of(
      "Name", "example.com."
    ));
  }

  @Test
  void setsSystemContextDomainAndSubdomain() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "example.com");
    context.put("subdomain", "jenkins");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainContextTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    var systemContext = builder.getSystemContext();
    assert systemContext.domain.get().isPresent();
    assert systemContext.subdomain.get().isPresent();
  }

  @Test
  void skipsCreationWhenDomainIsNull() {
    Map<String, Object> context = new HashMap<>();
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainNullTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::Route53::HostedZone", 0);
  }

  @Test
  void skipsCreationWhenDomainIsBlank() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "");
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainBlankTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::Route53::HostedZone", 0);
  }

  @Test
  void createsHostedZoneAcrossMultipleSecurityProfiles() {
    for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
      Map<String, Object> context = new HashMap<>();
      context.put("domain", "test-" + profile.toString().toLowerCase() + ".example.com");
      context.put("createZone", true);
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainMultiProfile" + profile, profile, RuntimeType.FARGATE, context);
      builder.createVpc().createAlb().createEfs().createFargate().createDomain();

      Template t = Template.fromStack(builder.getStack());
      t.resourceCountIs("AWS::Route53::HostedZone", 1);
    }
  }

  @Test
  void createsHostedZoneWithSubdomain() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "example.com");
    context.put("subdomain", "jenkins");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainSubdomainTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::Route53::HostedZone", 1);
  }

  @Test
  void createsHostedZoneForPublicZone() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "public.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainPublicTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::Route53::HostedZone", Map.of(
      "Name", "public.example.com."
    ));
  }

  @Test
  void createsHostedZoneWithStagingProfile() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "staging.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::Route53::HostedZone", Map.of(
      "DeletionPolicy", "Delete"
    ));
  }

  @Test
  void createsHostedZoneForFargateRuntime() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "test-fargate.example.com");
    context.put("createZone", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("DomainFargateRuntime", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createVpc().createAlb().createEfs().createFargate().createDomain();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::Route53::HostedZone", 1);
  }
}
