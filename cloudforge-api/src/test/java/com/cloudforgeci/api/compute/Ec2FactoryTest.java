package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import org.junit.jupiter.api.Disabled;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class Ec2FactoryTest {

  @Disabled("resolve path first")
  @Test
  void createsAsgAndRegistersToAlb() {
    App app = new App();
    Stack stack = new Stack(app, "Test");
    DeploymentContext cfc = DeploymentContext.from(stack);
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, iamProfile, cfc);

    VpcFactory vpc = new VpcFactory(stack, "Vpc");
    vpc.create();

    AlbFactory alb = new AlbFactory(stack, "Alb");
    alb.create();

    EfsFactory efs = new EfsFactory(stack, "Efs");
    efs.create();

    new FargateFactory(stack, "Ecs");

    Ec2Factory ec2 = new Ec2Factory(stack, "Ec2");
    ec2.create();

    Template t = Template.fromStack(stack);
    t.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
  }
}
