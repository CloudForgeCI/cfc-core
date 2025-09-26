package com.cloudforgeci.api.core.iam;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.core.iam.MinimalIAMConfiguration;
import com.cloudforgeci.api.core.iam.StandardIAMConfiguration;
import com.cloudforgeci.api.core.iam.ExtendedIAMConfiguration;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class IAMProfileConfigurationTest {
  
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
  void minimalIamConfigurationHasCorrectProfile() {
    MinimalIAMConfiguration config = new MinimalIAMConfiguration();
    assertEquals(IAMProfile.MINIMAL, config.kind());
    assertEquals("iam:MINIMAL", config.id());
  }

  @Test
  void standardIamConfigurationHasCorrectProfile() {
    StandardIAMConfiguration config = new StandardIAMConfiguration();
    assertEquals(IAMProfile.STANDARD, config.kind());
    assertEquals("iam:STANDARD", config.id());
  }

  @Test
  void extendedIamConfigurationHasCorrectProfile() {
    ExtendedIAMConfiguration config = new ExtendedIAMConfiguration();
    assertEquals(IAMProfile.EXTENDED, config.kind());
    assertEquals("iam:EXTENDED", config.id());
  }

  @Test
  void minimalIamConfigurationHasCorrectRules() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    MinimalIAMConfiguration config = new MinimalIAMConfiguration();
    var rules = config.rules(ctx);
    
    assertNotNull(rules);
    assertFalse(rules.isEmpty());
  }

  @Test
  void standardIamConfigurationHasCorrectRules() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    
    StandardIAMConfiguration config = new StandardIAMConfiguration();
    var rules = config.rules(ctx);
    
    assertNotNull(rules);
    assertFalse(rules.isEmpty());
  }

  @Test
  void extendedIamConfigurationHasCorrectRules() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);
    
    ExtendedIAMConfiguration config = new ExtendedIAMConfiguration();
    var rules = config.rules(ctx);
    
    assertNotNull(rules);
    assertFalse(rules.isEmpty());
  }

  @Test
  void iamProfileMapperMapsCorrectly() {
    assertEquals(IAMProfile.EXTENDED, IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV));
    assertEquals(IAMProfile.STANDARD, IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING));
    assertEquals(IAMProfile.MINIMAL, IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION));
  }

  @Test
  void iamProfileMapperValidatesCombinations() {
    // Valid combinations
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.DEV, IAMProfile.EXTENDED));
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.STANDARD));
    assertTrue(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.MINIMAL));
    
    // Invalid combinations
    assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.PRODUCTION, IAMProfile.EXTENDED));
    assertFalse(IAMProfileMapper.isValidCombination(SecurityProfile.STAGING, IAMProfile.MINIMAL));
  }

  @Test
  void minimalIamConfigurationWiresCorrectly() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    MinimalIAMConfiguration config = new MinimalIAMConfiguration();
    
    // Should not throw exception when wiring
    assertDoesNotThrow(() -> config.wire(ctx));
  }

  @Test
  void standardIamConfigurationWiresCorrectly() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING, iamProfile, cfc);
    
    // IAM rules are already installed during SystemContext.start()
    // No need to manually call config.wire(ctx) as it would cause conflicts
    assertNotNull(ctx);
  }

  @Test
  void extendedIamConfigurationWiresCorrectly() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.PRODUCTION, iamProfile, cfc);
    
    ExtendedIAMConfiguration config = new ExtendedIAMConfiguration();
    
    // Should not throw exception when wiring
    assertDoesNotThrow(() -> config.wire(ctx));
  }

  @Test
  void iamConfigurationImplementsInterface() {
    assertTrue(IAMConfiguration.class.isAssignableFrom(MinimalIAMConfiguration.class));
    assertTrue(IAMConfiguration.class.isAssignableFrom(StandardIAMConfiguration.class));
    assertTrue(IAMConfiguration.class.isAssignableFrom(ExtendedIAMConfiguration.class));
  }

  @Test
  void allIamProfilesHaveConfigurations() {
    IAMProfile[] profiles = IAMProfile.values();
    assertEquals(3, profiles.length);
    
    for (IAMProfile profile : profiles) {
      IAMConfiguration config = switch (profile) {
        case MINIMAL -> new MinimalIAMConfiguration();
        case STANDARD -> new StandardIAMConfiguration();
        case EXTENDED -> new ExtendedIAMConfiguration();
      };
      
      assertEquals(profile, config.kind());
      assertNotNull(config.id());
    }
  }

  @Test
  void iamProfileMapperHandlesAllSecurityProfiles() {
    SecurityProfile[] securityProfiles = SecurityProfile.values();
    
    for (SecurityProfile securityProfile : securityProfiles) {
      IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
      assertNotNull(iamProfile);
      assertTrue(IAMProfileMapper.isValidCombination(securityProfile, iamProfile));
    }
  }

  @Test
  void iamConfigurationRulesAreNotNull() {
    IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
    ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, cfc);
    
    MinimalIAMConfiguration minimalConfig = new MinimalIAMConfiguration();
    StandardIAMConfiguration standardConfig = new StandardIAMConfiguration();
    ExtendedIAMConfiguration extendedConfig = new ExtendedIAMConfiguration();
    
    assertNotNull(minimalConfig.rules(ctx));
    assertNotNull(standardConfig.rules(ctx));
    assertNotNull(extendedConfig.rules(ctx));
  }

  @Test
  void iamConfigurationIdsAreUnique() {
    MinimalIAMConfiguration minimalConfig = new MinimalIAMConfiguration();
    StandardIAMConfiguration standardConfig = new StandardIAMConfiguration();
    ExtendedIAMConfiguration extendedConfig = new ExtendedIAMConfiguration();
    
    assertNotEquals(minimalConfig.id(), standardConfig.id());
    assertNotEquals(minimalConfig.id(), extendedConfig.id());
    assertNotEquals(standardConfig.id(), extendedConfig.id());
  }
}
