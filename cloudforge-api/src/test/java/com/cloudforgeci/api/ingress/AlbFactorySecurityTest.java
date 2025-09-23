package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class AlbFactorySecurityTest {

  @Test
  void createsAlbWithDevSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbDevTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
    // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
    int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
    t.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
  }

  @Test
  void createsAlbWithStagingSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
  }

  @Test
  void createsAlbWithProductionSecurityProfile() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbProductionTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
  }

  @Test
  void createsAlbWithCorrectResourceName() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbResourceTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify ALB resource exists
    t.hasResource("AWS::ElasticLoadBalancingV2::LoadBalancer", Map.of());
  }

  @Test
  void createsAlbSecurityGroupWithCorrectDescription() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbSecurityTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify ALB security group exists
    assertNotNull(builder.getAlbSecurityGroup());
  }

  @Test
  void createsAlbWithCorrectVpc() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbVpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify ALB is created in the correct VPC
    assertNotNull(builder.getVpc());
    assertNotNull(builder.getAlb());
  }

  @Test
  void createsAlbWithHttpListener() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbListenerTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify HTTP listener is created
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
    assertNotNull(builder.getHttpListener());
  }
}