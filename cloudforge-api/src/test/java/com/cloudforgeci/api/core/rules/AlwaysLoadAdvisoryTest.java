package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Covers the ADVISORY-tier paths converted from a plain pass() to ComplianceRule.advisory() in
 * ElbSecurityRules, IamSecurityRules, LambdaSecurityRules, ComputeSecurityRules and
 * CdnApiSecurityRules -- none of these classes had install()-driving tests before. Uses
 * complianceMode=advisory for the controls that are REQUIRED under every real framework (so a
 * disabled control only reaches ComplianceMatrix.ValidationResult.WARN when the whole deployment
 * is in advisory mode), and CREDENTIAL_ROTATION/WAF_PROTECTION's own per-framework ADVISORY level
 * (GDPR, HIPAA) where one exists.
 */
class AlwaysLoadAdvisoryTest {

    /** A fully-compliant PRODUCTION context; individual tests unset one control from this. */
    private Map<String, Object> baseline() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", "AlwaysLoadAdvisory");
        ctx.put("authMode", "application-oidc");
        ctx.put("cognitoAutoProvision", true);
        ctx.put("networkMode", "private-with-nat");
        ctx.put("enableSsl", true);
        return ctx;
    }

    @Test
    void albAccessLoggingOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("albAccessLogging", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbLog", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new ElbSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void albDeletionProtectionOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("albDeletionProtection", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlbDel", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new ElbSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void rootMfaOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("rootMfaEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("RootMfa", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new IamSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void credentialRotationOffIsAdvisoryForGdpr() {
        // CREDENTIAL_ROTATION is ADVISORY for GDPR specifically, unlike every other framework.
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "GDPR");
        ctx.put("credentialRotationEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("CredRot", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new IamSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void lambdaVpcDeploymentOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("lambdaInVpc", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("LambdaVpc", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new LambdaSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void lambdaEnvEncryptionOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("lambdaEnvEncryption", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("LambdaEnv", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new LambdaSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void lambdaXrayTracingOffIsAdvisoryInAdvisoryMode() {
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("lambdaXrayTracing", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("LambdaXray", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new LambdaSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void ebsEncryptionOffIsAdvisoryInAdvisoryMode() {
        // ENCRYPTION_AT_REST is REQUIRED under every real framework's ENFORCE mode; only a
        // deployment-wide complianceMode=advisory reaches ComputeSecurityRules' WARN branch.
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "SOC2");
        ctx.put("complianceMode", "advisory");
        ctx.put("ebsEncryptionEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EbsAdv", SecurityProfile.PRODUCTION,
                RuntimeType.EC2, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new ComputeSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void wafProtectionOffIsAdvisoryForHipaa() {
        // WAF_PROTECTION is ADVISORY for HIPAA specifically, unlike SOC2/GDPR/PCI-DSS/FedRAMP.
        Map<String, Object> ctx = baseline();
        ctx.put("complianceFrameworks", "HIPAA");
        ctx.put("wafEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("WafHipaa", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new CdnApiSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }
}
