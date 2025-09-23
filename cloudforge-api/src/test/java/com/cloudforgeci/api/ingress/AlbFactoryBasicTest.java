package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class AlbFactoryBasicTest {
  
  @Test
  void createsAlbWithBasicConfiguration() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbBasicTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify ALB is created
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
    // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
    int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
    t.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
    
    // Verify ALB is internet-facing
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::LoadBalancer", 
        Map.of("Scheme", "internet-facing"));
  }

  @Test
  void createsAlbSecurityGroupWithCorrectVpc() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbSecurityGroupTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify security groups are created
    // Security group count depends on runtime: EC2=4 (VPC,ALB,Instance,EFS), Fargate=3 (VPC,ALB,EFS)
    int expectedSgCount = (builder.getRuntime() == RuntimeType.EC2) ? 4 : 3;
    t.resourceCountIs("AWS::EC2::SecurityGroup", expectedSgCount);
  }

  @Test
  void createsHttpListenerWithFixedResponse() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbListenerTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    
    // Verify HTTP listener is created on port 80
    t.hasResourceProperties("AWS::ElasticLoadBalancingV2::Listener", 
        Map.of("Port", 80, "Protocol", "HTTP"));
  }

  @Test
  void throwsExceptionWhenVpcNotAvailable() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbExceptionTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    
    // Should throw exception when trying to create ALB without VPC
    assertThrows(Exception.class, () -> {
      builder.createAlb();
    });
  }
}
