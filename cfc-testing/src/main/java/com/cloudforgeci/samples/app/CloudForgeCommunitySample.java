package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
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

    // Get security profile from DeploymentContext
    SecurityProfile security = cfc.securityProfile();
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);

    // Create stacks based on runtime type
    if (cfc.getRuntime() == RuntimeType.EC2) {
      new JenkinsEc2Stack(app, "JenkinsEc2", props, security, iamProfile);
    } else if (cfc.getRuntime() == RuntimeType.FARGATE) {
      new JenkinsFargateStack(app, "JenkinsFargate", props, security, iamProfile);
    } else {
      throw new IllegalArgumentException("Unsupported runtime type: " + cfc.getRuntime());
    }

    //Aspects.of(app).add(new AwsSolutionsChecks());
    app.synth();
  }

}
