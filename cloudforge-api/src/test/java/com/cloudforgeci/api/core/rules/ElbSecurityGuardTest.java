package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for elb-security.guard CloudFormation Guard rules.
 *
 * Validates Application Load Balancer, Network Load Balancer, and Classic ELB security rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class ElbSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/elb-security.guard";

    private String loadGuardFile() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(GUARD_FILE_PATH)) {
            assertNotNull(is, "Guard file should exist: " + GUARD_FILE_PATH);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    @Test
    void testGuardFileExists() throws IOException {
        String content = loadGuardFile();
        assertNotNull(content);
        assertFalse(content.isEmpty(), "Guard file should not be empty");
    }

    @Test
    void testGuardFileHasHeader() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("ELB Security"), "Should have ELB Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== Application/Network Load Balancer Security Rules ==========

    @Test
    void testAlbAccessLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_alb_access_logging"),
            "Should have ALB access logging rule");
        assertTrue(content.contains("access_logs.s3.enabled"),
            "Should check for access logging attribute");
    }

    @Test
    void testAlbDeletionProtectionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_alb_deletion_protection"),
            "Should have ALB deletion protection rule");
        assertTrue(content.contains("deletion_protection.enabled"),
            "Should check for deletion protection attribute");
    }

    @Test
    void testAlbDropHttpHeadersRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_alb_drop_http_headers"),
            "Should have drop invalid HTTP headers rule");
        assertTrue(content.contains("routing.http.drop_invalid_header_fields.enabled"),
            "Should check for drop headers attribute");
    }

    @Test
    void testCrossZoneLoadBalancingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_cross_zone_load_balancing"),
            "Should have cross-zone load balancing rule");
        assertTrue(content.contains("load_balancing.cross_zone.enabled"),
            "Should check for cross-zone attribute");
    }

    @Test
    void testAlbInternalSchemeRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_alb_internal_scheme"),
            "Should have internal scheme rule");
        assertTrue(content.contains("Scheme"),
            "Should check for Scheme property");
    }

    @Test
    void testAlbWafRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_alb_waf"),
            "Should have ALB WAF rule");
        assertTrue(content.contains("AWS::WAFv2::WebACLAssociation"),
            "Should target WAF association resource type");
    }

    // ========== Load Balancer Listener Security Rules ==========

    @Test
    void testListenerHttpsRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_listener_https"),
            "Should have listener HTTPS rule");
        assertTrue(content.contains("HTTPS"),
            "Should check for HTTPS protocol");
        assertTrue(content.contains("TLS"),
            "Should check for TLS protocol");
    }

    @Test
    void testListenerCertificateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_listener_certificate"),
            "Should have listener certificate rule");
        assertTrue(content.contains("Certificates"),
            "Should check for Certificates property");
    }

    @Test
    void testListenerSslPolicyRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_listener_ssl_policy"),
            "Should have listener SSL policy rule");
        assertTrue(content.contains("SslPolicy"),
            "Should check for SslPolicy property");
        assertTrue(content.contains("ELBSecurityPolicy-TLS13"),
            "Should accept TLS 1.3 policies");
    }

    // ========== Target Group Security Rules ==========

    @Test
    void testTargetGroupHealthCheckRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_target_group_health_check"),
            "Should have target group health check rule");
        assertTrue(content.contains("HealthCheckPath"),
            "Should check for HealthCheckPath property");
    }

    @Test
    void testTargetGroupDeregistrationRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_target_group_deregistration"),
            "Should have target group deregistration rule");
        assertTrue(content.contains("deregistration_delay.timeout_seconds"),
            "Should check for deregistration delay attribute");
    }

    // ========== Classic Load Balancer Security Rules ==========

    @Test
    void testClassicAccessLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_classic_access_logging"),
            "Should have Classic ELB access logging rule");
        assertTrue(content.contains("AccessLoggingPolicy"),
            "Should check for AccessLoggingPolicy property");
    }

    @Test
    void testClassicNotPublicRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_classic_not_public"),
            "Should have Classic ELB not public rule");
        assertTrue(content.contains("internet-facing"),
            "Should detect internet-facing scheme");
    }

    @Test
    void testClassicSslCertificateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_classic_ssl_certificate"),
            "Should have Classic ELB SSL certificate rule");
        assertTrue(content.contains("SSLCertificateId"),
            "Should check for SSLCertificateId property");
    }

    @Test
    void testClassicConnectionDrainingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_classic_connection_draining"),
            "Should have Classic ELB connection draining rule");
        assertTrue(content.contains("ConnectionDrainingPolicy"),
            "Should check for ConnectionDrainingPolicy property");
    }

    @Test
    void testClassicCrossZoneRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_classic_cross_zone"),
            "Should have Classic ELB cross-zone rule");
        assertTrue(content.contains("CrossZone"),
            "Should check for CrossZone property");
    }

    // ========== Listener Rule Security ==========

    @Test
    void testListenerRuleConditionsRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule elb_security_listener_rule_conditions"),
            "Should have listener rule conditions rule");
        assertTrue(content.contains("Conditions"),
            "Should check for Conditions property");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "AUDIT_LOGGING",
        "DELETION_PROTECTION",
        "WEB_APPLICATION_FIREWALL",
        "HIGH_AVAILABILITY",
        "ENCRYPTION_IN_TRANSIT",
        "CERTIFICATE_MANAGEMENT",
        "NETWORK_SECURITY"
    })
    void testCloudForgeMappingsExist(String control) throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains(control),
            "Should map to CloudForge control: " + control);
    }

    @Test
    void testAllRulesHaveCloudForgeMapping() throws IOException {
        String content = loadGuardFile();
        long ruleCount = content.lines()
            .filter(line -> line.trim().startsWith("rule elb_security"))
            .count();
        long mappingCount = content.lines()
            .filter(line -> line.contains("CloudForge Mapping:"))
            .count();

        assertTrue(ruleCount > 0, "Should have at least one rule");
        assertEquals(ruleCount, mappingCount,
            "Each rule should have a CloudForge Mapping");
    }

    @Test
    void testRuleCountIsExpected() throws IOException {
        String content = loadGuardFile();
        long ruleCount = content.lines()
            .filter(line -> line.trim().startsWith("rule elb_security"))
            .count();

        assertTrue(ruleCount >= 15, "Should have at least 15 ELB security rules");
    }

    @Test
    void testTls12PoliciesAreAccepted() throws IOException {
        String content = loadGuardFile();
        String[] tls12Policies = {
            "ELBSecurityPolicy-TLS13-1-2-2021-06",
            "ELBSecurityPolicy-TLS-1-2-2017-01",
            "ELBSecurityPolicy-FS-1-2-2019-08"
        };

        for (String policy : tls12Policies) {
            assertTrue(content.contains(policy),
                "Should accept TLS 1.2+ policy: " + policy);
        }
    }
}
