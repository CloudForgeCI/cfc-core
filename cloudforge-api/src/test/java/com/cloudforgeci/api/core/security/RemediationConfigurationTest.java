package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for AWS Config remediation configuration across all security profiles.
 *
 * <h2>Test Structure Rationale</h2>
 * <p>This test class intentionally maintains granular, per-remediation tests rather than
 * consolidated aggregate tests for the following reasons:</p>
 * <ul>
 *   <li><b>Compliance Audit Trail:</b> Individual test methods provide clear audit evidence
 *       for each security control (required for SOC2, HIPAA, PCI-DSS audits)</li>
 *   <li><b>Regression Detection:</b> Specific test failures immediately identify which
 *       remediation broke, reducing debugging time</li>
 *   <li><b>Documentation:</b> Test names serve as living documentation of what remediations
 *       are enabled/disabled per environment</li>
 *   <li><b>Test Idempotency:</b> Each @BeforeEach setUp() creates fresh instances to ensure
 *       test isolation and repeatability across different deployment contexts</li>
 * </ul>
 *
 * <p><b>Note on "Redundancy":</b> Tests that appear redundant (e.g., checking same remediation
 * across profiles) are intentionally kept separate to validate environment-specific behavior.
 * Consolidating these would obscure which specific controls are tested and make audit review more difficult.</p>
 */
@DisplayName("Remediation Configuration Tests")
public class RemediationConfigurationTest {

    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("Production Security Profile Remediation Tests")
    class ProductionRemediationTests {

        private ProductionSecurityProfileConfiguration config;

        @BeforeEach
        void setUp() {
            config = new ProductionSecurityProfileConfiguration(cfc);
        }

        @Test
        @DisplayName("Production profile returns correct security profile")
        void testSecurityProfile() {
            assertEquals(SecurityProfile.PRODUCTION, config.getSecurityProfile());
        }

        @Test
        @DisplayName("S3 versioning remediation disabled by default in production")
        void testS3VersioningRemediationDisabled() {
            assertFalse(config.isS3VersioningRemediationEnabled(),
                "S3 versioning remediation should be disabled due to cost implications");
        }

        @Test
        @DisplayName("CloudTrail bucket access remediation disabled by default in production")
        void testCloudTrailBucketAccessRemediationDisabled() {
            assertFalse(config.isCloudTrailBucketAccessRemediationEnabled(),
                "CloudTrail bucket access remediation should be disabled to prevent automatic policy changes");
        }

        @Test
        @DisplayName("EBS encryption remediation enabled in production")
        void testEbsEncryptionRemediationEnabled() {
            assertTrue(config.isEbsEncryptionRemediationEnabled(),
                "EBS encryption remediation should be enabled - low risk, high security value");
        }

        @Test
        @DisplayName("GuardDuty remediation enabled in production")
        void testGuardDutyRemediationEnabled() {
            assertTrue(config.isGuardDutyRemediationEnabled(),
                "GuardDuty remediation should be enabled - production should have threat detection");
        }

        @Test
        @DisplayName("VPC default security group remediation enabled in production")
        void testVpcDefaultSgRemediationEnabled() {
            assertTrue(config.isVpcDefaultSgRemediationEnabled(),
                "VPC default SG remediation should be enabled - best practice");
        }

        @Test
        @DisplayName("ELB deletion protection remediation enabled in production")
        void testElbDeletionProtectionRemediationEnabled() {
            assertTrue(config.isElbDeletionProtectionRemediationEnabled(),
                "ELB deletion protection remediation should be enabled - prevent accidental deletion");
        }

        @Test
        @DisplayName("KMS key rotation remediation enabled in production")
        void testKmsKeyRotationRemediationEnabled() {
            assertTrue(config.isKmsKeyRotationRemediationEnabled(),
                "KMS key rotation remediation should be enabled - compliance best practice");
        }

        @Test
        @DisplayName("SSH removal remediation disabled by default in production")
        void testSshRemovalRemediationDisabled() {
            assertFalse(config.isSshRemovalRemediationEnabled(),
                "SSH removal remediation should be disabled - could break required access");
        }

        @Test
        @DisplayName("Access key rotation remediation disabled by default in production")
        void testAccessKeyRotationRemediationDisabled() {
            assertFalse(config.isAccessKeyRotationRemediationEnabled(),
                "Access key rotation remediation should be disabled - requires user notification workflow");
        }

