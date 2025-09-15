package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class AlbFactoryTest {
  @Test
  void createsAlbListenerAndTg() {
    App app = new App();
    Stack stack = new Stack(app, "Test");
    DeploymentContext cfc = DeploymentContext.from(stack);
    SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, cfc);
    VpcFactory vpc = new VpcFactory(stack, "Vpc", new VpcFactory.Props(cfc));
    new AlbFactory(stack, "Alb", new AlbFactory.Props(cfc));
    new EfsFactory(stack, "Efs", new EfsFactory.Props(cfc));
    new FargateFactory(stack, "Ecs", new FargateFactory.Props(cfc));
    Template t = Template.fromStack(stack);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::LoadBalancer", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::Listener", 1);
    t.resourceCountIs("AWS::ElasticLoadBalancingV2::TargetGroup", 1);
  }
}
