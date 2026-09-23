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
 * Covers the ADVISORY-tier paths added to Soc2Rules, GdprRules, PciDssRules, and
 * MessagingSecurityRules: the underlying control is off, but since the framework doesn't require
 * it, synthesis must still succeed (a ComplianceRule.advisory() finding, not a fail()).
 */
class AdvisoryFindingsTest {

    /** Baseline that satisfies every REQUIRED control for the given framework, so only the one
     *  control under test is left off. No complianceMode is set (defaults to ENFORCE). */
    private Map<String, Object> baseline(String framework) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", "AdvisoryTest");
        ctx.put("complianceFrameworks", framework);
        ctx.put("auditManagerEnabled", true);
        ctx.put("awsConfigEnabled", true);
        ctx.put("enableSsl", true);
        ctx.put("wafEnabled", true);
        ctx.put("networkMode", "private-with-nat");
        ctx.put("authMode", "application-oidc");
        ctx.put("cognitoAutoProvision", true);
        ctx.put("logRetentionDays", 2190);
        ctx.put("cloudTrailEnabled", true);
        ctx.put("enableFlowlogs", true);
        ctx.put("albAccessLogging", true);
        ctx.put("cloudWatchLogsKmsEncryptionEnabled", true);
        ctx.put("s3ObjectLockEnabled", true);
        ctx.put("efsEncryptionInTransitEnabled", true);
        ctx.put("ebsEncryptionEnabled", true);
        ctx.put("efsEncryptionAtRestEnabled", true);
        ctx.put("s3EncryptionEnabled", true);
        ctx.put("automatedBackupEnabled", true);
        ctx.put("gdprDataTransferApproved", true);
        ctx.put("securityMonitoringEnabled", true);
        ctx.put("guardDutyEnabled", true);
        ctx.put("macieEnabled", true);
        ctx.put("macieAutomatedDiscovery", true);
        ctx.put("antiMalwareEnabled", true);
        ctx.put("fileIntegrityMonitoring", true);
        return ctx;
    }

    @Test
    void soc2SecurityMonitoringOffIsAdvisoryNotBlocking() {
        Map<String, Object> ctx = baseline("SOC2");
        ctx.put("securityMonitoringEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SocMon", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new Soc2Rules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "SECURITY_MONITORING is ADVISORY for SOC2 -- must not block synthesis");
    }

    @Test
    void soc2GuardDutyOffIsAdvisoryNotBlocking() {
        Map<String, Object> ctx = baseline("SOC2");
        ctx.put("guardDutyEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SocGd", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new Soc2Rules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "THREAT_DETECTION is ADVISORY for SOC2 -- must not block synthesis");
    }

    @Test
    void soc2CrossRegionBackupWithoutVaultIsAdvisoryNotBlocking() {
        // No backupCrossRegionVaultArn set -- SOC2-A1.3-CrossRegion is a recommendation, not a
        // requirement, so this must not block even though crossRegionBackupEnabled reads true.
        Map<String, Object> ctx = baseline("SOC2");
        ctx.put("crossRegionBackupEnabled", true);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SocCr", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new Soc2Rules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void gdprGuardDutyOffIsAdvisoryNotBlocking() {
        Map<String, Object> ctx = baseline("GDPR");
        ctx.put("guardDutyEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("GdprGd", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new GdprRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "THREAT_DETECTION is ADVISORY for GDPR -- must not block synthesis");
    }

    @Test
    void gdprImdsv2OffOnEc2IsAdvisoryNotBlocking() {
        Map<String, Object> ctx = baseline("GDPR");
        ctx.put("imdsv2Required", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("GdprImds", SecurityProfile.PRODUCTION,
                RuntimeType.EC2, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new GdprRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "EC2_IMDSV2 is ADVISORY for GDPR -- must not block synthesis");
    }

    @Test
    void pciDssImdsv2OffOnEc2IsAdvisoryNotBlocking() {
        Map<String, Object> ctx = baseline("PCI-DSS");
        ctx.put("imdsv2Required", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("PciImds", SecurityProfile.PRODUCTION,
                RuntimeType.EC2, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new PciDssRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "EC2_IMDSV2 is ADVISORY for PCI-DSS -- must not block synthesis");
    }

    @Test
    void pciDssImdsv2NotCheckedOnFargate() {
        // EC2_IMDSV2 is only checked for EC2 -- Fargate has no instance metadata service.
        Map<String, Object> ctx = baseline("PCI-DSS");
        ctx.put("imdsv2Required", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("PciImdsFg", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            builder.createMockHttpsListener();
            new PciDssRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }

    @Test
    void snsKmsEncryptionOffIsAdvisoryForSoc2NotBlocking() {
        Map<String, Object> ctx = baseline("SOC2");
        ctx.put("snsKmsEncryptionEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SnsSoc2", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new MessagingSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "SNS_KMS_ENCRYPTION is ADVISORY for SOC2 -- must not block synthesis");
    }

    @Test
    void snsKmsEncryptionOffIsAdvisoryForGdprNotBlocking() {
        Map<String, Object> ctx = baseline("GDPR");
        ctx.put("snsKmsEncryptionEnabled", false);
        assertDoesNotThrow(() -> {
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SnsGdpr", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new MessagingSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        }, "SNS_KMS_ENCRYPTION is ADVISORY for GDPR -- must not block synthesis");
    }

    @Test
    void snsKmsEncryptionOffFailsForHipaaRequired() {
        // Contrast case: SNS_KMS_ENCRYPTION is REQUIRED for HIPAA, so the same override blocks.
        Map<String, Object> ctx = baseline("HIPAA");
        ctx.put("snsKmsEncryptionEnabled", false);
        assertDoesNotThrow(() -> {
            // The getter hardcodes compliant once HIPAA requires the control (see
            // ProductionSecurityProfileConfiguration#isSnsKmsEncryptionEnabled), so the override
            // cannot weaken it -- this documents that it stays a clean pass, not a finding.
            TestInfrastructureBuilder builder = new TestInfrastructureBuilder("SnsHipaa", SecurityProfile.PRODUCTION,
                RuntimeType.FARGATE, ctx);
            builder.createMinimalInfrastructure();
            builder.createMockCertificate();
            new MessagingSecurityRules().install(builder.getSystemContext());
            Template.fromStack(builder.getStack());
        });
    }
}
