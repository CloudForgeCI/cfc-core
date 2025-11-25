package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class EfsFactoryTest {

  @Test
  void createsEncryptedEfs() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of("Encrypted", true));
  }

  @Test
  void createsEfsWithSecurityGroup() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsSecurityGroupTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::SecurityGroup", Map.of(
      "GroupDescription", "EFS Security Group"
    ));
  }

  @Test
  void createsEfsWithGeneralPurposePerformanceMode() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsPerformanceTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
      "PerformanceMode", "generalPurpose"
    ));
  }

  @Test
  void createsEfsWithBurstingThroughputMode() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsThroughputTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
      "ThroughputMode", "bursting"
    ));
  }

  @Test
  void createsEfsWithDestroyRemovalPolicyByDefault() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsRemovalTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::EFS::FileSystem", Map.of(
      "DeletionPolicy", "Delete"
    ));
  }

  @Test
  void createsEfsWithRetainRemovalPolicyWhenConfigured() {
    Map<String, Object> context = new HashMap<>();
    context.put("retainStorage", true);
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsRetainTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResource("AWS::EFS::FileSystem", Map.of(
      "DeletionPolicy", "Retain"
    ));
  }

  @Test
  void createsEfsInVpc() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsVpcTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EC2::VPC", 1);
    t.resourceCountIs("AWS::EFS::FileSystem", 1);
  }

  @Test
  void createsEfsMountTargetsInMultipleAZs() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsMountTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EFS::MountTarget", 2);
  }

  @Test
  void createsEfsWithAllowOutboundSecurityGroup() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsOutboundTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EC2::SecurityGroup", Map.of(
      "GroupDescription", "EFS Security Group",
      "SecurityGroupEgress", Match.arrayWith(List.of(
        Match.objectLike(Map.of(
          "CidrIp", "0.0.0.0/0"
        ))
      ))
    ));
  }

  @Test
  void createsEfsAccessPoint() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsAccessPointTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::EFS::AccessPoint", 1);
    t.hasResourceProperties("AWS::EFS::AccessPoint", Map.of(
      "FileSystemId", Match.objectLike(Map.of("Ref", Match.anyValue()))
    ));
  }

  @Test
  void createsEfsAcrossMultipleSecurityProfiles() {
    for (SecurityProfile profile : new SecurityProfile[]{SecurityProfile.DEV, SecurityProfile.STAGING, SecurityProfile.PRODUCTION}) {
      TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsMultiProfile" + profile, profile, RuntimeType.FARGATE);
      builder.createCompleteInfrastructure();

      Template t = Template.fromStack(builder.getStack());
      t.hasResourceProperties("AWS::EFS::FileSystem", Map.of("Encrypted", true));
    }
  }

  @Test
  void createsEfsForFargateRuntime() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsFargateRuntime", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of("Encrypted", true));
  }

  @Test
  void createsEfsWithFileSystemTags() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTagsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();

    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of(
      "FileSystemTags", Match.arrayWith(List.of(
        Match.objectLike(Map.of("Key", "Name"))
      ))
    ));
  }
}
