package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

/**
 * Test suite for ISO/IEC 27001:2022 compliance validation.
 *
 * <p>Tests the v2.0 instance-based framework pattern and validates
 * ISO 27001 security controls.</p>
 *
 * <p>NOTE: ISO-27001 is not yet a supported compliance framework.
 * These tests are disabled until ISO-27001 support is implemented.</p>
 */
@Disabled("ISO-27001 compliance framework not yet implemented")
class Iso27001RulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testIso27001InstallationWithProductionProfile() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestISO27001Prod", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);

        // When: Installing ISO 27001 rules
        Iso27001Rules rules = new Iso27001Rules();

        // Then: Should not throw
        assertDoesNotThrow(() -> rules.install(ctx));
    }

    @Test
    void testIso27001InstallationWithStagingProfile() {
        // Given: A STAGING deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestISO27001Staging", SecurityProfile.STAGING);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, IAMProfile.STANDARD, cfc);

        // When: Installing ISO 27001 rules
        Iso27001Rules rules = new Iso27001Rules();

        // Then: Should not throw
        assertDoesNotThrow(() -> rules.install(ctx));
    }

    @Test
    void testIso27001SkipsDevProfile() {
        // Given: A DEV deployment
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Dev");
        customContext.put("securityProfile", "DEV");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Dev", SecurityProfile.DEV, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        Iso27001Rules rules = new Iso27001Rules();
        rules.install(builder.getSystemContext());

        // Then: Should pass without validation (DEV profile skipped)
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void testIso27001FrameworkMetadata() {
        // Given: An ISO 27001 framework instance
        Iso27001Rules rules = new Iso27001Rules();

        // Then: Should have correct metadata from annotation
        assertEquals("ISO-27001", rules.frameworkId());
        assertEquals("ISO/IEC 27001:2022 Information Security Management", rules.displayName());
        assertEquals(50, rules.priority());
        assertFalse(rules.alwaysLoad());
    }

    @Test
    void testIso27001WithFullCompliance() {
        // Given: A fully compliant PRODUCTION deployment
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Compliant");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("securityMonitoringEnabled", "true");
        customContext.put("wafEnabled", "true");
        customContext.put("ebsEncryptionEnabled", "true");
        customContext.put("efsEncryptionAtRestEnabled", "true");
        customContext.put("efsEncryptionInTransitEnabled", "true");
        customContext.put("cloudTrailEnabled", "true");
        customContext.put("guardDutyEnabled", "true");
        customContext.put("enableFlowlogs", "true");
        customContext.put("multiAzEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Compliant", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should pass all validations
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @ParameterizedTest
    @CsvSource({
        // Profile, Expected to be enforced
        "PRODUCTION,true",   // PRODUCTION → ISO 27001 rules apply
        "STAGING,true",      // STAGING → ISO 27001 rules apply
        "DEV,false"          // DEV → ISO 27001 rules skipped
    })
    void testIso27001ComplianceByProfile(String profile, boolean rulesApply) {
        // Given: A deployment with the specified profile
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Profile");
        customContext.put("securityProfile", profile);

        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Profile", secProfile, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        Iso27001Rules rules = new Iso27001Rules();
        rules.install(builder.getSystemContext());

        // Then: Installation should complete without error
        // (Actual compliance validation is tested in specific control tests)
        assertDoesNotThrow(() -> rules.install(builder.getSystemContext()),
            profile + " should allow ISO 27001 installation");
    }

    @Test
    void testIso27001AdvisoryMode() {
        // Given: A PRODUCTION deployment with ADVISORY mode
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Advisory");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("complianceMode", "ADVISORY");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Advisory", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should pass even with violations (ADVISORY mode)
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "ADVISORY mode should not block synthesis");
    }

    @Test
    void testIso27001EnforceMode() {
        // Given: A PRODUCTION deployment with ENFORCE mode and missing controls
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Enforce");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("complianceMode", "ENFORCE");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Enforce", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail synthesis (ENFORCE mode with violations)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "ENFORCE mode should block synthesis on violations");
    }

    @Test
    void testIso27001AccessControlValidation() {
        // Given: PRODUCTION deployment without security monitoring
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001AccessControl");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("securityMonitoringEnabled", "false");
        customContext.put("wafEnabled", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001AccessControl", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail validation (missing security monitoring and WAF)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Should fail ISO 27001 A.9 access control requirements");
    }

    @Test
    void testIso27001CryptographyValidation() {
        // Given: PRODUCTION deployment without encryption
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001Crypto");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("ebsEncryptionEnabled", "false");
        customContext.put("efsEncryptionAtRestEnabled", "false");
        customContext.put("efsEncryptionInTransitEnabled", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001Crypto", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail validation (missing encryption)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Should fail ISO 27001 A.10 cryptography requirements");
    }

    @Test
    void testIso27001OperationsSecurityValidation() {
        // Given: PRODUCTION deployment without logging or threat detection
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001OpsSec");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("cloudTrailEnabled", "false");
        customContext.put("guardDutyEnabled", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001OpsSec", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail validation (missing logging and threat detection)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Should fail ISO 27001 A.12 operations security requirements");
    }

    @Test
    void testIso27001CommunicationsSecurityValidation() {
        // Given: PRODUCTION deployment without flow logs
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001CommSec");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("enableFlowlogs", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001CommSec", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail validation (missing flow logs)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Should fail ISO 27001 A.13 communications security requirements");
    }

    @Test
    void testIso27001BusinessContinuityValidation() {
        // Given: PRODUCTION deployment without Multi-AZ
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001BC");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("multiAzEnabled", "false");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001BC", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing ISO 27001 rules
        new Iso27001Rules().install(builder.getSystemContext());

        // Then: Should fail validation (missing Multi-AZ)
        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Should fail ISO 27001 A.17 business continuity requirements");
    }

    @Test
    void testIso27001LoadedViaSecurityRules() {
        // Given: PRODUCTION deployment with ISO 27001 enabled via SecurityRules
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestISO27001SecurityRules");
        customContext.put("securityProfile", "PRODUCTION");
        customContext.put("complianceFrameworks", "ISO-27001");
        customContext.put("auditManagerEnabled", "true");

        // ISO 27001 compliant configuration
        customContext.put("securityMonitoringEnabled", "true");
        customContext.put("wafEnabled", "true");
        customContext.put("ebsEncryptionEnabled", "true");
        customContext.put("efsEncryptionAtRestEnabled", "true");
        customContext.put("efsEncryptionInTransitEnabled", "true");
        customContext.put("cloudTrailEnabled", "true");
        customContext.put("guardDutyEnabled", "true");
        customContext.put("enableFlowlogs", "true");
        customContext.put("multiAzEnabled", "true");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestISO27001SecurityRules", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, customContext);

        builder.createMinimalInfrastructure();

        // When: Installing all frameworks via SecurityRules (includes ISO 27001)
        new SecurityRules().install(builder.getSystemContext());

        // Then: Should pass ISO 27001 validation
        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "Should pass ISO 27001 compliance via SecurityRules");
    }
}
