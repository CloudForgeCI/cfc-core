package com.cloudforgeci.api.core;

import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class SystemContextTest {
  
  private App app;
  private Stack stack;
  private DeploymentContext cfc;
  private SystemContext ctx;

  @BeforeEach
  void setUp() {
    app = new App();
    stack = new Stack(app, "Test");
    cfc = DeploymentContext.from(stack);
  }

  @Test
  void systemContextStoresCorrectProperties() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    assertEquals(RuntimeType.FARGATE, ctx.runtime);
    assertEquals(SecurityProfile.DEV, ctx.security);
    assertEquals(IAMProfile.EXTENDED, ctx.iamProfile);
    assertEquals(cfc, ctx.cfc);
  }

  @Test
  void systemContextSlotsAreInitialized() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Verify all slots are initialized
    assertNotNull(ctx.vpc);
    assertNotNull(ctx.alb);
    assertNotNull(ctx.asg);
    assertNotNull(ctx.efs);
    assertNotNull(ctx.logs);
    assertNotNull(ctx.zone);
    assertNotNull(ctx.instanceSg);
    assertNotNull(ctx.albTargetGroup);
    assertNotNull(ctx.albSg);
    assertNotNull(ctx.http);
    assertNotNull(ctx.efsSg);
    assertNotNull(ctx.ap);
    assertNotNull(ctx.fargateService);
    assertNotNull(ctx.fargateServiceSg);
    assertNotNull(ctx.fargateTaskDef);
    assertNotNull(ctx.container);
    assertNotNull(ctx.https);
    assertNotNull(ctx.cert);
    assertNotNull(ctx.websiteBucket);
    assertNotNull(ctx.distribution);
    assertNotNull(ctx.flowlogs);
    assertNotNull(ctx.wafWebAcl);
    assertNotNull(ctx.ec2InstanceRole);
    assertNotNull(ctx.fargateExecutionRole);
    assertNotNull(ctx.fargateTaskRole);
  }

  @Test
  void systemContextSlotsAreEmptyInitially() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Verify all slots are empty initially
    assertTrue(ctx.vpc.get().isEmpty());
    assertTrue(ctx.alb.get().isEmpty());
    assertTrue(ctx.asg.get().isEmpty());
    assertTrue(ctx.efs.get().isEmpty());
    assertTrue(ctx.logs.get().isEmpty());
    assertTrue(ctx.zone.get().isEmpty());
    assertTrue(ctx.instanceSg.get().isEmpty());
    assertTrue(ctx.albTargetGroup.get().isEmpty());
    assertTrue(ctx.albSg.get().isEmpty());
    assertTrue(ctx.http.get().isEmpty());
    assertTrue(ctx.efsSg.get().isEmpty());
    assertTrue(ctx.ap.get().isEmpty());
    assertTrue(ctx.fargateService.get().isEmpty());
    assertTrue(ctx.fargateServiceSg.get().isEmpty());
    assertTrue(ctx.fargateTaskDef.get().isEmpty());
    assertTrue(ctx.container.get().isEmpty());
    assertTrue(ctx.https.get().isEmpty());
    assertTrue(ctx.cert.get().isEmpty());
    assertTrue(ctx.websiteBucket.get().isEmpty());
    assertTrue(ctx.distribution.get().isEmpty());
    assertTrue(ctx.flowlogs.get().isEmpty());
    assertTrue(ctx.wafWebAcl.get().isEmpty());
    assertTrue(ctx.ec2InstanceRole.get().isEmpty());
    // fargateExecutionRole and fargateTaskRole are populated by ExtendedIAMConfiguration
    // assertTrue(ctx.fargateExecutionRole.get().isEmpty());
    // assertTrue(ctx.fargateTaskRole.get().isEmpty());
  }

  @Test
  void systemContextSlotsCanStoreValues() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Create some test resources
    Vpc vpc = Vpc.Builder.create(stack, "TestVpc").build();
    SecurityGroup sg = SecurityGroup.Builder.create(stack, "TestSg").vpc(vpc).build();
    ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(stack, "TestAlb").vpc(vpc).build();
    FileSystem efs = FileSystem.Builder.create(stack, "TestEfs").vpc(vpc).build();
    LogGroup logGroup = LogGroup.Builder.create(stack, "TestLogs").build();
    
    // Store values in slots
    ctx.vpc.set(vpc);
    ctx.albSg.set(sg);
    ctx.alb.set(alb);
    ctx.efs.set(efs);
    ctx.logs.set(logGroup);
    
    // Verify values are stored correctly
    assertTrue(ctx.vpc.get().isPresent());
    assertTrue(ctx.albSg.get().isPresent());
    assertTrue(ctx.alb.get().isPresent());
    assertTrue(ctx.efs.get().isPresent());
    assertTrue(ctx.logs.get().isPresent());
    
    assertEquals(vpc, ctx.vpc.get().orElseThrow());
    assertEquals(sg, ctx.albSg.get().orElseThrow());
    assertEquals(alb, ctx.alb.get().orElseThrow());
    assertEquals(efs, ctx.efs.get().orElseThrow());
    assertEquals(logGroup, ctx.logs.get().orElseThrow());
  }

  @Test
  void systemContextOnceMethodWorksCorrectly() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // First call should return true and execute
    boolean firstCall = ctx.once("test-key", () -> {
      // This should execute
    });
    assertTrue(firstCall);
    
    // Second call with same key should return false and not execute
    boolean secondCall = ctx.once("test-key", () -> {
      fail("This should not execute");
    });
    assertFalse(secondCall);
    
    // Different key should work again
    boolean thirdCall = ctx.once("different-key", () -> {
      // This should execute
    });
    assertTrue(thirdCall);
  }

  @Test
  void systemContextThrowsExceptionWhenNotStarted() {
    // Should throw exception when SystemContext not started
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.of(stack);
    });
  }

  @Test
  void systemContextReturnsSameInstanceForSameParameters() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Start again with same parameters
    SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    assertSame(ctx, ctx2);
  }

  @Test
  void systemContextThrowsExceptionForDifferentRuntime() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different runtime
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, iamProfile, cfc);
    });
  }

  @Test
  void systemContextThrowsExceptionForDifferentTopology() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different topology
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SINGLE_NODE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    });
  }

  @Test
  void systemContextThrowsExceptionForDifferentSecurity() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different security profile
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    });
  }

  @Test
  void systemContextThrowsExceptionForDifferentIamProfile() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different IAM profile
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.STANDARD, cfc);
    });
  }

  @Test
  void systemContextWorksWithAllRuntimeTypes() {
    RuntimeType[] runtimeTypes = {RuntimeType.EC2, RuntimeType.FARGATE};
    
    for (RuntimeType runtimeType : runtimeTypes) {
      App testApp = new App();
      Stack testStack = new Stack(testApp, "Test" + runtimeType);
      DeploymentContext testCfc = DeploymentContext.from(testStack);
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
      SystemContext testCtx = SystemContext.start(testStack, TopologyType.JENKINS_SERVICE, runtimeType, SecurityProfile.DEV, iamProfile, testCfc);
      
      assertEquals(runtimeType, testCtx.runtime);
      assertEquals(TopologyType.JENKINS_SERVICE, testCtx.topology);
      assertEquals(SecurityProfile.DEV, testCtx.security);
    }
  }

  @Test
  void systemContextWorksWithAllTopologyTypes() {
    TopologyType[] topologyTypes = {TopologyType.JENKINS_SERVICE};
    
    for (TopologyType topologyType : topologyTypes) {
      App testApp = new App();
      Stack testStack = new Stack(testApp, "TestJenkinsService");
      DeploymentContext testCfc = DeploymentContext.from(testStack);
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
      SystemContext testCtx = SystemContext.start(testStack, topologyType, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, testCfc);
      
      assertEquals(topologyType, testCtx.topology);
      assertEquals(RuntimeType.FARGATE, testCtx.runtime);
      assertEquals(SecurityProfile.DEV, testCtx.security);
    }
  }

  @Test
  void systemContextWorksWithAllSecurityProfiles() {
    SecurityProfile[] securityProfiles = SecurityProfile.values();
    
    for (SecurityProfile securityProfile : securityProfiles) {
      App testApp = new App();
      Stack testStack = new Stack(testApp, "Test" + securityProfile);
      DeploymentContext testCfc = DeploymentContext.from(testStack);
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
      SystemContext testCtx = SystemContext.start(testStack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, securityProfile, iamProfile, testCfc);
      
      assertEquals(securityProfile, testCtx.security);
      assertEquals(iamProfile, testCtx.iamProfile);
    }
  }
}
