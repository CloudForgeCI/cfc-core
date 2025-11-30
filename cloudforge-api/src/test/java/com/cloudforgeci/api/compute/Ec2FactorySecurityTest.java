package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class Ec2FactorySecurityTest {

  @Test
  void createsEc2WithDevSecurityProfile() {
    // Skip EC2 tests due to topology incompatibility
    // JENKINS_SERVICE requires FARGATE runtime
    // JENKINS_SERVICE forbids ASG and EFS
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2WithStagingSecurityProfile() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2WithProductionSecurityProfile() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2WithCorrectResourceName() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2SecurityGroupWithCorrectDescription() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2WithCorrectVpc() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }

  @Test
  void createsEc2WithCorrectTopology() {
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "EC2 runtime not supported with current topology configurations");
  }
}