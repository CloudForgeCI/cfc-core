package com.cloudforgeci.samples.app;


import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.samples.launchers.JenkinsEc2Stack;
import com.cloudforgeci.samples.launchers.JenkinsFargateStack;
import io.github.cdklabs.cdknag.AwsSolutionsChecks;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Aspects;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

import java.util.Map;


public class CloudForgeCommunitySample {

  public static void main(final String[] args) {
    App app = new App();

    DeploymentContext cfc = DeploymentContext.from(app);

    StackProps props = StackProps.builder().env(Environment.builder()
            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region(System.getenv("CDK_DEFAULT_REGION")).build()).build();

    if("ec2".equalsIgnoreCase(cfc.runtime().name()) || "ec2-domain".equalsIgnoreCase(cfc.runtime().name()))
      new JenkinsEc2Stack(app, "JenkinsEc2", props);
    if("fargate".equalsIgnoreCase(cfc.runtime().name()) || "fargate-domain".equalsIgnoreCase(cfc.runtime().name()))
      new JenkinsFargateStack(app,"JenkinsFargate", props);
    //Aspects.of(app).add(new AwsSolutionsChecks());
    app.synth();
  }
}
