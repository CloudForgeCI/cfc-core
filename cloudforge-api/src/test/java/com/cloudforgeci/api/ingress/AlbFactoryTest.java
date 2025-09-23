package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
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
}
