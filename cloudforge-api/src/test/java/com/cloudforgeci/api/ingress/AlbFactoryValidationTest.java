package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import software.amazon.awscdk.*;
import software.amazon.awscdk.assertions.Template;

public class AlbFactoryValidationTest {
  @Disabled("Revert when fix validating domain/subdomain")
  @Test
  void requiresDomainPairWhenOneProvided() {
    App app = new App();
    Stack stack = new Stack(app, "BadIngress");

    var cfc = DeploymentContext.from(app);
    var vpc = new VpcFactory(stack, "Vpc", new VpcFactory.Props(cfc));
    new AlbFactory(stack, "Alb", new AlbFactory.Props(cfc));
    new EfsFactory(stack, "Efs", new EfsFactory.Props(cfc));
    new FargateFactory(stack, "Ecs", new FargateFactory.Props(cfc));
    assertThrows(RuntimeException.class, () -> Template.fromStack(stack));
  }
}
