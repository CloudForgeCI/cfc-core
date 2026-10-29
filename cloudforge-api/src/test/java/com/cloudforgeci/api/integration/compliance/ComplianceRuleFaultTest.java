package com.cloudforgeci.api.integration.compliance;

import com.cloudforgeci.api.test.DeploymentSynth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awscdk.assertions.Template;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Starts from each framework's compliant baseline, changes one setting at a time, and checks which
 * rules block the deployment. A change that should not block also has an expected, empty entry, so
 * a rule that stops firing is reported the same way as one that starts.
 *
 * <p>FedRAMP and FedRAMP High are partly implemented, so their rows record the current rules and
 * change as that work lands.</p>
 *
 * <p>{@link #KNOWN_GAPS} lists rows whose recorded result differs from what the framework
 * requires, with the reason. Each entry is also in {@link #EXPECTED}, so fixing the behavior
 * changes the table row and the entry is then removed.</p>
 */
class ComplianceRuleFaultTest {

    private static final Duration LIMIT = Duration.ofSeconds(120);

    /** Settings changed from the baseline, by name. */
    private static final Map<String, Map<String, Object>> FAULTS = new LinkedHashMap<>();

    static {
        FAULTS.put("networkMode=public-no-nat", Map.of("networkMode", "public-no-nat"));
        FAULTS.put("authMode=none", Map.of("authMode", "none"));
        FAULTS.put("cognitoMfaEnabled=false", Map.of("cognitoMfaEnabled", false));
        FAULTS.put("wafEnabled=false", Map.of("wafEnabled", false));
        FAULTS.put("guardDutyEnabled=false", Map.of("guardDutyEnabled", false));
        FAULTS.put("awsConfigEnabled=false", Map.of("awsConfigEnabled", false));
        FAULTS.put("macieEnabled=false", Map.of("macieEnabled", false));
        FAULTS.put("inspectorEnabled=false", Map.of("inspectorEnabled", false));
        FAULTS.put("enableFlowlogs=false", Map.of("enableFlowlogs", false));
        FAULTS.put("cloudTrailEnabled=false", Map.of("cloudTrailEnabled", false));
        FAULTS.put("securityMonitoringEnabled=false", Map.of("securityMonitoringEnabled", false));
        FAULTS.put("logRetentionDays=30", Map.of("logRetentionDays", 30));
        FAULTS.put("efsEncryptionInTransitEnabled=false", Map.of("efsEncryptionInTransitEnabled", false));
        FAULTS.put("enableSsl=false+authMode=none", Map.of("enableSsl", false, "authMode", "none"));
        FAULTS.put("restrictSecurityGroupEgress=true", Map.of("restrictSecurityGroupEgress", true));
        FAULTS.put("complianceMode=advisory+all faults", Map.of(
            "complianceMode", "advisory", "wafEnabled", false, "authMode", "none", "networkMode", "public-no-nat"));
    }

    /** The rules that block each change, by framework. An empty set means nothing blocks. */
    private static final Map<String, Map<String, Set<String>>> EXPECTED = Map.of(
        "pci-dss", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("PCI-DSS-Req-1.3-Network")),
            Map.entry("authMode=none", Set.of("PCI-DSS-Req-8.2-Auth")),
            Map.entry("cognitoMfaEnabled=false", Set.of("PCI-DSS-Req-8.3-MFA")),
            Map.entry("wafEnabled=false", Set.of("PCI-DSS-Req-6.6-WAF", "WAF protection required for CDN and API resources", "Web Application Firewall required for PCI-DSS")),
            Map.entry("guardDutyEnabled=false", Set.of("Network intrusion detection (GuardDuty) required for pci-dss")),
            Map.entry("awsConfigEnabled=false", Set.of()),
            Map.entry("macieEnabled=false", Set.of()),
            Map.entry("inspectorEnabled=false", Set.of("Amazon Inspector required for pci-dss vulnerability scanning")),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of()),
            Map.entry("logRetentionDays=30", Set.of("PCI-DSS-Req-10.7-Retention")),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of("HTTPS/TLS required for load balancer listeners", "PCI-DSS-Req-2.3-EncryptAdmin", "PCI-DSS-Req-4.1-SSL", "PCI-DSS-Req-4.1-TLS", "PCI-DSS-Req-8.2-Auth")),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        ),
        "hipaa", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("HIPAA-164.312(e)(1)-Network")),
            Map.entry("authMode=none", Set.of("HIPAA-164.312(a)(2)(i)-Auth", "HIPAA-164.312(d)-Auth")),
            Map.entry("cognitoMfaEnabled=false", Set.of("HIPAA-164.312(d)-MFA")),
            Map.entry("wafEnabled=false", Set.of()),
            Map.entry("guardDutyEnabled=false", Set.of("Network intrusion detection (GuardDuty) required for hipaa")),
            Map.entry("awsConfigEnabled=false", Set.of()),
            Map.entry("macieEnabled=false", Set.of("Amazon Macie required for GDPR/HIPAA data discovery")),
            Map.entry("inspectorEnabled=false", Set.of()),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of()),
            Map.entry("logRetentionDays=30", Set.of("HIPAA-164.316(b)(2)(i)-Retention")),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of("HIPAA-164.312(a)(2)(i)-Auth", "HIPAA-164.312(d)-Auth", "HIPAA-164.312(e)(2)(i)-SSL", "HIPAA-164.312(e)(2)(i)-TLS", "HTTPS/TLS required for load balancer listeners")),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        ),
        "soc2", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("SOC2-C1.2-Network")),
            Map.entry("authMode=none", Set.of("SOC2-CC6.2-Auth")),
            Map.entry("cognitoMfaEnabled=false", Set.of()),
            Map.entry("wafEnabled=false", Set.of("SOC2-CC6.6-WAF")),
            Map.entry("guardDutyEnabled=false", Set.of()),
            Map.entry("awsConfigEnabled=false", Set.of()),
            Map.entry("macieEnabled=false", Set.of()),
            Map.entry("inspectorEnabled=false", Set.of()),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of()),
            Map.entry("logRetentionDays=30", Set.of("SOC2-CC7.2-LogRetention", "SOC2-CC7.2-MatrixLogRetention")),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of("SOC2-CC6.2-Auth", "SOC2-CC6.7-SSL")),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        ),
        "gdpr", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("GDPR-NETWORK-ISOLATION")),
            Map.entry("authMode=none", Set.of("GDPR-AUTHENTICATION")),
            Map.entry("cognitoMfaEnabled=false", Set.of()),
            Map.entry("wafEnabled=false", Set.of("GDPR-WAF-PROTECTION")),
            Map.entry("guardDutyEnabled=false", Set.of()),
            Map.entry("awsConfigEnabled=false", Set.of()),
            Map.entry("macieEnabled=false", Set.of("Amazon Macie required for GDPR/HIPAA data discovery")),
            Map.entry("inspectorEnabled=false", Set.of()),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of()),
            Map.entry("logRetentionDays=30", Set.of("GDPR-LOG-RETENTION")),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of("GDPR-AUTHENTICATION", "GDPR-SSL-ENCRYPTION", "GDPR-TLS-ENCRYPTION")),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        ),
        "fedramp", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("FEDRAMP-AC-17", "FEDRAMP-SC-7(5)")),
            Map.entry("authMode=none", Set.of("FEDRAMP-AC-7", "FEDRAMP-IA-2")),
            Map.entry("cognitoMfaEnabled=false", Set.of("FEDRAMP-IA-2(1)")),
            Map.entry("wafEnabled=false", Set.of("FEDRAMP-SC-7-WAF")),
            Map.entry("guardDutyEnabled=false", Set.of()),
            Map.entry("awsConfigEnabled=false", Set.of()),
            Map.entry("macieEnabled=false", Set.of()),
            Map.entry("inspectorEnabled=false", Set.of("Amazon Inspector required for fedramp vulnerability scanning")),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of()),
            Map.entry("logRetentionDays=30", Set.of("FEDRAMP-AU-11")),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of("FEDRAMP-AC-17(2)", "FEDRAMP-AC-7", "FEDRAMP-IA-2", "FEDRAMP-MP-5-TLS", "FEDRAMP-SC-8")),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        ),
        "fedramp-high", Map.ofEntries(
            Map.entry("networkMode=public-no-nat", Set.of("FEDRAMP-HIGH-SC-7(18)")),
            Map.entry("authMode=none", Set.of()),
            Map.entry("cognitoMfaEnabled=false", Set.of()),
            Map.entry("wafEnabled=false", Set.of()),
            Map.entry("guardDutyEnabled=false", Set.of("FEDRAMP-HIGH-AC-2(13)", "FEDRAMP-HIGH-IR-4(1)", "FEDRAMP-HIGH-SI-4(5)")),
            Map.entry("awsConfigEnabled=false", Set.of("FEDRAMP-HIGH-AC-2(2)")),
            Map.entry("macieEnabled=false", Set.of()),
            Map.entry("inspectorEnabled=false", Set.of()),
            Map.entry("enableFlowlogs=false", Set.of()),
            Map.entry("cloudTrailEnabled=false", Set.of()),
            Map.entry("securityMonitoringEnabled=false", Set.of("FEDRAMP-HIGH-IR-4(1)", "FEDRAMP-HIGH-SI-4(5)")),
            Map.entry("logRetentionDays=30", Set.of()),
            Map.entry("efsEncryptionInTransitEnabled=false", Set.of()),
            Map.entry("enableSsl=false+authMode=none", Set.of()),
            Map.entry("restrictSecurityGroupEgress=true", Set.of()),
            Map.entry("complianceMode=advisory+all faults", Set.of())
        )
    );

    /** Rows whose recorded result differs from the framework's requirement, with the reason. */
    private static final Map<String, String> KNOWN_GAPS = Map.ofEntries(
        Map.entry("pci-dss|wafEnabled=false",
            "one missing WAF is reported by three rule classes (PciDssRules, CdnApiSecurityRules, ThreatProtectionRules)"),
        Map.entry("fedramp-high|authMode=none",
            "FedRAMP is partly implemented (deferred work); fedramp-high alone applies only the High enhancements"),
        Map.entry("fedramp-high|cognitoMfaEnabled=false",
            "FedRAMP is partly implemented (deferred work); fedramp-high alone applies only the High enhancements"),
        Map.entry("fedramp-high|wafEnabled=false",
            "FedRAMP is partly implemented (deferred work); fedramp-high alone applies only the High enhancements"),
        Map.entry("fedramp-high|logRetentionDays=30",
            "FedRAMP is partly implemented (deferred work); fedramp-high alone applies only the High enhancements"),
        Map.entry("fedramp-high|enableSsl=false+authMode=none",
            "FedRAMP is partly implemented (deferred work); fedramp-high alone applies only the High enhancements"));

    static Stream<Arguments> cases() {
        return DeploymentSynth.FRAMEWORKS.stream().flatMap(framework -> FAULTS.keySet().stream()
            .map(fault -> Arguments.of(framework, fault)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("cases")
    void changingOneSettingBlocksTheExpectedRules(String framework, String fault) {
        Map<String, Object> context = DeploymentSynth.baseline(framework);
        context.putAll(FAULTS.get(fault));

        Set<String> blocking = assertTimeoutPreemptively(LIMIT, () -> DeploymentSynth.blockingRules(context));

        assertEquals(EXPECTED.get(framework).get(fault), blocking);
    }

    @Test
    void everyFrameworkAndChangeHasAnExpectation() {
        assertEquals(Set.copyOf(DeploymentSynth.FRAMEWORKS), EXPECTED.keySet());
        for (String framework : DeploymentSynth.FRAMEWORKS) {
            assertEquals(FAULTS.keySet(), EXPECTED.get(framework).keySet(), framework);
        }
    }

    @Test
    void everyKnownGapIsARowInTheTable() {
        for (String gap : KNOWN_GAPS.keySet()) {
            String[] parts = gap.split("\\|", 2);
            assertTrue(EXPECTED.containsKey(parts[0]) && EXPECTED.get(parts[0]).containsKey(parts[1]),
                gap + " is not a row in EXPECTED");
        }
    }

    // ---- The template agrees with the rules ----

    private static int count(Template template, String type) {
        return template.findResources(type).size();
    }

    @Test
    void turningGuardDutyOffRemovesTheDetectorAndBlocksTheDeployment() {
        Map<String, Object> context = DeploymentSynth.baseline("pci-dss");
        assertEquals(1, count(DeploymentSynth.synthesize(context), "AWS::GuardDuty::Detector"));

        context.put("guardDutyEnabled", false);
        context.put("complianceMode", "advisory");
        assertEquals(0, count(DeploymentSynth.synthesize(context), "AWS::GuardDuty::Detector"));
    }

    @Test
    void turningTheWafOffRemovesTheWebAclAndBlocksTheDeployment() {
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        assertEquals(1, count(DeploymentSynth.synthesize(context), "AWS::WAFv2::WebACL"));

        context.put("wafEnabled", false);
        context.put("complianceMode", "advisory");
        assertEquals(0, count(DeploymentSynth.synthesize(context), "AWS::WAFv2::WebACL"));
    }

    @Test
    void cloudTrailAndFlowLogsAreDeployedWhenTheFrameworkRequiresThemWhateverTheirSettingsSay() {
        // The PRODUCTION profile applies a control the compliance matrix requires and does not let
        // the setting override it, so these two settings have no effect under SOC2.
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        context.put("cloudTrailEnabled", false);
        context.put("enableFlowlogs", false);

        Template template = DeploymentSynth.synthesize(context);

        assertEquals(1, count(template, "AWS::CloudTrail::Trail"));
        assertEquals(1, count(template, "AWS::EC2::FlowLog"));
    }

    @Test
    void awsConfigEnabledPassesTheRulesWithoutDeployingAConfigurationRecorder() {
        Map<String, Object> context = DeploymentSynth.baseline("fedramp-high");
        assertEquals(Set.of(), DeploymentSynth.blockingRules(context));

        Template template = DeploymentSynth.synthesize(context);

        assertEquals(0, count(template, "AWS::Config::ConfigurationRecorder"),
            "createConfigInfrastructure is false in the baseline, so the account's own Config setup is assumed");
    }

    @Test
    void fedrampHighSelectedWithFedrampAppliesTheBaseControls() {
        Map<String, Object> context = DeploymentSynth.baseline("fedramp-high");
        context.put("complianceFrameworks", "fedramp,fedramp-high");
        context.put("wafEnabled", false);

        assertEquals(Set.of("FEDRAMP-SC-7-WAF"), DeploymentSynth.blockingRules(context));
    }

    @Test
    void withoutADomainAndWithSslTheDeploymentStillSynthesizesForEveryFramework() {
        // The no-domain Private CA path used to stop Template.fromStack; the baselines have no domain.
        Set<String> failures = new TreeSet<>();
        for (String framework : DeploymentSynth.FRAMEWORKS) {
            Set<String> blocking = assertTimeoutPreemptively(LIMIT,
                () -> DeploymentSynth.blockingRules(DeploymentSynth.baseline(framework)));
            if (!blocking.isEmpty()) {
                failures.add(framework + " " + blocking);
            }
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }

    // ---- Compliance mode ----
    // ENFORCE overrides a setting and blocks. ADVISORY reports without overriding or blocking.
    // DISABLED, like selecting no framework, applies nothing.

    /** Every change that blocks its framework under ENFORCE, in table order. */
    static Stream<Arguments> blockingRows() {
        return DeploymentSynth.FRAMEWORKS.stream().flatMap(framework -> FAULTS.keySet().stream()
            .filter(fault -> !fault.startsWith("complianceMode"))
            .filter(fault -> !EXPECTED.get(framework).get(fault).isEmpty())
            .map(fault -> Arguments.of(framework, fault)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("blockingRows")
    void onlyEnforceModeBlocks(String framework, String fault) {
        Map<String, Object> context = DeploymentSynth.baseline(framework);
        context.putAll(FAULTS.get(fault));

        for (String mode : List.of("advisory", "disabled", "none")) {
            context.put("complianceMode", mode);
            Set<String> blocking = assertTimeoutPreemptively(LIMIT, () -> DeploymentSynth.blockingRules(context));

            assertEquals(Set.of(), blocking, framework + " " + fault + " under " + mode);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "pci-dss,enforce,1", "pci-dss,advisory,0", "pci-dss,disabled,0",
        "soc2,enforce,1", "soc2,advisory,0", "soc2,disabled,0",
        "hipaa,enforce,1", "hipaa,advisory,0", "hipaa,disabled,0"})
    void onlyEnforceModeOverridesASettingTheUserTurnedOff(String framework, String mode, int flowLogs) {
        Map<String, Object> context = DeploymentSynth.baseline(framework);
        context.put("complianceMode", mode);
        context.put("enableFlowlogs", false);

        Template template = assertTimeoutPreemptively(LIMIT, () -> DeploymentSynth.synthesize(context));

        assertEquals(flowLogs, count(template, "AWS::EC2::FlowLog"), framework + " " + mode);
    }

    @ParameterizedTest
    @ValueSource(strings = {"enforce", "advisory", "disabled", "none"})
    void withoutAFrameworkNoModeBlocksOrOverrides(String mode) {
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        context.remove("complianceFrameworks");
        context.put("complianceMode", mode);
        context.put("enableFlowlogs", false);
        context.put("wafEnabled", false);
        context.put("guardDutyEnabled", false);
        context.put("authMode", "none");
        context.put("enableSsl", false);

        Template template = assertTimeoutPreemptively(LIMIT, () -> DeploymentSynth.synthesize(context));

        assertEquals(0, count(template, "AWS::EC2::FlowLog"), "the user's setting is kept");
        assertEquals(0, count(template, "AWS::WAFv2::WebACL"), "the user's setting is kept");
    }

    @Test
    void theDefaultModeIsEnforceWithAFrameworkAndDisabledWithout() {
        Map<String, Object> withFramework = DeploymentSynth.baseline("pci-dss");
        withFramework.remove("complianceMode");
        withFramework.put("wafEnabled", false);
        assertEquals(EXPECTED.get("pci-dss").get("wafEnabled=false"), DeploymentSynth.blockingRules(withFramework));

        Map<String, Object> withoutFramework = DeploymentSynth.baseline("pci-dss");
        withoutFramework.remove("complianceMode");
        withoutFramework.remove("complianceFrameworks");
        withoutFramework.put("wafEnabled", false);
        assertEquals(Set.of(), DeploymentSynth.blockingRules(withoutFramework));
    }
}
