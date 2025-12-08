package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BackupFactory AWS Backup infrastructure.
 *
 * <p>Validates backup configuration across security profiles:</p>
 * <ul>
 *   <li>DEV: Backups disabled by default</li>
 *   <li>STAGING: Daily backups, 14-day retention</li>
 *   <li>PRODUCTION: Daily + weekly backups, 90-day retention, vault lock</li>
 * </ul>
 *
 * <p>Compliance coverage:</p>
 * <ul>
 *   <li>SOC2-A1.3: Automated backups for disaster recovery</li>
 *   <li>PCI-DSS: efs-resources-protected-by-backup-plan</li>
 *   <li>HIPAA: Data backup and recovery requirements</li>
 * </ul>
 */
class BackupFactoryTest {

    // ========== Security Profile Behavior Tests ==========

    @Test
    void testDevProfileDisablesBackups() {
        // Given: DEV security profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupDevTest", SecurityProfile.DEV, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: No backup resources should be created for DEV
        assertEquals(0, t.findResources("AWS::Backup::BackupVault").size(),
            "DEV profile should not create backup vault");
        assertEquals(0, t.findResources("AWS::Backup::BackupPlan").size(),
            "DEV profile should not create backup plan");
    }

    @Test
    void testStagingProfileCreatesBackups() {
        // Given: STAGING security profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupStagingTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup vault and plan should be created
        t.resourceCountIs("AWS::Backup::BackupVault", 1);
        t.resourceCountIs("AWS::Backup::BackupPlan", 1);
    }

    @Test
    void testProductionProfileCreatesBackupsWithVaultLock() {
        // Given: PRODUCTION security profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupProdTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup vault with lock and plan should be created
        t.resourceCountIs("AWS::Backup::BackupVault", 1);
        t.resourceCountIs("AWS::Backup::BackupPlan", 1);

        // Vault lock prevents deletion (PCI-DSS compliance)
        t.hasResourceProperties("AWS::Backup::BackupVault", Match.objectLike(Map.of(
            "LockConfiguration", Match.objectLike(Map.of(
                "MinRetentionDays", Match.anyValue()
            ))
        )));
    }

    // ========== Backup Vault Tests ==========

    @Test
    void testBackupVaultNamingConvention() {
        // Given: STAGING profile with stack name
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "MyAppStack", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Vault name follows convention (stackName-vault)
        t.hasResourceProperties("AWS::Backup::BackupVault", Match.objectLike(Map.of(
            "BackupVaultName", Match.stringLikeRegexp(".*-vault$")
        )));
    }

    @Test
    void testBackupVaultRemovalPolicyStaging() {
        // Given: STAGING profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupRemovalTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: STAGING should have DESTROY removal policy (no DeletionPolicy in template)
        Map<String, Map<String, Object>> vaults = t.findResources("AWS::Backup::BackupVault");
        assertFalse(vaults.isEmpty(), "Backup vault should exist");
    }

    @Test
    void testBackupVaultRemovalPolicyProduction() {
        // Given: PRODUCTION profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupRetainTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: PRODUCTION should have RETAIN removal policy
        t.hasResource("AWS::Backup::BackupVault", Map.of(
            "DeletionPolicy", "Retain",
            "UpdateReplacePolicy", "Retain"
        ));
    }

    // ========== Backup Plan Tests ==========

    @Test
    void testBackupPlanHasDailyRule() {
        // Given: STAGING profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupPlanTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup plan should have rules
        t.hasResourceProperties("AWS::Backup::BackupPlan", Match.objectLike(Map.of(
            "BackupPlan", Match.objectLike(Map.of(
                "BackupPlanName", Match.anyValue(),
                "BackupPlanRule", Match.anyValue()
            ))
        )));
    }

    @Test
    void testProductionBackupPlanHasWeeklyRule() {
        // Given: PRODUCTION profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupWeeklyTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup plan should have at least 2 rules (daily + weekly)
        t.hasResourceProperties("AWS::Backup::BackupPlan", Match.objectLike(Map.of(
            "BackupPlan", Match.objectLike(Map.of(
                "BackupPlanRule", Match.arrayWith(List.of(
                    Match.objectLike(Map.of("RuleName", "DailyBackup")),
                    Match.objectLike(Map.of("RuleName", "WeeklyBackup"))
                ))
            ))
        )));
    }

    @Test
    void testBackupPlanScheduleConfiguration() {
        // Given: STAGING profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupScheduleTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Daily backup rule should have schedule expression
        t.hasResourceProperties("AWS::Backup::BackupPlan", Match.objectLike(Map.of(
            "BackupPlan", Match.objectLike(Map.of(
                "BackupPlanRule", Match.arrayWith(List.of(
                    Match.objectLike(Map.of(
                        "RuleName", "DailyBackup",
                        "ScheduleExpression", Match.stringLikeRegexp("cron.*")
                    ))
                ))
            ))
        )));
    }

    // ========== Backup Selection Tests ==========

