package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.network.VpcFactory;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.Template;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EfsFactoryTest {
  @Test
  void createsEncryptedEfs() {
    App app = new App();
    Stack stack = new Stack(app, "Test");
    var cfc = DeploymentContext.from(app);
    SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, cfc);
    VpcFactory vpc = new VpcFactory(stack, "Vpc", new VpcFactory.Props(cfc));
    new AlbFactory(stack, "Alb", new AlbFactory.Props(cfc));
    new EfsFactory(stack, "Efs", new EfsFactory.Props(cfc));
    new FargateFactory(stack, "Ecs", new FargateFactory.Props(cfc));
    Template t = Template.fromStack(stack);
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of("Encrypted", true));
  }
}
