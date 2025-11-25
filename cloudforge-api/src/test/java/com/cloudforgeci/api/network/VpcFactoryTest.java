package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class VpcFactoryTest {

  @Test
  void createsVpcWithNoNatForDevProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
  }

  @Test
  void createsVpcWithNatForProductionProfile() {
    Map<String, Object> context = new HashMap<>();
    context.put("networkMode", "private-with-nat");
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcProdTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 2);
  }

  @Test
  void createsVpcWithPublicSubnets() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcPublicTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::Subnet", 4);
    t.hasResourceProperties("AWS::EC2::Subnet", Map.of(
      "MapPublicIpOnLaunch", true
    ));
  }

  @Test
  void createsVpcWithPrivateSubnets() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcPrivateTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::Subnet", Map.of(
      "MapPublicIpOnLaunch", false
    ));
  }

  @Test
  void createsVpcWithInternetGateway() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcIgwTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::InternetGateway", 1);
    t.resourceCountIs("AWS::EC2::VPCGatewayAttachment", 1);
  }

  @Test
  void createsVpcWithRouteTables() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcRouteTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::RouteTable", Map.of(
      "VpcId", Match.objectLike(Map.of("Ref", Match.anyValue()))
    ));
  }

  @Test
  void createsVpcWith2AvailabilityZones() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcAzTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::Subnet", 4);
  }

  @Test
  void createsVpcWithCidr24Subnets() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcCidrTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::Subnet", Map.of(
      "CidrBlock", Match.stringLikeRegexp(".*\\.0/24")
    ));
  }

  @Test
  void createsVpcWithNameTag() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcNameTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::VPC", Map.of(
      "Tags", Match.arrayWith(List.of(
        Match.objectLike(Map.of("Key", "Name"))
      ))
    ));
  }

  @Test
  void createsVpcAcrossMultipleSecurityProfiles() {
    for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcMultiProfile" + profile, profile, RuntimeType.FARGATE);
      builder.createCompleteInfrastructure();

      Template t = Template.fromStack(builder.getStack());
      t.resourceCountIs("AWS::EC2::VPC", 1);
    }
  }

  @Test
  void createsVpcForFargateRuntime() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcFargateRuntime", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
  }

  @Test
  void createsVpcWithPublicNoNatNetworkMode() {
    Map<String, Object> context = new HashMap<>();
    context.put("networkMode", "public-no-nat");
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcPublicNoNatTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
  }

  @Test
  void createsVpcWithPrivateWithNatNetworkMode() {
    Map<String, Object> context = new HashMap<>();
    context.put("networkMode", "private-with-nat");
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcPrivateNatTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 2);
  }

  @Test
  void createsVpcWithSubnetRouteTableAssociations() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcSubnetAssocTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::SubnetRouteTableAssociation", 4);
  }
}