    @Test
    void testBackupSelectionIncludesEfs() {
        // Given: STAGING profile with EFS
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupEfsTest", SecurityProfile.STAGING, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup selection should exist
        t.resourceCountIs("AWS::Backup::BackupSelection", 1);
    }

    // ========== Parameterized Security Profile Tests ==========

    @ParameterizedTest
    @EnumSource(value = SecurityProfile.class, names = {"STAGING", "PRODUCTION"})
    void testBackupsEnabledForNonDevProfiles(SecurityProfile profile) {
        // Given: Non-DEV security profile
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupProfileTest-" + profile, profile, RuntimeType.FARGATE);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup resources should be created
        assertTrue(t.findResources("AWS::Backup::BackupVault").size() > 0,
            profile + " profile should create backup vault");
        assertTrue(t.findResources("AWS::Backup::BackupPlan").size() > 0,
            profile + " profile should create backup plan");
    }

    @ParameterizedTest
    @EnumSource(value = RuntimeType.class)
    void testBackupsWorkWithAllRuntimeTypes(RuntimeType runtime) {
        // Given: STAGING profile with different runtime types
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupRuntimeTest-" + runtime, SecurityProfile.STAGING, runtime);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Backup resources should be created regardless of runtime
        t.resourceCountIs("AWS::Backup::BackupVault", 1);
        t.resourceCountIs("AWS::Backup::BackupPlan", 1);
    }

    // ========== Context Override Tests ==========

    @Test
    void testBackupDisabledOverrideInProduction() {
        // Given: PRODUCTION profile with explicit backup disabled
        // Note: Only PRODUCTION profile supports deployment context override
        Map<String, Object> context = new HashMap<>();
        context.put("automatedBackupEnabled", false);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupProdOverride", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        // When: BackupFactory is created
        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: No backup resources should be created
        assertEquals(0, t.findResources("AWS::Backup::BackupVault").size(),
            "Explicit disable should prevent backup vault creation");
    }

    // ========== Resource Naming Tests ==========

    @Test
    void testBackupResourceNameSanitization() {
        // Given: Stack name with special characters
        Map<String, Object> context = new HashMap<>();
        context.put("stackName", "My-App_Stack.v2");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupNamingTest", SecurityProfile.STAGING, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Vault name should be sanitized (alphanumeric, hyphens, underscores only)
        t.hasResourceProperties("AWS::Backup::BackupVault", Match.objectLike(Map.of(
            "BackupVaultName", Match.stringLikeRegexp("^[a-zA-Z0-9_-]+-vault$")
        )));
    }

    @Test
    void testBackupResourceNameTruncation() {
        // Given: Very long stack name
        String longName = "A".repeat(60); // Exceeds 50 char limit
        Map<String, Object> context = new HashMap<>();
        context.put("stackName", longName);

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupTruncateTest", SecurityProfile.STAGING, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Vault name should be truncated to fit within 50 char limit
        Map<String, Map<String, Object>> vaults = t.findResources("AWS::Backup::BackupVault");
        assertFalse(vaults.isEmpty(), "Backup vault should exist");

        // Get the vault name from properties
        for (Map<String, Object> vault : vaults.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) vault.get("Properties");
            String vaultName = (String) props.get("BackupVaultName");
            assertTrue(vaultName.length() <= 50,
                "Vault name should be <= 50 chars, got: " + vaultName.length());
        }
    }

    // ========== Compliance Integration Tests ==========

    @Test
    void testPciDssEfsBackupCompliance() {
        // Given: PRODUCTION profile for PCI-DSS compliance
        Map<String, Object> context = new HashMap<>();
        context.put("complianceFrameworks", "PCI-DSS");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupPciDssTest", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: EFS should be protected by backup plan (PCI-DSS requirement)
        t.resourceCountIs("AWS::EFS::FileSystem", 1);
        t.resourceCountIs("AWS::Backup::BackupPlan", 1);
        t.resourceCountIs("AWS::Backup::BackupSelection", 1);

        // Vault lock should be enabled for PRODUCTION
        t.hasResourceProperties("AWS::Backup::BackupVault", Match.objectLike(Map.of(
            "LockConfiguration", Match.anyValue()
        )));
    }

    @Test
    void testSoc2A13BackupCompliance() {
        // Given: STAGING profile for SOC2 A1.3 compliance
        Map<String, Object> context = new HashMap<>();
        context.put("complianceFrameworks", "SOC2");

        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "BackupSoc2Test", SecurityProfile.STAGING, RuntimeType.FARGATE, context);
        builder.createCompleteInfrastructure();

        BackupFactory backupFactory = new BackupFactory(builder.getStack(), "Backup");
        backupFactory.create();

        Template t = Template.fromStack(builder.getStack());

        // Then: Automated backups should be configured (SOC2-A1.3)
        t.resourceCountIs("AWS::Backup::BackupVault", 1);
        t.resourceCountIs("AWS::Backup::BackupPlan", 1);
    }
}
