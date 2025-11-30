package com.cloudforgeci.samples.app;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import com.cloudforgeci.samples.launchers.ApplicationFargateStack;
import com.cloudforgeci.samples.launchers.ApplicationEc2Stack;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import io.github.cdklabs.cdknag.AwsSolutionsChecks;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Aspects;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

import java.util.Map;

/**
 * CloudForge Community Sample Application.
 *
 * <p>CloudForge 3.0.0: Updated for universal application framework</p>
 *
 * <p>This sample demonstrates deploying Jenkins with full SOC2 compliance using the
 * new ApplicationSpec pattern that separates application concerns from infrastructure.</p>
 */
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
        "runtime", "topology", "securityProfile", "stackName", "region",
        "domain", "subdomain", "enableSsl", "env", "tier",
        "networkMode", "wafEnabled", "cloudfrontEnabled", "authMode",
        "cpu", "memory", "instanceType", "minInstanceCapacity", "maxInstanceCapacity",
        "cpuTargetUtilization", "enableMonitoring", "enableEncryption",
        "logRetentionDays", "healthCheckGracePeriod", "healthCheckInterval",
        "healthCheckTimeout", "healthyThreshold", "unhealthyThreshold",
        "bastionCidr", "lbType", "enableFlowlogs", "createZone", "artifactsPrefix",
        "autoProvisionIdentityCenter", "identityCenterGroupName",
        "ssoInstanceArn", "ssoGroupId", "ssoTargetAccountId", "iamProfile"
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

    // Use region from DeploymentContext (cdk.json or deployment-context.json) with fallback to environment variable
    String region = cfc.region() != null ? cfc.region() : System.getenv("CDK_DEFAULT_REGION");
    String account = System.getenv("CDK_DEFAULT_ACCOUNT");

    // Get security profile from DeploymentContext
    SecurityProfile security = cfc.securityProfile();

    // Map IAM profile from security profile
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);

    System.out.println("=".repeat(80));
    System.out.println("CloudForge 3.0.0 deployment configuration:");
    System.out.println("  Region: " + region);
    System.out.println("  Account: " + account);
    System.out.println("  Runtime: " + cfc.runtime());
    System.out.println("  Security Profile: " + security);
    System.out.println("  IAM Profile: " + iamProfile + " (auto-mapped from " + security + ")");
    System.out.println("  Topology: " + cfc.topology());
    System.out.println("  Network Mode: " + (cfc.networkMode() != null ? cfc.networkMode() : "private-with-nat (default)"));
    System.out.println("  SSL Enabled: " + cfc.enableSsl());
    System.out.println("  WAF Enabled: " + cfc.wafEnabled());
    System.out.println("=".repeat(80));

    StackProps props = StackProps.builder().env(Environment.builder()
            .account(account)
            .region(region).build()).build();

    // Use stack name from context or default to runtime-based name
    String stackName = cfc.stackName();
    if (stackName == null || stackName.isEmpty()) {
      stackName = (cfc.runtime() == RuntimeType.EC2) ? "JenkinsEc2" : "JenkinsFargate";
    }

    // Create JenkinsApplicationSpec
    JenkinsApplicationSpec jenkinsSpec = new JenkinsApplicationSpec();

    // Create stacks based on runtime type using universal ApplicationFactory
    if (cfc.runtime() == RuntimeType.EC2) {
      new ApplicationEc2Stack(app, stackName, props, security, iamProfile, jenkinsSpec);
    } else if (cfc.runtime() == RuntimeType.FARGATE) {
      new ApplicationFargateStack(app, stackName, props, security, iamProfile, jenkinsSpec);
    } else {
      throw new IllegalArgumentException("Unsupported runtime type: " + cfc.runtime());
    }

    // Uncomment to enable AWS Solutions checks (cdk-nag)
    //Aspects.of(app).add(new AwsSolutionsChecks());

    app.synth();
  }

}
