package com.cloudforgeci.community.compute.jenkins;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.compute.JenkinsFactory;

import software.constructs.Construct;

public final class Jenkins {
  private Jenkins() {}

  public static JenkinsFactory.JenkinsSystem ec2(Construct scope, String id, DeploymentContext cfc) {
    //ContextValidator.attach(scope, cfc);
    return JenkinsFactory.createEc2(scope, id, cfc);
  }

  public static JenkinsFactory.JenkinsSystem fargate(Construct scope, String id, DeploymentContext cfc) {
    //ContextValidator.attach(scope, cfc);
    return JenkinsFactory.createFargate(scope, id, cfc);
  }
}
