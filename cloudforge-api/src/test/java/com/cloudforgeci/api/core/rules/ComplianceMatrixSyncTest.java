package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.interfaces.FrameworkRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps every {@code FrameworkRules} plugin's declared {@link FrameworkRules#claimedControls}
 * in sync with {@link ComplianceMatrix}, so a control the matrix marks REQUIRED for a framework
 * can't silently go unchecked the way HIPAA's encryption-at-rest gap did earlier in the
 * compliance audit, or drift the other way (a plugin claiming a control it no longer checks).
 *
 * <p>This is the v4 plugin system's sync-enforcement mechanism: any {@code FrameworkRules}
 * implementation, first-party or a third-party plugin discovered via {@code ServiceLoader}, is
 * checked the same way as long as its {@code frameworkId()} matches a matrix column (see {@link
 * ComplianceMatrix#knownFrameworkKeys}) and it declares {@code claimedControls()}. A framework
 * that hasn't declared anything yet (still the interface's empty default) is treated as fully
 * undeclared, not as "nothing required" -- see {@link #frameworksMustDeclareCoverage}.
 *
 * <p><b>How to read a failure here:</b> {@link #frameworkCoverageMatchesKnownGaps} fails in two
 * directions. If a framework now claims MORE controls than {@code KNOWN_GAPS} expects, that's
 * progress -- shrink the framework's entry in {@code KNOWN_GAPS} to match. If it claims FEWER
 * (a check was removed, or the matrix gained a new REQUIRED control for this framework), that's
 * drift -- fix the plugin, not the test.
 */
class ComplianceMatrixSyncTest {

    /**
     * Every {@code alwaysLoad} cross-framework validator. These run for every deployment
     * regardless of which frameworks are selected, so their claims count toward every framework's
     * coverage -- a control a framework-specific class leaves unclaimed can still be covered
     * if one of these claims it instead.
     */
    private static final List<FrameworkRules<?>> ALWAYS_LOAD_RULES = List.of(
        new KeyManagementRules(), new DatabaseSecurityRules(), new AdvancedMonitoringRules(),
        new ThreatProtectionRules(), new IncidentResponseRules(), new ComputeSecurityRules(),
        new LambdaSecurityRules(), new CdnApiSecurityRules(), new ElbSecurityRules(),
        new MessagingSecurityRules(), new IamSecurityRules()
    );

    /**
     * Framework-specific validators to check, including ones not yet wired into {@code
     * META-INF/services} (FedRampRules, FedRampHighRules) -- matrix sync matters even for a
     * framework nobody can select yet, so it's correct the moment it's registered instead of
     * needing its own audit pass then too.
     */
    private static final List<FrameworkRules<?>> FRAMEWORK_RULES = List.of(
        new Soc2Rules(), new HipaaRules(), new HipaaOrganizationalRules(), new PciDssRules(),
        new GdprRules(), new GdprOrganizationalRules(), new Iso27001Rules(),
        new FedRampRules(), new FedRampHighRules()
    );

    private static final Set<String> ALWAYS_LOAD_CLAIMS = ALWAYS_LOAD_RULES.stream()
        .flatMap(r -> r.claimedControls().stream())
        .collect(Collectors.toCollection(HashSet::new));

    /**
     * Controls the matrix marks REQUIRED for a framework that neither the framework's own class
     * nor any always-load class currently claims to check. This is tracked, known debt -- not a
     * silent pass. As each gets a real check added (see the "Left unclaimed" javadoc on each
     * framework's {@code claimedControls()}), shrink its entry here; the test enforces the list
     * stays accurate in both directions.
     */
    private static final Map<String, Set<String>> KNOWN_GAPS = Map.of(
        // SOC2's AUDIT_MANAGER and HIPAA's CHANGE_MANAGEMENT are now closed: AUDIT_MANAGER was
        // fixed by separating DeploymentConfig's master validation gate (auditManagerEnabled) from
        // a new, separately configurable auditManagerServiceEnabled field -- see
        // Soc2Rules#validateMatrixControls and DeploymentConfig's javadoc on both fields.
        "SOC2", Set.of(),
        "HIPAA", Set.of(),
        "PCI-DSS", Set.of(),
        "GDPR", Set.of(),
        "FEDRAMP", Set.of(),
        // Iso27001Rules doesn't check these five itself, but every always-load class (see
        // ALWAYS_LOAD_RULES) covers the rest of ISO-27001's REQUIRED controls, so this is the
        // real gap, not the plugin's claimedControls() read in isolation. NETWORK_SEGMENTATION
        // was previously masked by LambdaSecurityRules over-claiming it despite its checks being
        // gated behind the disabled-by-default lambdaEnabled flag -- see
        // LambdaSecurityRules#claimedControls().
        "ISO-27001", Set.of(
            "LOG_RETENTION", "CLOUDWATCH_LOGS_KMS_ENCRYPTION", "S3_OBJECT_LOCK", "CHANGE_MANAGEMENT",
            "NETWORK_SEGMENTATION"
        )
    );

    static Stream<FrameworkRules<?>> declaredFrameworks() {
        return FRAMEWORK_RULES.stream().filter(r -> !r.claimedControls().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredFrameworks")
    void frameworkCoverageMatchesKnownGaps(FrameworkRules<?> framework) {
        String matrixKey = framework.frameworkId();
        if (!ComplianceMatrix.knownFrameworkKeys().contains(matrixKey)) {
            // No matrix column for this framework at all (e.g. FEDRAMP-HIGH) -- out of scope.
            return;
        }

        Set<String> claimed = new HashSet<>(framework.claimedControls());
        claimed.addAll(ALWAYS_LOAD_CLAIMS);

        Set<String> actualGaps = ComplianceMatrix.findUnclaimedRequiredControls(matrixKey, claimed)
            .stream().map(Enum::name).collect(Collectors.toSet());

        Set<String> expectedGaps = KNOWN_GAPS.getOrDefault(matrixKey, Set.of());

        assertEquals(expectedGaps, actualGaps,
            () -> matrixKey + ": unclaimed-but-REQUIRED controls no longer match "
                + "KNOWN_GAPS. If this shrank, update KNOWN_GAPS to match -- that's progress. "
                + "If it grew, a check was lost or the matrix gained a new requirement -- fix "
                + "the plugin's claimedControls() (or add the check), don't just widen "
                + "KNOWN_GAPS.");
    }

    /**
     * Every framework already reconciled against the matrix should have a non-empty declaration
     * -- an empty one silently opts a framework out of sync enforcement entirely (see {@link
     * #declaredFrameworks}), which defeats the point.
     */
    @Test
    void frameworksMustDeclareCoverage() {
        List<String> undeclared = FRAMEWORK_RULES.stream()
            .filter(r -> r.claimedControls().isEmpty())
            .map(FrameworkRules::frameworkId)
            .toList();

        // GdprOrganizationalRules and HipaaOrganizationalRules validate administrative/policy
        // attestations (DPIAs, BAAs, workforce training) with no infrastructure-conditional checks
        // at all -- their claimedControls() is empty by design, not undeclared. See the "no matrix
        // column" exemption in everyFrameworkRulesIdIsAKnownMatrixKeyOrDeliberatelyOut below.
        assertEquals(
            Set.of("GDPR-Organizational", "HIPAA-Organizational"),
            new HashSet<>(undeclared),
            "A framework's claimedControls() declaration changed -- if newly non-empty, remove "
                + "it from this expected set (and it'll now be enforced by "
                + "frameworkCoverageMatchesKnownGaps); if newly empty, that's a regression.");
    }

    /**
     * Sanity check on the matrix keys used above -- catches the exact class of bug this whole
     * mechanism grew out of (the FedRAMP column existing under the wrong case, "FedRAMP" instead
     * of "FEDRAMP", silently making every FedRAMP matrix entry unreachable from real code).
     */
    @Test
    void everyFrameworkRulesIdIsAKnownMatrixKeyOrDeliberatelyOut() {
        Set<String> matrixKeys = ComplianceMatrix.knownFrameworkKeys();
        for (FrameworkRules<?> framework : FRAMEWORK_RULES) {
            String id = framework.frameworkId();
            boolean inMatrix = matrixKeys.contains(id);
            // No High column yet (see above); HIPAA-Organizational and GDPR-Organizational validate
            // administrative/policy attestations (workforce training, BAAs, DPIAs, breach-notification
            // procedures) rather than infrastructure, so neither belongs in an infrastructure matrix.
            // ISO-27001 now has a matrix column (33 REQUIRED, 10 ADVISORY across all 43 controls)
            // -- no longer exempt here, see the ISO-27001 entry in KNOWN_GAPS instead.
            boolean deliberatelyOut = id.equals("FEDRAMP-HIGH") || id.equals("HIPAA-Organizational")
                || id.equals("GDPR-Organizational");
            assertTrue(inMatrix || deliberatelyOut,
                id + " has no matrix column and isn't in the deliberately-out list -- either add "
                    + "matrix entries for it or document why it's exempt here.");
        }
    }
}
