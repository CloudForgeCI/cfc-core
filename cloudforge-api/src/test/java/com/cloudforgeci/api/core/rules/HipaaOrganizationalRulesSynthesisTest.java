package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HipaaOrganizationalRulesTest} only calls {@code install}, which just registers a CDK
 * validation callback -- the callback body only runs when the stack is synthesized. These tests
 * synthesize, so they exercise the rule logic itself, not just registration.
 */
class HipaaOrganizationalRulesSynthesisTest {

    private static final Map<String, Object> ALL_ATTESTATIONS_TRUE = Map.ofEntries(
        Map.entry("awsBaaSigned", "true"),
        Map.entry("thirdPartyBaasDocumented", "true"),
        Map.entry("baaProvisionsVerified", "true"),
        Map.entry("subcontractorBaasTracked", "true"),
        Map.entry("workforceAuthorizationProcedures", "true"),
        Map.entry("terminationProcedures", "true"),
        Map.entry("hipaaTrainingProgram", "true"),
        Map.entry("emergencyAccessProcedures", "true"),
        Map.entry("automaticLogoffEnabled", "true"),
        Map.entry("incidentResponsePlan", "true"),
        Map.entry("breachNotificationProcedures", "true"),
        Map.entry("breachDetectionAutomation", "true"));

    private TestInfrastructureBuilder builderFor(String stackName, SecurityProfile profile,
                                                 Map<String, Object> attestations) {
        Map<String, Object> context = new java.util.HashMap<>(attestations);
        context.put("complianceFrameworks", "HIPAA");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder(stackName, profile, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new HipaaOrganizationalRules().install(builder.getSystemContext());
        return builder;
    }

    @Test
    void productionSynthesizesCleanlyWhenEveryOrganizationalAttestationIsTrue() {
        TestInfrastructureBuilder builder =
            builderFor("HipaaOrgAllTrue", SecurityProfile.PRODUCTION, ALL_ATTESTATIONS_TRUE);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void productionFailsSynthesisWhenNoOrganizationalAttestationIsSet() {
        TestInfrastructureBuilder builder = builderFor("HipaaOrgAllFalse", SecurityProfile.PRODUCTION, Map.of());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void devAlsoFailsSynthesisWithNoAttestations() {
        // Unlike GdprOrganizationalRules, this class has no DEV skip: every profile is held to the
        // same organizational attestations. Documented here so a future DEV exemption is a deliberate
        // change, not an untested one.
        TestInfrastructureBuilder builder = builderFor("HipaaOrgDev", SecurityProfile.DEV, Map.of());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void stagingFailsSynthesisTheSameWayProductionDoes() {
        TestInfrastructureBuilder builder = builderFor("HipaaOrgStaging", SecurityProfile.STAGING, Map.of());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void frameworkIsSkippedWhenHipaaIsNotSelected() {
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("complianceFrameworks", "SOC2");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder("HipaaOrgNotSelected", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new HipaaOrganizationalRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }
}