        @Test
        @DisplayName("DynamoDB PITR remediation enabled in production")
        void testDynamoDbPitrRemediationEnabled() {
            assertTrue(config.isDynamoDbPitrRemediationEnabled(),
                "DynamoDB PITR remediation should be enabled - low risk, high security value");
        }

        @Test
        @DisplayName("RDS Multi-AZ remediation disabled by default in production")
        void testRdsMultiAzRemediationDisabled() {
            assertFalse(config.isRdsMultiAzRemediationEnabled(),
                "RDS Multi-AZ remediation should be disabled - requires maintenance window");
        }

        @Test
        @DisplayName("RDS encryption remediation disabled by default in production")
        void testRdsEncryptionRemediationDisabled() {
            assertFalse(config.isRdsEncryptionRemediationEnabled(),
                "RDS encryption remediation should be disabled - complex operation requiring snapshot recreation");
        }

        @Test
        @DisplayName("Production has exactly 6 enabled remediations")
        void testProductionEnabledRemediationCount() {
            int enabledCount = 0;
            if (config.isS3VersioningRemediationEnabled()) enabledCount++;
            if (config.isCloudTrailBucketAccessRemediationEnabled()) enabledCount++;
            if (config.isEbsEncryptionRemediationEnabled()) enabledCount++;
            if (config.isGuardDutyRemediationEnabled()) enabledCount++;
            if (config.isVpcDefaultSgRemediationEnabled()) enabledCount++;
            if (config.isElbDeletionProtectionRemediationEnabled()) enabledCount++;
            if (config.isKmsKeyRotationRemediationEnabled()) enabledCount++;
            if (config.isSshRemovalRemediationEnabled()) enabledCount++;
            if (config.isAccessKeyRotationRemediationEnabled()) enabledCount++;
            if (config.isDynamoDbPitrRemediationEnabled()) enabledCount++;
            if (config.isRdsMultiAzRemediationEnabled()) enabledCount++;
            if (config.isRdsEncryptionRemediationEnabled()) enabledCount++;

            assertEquals(6, enabledCount,
                "Production should have exactly 6 safe remediations enabled by default");
        }
    }

    @Nested
    @DisplayName("Staging Security Profile Remediation Tests")
    class StagingRemediationTests {

        private StagingSecurityProfileConfiguration config;

        @BeforeEach
        void setUp() {
            config = new StagingSecurityProfileConfiguration(cfc);
        }

        @Test
        @DisplayName("Staging profile returns correct security profile")
        void testSecurityProfile() {
            assertEquals(SecurityProfile.STAGING, config.getSecurityProfile());
        }

        @Test
        @DisplayName("Staging mirrors production for EBS encryption remediation")
        void testEbsEncryptionRemediationEnabled() {
            assertTrue(config.isEbsEncryptionRemediationEnabled(),
                "Staging should mirror production EBS encryption remediation");
        }

        @Test
        @DisplayName("Staging mirrors production for GuardDuty remediation")
        void testGuardDutyRemediationEnabled() {
            assertTrue(config.isGuardDutyRemediationEnabled(),
                "Staging should mirror production GuardDuty remediation");
        }

        @Test
        @DisplayName("Staging mirrors production for VPC default SG remediation")
        void testVpcDefaultSgRemediationEnabled() {
            assertTrue(config.isVpcDefaultSgRemediationEnabled(),
                "Staging should mirror production VPC default SG remediation");
        }

        @Test
        @DisplayName("Staging mirrors production for ELB deletion protection remediation")
        void testElbDeletionProtectionRemediationEnabled() {
            assertTrue(config.isElbDeletionProtectionRemediationEnabled(),
                "Staging should mirror production ELB deletion protection remediation");
        }

        @Test
        @DisplayName("Staging mirrors production for KMS key rotation remediation")
        void testKmsKeyRotationRemediationEnabled() {
            assertTrue(config.isKmsKeyRotationRemediationEnabled(),
                "Staging should mirror production KMS key rotation remediation");
        }

        @Test
        @DisplayName("Staging mirrors production for DynamoDB PITR remediation")
        void testDynamoDbPitrRemediationEnabled() {
            assertTrue(config.isDynamoDbPitrRemediationEnabled(),
                "Staging should mirror production DynamoDB PITR remediation");
        }

