package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for S3WebsiteTopologyConfiguration.
 *
 * Tests S3 Website topology which handles:
 * - S3 bucket creation with proper encryption
 * - CloudFront distribution for SSL/TLS
 * - DNS record creation (A/AAAA) for CloudFront
 * - SSL validation rules
 * - Bucket encryption based on security profile
 */
class S3WebsiteTopologyConfigurationTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile,
                                   Map<String, Object> additionalContext) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        cfcContext.put("domain", "example.com");
        if (additionalContext != null) {
            cfcContext.putAll(additionalContext);
        }
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testS3WebsiteTopologyConfigurationKind() {
        // Given: S3 Website topology configuration
        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting kind
        TopologyType kind = config.kind();

        // Then: Should return S3_WEBSITE
        assertEquals(TopologyType.S3_WEBSITE, kind);
    }

    @Test
    void testS3WebsiteTopologyConfigurationId() {
        // Given: S3 Website topology configuration
        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting ID
        String id = config.id();

        // Then: Should return expected ID
        assertEquals("topology:S3_WEBSITE", id);
    }

    @Test
    void testS3WebsiteTopologyConfigurationRules() {
        // Given: An S3 Website deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteRules", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Getting rules
        var rules = config.rules(ctx);

        // Then: Should have rules for SSL/CloudFront validation, bucket requirement, etc.
        assertNotNull(rules);
        assertTrue(rules.size() >= 5);
    }

    @Test
    void testS3WebsiteTopologyConfigurationBasicWiring() {
        // Given: A basic S3 Website deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteBasic", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring configuration
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithCloudFront() {
        // Given: An S3 Website deployment with CloudFront enabled
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("cloudfrontEnabled", true);
        context.put("fqdn", "www.example.com");
        Stack stack = createTestStack(app, "TestS3WebsiteCloudFront", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with CloudFront
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithSslAndCloudFront() {
        // Given: An S3 Website deployment with SSL and CloudFront
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("enableSsl", true);
        context.put("cloudfrontEnabled", true);
        context.put("fqdn", "www.example.com");
        Stack stack = createTestStack(app, "TestS3WebsiteSsl", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with SSL and CloudFront
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithAllSecurityProfiles() {
        // Given: Each security profile
        int counter = 0;
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestS3WebsiteProfile" + counter++, profile, null);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

            // When: Wiring for each profile
            assertDoesNotThrow(() -> config.wire(ctx),
                    "S3WebsiteTopologyConfiguration should not throw for security profile: " + profile);
        }
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithSubdomain() {
        // Given: An S3 Website deployment with subdomain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("cloudfrontEnabled", true);
        context.put("subdomain", "www");
        Stack stack = createTestStack(app, "TestS3WebsiteSubdomain", SecurityProfile.DEV, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with subdomain
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithMinimalIamProfile() {
        // Given: An S3 Website deployment with MINIMAL IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteMinimalIam", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with MINIMAL IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithExtendedIamProfile() {
        // Given: An S3 Website deployment with EXTENDED IAM profile
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteExtendedIam", SecurityProfile.PRODUCTION, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, IAMProfile.EXTENDED, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with EXTENDED IAM
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationMultipleWireCalls() {
        // Given: An S3 Website deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteMultiWire", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Calling wire() multiple times
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));
        assertDoesNotThrow(() -> config.wire(ctx));

        // Then: Should handle duplicate wire calls gracefully
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithProductionProfile() {
        // Given: An S3 Website deployment with PRODUCTION profile (KMS encryption)
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteProduction", SecurityProfile.PRODUCTION, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring for PRODUCTION (should use KMS encryption)
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithStagingProfile() {
        // Given: An S3 Website deployment with STAGING profile (S3-managed encryption)
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteStaging", SecurityProfile.STAGING, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.STAGING);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.STAGING, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring for STAGING (should use S3-managed encryption)
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithDevProfile() {
        // Given: An S3 Website deployment with DEV profile (S3-managed encryption)
        App app = new App();
        Stack stack = createTestStack(app, "TestS3WebsiteDev", SecurityProfile.DEV, null);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring for DEV (should use S3-managed encryption)
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithCloudFrontAndFqdn() {
        // Given: An S3 Website deployment with CloudFront and explicit fqdn
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("cloudfrontEnabled", true);
        context.put("fqdn", "website.example.com");
        Stack stack = createTestStack(app, "TestS3WebsiteCfFqdn", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with CloudFront and fqdn
        assertDoesNotThrow(() -> config.wire(ctx));
    }

    @Test
    void testS3WebsiteTopologyConfigurationWithCloudFrontAndSubdomainDomain() {
        // Given: An S3 Website deployment with CloudFront using subdomain + domain
        App app = new App();
        Map<String, Object> context = new HashMap<>();
        context.put("cloudfrontEnabled", true);
        context.put("subdomain", "www");
        Stack stack = createTestStack(app, "TestS3WebsiteCfSubdomain", SecurityProfile.PRODUCTION, context);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        S3WebsiteTopologyConfiguration config = new S3WebsiteTopologyConfiguration();

        // When: Wiring with CloudFront using subdomain + domain
        assertDoesNotThrow(() -> config.wire(ctx));
    }
}
