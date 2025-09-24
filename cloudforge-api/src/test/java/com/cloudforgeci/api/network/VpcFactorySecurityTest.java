package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class VpcFactorySecurityTest {

  @Test
  void createsVpcWithDevSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcDevTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
    t.resourceCountIs("AWS::EC2::Subnet", 4); // 2 public + 2 private
    t.resourceCountIs("AWS::EC2::RouteTable", 4); // 1 main + 1 public + 2 private
  }

  @Test
  void createsVpcWithStagingSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
    t.resourceCountIs("AWS::EC2::Subnet", 4); // 2 public + 2 private
  }

  @Test
  void createsVpcWithProductionSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 2); // Production always creates 2 NAT gateways
    t.resourceCountIs("AWS::EC2::Subnet", 4); // 2 public + 2 private
  }

  @Test
  void createsVpcWithCorrectResourceName() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcResourceTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify VPC resource exists
    t.hasResource("AWS::EC2::VPC", Map.of());
  }

  @Test
  void createsVpcWithNatGateway() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcNatTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify NAT Gateway is not created (set to 0 by default)
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
  }

  @Test
  void createsVpcWithPublicAndPrivateSubnets() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcSubnetTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify subnets are created
    t.resourceCountIs("AWS::EC2::Subnet", 4); // 2 public + 2 private
  }

  @Test
  void createsVpcWithRouteTables() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcRouteTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify route tables are created
    t.resourceCountIs("AWS::EC2::RouteTable", 4); // 1 main + 1 public + 2 private
  }
}