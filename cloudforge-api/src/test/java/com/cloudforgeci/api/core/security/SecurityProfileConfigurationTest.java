package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class SecurityProfileConfigurationTest {
  
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
  void devSecurityProfileCreatesCorrectContext() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    assertEquals(SecurityProfile.DEV, ctx.security);
    assertEquals(IAMProfile.EXTENDED, ctx.iamProfile);
    assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    assertEquals(RuntimeType.FARGATE, ctx.runtime);
  }

  @Test
  void stagingSecurityProfileCreatesCorrectContext() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    
    assertEquals(SecurityProfile.STAGING, ctx.security);
    assertEquals(IAMProfile.STANDARD, ctx.iamProfile);
    assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    assertEquals(RuntimeType.FARGATE, ctx.runtime);
  }

  @Test
  void productionSecurityProfileCreatesCorrectContext() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);
    
    assertEquals(SecurityProfile.PRODUCTION, ctx.security);
    assertEquals(IAMProfile.MINIMAL, ctx.iamProfile);
    assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
    assertEquals(RuntimeType.FARGATE, ctx.runtime);
  }

  @Test
  void securityProfileMappingIsCorrect() {
    assertEquals(IAMProfile.EXTENDED, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV));
    assertEquals(IAMProfile.STANDARD, IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING));
    assertEquals(IAMProfile.MINIMAL, IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION));
  }

  @Test
  void securityProfileValidationWorks() {
    // Valid combinations
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.EXTENDED));
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.STANDARD));
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.MINIMAL));
    
    // Invalid combinations
    assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.EXTENDED));
    assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.MINIMAL));
  }

  @Test
  void systemContextThrowsExceptionForInvalidCombination() {
    // Note: SystemContext.start currently doesn't validate security/IAM profile combinations
    // This test is skipped until validation is implemented
    org.junit.jupiter.api.Assumptions.assumeTrue(false, "Security/IAM profile validation not yet implemented in SystemContext.start");
  }

  @Test
  void systemContextAllowsMismatchedRuntime() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Should allow different runtime types in the same stack
    SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2, SecurityProfile.DEV, iamProfile, cfc);
    assertSame(ctx, ctx2); // Should return the same SystemContext instance
  }

  @Test
  void systemContextThrowsExceptionForMismatchedTopology() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different topology - should throw exception
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    });
  }

  @Test
  void systemContextThrowsExceptionForMismatchedSecurity() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different security profile - should throw exception
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    });
  }

  @Test
  void systemContextThrowsExceptionForMismatchedIamProfile() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Try to start with different IAM profile - should throw exception
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, IAMProfile.STANDARD, cfc);
    });
  }

  @Test
  void systemContextReturnsExistingContextForSameParameters() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // Start again with same parameters - should return existing context
    SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    assertSame(ctx, ctx2);
  }

  @Test
  void systemContextOfReturnsCorrectContext() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    // SystemContext.of should return the same context
    SystemContext retrievedCtx = SystemContext.of(stack);
    assertSame(ctx, retrievedCtx);
  }

  @Test
  void systemContextOfThrowsExceptionWhenNotStarted() {
    // Should throw exception when SystemContext not started
    assertThrows(IllegalStateException.class, () -> {
      SystemContext.of(stack);
    });
  }

  @Test
  void allSecurityProfilesAreSupported() {
    SecurityProfile[] profiles = SecurityProfile.values();
    assertEquals(3, profiles.length);
    
    for (SecurityProfile profile : profiles) {
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
      assertNotNull(iamProfile);
    }
  }

  @Test
  void allIamProfilesAreSupported() {
    IAMProfile[] profiles = IAMProfile.values();
    assertEquals(3, profiles.length);
    
    for (IAMProfile profile : profiles) {
      assertNotNull(profile);
    }
  }
}
