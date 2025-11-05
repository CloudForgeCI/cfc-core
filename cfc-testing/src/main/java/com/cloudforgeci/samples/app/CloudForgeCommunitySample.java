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

    // Check if we have a pre-configured cfc context (from cdk.json)
    // or if we need to build it from individual context parameters
    Object cfcObj = app.getNode().tryGetContext("cfc");

    if (cfcObj == null) {
      // Build cfc context from individual --context cfc.* parameters
      Map<String, Object> cfcContext = new java.util.HashMap<>();

      // Read all cfc.* parameters and build the context map
      String[] contextKeys = {
        "runtime", "topology", "securityProfile", "stackName",
        "domain", "subdomain", "enableSsl", "env", "tier",
        "networkMode", "wafEnabled", "cloudfrontEnabled", "authMode",
        "cpu", "memory", "instanceType", "minInstanceCapacity", "maxInstanceCapacity",
        "cpuTargetUtilization", "enableMonitoring", "enableEncryption",
        "logRetentionDays", "healthCheckGracePeriod", "healthCheckInterval",
        "healthCheckTimeout", "healthyThreshold", "unhealthyThreshold",
        "bastionCidr", "lbType", "enableFlowlogs", "createZone", "artifactsPrefix"
      };

      for (String key : contextKeys) {
        Object value = app.getNode().tryGetContext("cfc." + key);
        if (value != null) {
          cfcContext.put(key, value);
        }
      }

      // Set the cfc context on the app
      if (!cfcContext.isEmpty()) {
        app.getNode().setContext("cfc", cfcContext);
      }
    }

    DeploymentContext cfc = DeploymentContext.from(app);

    StackProps props = StackProps.builder().env(Environment.builder()
            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region(System.getenv("CDK_DEFAULT_REGION")).build()).build();

    // Get security profile from DeploymentContext
    SecurityProfile security = cfc.securityProfile();
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);

    // Use stack name from context or default to runtime-based name
    String stackName = cfc.stackName();
    if (stackName == null || stackName.isEmpty()) {
      stackName = (cfc.getRuntime() == RuntimeType.EC2) ? "JenkinsEc2" : "JenkinsFargate";
    }

    // Create stacks based on runtime type
    if (cfc.getRuntime() == RuntimeType.EC2) {
      new JenkinsEc2Stack(app, stackName, props, security, iamProfile);
    } else if (cfc.getRuntime() == RuntimeType.FARGATE) {
      new JenkinsFargateStack(app, stackName, props, security, iamProfile);
    } else {
      throw new IllegalArgumentException("Unsupported runtime type: " + cfc.getRuntime());
    }

    //Aspects.of(app).add(new AwsSolutionsChecks());
    app.synth();
  }

}
