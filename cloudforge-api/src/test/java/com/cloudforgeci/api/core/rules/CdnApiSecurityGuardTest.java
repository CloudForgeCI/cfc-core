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
 * Test suite for cdn-api-security.guard CloudFormation Guard rules.
 *
 * Validates CloudFront, API Gateway, and WAF security rules.
 * CloudForge Core - Multi-Layer Compliance Validation
 * Layer 3: Template-Level Policy Enforcement (cfn-guard)
 */
class CdnApiSecurityGuardTest {

    private static final String GUARD_FILE_PATH = "/cfn-guard/frameworks/cdn-api-security.guard";

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
        assertTrue(content.contains("CDN and API Security"), "Should have CDN and API Security header");
        assertTrue(content.contains("CloudForge Core"), "Should reference CloudForge Core");
        assertTrue(content.contains("Layer 3"), "Should reference Layer 3");
    }

    // ========== CloudFront Distribution Security Rules ==========

    @Test
    void testCloudfrontLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_logging"),
            "Should have CloudFront logging rule");
        assertTrue(content.contains("Logging"),
            "Should check for Logging property");
    }

    @Test
    void testCloudfrontWafRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_waf"),
            "Should have CloudFront WAF rule");
        assertTrue(content.contains("WebACLId"),
            "Should check for WebACLId property");
    }

    @Test
    void testCloudfrontGeoRestrictionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_geo_restriction"),
            "Should have CloudFront geo restriction rule");
        assertTrue(content.contains("GeoRestriction"),
            "Should check for GeoRestriction property");
    }

    @Test
    void testCloudfrontNoDeprecatedSslRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_no_deprecated_ssl"),
            "Should have deprecated SSL rule");
        assertTrue(content.contains("SSLv3"),
            "Should detect SSLv3");
        assertTrue(content.contains("TLSv1"),
            "Should detect TLSv1");
    }

    @Test
    void testCloudfrontAcmCertificateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_acm_certificate"),
            "Should have ACM certificate rule");
        assertTrue(content.contains("AcmCertificateArn"),
            "Should check for AcmCertificateArn property");
    }

    @Test
    void testCloudfrontMinimumTlsRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_minimum_tls"),
            "Should have minimum TLS rule");
        assertTrue(content.contains("MinimumProtocolVersion"),
            "Should check for MinimumProtocolVersion property");
        assertTrue(content.contains("TLSv1.2"),
            "Should require TLS 1.2");
    }

    @Test
    void testCloudfrontHttpsOnlyRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule cdn_security_cloudfront_https_only"),
            "Should have HTTPS only rule");
        assertTrue(content.contains("ViewerProtocolPolicy"),
            "Should check for ViewerProtocolPolicy property");
        assertTrue(content.contains("redirect-to-https"),
            "Should accept redirect-to-https");
    }

    @Test
    void testCloudfrontOriginHttpsRule() throws IOException {
        String content = loadGuardFile();
        // Origin HTTPS check is now integrated into the deprecated SSL rule
        assertTrue(content.contains("rule cdn_security_cloudfront_no_deprecated_ssl"),
            "Should have deprecated SSL rule that includes origin HTTPS check");
        assertTrue(content.contains("OriginProtocolPolicy"),
            "Should check for OriginProtocolPolicy property");
    }

    // ========== API Gateway REST API Security Rules ==========

    @Test
    void testApiGatewayClientCertificateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_restapi_client_certificate"),
            "Should have client certificate rule");
        assertTrue(content.contains("ClientCertificateId"),
            "Should check for ClientCertificateId property");
    }

    @Test
    void testApiGatewayPrivateRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_restapi_private"),
            "Should have private API rule");
        assertTrue(content.contains("PRIVATE"),
            "Should check for PRIVATE endpoint type");
    }

    @Test
    void testApiGatewayAccessLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_stage_access_logging"),
            "Should have access logging rule");
        assertTrue(content.contains("AccessLogSetting"),
            "Should check for AccessLogSetting property");
    }

    @Test
    void testApiGatewayXrayTracingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_stage_xray_tracing"),
            "Should have X-Ray tracing rule");
        assertTrue(content.contains("TracingEnabled"),
            "Should check for TracingEnabled property");
    }

    @Test
    void testApiGatewayMethodLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_stage_method_logging"),
            "Should have method logging rule");
        assertTrue(content.contains("MethodSettings"),
            "Should check for MethodSettings property");
    }

    @Test
    void testApiGatewayCacheEncryptionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_stage_cache_encryption"),
            "Should have cache encryption rule");
        assertTrue(content.contains("CacheDataEncrypted"),
            "Should check for CacheDataEncrypted property");
    }

    @Test
    void testApiGatewayWafRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_restapi_waf"),
            "Should have API WAF rule");
        assertTrue(content.contains("WebAclArn"),
            "Should check for WebAclArn property");
    }

    // ========== API Gateway HTTP API Security Rules ==========

    @Test
    void testHttpApiLoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_httpapi_logging"),
            "Should have HTTP API logging rule");
        assertTrue(content.contains("AccessLogSettings"),
            "Should check for AccessLogSettings property");
    }

    @Test
    void testHttpApiThrottlingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule api_security_httpapi_throttling"),
            "Should have HTTP API throttling rule");
        assertTrue(content.contains("ThrottlingBurstLimit"),
            "Should check for ThrottlingBurstLimit property");
        assertTrue(content.contains("ThrottlingRateLimit"),
            "Should check for ThrottlingRateLimit property");
    }

    // ========== WAF Security Rules ==========

    @Test
    void testWafNoClassicRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule waf_security_no_classic"),
            "Should have WAF Classic deprecation rule");
        assertTrue(content.contains("AWS::WAF::WebACL"),
            "Should target WAF Classic resource type");
    }

    @Test
    void testWafv2RulesRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule waf_security_wafv2_rules"),
            "Should have WAFv2 rules rule");
        assertTrue(content.contains("AWS::WAFv2::WebACL"),
            "Should target WAFv2 resource type");
    }

    @Test
    void testWafv2DefaultActionRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule waf_security_wafv2_default_action"),
            "Should have WAFv2 default action rule");
        assertTrue(content.contains("DefaultAction"),
            "Should check for DefaultAction property");
    }

    @Test
    void testWafv2LoggingRule() throws IOException {
        String content = loadGuardFile();
        assertTrue(content.contains("rule waf_security_wafv2_logging"),
            "Should have WAFv2 logging rule");
        assertTrue(content.contains("LogDestinationConfigs"),
            "Should check for LogDestinationConfigs property");
    }

    // ========== CloudForge Mapping Validation ==========

    @ParameterizedTest
    @ValueSource(strings = {
        "CDN_SECURITY",
        "API_SECURITY",
        "AUDIT_LOGGING",
        "ENCRYPTION_IN_TRANSIT",
        "WEB_APPLICATION_FIREWALL",
        "CERTIFICATE_MANAGEMENT"
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
            .filter(line -> line.trim().startsWith("rule cdn_security") ||
                           line.trim().startsWith("rule api_security") ||
                           line.trim().startsWith("rule waf_security"))
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
            .filter(line -> line.trim().startsWith("rule cdn_security") ||
                           line.trim().startsWith("rule api_security") ||
                           line.trim().startsWith("rule waf_security"))
            .count();

        assertTrue(ruleCount >= 18, "Should have at least 18 CDN/API security rules");
    }
}
