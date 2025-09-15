package com.cloudforgeci.api.scaling;

import com.cloudforgeci.api.core.DeploymentContext;

import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.compute.Ec2Factory;
import org.junit.jupiter.api.Disabled;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class ScalingFactoryTest {
  @Disabled
  @Test
  void createsScalingPoliciesForAsg() {
    App app = new App();
    Stack stack = new Stack(app, "Test");
    var cfc = DeploymentContext.from(app);
    var vpc = new VpcFactory(stack, "Vpc", new VpcFactory.Props(cfc));
    var alb = new AlbFactory(stack, "Alb", new AlbFactory.Props(cfc));
    var efs = new EfsFactory(stack, "Efs", new EfsFactory.Props(cfc));
    var ec2 = new Ec2Factory(stack, "Ec2", new Ec2Factory.Props(cfc));
    new ScalingFactory(stack, "Scaling");
    Template t = Template.fromStack(stack);
    t.resourceCountIs("AWS::AutoScaling::ScalingPolicy", 1);
  }
}
