package com.cloudforgeci.api.network;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class VpcFactoryTest {

  @Test
  void createsVpcWithNat() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("VpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EC2::NatGateway", 0);
  }
}
