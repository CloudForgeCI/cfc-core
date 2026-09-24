package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the SOC2 checks for matrix controls, driven through {@link Soc2Rules#validateMatrixControls}
 * with a profile whose boolean flags are set individually.
 */
class Soc2MatrixControlsTest {

    private static final Map<String, String> RULE_BY_FLAG = Map.ofEntries(
        Map.entry("isMfaRequired", "SOC2-CC6.1-MFA"),
        Map.entry("isCloudWatchLogsKmsEncryptionEnabled", "SOC2-CC6.1-LogEncryption"),
        Map.entry("isImdsv2Required", "SOC2-CC6.6-IMDSv2"),
        Map.entry("isCloudTrailInsightsEnabled", "SOC2-CC7.2-CloudTrailInsights"),
        Map.entry("isRoute53QueryLoggingEnabled", "SOC2-CC7.2-Route53QueryLogging"),
        Map.entry("isS3ObjectLockEnabled", "SOC2-CC7.2-AuditLogImmutability"),
        Map.entry("getLogRetentionDays", "SOC2-CC7.2-MatrixLogRetention"),
        Map.entry("isRdsDatabaseMultiAzEnabled", "SOC2-A1.2-DatabaseMultiAZ"),
        Map.entry("isRdsDeletionProtectionEnabled", "SOC2-A1.3-DatabaseDeletionProtection"),
        Map.entry("isAuditManagerEnabled", "SOC2-CC7.2-AuditManager"));

    /** A profile that answers true for every boolean flag except the ones named in {@code disabled};
     *  {@code getLogRetentionDays} returns a sufficient (1+ year) value unless named in {@code disabled}. */
    private static SecurityProfileConfiguration profile(Set<String> disabled) {
        return (SecurityProfileConfiguration) Proxy.newProxyInstance(
            SecurityProfileConfiguration.class.getClassLoader(),
            new Class<?>[] {SecurityProfileConfiguration.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getLogRetentionDays")) {
                    return disabled.contains("getLogRetentionDays")
                        ? RetentionDays.ONE_MONTH
                        : RetentionDays.ONE_YEAR;
                }
                return method.getReturnType() == boolean.class
                    ? !disabled.contains(method.getName())
                    : null;
            });
    }

    private static List<ComplianceRule> evaluate(Set<String> disabled, SecurityProfile security,
                                                 RuntimeType runtime, AuthMode auth, boolean database) {
        return new Soc2Rules().validateMatrixControls(profile(disabled), security, runtime, auth, database);
    }

    private static Set<String> failedIds(List<ComplianceRule> rules) {
        return rules.stream().filter(r -> !r.passed()).map(ComplianceRule::ruleId).collect(Collectors.toSet());
    }

    @Test
    void everyRequiredControlProducesAPassingRuleWhenEnabled() {
        List<ComplianceRule> rules = evaluate(Set.of(), SecurityProfile.PRODUCTION,
            RuntimeType.EC2, AuthMode.ALB_OIDC, true);

        assertEquals(Set.copyOf(RULE_BY_FLAG.values()),
            rules.stream().map(ComplianceRule::ruleId).collect(Collectors.toSet()));
        assertTrue(failedIds(rules).isEmpty());
    }

    @ParameterizedTest(name = "{0} disabled fails only {1}")
    @CsvSource({
        "isMfaRequired,SOC2-CC6.1-MFA",
        "isCloudWatchLogsKmsEncryptionEnabled,SOC2-CC6.1-LogEncryption",
        "isImdsv2Required,SOC2-CC6.6-IMDSv2",
        "isCloudTrailInsightsEnabled,SOC2-CC7.2-CloudTrailInsights",
        "isRoute53QueryLoggingEnabled,SOC2-CC7.2-Route53QueryLogging",
        "isS3ObjectLockEnabled,SOC2-CC7.2-AuditLogImmutability",
        "getLogRetentionDays,SOC2-CC7.2-MatrixLogRetention",
        "isRdsDatabaseMultiAzEnabled,SOC2-A1.2-DatabaseMultiAZ",
        "isRdsDeletionProtectionEnabled,SOC2-A1.3-DatabaseDeletionProtection",
        "isAuditManagerEnabled,SOC2-CC7.2-AuditManager"
    })
    void disablingAControlFailsExactlyItsRule(String flag, String ruleId) {
        List<ComplianceRule> rules = evaluate(Set.of(flag), SecurityProfile.PRODUCTION,
            RuntimeType.EC2, AuthMode.ALB_OIDC, true);

        assertEquals(Set.of(ruleId), failedIds(rules));
    }

    @Test
    void mfaIsNotCheckedWhenThereIsNoAuthentication() {
        List<ComplianceRule> rules = evaluate(Set.of("isMfaRequired"), SecurityProfile.PRODUCTION,
            RuntimeType.EC2, AuthMode.NONE, true);

        assertTrue(rules.stream().noneMatch(r -> r.ruleId().equals("SOC2-CC6.1-MFA")));
    }

    @Test
    void imdsv2IsNotCheckedOnFargate() {
        List<ComplianceRule> rules = evaluate(Set.of("isImdsv2Required"), SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, AuthMode.ALB_OIDC, true);

        assertTrue(rules.stream().noneMatch(r -> r.ruleId().equals("SOC2-CC6.6-IMDSv2")));
        assertTrue(failedIds(rules).isEmpty());
    }

    @Test
    void databaseRulesRequireAProvisionedDatabaseInProduction() {
        Set<String> dbFlagsOff = Set.of("isRdsDatabaseMultiAzEnabled", "isRdsDeletionProtectionEnabled");

        List<ComplianceRule> noDatabase = evaluate(dbFlagsOff, SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, AuthMode.ALB_OIDC, false);
        List<ComplianceRule> staging = evaluate(dbFlagsOff, SecurityProfile.STAGING,
            RuntimeType.FARGATE, AuthMode.ALB_OIDC, true);

        assertTrue(failedIds(noDatabase).isEmpty());
        assertTrue(failedIds(staging).isEmpty());
    }

    @Test
    void productionTierControlsAreNotCheckedInStaging() {
        Set<String> productionOnly = Set.of(
            "isCloudWatchLogsKmsEncryptionEnabled", "isCloudTrailInsightsEnabled",
            "isRoute53QueryLoggingEnabled", "isS3ObjectLockEnabled",
            "getLogRetentionDays", "isAuditManagerEnabled");

        List<ComplianceRule> rules = evaluate(productionOnly, SecurityProfile.STAGING,
            RuntimeType.EC2, AuthMode.ALB_OIDC, false);

        assertTrue(failedIds(rules).isEmpty());
        assertEquals(Set.of("SOC2-CC6.1-MFA", "SOC2-CC6.6-IMDSv2"),
            rules.stream().map(ComplianceRule::ruleId).collect(Collectors.toSet()));
    }

    @Test
    void everyRuleIdMapsToAControlTheMatrixRequiresForSoc2() {
        List<ComplianceRule> rules = evaluate(Set.of(), SecurityProfile.PRODUCTION,
            RuntimeType.EC2, AuthMode.ALB_OIDC, true);

        // The rule set is derived from the matrix: it must cover exactly the controls it lists.
        assertEquals(RULE_BY_FLAG.size(), rules.size());
    }

    @Test
    void autoScalingAppliesOnlyToApplicationsThatCanScaleBeyondOneInstance() {
        assertTrue(Soc2Rules.autoScalingApplies(true, 3));
        assertTrue(Soc2Rules.autoScalingApplies(true, null));
        assertFalse(Soc2Rules.autoScalingApplies(true, 1));
        assertFalse(Soc2Rules.autoScalingApplies(false, 3));
        assertFalse(Soc2Rules.autoScalingApplies(false, null));
    }
}
