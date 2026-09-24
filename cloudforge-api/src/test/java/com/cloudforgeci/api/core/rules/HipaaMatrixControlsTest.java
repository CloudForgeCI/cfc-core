package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the HIPAA checks for matrix controls, driven through {@link HipaaRules#validateMatrixControls}
 *  with a profile whose boolean flags are set individually -- see {@code Soc2MatrixControlsTest} for the
 *  same pattern applied to SOC2. */
class HipaaMatrixControlsTest {

    private static final Map<String, String> RULE_BY_FLAG = Map.of(
        "isEbsEncryptionEnabled", "HIPAA-164.312(a)(2)(iv)-EncryptionAtRest",
        "isCloudWatchLogsKmsEncryptionEnabled", "HIPAA-164.312(a)(2)(iv)-LogEncryption",
        "isS3ObjectLockEnabled", "HIPAA-164.312(c)(1)-AuditLogImmutability",
        "isImdsv2Required", "HIPAA-164.312(a)(1)-IMDSv2",
        "isRdsDatabaseMultiAzEnabled", "HIPAA-164.308(a)(7)(ii)(B)-DatabaseMultiAZ",
        "isRdsDeletionProtectionEnabled", "HIPAA-164.310(d)(2)(iii)-DatabaseDeletionProtection",
        "isMultiAzEnforced", "HIPAA-164.308(a)(7)(ii)(B)-HighAvailability",
        "isCloudTrailEnabled", "HIPAA-164.308(a)(8)-ChangeManagement");

    /** A profile that answers true for every boolean flag except the ones named in {@code disabled}.
     *  {@code isEfsEncryptionAtRestEnabled}/{@code isS3EncryptionEnabled} track {@code
     *  isEbsEncryptionEnabled} so the single EncryptionAtRest rule can be tested as one control.
     *  {@code isAwsConfigEnabled} tracks {@code isCloudTrailEnabled} so the single
     *  ChangeManagement rule (which requires both) can be tested as one control. */
    private static SecurityProfileConfiguration profile(Set<String> disabled) {
        return (SecurityProfileConfiguration) Proxy.newProxyInstance(
            SecurityProfileConfiguration.class.getClassLoader(),
            new Class<?>[] {SecurityProfileConfiguration.class},
            (proxy, method, args) -> {
                if (method.getReturnType() != boolean.class) {
                    return null;
                }
                if (method.getName().equals("isEfsEncryptionAtRestEnabled")
                        || method.getName().equals("isS3EncryptionEnabled")) {
                    return !disabled.contains("isEbsEncryptionEnabled");
                }
                if (method.getName().equals("isAutoScalingEnabled")) {
                    return !disabled.contains("isMultiAzEnforced");
                }
                if (method.getName().equals("isAwsConfigEnabled")) {
                    return !disabled.contains("isCloudTrailEnabled");
                }
                return !disabled.contains(method.getName());
            });
    }

    /** Runs {@code validateMatrixControls} against a profile with the given controls disabled. */
    private static List<ComplianceRule> evaluate(Set<String> disabled, SecurityProfile security,
                                                 RuntimeType runtime, boolean database) {
        return new HipaaRules().validateMatrixControls(profile(disabled), security, runtime, database, 3, true);
    }

    /** @return the rule IDs of every failed rule in {@code rules}. */
    private static Set<String> failedIds(List<ComplianceRule> rules) {
        return rules.stream().filter(r -> !r.passed()).map(ComplianceRule::ruleId).collect(Collectors.toSet());
    }

    @Test
    void everyRequiredControlProducesAPassingRuleWhenEnabled() {
        List<ComplianceRule> rules = evaluate(Set.of(), SecurityProfile.PRODUCTION, RuntimeType.EC2, true);

        assertEquals(Set.copyOf(RULE_BY_FLAG.values()),
            rules.stream().map(ComplianceRule::ruleId).collect(Collectors.toSet()));
        assertTrue(failedIds(rules).isEmpty());
    }

    @ParameterizedTest(name = "{0} disabled fails only {1}")
    @CsvSource({
        "isEbsEncryptionEnabled,HIPAA-164.312(a)(2)(iv)-EncryptionAtRest",
        "isCloudWatchLogsKmsEncryptionEnabled,HIPAA-164.312(a)(2)(iv)-LogEncryption",
        "isS3ObjectLockEnabled,HIPAA-164.312(c)(1)-AuditLogImmutability",
        "isImdsv2Required,HIPAA-164.312(a)(1)-IMDSv2",
        "isRdsDatabaseMultiAzEnabled,HIPAA-164.308(a)(7)(ii)(B)-DatabaseMultiAZ",
        "isRdsDeletionProtectionEnabled,HIPAA-164.310(d)(2)(iii)-DatabaseDeletionProtection",
        "isMultiAzEnforced,HIPAA-164.308(a)(7)(ii)(B)-HighAvailability",
        "isCloudTrailEnabled,HIPAA-164.308(a)(8)-ChangeManagement"
    })
    void disablingAControlFailsExactlyItsRule(String flag, String ruleId) {
        List<ComplianceRule> rules = evaluate(Set.of(flag), SecurityProfile.PRODUCTION, RuntimeType.EC2, true);

        assertEquals(Set.of(ruleId), failedIds(rules));
    }

    @Test
    void imdsv2IsNotCheckedOnFargate() {
        List<ComplianceRule> rules =
            evaluate(Set.of("isImdsv2Required"), SecurityProfile.PRODUCTION, RuntimeType.FARGATE, true);

        assertTrue(rules.stream().noneMatch(r -> r.ruleId().equals("HIPAA-164.312(a)(1)-IMDSv2")));
        assertTrue(failedIds(rules).isEmpty());
    }

    @Test
    void databaseRulesRequireAProvisionedDatabaseInProduction() {
        Set<String> dbFlagsOff = Set.of("isRdsDatabaseMultiAzEnabled", "isRdsDeletionProtectionEnabled");

        List<ComplianceRule> noDatabase = evaluate(dbFlagsOff, SecurityProfile.PRODUCTION, RuntimeType.FARGATE, false);
        List<ComplianceRule> staging = evaluate(dbFlagsOff, SecurityProfile.STAGING, RuntimeType.FARGATE, true);

        assertTrue(failedIds(noDatabase).isEmpty());
        assertTrue(failedIds(staging).isEmpty());
    }

    @Test
    void productionTierControlsAreNotCheckedInStaging() {
        // isMultiAzEnforced (HighAvailability) stays PRODUCTION-only.
        // isCloudWatchLogsKmsEncryptionEnabled/isS3ObjectLockEnabled/isCloudTrailEnabled
        // (LogEncryption/AuditLogImmutability/ChangeManagement) share the same profile gate,
        // which now covers STAGING too -- the first two are in HipaaRules.STAGING_BLOCKING_RULES,
        // so they need a finding at STAGING to block on; ChangeManagement isn't blocking but still
        // runs and reports there.
        Set<String> productionOnly = Set.of("isCloudWatchLogsKmsEncryptionEnabled", "isS3ObjectLockEnabled",
            "isMultiAzEnforced", "isCloudTrailEnabled");

        List<ComplianceRule> rules = evaluate(productionOnly, SecurityProfile.STAGING, RuntimeType.EC2, false);

        assertEquals(Set.of("HIPAA-164.312(a)(2)(iv)-LogEncryption", "HIPAA-164.312(c)(1)-AuditLogImmutability",
                "HIPAA-164.308(a)(8)-ChangeManagement"),
            failedIds(rules));
        assertEquals(Set.of("HIPAA-164.312(a)(2)(iv)-EncryptionAtRest", "HIPAA-164.312(a)(1)-IMDSv2",
                "HIPAA-164.312(a)(2)(iv)-LogEncryption", "HIPAA-164.312(c)(1)-AuditLogImmutability",
                "HIPAA-164.308(a)(8)-ChangeManagement"),
            rules.stream().map(ComplianceRule::ruleId).collect(Collectors.toSet()));
    }

    @Test
    void highAvailabilityIsNotCheckedForApplicationsPinnedToOneInstance() {
        List<ComplianceRule> pinned = new HipaaRules().validateMatrixControls(
            profile(Set.of("isMultiAzEnforced")), SecurityProfile.PRODUCTION, RuntimeType.FARGATE, false, 1, true);
        List<ComplianceRule> noScalingSupport = new HipaaRules().validateMatrixControls(
            profile(Set.of("isMultiAzEnforced")), SecurityProfile.PRODUCTION, RuntimeType.FARGATE, false, 3, false);

        assertTrue(pinned.stream().noneMatch(r -> r.ruleId().equals("HIPAA-164.308(a)(7)(ii)(B)-HighAvailability")));
        assertTrue(noScalingSupport.stream().noneMatch(
            r -> r.ruleId().equals("HIPAA-164.308(a)(7)(ii)(B)-HighAvailability")));
    }

    @Test
    void everyRuleIdMapsToAControlTheMatrixRequiresForHipaa() {
        List<ComplianceRule> rules = evaluate(Set.of(), SecurityProfile.PRODUCTION, RuntimeType.EC2, true);

        // The rule set is derived from the matrix: it must cover exactly the controls it lists.
        assertEquals(RULE_BY_FLAG.size(), rules.size());
    }
}