        @Test
        @DisplayName("Staging has same enabled remediation count as production")
        void testStagingMatchesProductionRemediationCount() {
            ProductionSecurityProfileConfiguration prodConfig = new ProductionSecurityProfileConfiguration(cfc);

            int stagingEnabledCount = countEnabledRemediations(config);
            int productionEnabledCount = countEnabledRemediations(prodConfig);

            assertEquals(productionEnabledCount, stagingEnabledCount,
                "Staging should have same number of enabled remediations as production");
        }
    }

    @Nested
    @DisplayName("Dev Security Profile Remediation Tests")
    class DevRemediationTests {

        private DevSecurityProfileConfiguration config;

        @BeforeEach
        void setUp() {
            config = new DevSecurityProfileConfiguration(cfc);
        }

        @Test
        @DisplayName("Dev profile returns correct security profile")
        void testSecurityProfile() {
            assertEquals(SecurityProfile.DEV, config.getSecurityProfile());
        }

        @Test
        @DisplayName("All remediations disabled in dev for flexibility")
        void testAllRemediationsDisabled() {
            assertFalse(config.isS3VersioningRemediationEnabled());
            assertFalse(config.isCloudTrailBucketAccessRemediationEnabled());
            assertFalse(config.isEbsEncryptionRemediationEnabled());
            assertFalse(config.isGuardDutyRemediationEnabled());
            assertFalse(config.isVpcDefaultSgRemediationEnabled());
            assertFalse(config.isElbDeletionProtectionRemediationEnabled());
            assertFalse(config.isKmsKeyRotationRemediationEnabled());
            assertFalse(config.isSshRemovalRemediationEnabled());
            assertFalse(config.isAccessKeyRotationRemediationEnabled());
            assertFalse(config.isDynamoDbPitrRemediationEnabled());
            assertFalse(config.isRdsMultiAzRemediationEnabled());
            assertFalse(config.isRdsEncryptionRemediationEnabled());
        }

        @Test
        @DisplayName("Dev has zero enabled remediations")
        void testDevHasNoEnabledRemediations() {
            int enabledCount = countEnabledRemediations(config);
            assertEquals(0, enabledCount,
                "Dev should have all remediations disabled for developer flexibility");
        }
    }

    @Nested
    @DisplayName("Cross-Profile Remediation Comparison Tests")
    class CrossProfileTests {

        @Test
        @DisplayName("Production has more enabled remediations than dev")
        void testProductionHasMoreRemediationsThanDev() {
            ProductionSecurityProfileConfiguration prodConfig = new ProductionSecurityProfileConfiguration(cfc);
            DevSecurityProfileConfiguration devConfig = new DevSecurityProfileConfiguration(cfc);

            int prodEnabled = countEnabledRemediations(prodConfig);
            int devEnabled = countEnabledRemediations(devConfig);

            assertTrue(prodEnabled > devEnabled,
                "Production should have more enabled remediations than dev");
        }

        @Test
        @DisplayName("Staging has more enabled remediations than dev")
        void testStagingHasMoreRemediationsThanDev() {
            StagingSecurityProfileConfiguration stagingConfig = new StagingSecurityProfileConfiguration(cfc);
            DevSecurityProfileConfiguration devConfig = new DevSecurityProfileConfiguration(cfc);

            int stagingEnabled = countEnabledRemediations(stagingConfig);
            int devEnabled = countEnabledRemediations(devConfig);

            assertTrue(stagingEnabled > devEnabled,
                "Staging should have more enabled remediations than dev");
        }

        @Test
        @DisplayName("All security profiles have all 12 remediation methods")
        void testAllProfilesHaveAllRemediationMethods() {
            SecurityProfileConfiguration[] configs = {
                new ProductionSecurityProfileConfiguration(cfc),
                new StagingSecurityProfileConfiguration(cfc),
                new DevSecurityProfileConfiguration(cfc)
            };

            for (SecurityProfileConfiguration config : configs) {
                // Verify all 12 methods are callable (no exceptions thrown)
                assertDoesNotThrow(() -> config.isS3VersioningRemediationEnabled());
                assertDoesNotThrow(() -> config.isCloudTrailBucketAccessRemediationEnabled());
                assertDoesNotThrow(() -> config.isEbsEncryptionRemediationEnabled());
                assertDoesNotThrow(() -> config.isGuardDutyRemediationEnabled());
                assertDoesNotThrow(() -> config.isVpcDefaultSgRemediationEnabled());
                assertDoesNotThrow(() -> config.isElbDeletionProtectionRemediationEnabled());
                assertDoesNotThrow(() -> config.isKmsKeyRotationRemediationEnabled());
                assertDoesNotThrow(() -> config.isSshRemovalRemediationEnabled());
                assertDoesNotThrow(() -> config.isAccessKeyRotationRemediationEnabled());
                assertDoesNotThrow(() -> config.isDynamoDbPitrRemediationEnabled());
                assertDoesNotThrow(() -> config.isRdsMultiAzRemediationEnabled());
                assertDoesNotThrow(() -> config.isRdsEncryptionRemediationEnabled());
            }
        }

