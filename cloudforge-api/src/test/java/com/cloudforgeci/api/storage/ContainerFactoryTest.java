package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ContainerFactoryTest {

  @Test
  void createsContainerDefinition() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of("Name", Match.anyValue()))
      ))
    ));
  }

  @Test
  void createsContainerWithJenkinsPort() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerPortTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "PortMappings", Match.arrayWith(List.of(
            Match.objectLike(Map.of("ContainerPort", 8080))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithMountPoints() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerMountTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "MountPoints", Match.arrayWith(List.of(
            Match.objectLike(Map.of(
              "ContainerPath", "/var/jenkins_home",
              "SourceVolume", "jenkinsHome",
              "ReadOnly", false
            ))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithUser1000() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerUserTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of("User", "1000:1000"))
      ))
    ));
  }

  @Test
  void createsContainerWithAwsLogging() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerLoggingTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "LogConfiguration", Match.objectLike(Map.of(
            "LogDriver", "awslogs"
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithJavaOptsForReverseProxy() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerJavaOptsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Name", "JAVA_OPTS"))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithJenkinsOpts() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerJenkinsOptsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Name", "JENKINS_OPTS"))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithDomainConfiguration() {
    Map<String, Object> context = new HashMap<>();
    context.put("domain", "example.com");
    context.put("subdomain", "jenkins");
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerDomainTest", SecurityProfile.DEV, RuntimeType.FARGATE, context);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of("Name", "JENKINS_URL"))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerAcrossMultipleSecurityProfiles() {
    for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerMultiProfile" + profile, profile, RuntimeType.FARGATE);
      builder.createCompleteInfrastructure();

      Template t = Template.fromStack(builder.getStack());
      t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
        "ContainerDefinitions", Match.arrayWith(List.of(
          Match.objectLike(Map.of("Name", Match.anyValue()))
        ))
      ));
    }
  }

  @Test
  void createsContainerWithLogStreamPrefix() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerLogStreamTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "LogConfiguration", Match.objectLike(Map.of(
            "Options", Match.objectLike(Map.of(
              "awslogs-stream-prefix", "jenkins"
            ))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithoutCspRestrictions() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerCspTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of(
              "Name", "JAVA_OPTS",
              "Value", Match.stringLikeRegexp(".*CSP.*")
            ))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithMaxFormContentSize() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerFormSizeTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of(
              "Name", "JAVA_OPTS",
              "Value", Match.stringLikeRegexp(".*maxFormContentSize.*")
            ))
          ))
        ))
      ))
    ));
  }

  @Test
  void createsContainerWithHttpsDisabled() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerHttpsDisabledTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Environment", Match.arrayWith(List.of(
            Match.objectLike(Map.of(
              "Name", "JENKINS_OPTS",
              "Value", Match.stringLikeRegexp(".*httpsPort=-1.*")
            ))
          ))
        ))
      ))
    ));
  }

  @Test
  void containerIsUnprivilegedWithWritableRootByDefault() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("ContainerDefaultsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());

    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of("Privileged", false, "ReadonlyRootFilesystem", false))
      ))
    ));
  }

  @Test
  void readOnlyRootFilesystemAddsWritableTmpVolume() {
    Map<String, Object> context = new HashMap<>();
    context.put("stackName", "ContainerReadOnlyTest");
    context.put("region", "us-east-1");
    context.put("runtime", "FARGATE");
    context.put("topology", "APPLICATION_SERVICE");
    context.put("securityProfile", "DEV");
    context.put("applicationId", "jenkins");
    Stack stack = new Stack(new App(), "ContainerReadOnlyTest", StackProps.builder()
      .env(Environment.builder().account("123456789012").region("us-east-1").build()).build());
    stack.getNode().setContext("cfc", context);
    JenkinsApplicationSpec readOnly = new JenkinsApplicationSpec() {
      @Override public boolean readOnlyRootFilesystem() {
        return true;
      }
    };
    ApplicationFactory.createFargate(stack, "TestApp", DeploymentContext.from(stack), SecurityProfile.DEV,
      IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV), readOnly);

    Template t = Template.fromStack(stack);
    t.hasResourceProperties("AWS::ECS::TaskDefinition", Map.of(
      "ContainerDefinitions", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "Privileged", false,
          "ReadonlyRootFilesystem", true,
          "MountPoints", Match.arrayWith(List.of(
            Match.objectLike(Map.of("ContainerPath", "/tmp", "SourceVolume", "tmp", "ReadOnly", false))
          ))
        ))
      )),
      "Volumes", Match.arrayWith(List.of(Match.objectLike(Map.of("Name", "tmp"))))
    ));
  }
}
