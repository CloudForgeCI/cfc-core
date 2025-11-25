package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AlbFactoryTest {

  @Test
  void createsAlbListenerAndTg() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
  }

  @Test
  void createsAlbWithInternetFacingScheme() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbInternetTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", Map.of(
      "Scheme", "internet-facing"
    ));
  }

  @Test
  void createsAlbWithSecurityGroup() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbSgTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", Map.of(
      "SecurityGroups", Match.arrayWith(List.of(
        Match.objectLike(Map.of("Fn::GetAtt", Match.anyValue()))
      ))
    ));
  }

  @Test
  void createsAlbWithHttpListener() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbHttpTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
      "Protocol", "HTTP",
      "Port", 80
    ));
  }

  @Test
  void createsAlbInVpc() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbVpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", Map.of(
      "Subnets", Match.anyValue()
    ));
  }

  @Test
  void createsAlbWithApplicationType() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbTypeTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", Map.of(
      "Type", "application"
    ));
  }

  @Test
  void createsAlbAcrossMultipleSecurityProfiles() {
    for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbMultiProfile" + profile, profile, RuntimeType.FARGATE);
      builder.createCompleteInfrastructure();

      Template t = Template.fromStack(builder.getStack());
      t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    }
  }

  @Test
  void createsAlbForFargateRuntime() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbFargateRuntime", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
  }

  @Test
  void createsAlbWithDefaultActions() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbDefaultActionTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", Map.of(
      "DefaultActions", Match.anyValue()
    ));
  }

  @Test
  void createsSecurityGroupForAlb() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbSgCreateTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    // Verify that at least one security group was created
    t.resourceCountIs("AWS::EC2::SecurityGroup", 3);
  }
}
