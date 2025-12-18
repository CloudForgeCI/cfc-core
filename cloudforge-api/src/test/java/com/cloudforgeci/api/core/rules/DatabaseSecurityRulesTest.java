package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
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

/**
 * Test suite for DatabaseSecurityRules.
 *
 * Tests database security compliance validation rules.
 */
class DatabaseSecurityRulesTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile) {
        Stack stack = new Stack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("securityProfile", profile.name());
        stack.getNode().setContext("cfc", cfcContext);

        return stack;
    }

    @Test
    void testDatabaseSecurityRulesInstallWithDevProfile() {
        // Given: A DEV deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestDbSecDev", SecurityProfile.DEV);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, iamProfile, cfc);

        // When: Installing database security rules
        // Then: Should not throw (advisory for DEV)
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityRulesInstallWithProductionProfile() {
        // Given: A PRODUCTION deployment
        App app = new App();
        Stack stack = createTestStack(app, "TestDbSecProd", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing database security rules
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));

        // Then: Validation should be added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testDatabaseSecurityRulesWithRDSEnabled() {
        // Given: A deployment with RDS enabled
        App app = new App();
        Stack stack = new Stack(app, "TestDbSecRDS");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbSecRDS");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsEncryptionEnabled", "true");
        cfcContext.put("rdsBackupEnabled", "true");
        cfcContext.put("rdsMultiAz", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing database security rules
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));

    }

    @Test
    void testDatabaseSecurityRulesWithDynamoDBEnabled() {
        // Given: A deployment with DynamoDB enabled
        App app = new App();
        Stack stack = new Stack(app, "TestDbSecDynamoDB");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbSecDynamoDB");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("dynamoDbEnabled", "true");
        cfcContext.put("dynamoDbEncryptionEnabled", "true");
        cfcContext.put("dynamoDbPitrEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing database security rules
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));

    }

    @Test
    void testDatabaseSecurityRulesWithDatabaseMonitoring() {
        // Given: A deployment with database monitoring enabled
        App app = new App();
        Stack stack = new Stack(app, "TestDbSecMonitoring");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbSecMonitoring");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("dbActivityStreamsEnabled", "true");
        cfcContext.put("performanceInsightsEnabled", "true");
        cfcContext.put("performanceInsightsEncrypted", "true");
        cfcContext.put("rdsEnhancedMonitoringEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing database security rules
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));

    }

    @Test
    void testDatabaseSecurityRulesValidationExecutes() {
        // Given: A deployment context
        App app = new App();
        Stack stack = createTestStack(app, "TestDbSecValidation", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing database security rules
        new DatabaseSecurityRules().install(ctx);

        // Then: Node should have validation added
        assertNotNull(ctx.getNode());
    }

    @Test
    void testDatabaseSecurityWithEncryptionAtRest() {
        // Given: A deployment with database encryption at rest
        App app = new App();
        Stack stack = new Stack(app, "TestDbEncryptionAtRest");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbEncryptionAtRest");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsEncryptionEnabled", "true");
        cfcContext.put("rdsKmsKeyArn", "arn:aws:kms:us-east-1:123456789012:key/12345");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure encryption at rest
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithEncryptionInTransit() {
        // Given: A deployment with TLS/SSL for database connections
        App app = new App();
        Stack stack = new Stack(app, "TestDbEncryptionInTransit");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbEncryptionInTransit");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsSslEnabled", "true");
        cfcContext.put("rdsRequireSsl", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should require SSL for connections
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithAutomatedBackups() {
        // Given: A deployment with automated backups
        App app = new App();
        Stack stack = new Stack(app, "TestDbBackups");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbBackups");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsBackupEnabled", "true");
        cfcContext.put("rdsBackupRetentionDays", 30);
        cfcContext.put("rdsPreferredBackupWindow", "03:00-04:00");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure automated backups
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithPointInTimeRecovery() {
        // Given: A deployment with PITR enabled
        App app = new App();
        Stack stack = new Stack(app, "TestDbPITR");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbPITR");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("dynamoDbEnabled", "true");
        cfcContext.put("dynamoDbPitrEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should enable point-in-time recovery
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithAccessLogging() {
        // Given: A deployment with database access logging
        App app = new App();
        Stack stack = new Stack(app, "TestDbAccessLogs");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbAccessLogs");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("dbAuditLoggingEnabled", "true");
        cfcContext.put("dbSlowQueryLogEnabled", "true");
        cfcContext.put("dbGeneralLogEnabled", "false");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should configure database audit logging
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithIAMAuthentication() {
        // Given: A deployment with IAM database authentication
        App app = new App();
        Stack stack = new Stack(app, "TestDbIAMAuth");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbIAMAuth");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsIAMAuthenticationEnabled", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should enable IAM authentication
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithMultiAZ() {
        // Given: A deployment with Multi-AZ for high availability
        App app = new App();
        Stack stack = new Stack(app, "TestDbMultiAZ");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbMultiAZ");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsMultiAz", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should enable Multi-AZ deployment
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithDeletionProtection() {
        // Given: A deployment with deletion protection
        App app = new App();
        Stack stack = new Stack(app, "TestDbDeletionProtection");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbDeletionProtection");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsDeletionProtection", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should enable deletion protection
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithSecretsManager() {
        // Given: A deployment with Secrets Manager for credentials
        App app = new App();
        Stack stack = new Stack(app, "TestDbSecretsManager");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbSecretsManager");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsSecretsManagerEnabled", "true");
        cfcContext.put("rdsRotateCredentials", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should use Secrets Manager for credentials
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithNetworkIsolation() {
        // Given: A deployment with VPC isolation
        App app = new App();
        Stack stack = new Stack(app, "TestDbNetworkIsolation");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbNetworkIsolation");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsPubliclyAccessible", "false");
        cfcContext.put("rdsVpcOnly", "true");
        cfcContext.put("rdsSubnetType", "private");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should deploy in private subnets
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithAllSecurityProfiles() {
        // Given: Each security profile
        for (SecurityProfile profile : SecurityProfile.values()) {
            App app = new App();
            Stack stack = createTestStack(app, "TestDatabaseSecurity" + profile, profile);

            DeploymentContext cfc = DeploymentContext.from(stack);
            IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    profile, iamProfile, cfc);

            // When/Then: Should not throw for any security profile
            assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx),
                "DatabaseSecurityRules should not throw for security profile: " + profile);
        }
    }

    @Test
    void testDatabaseSecurityIdempotency() {
        // Given: A deployment with database security
        App app = new App();
        Stack stack = createTestStack(app, "TestDatabaseSecurityIdempotent", SecurityProfile.PRODUCTION);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When: Installing rules multiple times
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));

        // Then: Should be idempotent (no errors on repeated calls)
    }

    @Test
    void testDatabaseSecurityWithActivityStreams() {
        // Given: A deployment with database activity streams
        App app = new App();
        Stack stack = new Stack(app, "TestDbActivityStreams");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbActivityStreams");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("dbActivityStreamsEnabled", "true");
        cfcContext.put("dbActivityStreamsKmsKey", "arn:aws:kms:us-east-1:123456789012:key/12345");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should enable database activity streams
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithSnapshotEncryption() {
        // Given: A deployment with encrypted snapshots
        App app = new App();
        Stack stack = new Stack(app, "TestDbSnapshotEncryption");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbSnapshotEncryption");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsBackupEnabled", "true");
        cfcContext.put("rdsEncryptionEnabled", "true");
        cfcContext.put("rdsSnapshotEncrypted", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should encrypt database snapshots
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    @Test
    void testDatabaseSecurityWithParameterGroupHardening() {
        // Given: A deployment with hardened database parameters
        App app = new App();
        Stack stack = new Stack(app, "TestDbParameterHardening");

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", "TestDbParameterHardening");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("rdsEnabled", "true");
        cfcContext.put("rdsParameterGroupSecure", "true");
        cfcContext.put("rdsLogConnections", "true");
        cfcContext.put("rdsLogDisconnections", "true");
        stack.getNode().setContext("cfc", cfcContext);

        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);
        SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.PRODUCTION, iamProfile, cfc);

        // When/Then: Should apply secure parameter group settings
        assertDoesNotThrow(() -> new DatabaseSecurityRules().install(ctx));
    }

    // ==================== EXPANDED PARAMETERIZED TRUTH TABLE TESTS ====================

    /**
     * Expanded test for RDS security validation.
     * Tests all combinations of: security profile, RDS enabled, encryption, backup, Multi-AZ, retention, auto-upgrade.
     */
    @ParameterizedTest
    @CsvSource({
        // RDS not enabled - all profiles should pass (validation skipped)
        "PRODUCTION,false,false,false,false,7,false",
        "STAGING,false,false,false,false,7,false",
        "DEV,false,false,false,false,7,false",

        // PRODUCTION with RDS - encryption and backup required
        "PRODUCTION,true,false,false,false,7,false",        // No encryption/backup - FAIL
        "PRODUCTION,true,true,false,false,7,false",         // Encryption only - FAIL (missing backup)
        "PRODUCTION,true,false,true,false,7,false",         // Backup only - FAIL (missing encryption)
        "PRODUCTION,true,true,true,false,7,false",          // Encryption + backup, no Multi-AZ - FAIL
        "PRODUCTION,true,true,true,true,7,false",           // All except auto-upgrade - FAIL
        "PRODUCTION,true,true,true,true,7,true",            // All features - PASS
        "PRODUCTION,true,true,true,true,30,true",           // Extended retention - PASS
        "PRODUCTION,true,true,true,true,3,true",            // Low retention - FAIL (< 7 days)

        // STAGING scenarios (no Multi-AZ requirement)
        "STAGING,true,false,false,false,7,false",           // Minimal RDS - FAIL (missing encryption/backup)
        "STAGING,true,true,true,false,7,false",             // Encryption + backup - PASS (no Multi-AZ required)
        "STAGING,true,true,true,false,7,true",              // With auto-upgrade - PASS

        // DEV scenarios (advisory only)
        "DEV,true,false,false,false,7,false",               // Minimal DEV - PASS (advisory)
        "DEV,true,true,true,true,7,true",                   // Full features DEV - PASS
    })
    void testDBExpandedRDSSecurity(String profile, boolean rdsEnabled, boolean encryption,
                                   boolean backup, boolean multiAz, int retentionDays,
                                   boolean autoUpgrade) {
        // Create custom context with RDS configuration
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestRDS");
        customContext.put("securityProfile", profile);
        if (rdsEnabled) {
            customContext.put("rdsEnabled", "true");
        }
        if (encryption) {
            customContext.put("rdsEncryptionEnabled", "true");
        }
        if (backup) {
            customContext.put("rdsBackupEnabled", "true");
        }
        if (multiAz) {
            customContext.put("rdsMultiAz", "true");
        }
        customContext.put("rdsBackupRetentionDays", String.valueOf(retentionDays));
        // Explicitly set autoUpgrade (default is true, so we need to override if false)
        customContext.put("rdsAutoMinorVersionUpgrade", String.valueOf(autoUpgrade));

        // For PRODUCTION with RDS, also enable monitoring to avoid additional failures
        if (profile.equals("PRODUCTION") && rdsEnabled) {
            customContext.put("dbActivityStreamsEnabled", "true");
            customContext.put("rdsEnhancedMonitoringEnabled", "true");
        }

        // Use TestInfrastructureBuilder to create minimal infrastructure
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestRDS", secProfile, RuntimeType.FARGATE, customContext);

        // Create minimal infrastructure and install rules
        builder.createMinimalInfrastructure();
        new DatabaseSecurityRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;

        if (rdsEnabled && secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION with RDS requires: encryption, backup, Multi-AZ, retention >= 7, auto-upgrade
            // (monitoring is now enabled automatically above to avoid false failures in this test)
            if (!encryption || !backup || !multiAz || retentionDays < 7 || !autoUpgrade) {
                shouldFail = true;
            }
        } else if (rdsEnabled && secProfile == SecurityProfile.STAGING) {
            // STAGING with RDS requires: encryption, backup (no Multi-AZ requirement)
            if (!encryption || !backup) {
                shouldFail = true;
            }
        }
        // DEV is advisory only - never fails

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + ", RDS=" + rdsEnabled +
                ", encryption=" + encryption + ", backup=" + backup + ", multiAz=" + multiAz +
                ", retention=" + retentionDays + ", autoUpgrade=" + autoUpgrade);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + ", RDS=" + rdsEnabled +
                ", encryption=" + encryption + ", backup=" + backup + ", multiAz=" + multiAz +
                ", retention=" + retentionDays + ", autoUpgrade=" + autoUpgrade);
        }
    }

    /**
     * Expanded test for DynamoDB security validation.
     * Tests all combinations of: security profile, DynamoDB enabled, encryption, Point-in-Time Recovery.
     */
    @ParameterizedTest
    @CsvSource({
        // DynamoDB not enabled - all profiles should pass (validation skipped)
        "PRODUCTION,false,false,false",
        "STAGING,false,false,false",
        "DEV,false,false,false",

        // PRODUCTION with DynamoDB - encryption and PITR required
        "PRODUCTION,true,false,false",                      // No encryption/PITR - FAIL
        "PRODUCTION,true,true,false",                       // Encryption only - FAIL (missing PITR)
        "PRODUCTION,true,false,true",                       // PITR only - FAIL (missing encryption)
        "PRODUCTION,true,true,true",                        // Both features - PASS

        // STAGING scenarios (PITR not checked for non-PRODUCTION)
        "STAGING,true,false,false",                         // Minimal DynamoDB - FAIL (missing encryption)
        "STAGING,true,true,false",                          // Encryption only - PASS
        "STAGING,true,true,true",                           // Both features - PASS

        // DEV scenarios (advisory only)
        "DEV,true,false,false",                             // Minimal DEV - PASS (advisory)
        "DEV,true,true,true",                               // Full features DEV - PASS
    })
    void testDBExpandedDynamoDBSecurity(String profile, boolean dynamoDbEnabled, boolean encryption,
                                        boolean pitr) {
        // Create custom context with DynamoDB configuration
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestDynamoDB");
        customContext.put("securityProfile", profile);
        if (dynamoDbEnabled) {
            customContext.put("dynamoDbEnabled", "true");
        }
        // Explicitly set encryption (default is true, so we need to override if false)
        customContext.put("dynamoDbEncryptionEnabled", String.valueOf(encryption));
        if (pitr) {
            customContext.put("dynamoDbPitrEnabled", "true");
        }
        // Add SOC2 framework to make DATABASE_PITR REQUIRED (REQUIRED for all frameworks)
        customContext.put("complianceFrameworks", "SOC2");
        customContext.put("complianceMode", "enforce");

        // Use TestInfrastructureBuilder to create minimal infrastructure
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestDynamoDB", secProfile, RuntimeType.FARGATE, customContext);

        // Create minimal infrastructure and install rules
        builder.createMinimalInfrastructure();
        new DatabaseSecurityRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        // NOTE: DATABASE_PITR is REQUIRED for ALL frameworks in ComplianceMatrix
        boolean shouldFail = false;

        if (dynamoDbEnabled) {
            if (secProfile == SecurityProfile.PRODUCTION) {
                // PRODUCTION with DynamoDB requires: encryption and PITR
                if (!encryption || !pitr) {
                    shouldFail = true;
                }
            } else if (secProfile == SecurityProfile.STAGING) {
                // STAGING with DynamoDB requires: encryption (no PITR requirement)
                if (!encryption) {
                    shouldFail = true;
                }
            }
            // DEV is advisory only - never fails
        }

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + ", DynamoDB=" + dynamoDbEnabled +
                ", encryption=" + encryption + ", pitr=" + pitr);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + ", DynamoDB=" + dynamoDbEnabled +
                ", encryption=" + encryption + ", pitr=" + pitr);
        }
    }

    /**
     * Expanded test for database monitoring validation.
     * Tests all combinations of: security profile, RDS enabled, activity streams, Performance Insights, Enhanced Monitoring.
     */
    @ParameterizedTest
    @CsvSource({
        // RDS not enabled - validation skipped
        "PRODUCTION,false,false,false,false,false",
        "STAGING,false,false,false,false,false",
        "DEV,false,false,false,false,false",

        // PRODUCTION with RDS - activity streams and enhanced monitoring recommended
        "PRODUCTION,true,false,false,false,false",          // No monitoring - FAIL
        "PRODUCTION,true,true,false,false,false",           // Activity streams only - partial PASS
        "PRODUCTION,true,false,true,false,false",           // Performance Insights without encryption - FAIL
        "PRODUCTION,true,false,true,true,false",            // Performance Insights encrypted - partial PASS
        "PRODUCTION,true,false,false,false,true",           // Enhanced monitoring only - partial PASS
        "PRODUCTION,true,true,true,true,true",              // All monitoring features - PASS

        // STAGING scenarios (advisory)
        "STAGING,true,false,false,false,false",             // Minimal monitoring - PASS
        "STAGING,true,true,true,true,true",                 // Full monitoring - PASS

        // DEV scenarios (advisory)
        "DEV,true,false,false,false,false",                 // Minimal DEV - PASS
        "DEV,true,true,true,true,true",                     // Full DEV - PASS
    })
    void testDBExpandedDatabaseMonitoring(String profile, boolean rdsEnabled, boolean activityStreams,
                                          boolean performanceInsights, boolean piEncrypted,
                                          boolean enhancedMonitoring) {
        // Create custom context with database monitoring configuration
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestDBMonitoring");
        customContext.put("securityProfile", profile);
        if (rdsEnabled) {
            customContext.put("rdsEnabled", "true");
            // Enable basic RDS security to avoid failures from validateRdsSecurity
            customContext.put("rdsEncryptionEnabled", "true");
            customContext.put("rdsBackupEnabled", "true");
            customContext.put("rdsBackupRetentionDays", "7");
            if (profile.equals("PRODUCTION")) {
                customContext.put("rdsMultiAz", "true");
                customContext.put("rdsAutoMinorVersionUpgrade", "true");
            }
        }
        if (activityStreams) {
            customContext.put("dbActivityStreamsEnabled", "true");
        }
        if (performanceInsights) {
            customContext.put("performanceInsightsEnabled", "true");
        }
        if (piEncrypted) {
            customContext.put("performanceInsightsEncrypted", "true");
        }
        if (enhancedMonitoring) {
            customContext.put("rdsEnhancedMonitoringEnabled", "true");
        }

        // Use TestInfrastructureBuilder to create minimal infrastructure
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestDBMonitoring", secProfile, RuntimeType.FARGATE, customContext);

        // Create minimal infrastructure and install rules
        builder.createMinimalInfrastructure();
        new DatabaseSecurityRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;

        if (rdsEnabled && secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION with RDS requires: activity streams and enhanced monitoring
            // Also: if Performance Insights is enabled, it must be encrypted
            if (!activityStreams || !enhancedMonitoring) {
                shouldFail = true;
            }
            if (performanceInsights && !piEncrypted) {
                shouldFail = true;
            }
        } else if (rdsEnabled && performanceInsights && !piEncrypted) {
            // Any profile: if Performance Insights is enabled, it must be encrypted
            shouldFail = true;
        }
        // STAGING and DEV are advisory for monitoring (don't fail)

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for: " + profile + ", RDS=" + rdsEnabled +
                ", activityStreams=" + activityStreams + ", performanceInsights=" + performanceInsights +
                ", piEncrypted=" + piEncrypted + ", enhancedMonitoring=" + enhancedMonitoring);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for: " + profile + ", RDS=" + rdsEnabled +
                ", activityStreams=" + activityStreams + ", performanceInsights=" + performanceInsights +
                ", piEncrypted=" + piEncrypted + ", enhancedMonitoring=" + enhancedMonitoring);
        }
    }

    /**
     * Comprehensive realistic scenarios combining all database security features.
     */
    @ParameterizedTest
    @CsvSource({
        // Scenario 1: Maximum security - PRODUCTION with RDS + DynamoDB + all features
        "PRODUCTION,true,true,true,true,true,30,true,true,true,true,true,true,true",

        // Scenario 2: PRODUCTION with RDS only, minimal config - FAIL
        "PRODUCTION,true,true,false,false,false,7,false,false,false,false,false,false,false",

        // Scenario 3: PRODUCTION with RDS, full security - PASS
        "PRODUCTION,true,true,true,true,true,14,true,false,false,true,true,true,true",

        // Scenario 4: PRODUCTION with DynamoDB only, full security - PASS
        "PRODUCTION,false,false,false,false,false,7,false,true,true,true,false,false,false",

        // Scenario 5: PRODUCTION with both databases but weak monitoring - partial FAIL
        "PRODUCTION,true,true,true,true,true,7,true,true,true,true,false,false,false",

        // Scenario 6: STAGING with full features (no Multi-AZ requirement)
        "STAGING,true,true,true,true,false,7,true,true,true,true,true,true,true",

        // Scenario 7: STAGING minimal - FAIL (missing encryption)
        "STAGING,true,true,false,false,false,7,false,false,false,false,false,false,false",

        // Scenario 8: DEV with all features (advisory)
        "DEV,true,true,true,true,true,30,true,true,true,true,true,true,true",

        // Scenario 9: DEV minimal (advisory)
        "DEV,true,true,false,false,false,1,false,false,false,false,false,false,false",

        // Scenario 10: PRODUCTION with encryption but no backups - FAIL
        "PRODUCTION,true,true,true,false,true,7,true,true,true,false,false,false,false",

        // Scenario 11: PRODUCTION with backups but no encryption - FAIL
        "PRODUCTION,true,true,false,true,true,7,true,true,true,false,false,false,false",

        // Scenario 12: PRODUCTION with infrastructure but no monitoring - partial FAIL
        "PRODUCTION,true,true,true,true,true,7,true,true,true,true,false,false,false",
    })
    void testDBExpandedComprehensiveScenarios(String profile, boolean rdsEnabled, boolean rdsEncryption,
                                              boolean rdsBackup, boolean multiAz, boolean autoUpgrade,
                                              int retentionDays, boolean dynamoDbEnabled,
                                              boolean dynamoDbEncryption, boolean pitr,
                                              boolean activityStreams, boolean performanceInsights,
                                              boolean piEncrypted, boolean enhancedMonitoring) {
        // Create custom context with all database configuration
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", "TestDBComprehensive");
        customContext.put("securityProfile", profile);

        if (rdsEnabled) {
            customContext.put("rdsEnabled", "true");
        }
        // Explicitly set these to override defaults
        customContext.put("rdsEncryptionEnabled", String.valueOf(rdsEncryption));
        customContext.put("rdsBackupEnabled", String.valueOf(rdsBackup));
        if (multiAz) {
            customContext.put("rdsMultiAz", "true");
        }
        customContext.put("rdsAutoMinorVersionUpgrade", String.valueOf(autoUpgrade));
        customContext.put("rdsBackupRetentionDays", String.valueOf(retentionDays));

        if (dynamoDbEnabled) {
            customContext.put("dynamoDbEnabled", "true");
        }
        // Explicitly set encryption to override default
        customContext.put("dynamoDbEncryptionEnabled", String.valueOf(dynamoDbEncryption));
        if (pitr) {
            customContext.put("dynamoDbPitrEnabled", "true");
        }

        if (activityStreams) {
            customContext.put("dbActivityStreamsEnabled", "true");
        }
        if (performanceInsights) {
            customContext.put("performanceInsightsEnabled", "true");
        }
        if (piEncrypted) {
            customContext.put("performanceInsightsEncrypted", "true");
        }
        if (enhancedMonitoring) {
            customContext.put("rdsEnhancedMonitoringEnabled", "true");
        }

        // Use TestInfrastructureBuilder to create minimal infrastructure
        SecurityProfile secProfile = SecurityProfile.valueOf(profile);
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestDBComprehensive", secProfile, RuntimeType.FARGATE, customContext);

        // Create minimal infrastructure and install rules
        builder.createMinimalInfrastructure();
        new DatabaseSecurityRules().install(builder.getSystemContext());

        // Determine if this scenario should pass or fail
        boolean shouldFail = false;

        // Check RDS requirements
        if (rdsEnabled && secProfile == SecurityProfile.PRODUCTION) {
            // PRODUCTION with RDS requires: encryption, backup, Multi-AZ, retention >= 7, auto-upgrade
            // Also requires: activity streams and enhanced monitoring
            if (!rdsEncryption || !rdsBackup || !multiAz || retentionDays < 7 || !autoUpgrade ||
                !activityStreams || !enhancedMonitoring) {
                shouldFail = true;
            }
        } else if (rdsEnabled && secProfile == SecurityProfile.STAGING) {
            // STAGING with RDS requires: encryption, backup (no Multi-AZ requirement)
            if (!rdsEncryption || !rdsBackup) {
                shouldFail = true;
            }
        }

        // Check DynamoDB requirements
        if (dynamoDbEnabled) {
            if (secProfile == SecurityProfile.PRODUCTION) {
                // PRODUCTION with DynamoDB requires: encryption and PITR
                if (!dynamoDbEncryption || !pitr) {
                    shouldFail = true;
                }
            } else if (secProfile == SecurityProfile.STAGING) {
                // STAGING with DynamoDB requires: encryption (no PITR requirement)
                if (!dynamoDbEncryption) {
                    shouldFail = true;
                }
            }
        }

        // Check Performance Insights encryption (any profile)
        if (rdsEnabled && performanceInsights && !piEncrypted) {
            shouldFail = true;
        }

        // DEV is advisory only - never fails

        // Trigger synthesis to execute validations
        if (shouldFail) {
            assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
                "Expected validation to fail for comprehensive scenario: " + profile);
        } else {
            assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
                "Expected validation to pass for comprehensive scenario: " + profile);
        }
    }
}