        @Test
        @DisplayName("High-risk remediations disabled across all profiles")
        void testHighRiskRemediationsDisabled() {
            SecurityProfileConfiguration[] configs = {
                new ProductionSecurityProfileConfiguration(cfc),
                new StagingSecurityProfileConfiguration(cfc),
                new DevSecurityProfileConfiguration(cfc)
            };

            for (SecurityProfileConfiguration config : configs) {
                // SSH removal is high-risk - could break access
                assertFalse(config.isSshRemovalRemediationEnabled(),
                    config.getSecurityProfile() + " should have SSH removal disabled");

                // Access key rotation is high-risk - requires user notification
                assertFalse(config.isAccessKeyRotationRemediationEnabled(),
                    config.getSecurityProfile() + " should have access key rotation disabled");

                // RDS Multi-AZ is high-risk - requires maintenance window
                assertFalse(config.isRdsMultiAzRemediationEnabled(),
                    config.getSecurityProfile() + " should have RDS Multi-AZ disabled");

                // RDS encryption is high-risk - complex operation
                assertFalse(config.isRdsEncryptionRemediationEnabled(),
                    config.getSecurityProfile() + " should have RDS encryption disabled");
            }
        }
    }

    @Nested
    @DisplayName("Security Profile Configuration Without Deployment Context")
    class WithoutDeploymentContextTests {

        @Test
        @DisplayName("Production config works without deployment context")
        void testProductionWithoutDeploymentContext() {
            ProductionSecurityProfileConfiguration config = new ProductionSecurityProfileConfiguration();

            assertNotNull(config);
            assertEquals(SecurityProfile.PRODUCTION, config.getSecurityProfile());
            // Should still have default remediation settings
            assertTrue(config.isEbsEncryptionRemediationEnabled());
        }

        @Test
        @DisplayName("Staging config works without deployment context")
        void testStagingWithoutDeploymentContext() {
            StagingSecurityProfileConfiguration config = new StagingSecurityProfileConfiguration();

            assertNotNull(config);
            assertEquals(SecurityProfile.STAGING, config.getSecurityProfile());
            // Should still have default remediation settings
            assertTrue(config.isGuardDutyRemediationEnabled());
        }

        @Test
        @DisplayName("Dev config works without deployment context")
        void testDevWithoutDeploymentContext() {
            DevSecurityProfileConfiguration config = new DevSecurityProfileConfiguration();

            assertNotNull(config);
            assertEquals(SecurityProfile.DEV, config.getSecurityProfile());
            // All remediations should be disabled
            assertFalse(config.isEbsEncryptionRemediationEnabled());
        }
    }

    // Helper method to count enabled remediations
    private int countEnabledRemediations(SecurityProfileConfiguration config) {
        int count = 0;
        if (config.isS3VersioningRemediationEnabled()) count++;
        if (config.isCloudTrailBucketAccessRemediationEnabled()) count++;
        if (config.isEbsEncryptionRemediationEnabled()) count++;
        if (config.isGuardDutyRemediationEnabled()) count++;
        if (config.isVpcDefaultSgRemediationEnabled()) count++;
        if (config.isElbDeletionProtectionRemediationEnabled()) count++;
        if (config.isKmsKeyRotationRemediationEnabled()) count++;
        if (config.isSshRemovalRemediationEnabled()) count++;
        if (config.isAccessKeyRotationRemediationEnabled()) count++;
        if (config.isDynamoDbPitrRemediationEnabled()) count++;
        if (config.isRdsMultiAzRemediationEnabled()) count++;
        if (config.isRdsEncryptionRemediationEnabled()) count++;
        return count;
    }
}
