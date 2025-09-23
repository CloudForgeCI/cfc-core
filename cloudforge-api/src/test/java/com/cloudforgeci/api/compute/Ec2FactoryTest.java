package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
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
    SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, iamProfile, cfc);
    
    VpcFactory vpc = new VpcFactory(stack, "Vpc");
    vpc.injectContexts(); // Manual injection after SystemContext.start()
    vpc.create();
    
    AlbFactory alb = new AlbFactory(stack, "Alb");
    alb.injectContexts(); // Manual injection after SystemContext.start()
    alb.create();
    
    EfsFactory efs = new EfsFactory(stack, "Efs");
    efs.create(ctx);
    
    new FargateFactory(stack, "Ecs", new FargateFactory.Props(cfc));
    
    Ec2Factory ec2 = new Ec2Factory(stack, "Ec2");
    ec2.create(ctx);
    
    Template t = Template.fromStack(stack);
    t.resourceCountIs("AWS::AutoScaling::AutoScalingGroup", 1);
  }
}
