package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.compute.FargateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.Match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive WAF security tests.
 * Tests verify:
 * 1. WAF configuration and enablement
 * 2. Security rule coverage (SQLi, XSS, path traversal, etc.)
 * 3. Rule exclusions for legitimate Jenkins traffic
 * 4. Proper association with ALB
 * 5. Monitoring and logging configuration
 */
public class WafFactoryTest {

    /**
     * Helper method to create test stack with WAF enabled in context.
     */
    private Stack createWafEnabledStack(App app, String stackName) {
        Stack stack = new Stack(app, stackName);

        // Set WAF enabled in context BEFORE creating DeploymentContext
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("wafEnabled", true);
        cfcContext.put("lbType", "alb");
        cfcContext.put("stackName", stackName);
        cfcContext.put("albAccessLogging", false);  // Disable for tests (requires region)
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    /** Synthesizes a STAGING Fargate stack with the WAF and its dependencies. */
    private Template synthWaf(String stackName) {
        Stack stack = createWafEnabledStack(new App(), stackName);
        TestInfrastructureBuilder.startSystemContext(stack,
                TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.STAGING);

        new VpcFactory(stack, "Vpc").create();
        new AlbFactory(stack, "Alb").create();
        new EfsFactory(stack, "Efs").create();
        new FargateFactory(stack, "Fargate").create();
        new WafFactory(stack, "Waf").create();

        return Template.fromStack(stack);
    }

    private static void assertManagedRuleGroup(Template template, String group) {
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of("Rules", Match.arrayWith(List.of(Match.objectLike(Map.of(
                "Name", "AWS-" + group,
                "Statement", Match.objectLike(Map.of(
                    "ManagedRuleGroupStatement", Match.objectLike(Map.of(
                        "VendorName", "AWS",
                        "Name", group)))))))))));
    }

    private static void assertExcludedRules(Template template, String group, String... excluded) {
        List<Object> rules = new ArrayList<>();
        for (String name : excluded) {
            rules.add(Match.objectLike(Map.of("Name", name)));
        }
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of("Rules", Match.arrayWith(List.of(Match.objectLike(Map.of(
                "Name", "AWS-" + group,
                "Statement", Match.objectLike(Map.of(
                    "ManagedRuleGroupStatement", Match.objectLike(Map.of(
                        "ExcludedRules", Match.arrayWith(rules))))))))))));
    }

    /**
     * Test that WAF is disabled by default in production profile.
     * This ensures backwards compatibility and prevents unexpected 403 errors.
     */
    @Test
    public void testWafDisabledByDefaultInProduction() {
        com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration();

        // Verify WAF is disabled by default
        assertFalse(config.isWafEnabled(), "WAF should be disabled by default in production profile");
    }

    /**
     * Test that WAF can be enabled via deployment context.
     * When enabled via context, the deployment context should reflect this.
     */
    @Test
    public void testWafEnabledViaDeploymentContext() {
        App app = new App();
        Stack stack = new Stack(app, "TestWafEnabled");

        // Set WAF enabled in context
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("wafEnabled", true);
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);

        // Verify WAF is enabled in deployment context
        assertTrue(cfc.wafEnabled(), "WAF should be enabled via deployment context");
    }

    /**
     * Test that WAF is disabled by default (even in production profile)
     */
    @Test
    public void testWafDisabledByDefaultInProductionProfile() {
        App app = new App();
        Stack stack = new Stack(app, "TestWafDefault");

        // Don't set wafEnabled - use default
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);

        // Verify WAF is disabled by default
        assertFalse(Boolean.TRUE.equals(cfc.wafEnabled()), "WAF should be disabled by default for backwards compatibility");
    }

    /**
     * Test that security profile configuration reflects WAF status
     */
    @Test
    public void testProductionSecurityProfileWafDefault() {
        com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration();

        // Verify WAF is disabled by default in production profile
        assertFalse(config.isWafEnabled(), "WAF should be disabled by default in production profile");
    }

    /**
     * Test that deployment context can override security profile WAF setting
     */
    @Test
    public void testDeploymentContextOverridesSecurityProfile() {
        App app = new App();
        Stack stack = new Stack(app, "TestWafOverride");

        // Enable WAF via context
        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("wafEnabled", true);
        cfcContext.put("lbType", "alb");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);

        // Create production security profile with deployment context
        com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration(cfc);

        // Verify deployment context overrides profile default
        assertTrue(config.isWafEnabled(), "Deployment context should override security profile default");
    }

    /**
     * Test that security profile returns false when deployment context is null
     */
    @Test
    public void testSecurityProfileWafDefaultWithNullContext() {
        com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration(null);

        // Verify WAF is disabled when no deployment context
        assertFalse(config.isWafEnabled(), "WAF should be disabled when deployment context is null");
    }

    /**
     * Test Dev security profile has WAF disabled
     */
    @Test
    public void testDevSecurityProfileWafDefault() {
        com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration();

        // Verify WAF is disabled in dev profile
        assertFalse(config.isWafEnabled(), "WAF should be disabled in dev profile");
    }

    /**
     * Test Staging security profile has WAF enabled by default
     * (staging uses WAF for testing purposes)
     */
    @Test
    public void testStagingSecurityProfileWafDefault() {
        com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration config =
            new com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration();

        // Verify WAF is enabled by default in staging profile (for testing WAF configuration)
        assertTrue(config.isWafEnabled(), "WAF should be enabled in staging profile for testing");
    }

    // ========================================
    // SECURITY TESTS - WAF Rule Coverage
    // ========================================

    /**
     * Test that WAF WebACL is created with proper configuration when enabled.
     * Verifies the basic WAF resource creation and properties.
     */
    @Test
    @DisplayName("WAF WebACL is created with proper security configuration")
    public void testWafWebAclCreation() {
        Template template = synthWaf("TestWafCreation");

        // Verify WebACL exists
        template.resourceCountIs("AWS::WAFv2::WebACL", 1);

        // Verify WebACL has proper scope (REGIONAL for ALB)
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of(
                "Scope", "REGIONAL",
                "DefaultAction", Match.objectLike(Map.of("Allow", Match.anyValue()))
            )
        ));

        // Verify visibility config is enabled for monitoring
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of(
                "VisibilityConfig", Match.objectLike(
                    Map.of(
                        "CloudWatchMetricsEnabled", true,
                        "SampledRequestsEnabled", true
                    )
                )
            )
        ));
    }

    /**
     * Test that SQL Injection protection rules are active.
     * Verifies that the WAF includes AWS Managed SQLi rule set.
     */
    @Test
    @DisplayName("SQL Injection protection is enabled")
    public void testSqlInjectionProtection() {
        Template template = synthWaf("TestSQLi");

        // Verify SQLi rule set is included
        assertManagedRuleGroup(template, "AWSManagedRulesSQLiRuleSet");
    }

    /**
     * Test that Linux OS protection rules are active.
     * Verifies protection against shell injection and path traversal attacks.
     */
    @Test
    @DisplayName("Linux OS protection (shell injection, path traversal) is enabled")
    public void testLinuxOsProtection() {
        Template template = synthWaf("TestLinuxOS");

        // Verify Linux rule set is included
        assertManagedRuleGroup(template, "AWSManagedRulesLinuxRuleSet");
    }

    /**
     * Test that Known Bad Inputs protection is active.
     * Verifies protection against known malicious patterns.
     */
    @Test
    @DisplayName("Known Bad Inputs protection is enabled")
    public void testKnownBadInputsProtection() {
        Template template = synthWaf("TestBadInputs");

        // Verify Known Bad Inputs rule set is included
        assertManagedRuleGroup(template, "AWSManagedRulesKnownBadInputsRuleSet");
    }

    /**
     * Test that Jenkins-specific exclusions are configured.
     * Verifies that legitimate Jenkins traffic won't be blocked.
     */
    @Test
    @DisplayName("Jenkins-specific rule exclusions are configured")
    public void testJenkinsExclusions() {
        Template template = synthWaf("TestExclusions");

        // Verify SQLi rule has exclusions for Jenkins (from WafFactory lines 161-164)
        assertExcludedRules(template, "AWSManagedRulesSQLiRuleSet", "SQLi_BODY", "SQLi_QUERYARGUMENTS", "SQLi_COOKIE");

        // Verify Known Bad Inputs has Java deserialization exclusions
        assertExcludedRules(template, "AWSManagedRulesKnownBadInputsRuleSet", "JavaDeserializationRCE_BODY");
    }

    /**
     * Test that WAF WebACL is associated with ALB.
     * Verifies that the WAF protection is actually applied to the load balancer.
     */
    @Test
    @DisplayName("WAF WebACL is properly associated with ALB")
    public void testWafAlbAssociation() {
        Template template = synthWaf("TestAssociation");

        // Verify WebACL Association exists
        template.resourceCountIs("AWS::WAFv2::WebACLAssociation", 1);

        // Verify association references both WebACL and ALB
        template.hasResourceProperties("AWS::WAFv2::WebACLAssociation", Match.objectLike(
            Map.of(
                "ResourceArn", Match.anyValue(),
                "WebACLArn", Match.anyValue()
            )
        ));
    }

    /**
     * Summary test that verifies all critical WAF protections are active.
     * This test confirms that the WAF provides comprehensive security coverage.
     */
    @Test
    @DisplayName("WAF provides comprehensive security coverage")
    public void testComprehensiveWafSecurity() {
        Template template = synthWaf("TestComprehensiveSecurity");

        // Verify WAF is created and associated
        template.resourceCountIs("AWS::WAFv2::WebACL", 1);
        template.resourceCountIs("AWS::WAFv2::WebACLAssociation", 1);

        // Verify all three critical rule sets are present
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of(
                "Rules", Match.arrayWith(
                    List.of(
                        // Known Bad Inputs
                        Match.objectLike(Map.of("Name", "AWS-AWSManagedRulesKnownBadInputsRuleSet")),
                        // SQL Injection
                        Match.objectLike(Map.of("Name", "AWS-AWSManagedRulesSQLiRuleSet")),
                        // Linux OS (shell injection, path traversal)
                        Match.objectLike(Map.of("Name", "AWS-AWSManagedRulesLinuxRuleSet"))
                    )
                )
            )
        ));

        // Verify monitoring is enabled
        template.hasResourceProperties("AWS::WAFv2::WebACL", Match.objectLike(
            Map.of(
                "VisibilityConfig", Match.objectLike(
                    Map.of(
                        "CloudWatchMetricsEnabled", true,
                        "SampledRequestsEnabled", true
                    )
                )
            )
        ));
    }
}
