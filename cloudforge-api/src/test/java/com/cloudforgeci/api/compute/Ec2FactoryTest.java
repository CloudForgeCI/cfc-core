package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import org.junit.jupiter.api.Disabled;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class Ec2FactoryTest {

  @Disabled("resolve path first")
  @Test
  void createsAsgAndRegistersToAlb() {
    App app = new App();
    Stack stack = new Stack(app, "Test");
    DeploymentContext cfc = DeploymentContext.from(stack);
    SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, cfc);
    var vpc = new VpcFactory(stack, "Vpc", new VpcFactory.Props(cfc));
    new AlbFactory(stack, "Alb", new AlbFactory.Props(cfc));
    new EfsFactory(stack, "Efs", new EfsFactory.Props(cfc));
    new FargateFactory(stack, "Ecs", new FargateFactory.Props(cfc));
    new Ec2Factory(stack, "Ec2", new Ec2Factory.Props(cfc));
    Template t = Template.fromStack(stack);
    t.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 0);
  }
}
