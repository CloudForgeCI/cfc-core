package com.cloudforgeci.api.core.security;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the deployment-context override branch each of
 * {@code ebsEncryptionEnabled}/{@code efsEncryptionAtRestEnabled}/{@code s3EncryptionEnabled}/
 * {@code backupVaultLockEnabled}/{@code backupVaultRetentionEnabled}/
 * {@code rdsDeletionProtectionEnabled}/{@code rdsDatabaseMultiAzEnabled}/
 * {@code snsKmsEncryptionEnabled}/{@code imdsv2Required} now reads in
 * Production/Staging/DevSecurityProfileConfiguration: override set true, override set false, and
 * override left unset (falls through to the profile default). No complianceFrameworks is set in
 * any of these contexts, so ComplianceMatrix never short-circuits the override to its own
 * hardcoded value -- these tests are only meaningful without it.
 */
@DisplayName("Security Profile Configuration Override Tests")
class SecurityProfileConfigurationOverrideTest {

    private App app;
    private Stack stack;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "OverrideTest");
    }

    /** A {@code DeploymentContext} backed by a fresh stack with the given {@code cfc} context overrides. */
    private DeploymentContext cfcWith(Map<String, Object> overrides) {
        Stack s = new Stack(app, "OverrideTest" + java.util.UUID.randomUUID());
        s.getNode().setContext("cfc", new HashMap<>(overrides));
        return DeploymentContext.from(s);
    }

    /** A {@code DeploymentContext} backed by a fresh stack with no {@code cfc} context set. */
    private DeploymentContext cfcUnset() {
        Stack s = new Stack(app, "OverrideTest" + java.util.UUID.randomUUID());
        return DeploymentContext.from(s);
    }

    @Nested
    @DisplayName("Production overrides")
    class ProductionOverrides {

        @Test
        void ebsEncryptionEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("ebsEncryptionEnabled", true)))
                .isEbsEncryptionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("ebsEncryptionEnabled", false)))
                .isEbsEncryptionEnabled());
            assertTrue(new ProductionSecurityProfileConfiguration(cfcUnset()).isEbsEncryptionEnabled(),
                "default: mandatory encryption for production");
        }

        @Test
        void efsEncryptionAtRestEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("efsEncryptionAtRestEnabled", true)))
                .isEfsEncryptionAtRestEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("efsEncryptionAtRestEnabled", false)))
                .isEfsEncryptionAtRestEnabled());
            assertTrue(new ProductionSecurityProfileConfiguration(cfcUnset()).isEfsEncryptionAtRestEnabled());
        }

        @Test
        void s3EncryptionEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("s3EncryptionEnabled", true)))
                .isS3EncryptionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("s3EncryptionEnabled", false)))
                .isS3EncryptionEnabled());
            assertTrue(new ProductionSecurityProfileConfiguration(cfcUnset()).isS3EncryptionEnabled());
        }

        @Test
        void backupVaultLockEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", true)))
                .isBackupVaultLockEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", false)))
                .isBackupVaultLockEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcUnset()).isBackupVaultLockEnabled(),
                "default: not enabled in production without a required control");
        }

        @Test
        void backupVaultRetentionEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", true)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", false)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcUnset()).isBackupVaultRetentionEnabled());
        }

        @Test
        void rdsDeletionProtectionEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", true)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", false)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcUnset()).isRdsDeletionProtectionEnabled());
        }

        @Test
        void rdsDatabaseMultiAzEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", true)))
                .isRdsDatabaseMultiAzEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", false)))
                .isRdsDatabaseMultiAzEnabled());
            assertTrue(new ProductionSecurityProfileConfiguration(cfcUnset()).isRdsDatabaseMultiAzEnabled(),
                "default: enabled for production best practice");
        }

        @Test
        void snsKmsEncryptionEnabledHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", true)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", false)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcUnset()).isSnsKmsEncryptionEnabled());
        }

        @Test
        void imdsv2RequiredHonorsOverride() {
            assertTrue(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", true)))
                .isImdsv2Required());
            assertFalse(new ProductionSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", false)))
                .isImdsv2Required());
            assertTrue(new ProductionSecurityProfileConfiguration(cfcUnset()).isImdsv2Required(),
                "default: true, required for HIPAA compliance");
        }
    }

    @Nested
    @DisplayName("Staging overrides")
    class StagingOverrides {

        @Test
        void ebsEncryptionEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("ebsEncryptionEnabled", true)))
                .isEbsEncryptionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("ebsEncryptionEnabled", false)))
                .isEbsEncryptionEnabled());
            assertTrue(new StagingSecurityProfileConfiguration(cfcUnset()).isEbsEncryptionEnabled());
        }

        @Test
        void efsEncryptionAtRestEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("efsEncryptionAtRestEnabled", true)))
                .isEfsEncryptionAtRestEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("efsEncryptionAtRestEnabled", false)))
                .isEfsEncryptionAtRestEnabled());
            assertTrue(new StagingSecurityProfileConfiguration(cfcUnset()).isEfsEncryptionAtRestEnabled());
        }

        @Test
        void s3EncryptionEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("s3EncryptionEnabled", true)))
                .isS3EncryptionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("s3EncryptionEnabled", false)))
                .isS3EncryptionEnabled());
            assertTrue(new StagingSecurityProfileConfiguration(cfcUnset()).isS3EncryptionEnabled());
        }

        @Test
        void backupVaultLockEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", true)))
                .isBackupVaultLockEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", false)))
                .isBackupVaultLockEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcUnset()).isBackupVaultLockEnabled());
        }

        @Test
        void backupVaultRetentionEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", true)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", false)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcUnset()).isBackupVaultRetentionEnabled());
        }

        @Test
        void rdsDeletionProtectionEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", true)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", false)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcUnset()).isRdsDeletionProtectionEnabled());
        }

        @Test
        void rdsDatabaseMultiAzEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", true)))
                .isRdsDatabaseMultiAzEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", false)))
                .isRdsDatabaseMultiAzEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcUnset()).isRdsDatabaseMultiAzEnabled(),
                "default: staging uses single-AZ for cost savings");
        }

        @Test
        void snsKmsEncryptionEnabledHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", true)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", false)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new StagingSecurityProfileConfiguration(cfcUnset()).isSnsKmsEncryptionEnabled());
        }

        @Test
        void imdsv2RequiredHonorsOverride() {
            assertTrue(new StagingSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", true)))
                .isImdsv2Required());
            assertFalse(new StagingSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", false)))
                .isImdsv2Required());
            assertTrue(new StagingSecurityProfileConfiguration(cfcUnset()).isImdsv2Required(),
                "default: staging tests production security behavior");
        }
    }

    @Nested
    @DisplayName("Dev overrides")
    class DevOverrides {

        @Test
        void backupVaultLockEnabledHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", true)))
                .isBackupVaultLockEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("backupVaultLockEnabled", false)))
                .isBackupVaultLockEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isBackupVaultLockEnabled());
        }

        @Test
        void backupVaultRetentionEnabledHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", true)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("backupVaultRetentionEnabled", false)))
                .isBackupVaultRetentionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isBackupVaultRetentionEnabled());
        }

        @Test
        void rdsDeletionProtectionEnabledHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", true)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("rdsDeletionProtectionEnabled", false)))
                .isRdsDeletionProtectionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isRdsDeletionProtectionEnabled());
        }

        @Test
        void rdsDatabaseMultiAzEnabledHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", true)))
                .isRdsDatabaseMultiAzEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("rdsDatabaseMultiAzEnabled", false)))
                .isRdsDatabaseMultiAzEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isRdsDatabaseMultiAzEnabled());
        }

        @Test
        void snsKmsEncryptionEnabledHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", true)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("snsKmsEncryptionEnabled", false)))
                .isSnsKmsEncryptionEnabled());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isSnsKmsEncryptionEnabled());
        }

        @Test
        void imdsv2RequiredHonorsOverride() {
            assertTrue(new DevSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", true)))
                .isImdsv2Required());
            assertFalse(new DevSecurityProfileConfiguration(cfcWith(Map.of("imdsv2Required", false)))
                .isImdsv2Required());
            assertFalse(new DevSecurityProfileConfiguration(cfcUnset()).isImdsv2Required(),
                "default: IMDSv1 allowed for development convenience");
        }
    }
}
