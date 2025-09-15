package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.application.JenkinsBootstrap;
import com.cloudforgeci.api.core.*;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.observability.LoggingFactory;
import com.cloudforgeci.api.security.CertificateFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.observability.AlarmFactory;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.constructs.Construct;

public class JenkinsFactory {
  public record JenkinsSystem(
      VpcFactory vpc,
      AlbFactory alb,
      EfsFactory efs
  ) {}

  public static JenkinsSystem createEc2(Construct scope, String id, DeploymentContext cfc) {
    SystemContext ctx = SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, cfc);
    VpcFactory vpc = new VpcFactory(scope, id + "Vpc", new VpcFactory.Props(cfc));
    AlbFactory alb = new AlbFactory(scope, id + "Alb", new AlbFactory.Props(cfc));
    EfsFactory efs = new EfsFactory(scope, id + "Efs", new EfsFactory.Props(cfc));
    LoggingFactory log = new LoggingFactory(scope, id + "Logging");
    Ec2Factory ec2 = new Ec2Factory(scope, id + "Ec2", new Ec2Factory.Props(cfc));
    new AlarmFactory(scope, id + "Alarms", null);
    DomainFactory domain = new DomainFactory(scope, id + "Domain", new DomainFactory.Props(cfc));
    new CertificateFactory(scope, id + "Certificate", new CertificateFactory.Props(cfc));
    return new JenkinsSystem(vpc, alb, efs);
  }

  public static JenkinsSystem createFargate(Construct scope, String id, DeploymentContext cfc) {
    SystemContext ctx = SystemContext.start(scope, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, cfc);
    new FlowLogFactory(scope, id + "Flowlog");
    VpcFactory vpc = new VpcFactory(scope, id + "Vpc", new VpcFactory.Props(cfc));
    AlbFactory alb = new AlbFactory(scope, id + "Alb", new AlbFactory.Props(cfc));
    EfsFactory efs = new EfsFactory(scope, id + "Efs", new EfsFactory.Props(cfc));
    LoggingFactory log = new LoggingFactory(scope, id + "Logging");
    LogDriver logDriver = LogDriver.awsLogs(AwsLogDriverProps.builder().logGroup(ctx.logs.get().orElseThrow()).streamPrefix("jenkins").build());


    FargateFactory fargate = new FargateFactory(scope, id + "Fargate", new FargateFactory.Props(cfc));
    new JenkinsBootstrap(scope, id + "Jenkins", new JenkinsBootstrap.Props(cfc));
    new AlarmFactory(scope, id + "Alarms", null);
    DomainFactory domain = new DomainFactory(scope, id + "Domain", new DomainFactory.Props(cfc));
    //new CertificateFactory(scope, id + "Certificate", new CertificateFactory.Props(cfc));
    return new JenkinsSystem(vpc, alb, efs);
  }
}
