package com.cloudforgeci.api.ingress;

import com.cloudforgeci.api.compute.FargateFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

public class AlbFactoryValidationTest {
  @Disabled("Revert when fix validating domain/subdomain")
  @Test
  void requiresDomainPairWhenOneProvided() {
    App app = new App();
    Stack stack = new Stack(app, "BadIngress");

    var cfc = DeploymentContext.from(stack);
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);

    VpcFactory vpc = new VpcFactory(stack, "Vpc");
    vpc.create();

    AlbFactory alb = new AlbFactory(stack, "Alb");
    alb.create();

    EfsFactory efs = new EfsFactory(stack, "Efs");
    efs.create();

    new FargateFactory(stack, "Ecs");
    assertThrows(RuntimeException.class, () -> Template.fromStack(stack));
  }
}
