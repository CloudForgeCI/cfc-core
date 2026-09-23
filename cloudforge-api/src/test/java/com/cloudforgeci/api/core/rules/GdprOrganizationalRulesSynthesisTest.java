package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GdprOrganizationalRulesTest} only calls {@code install}, which just registers a CDK validation
 * callback -- the callback body, where every branch actually lives, only runs when the stack is
 * synthesized. These tests synthesize, so they exercise the rule logic itself, not just registration.
 */
class GdprOrganizationalRulesSynthesisTest {

    private static final Map<String, Object> ALL_ATTESTATIONS_TRUE = Map.ofEntries(
        Map.entry("gdprLegalBasisDocumented", "true"),
        Map.entry("gdprConsentMechanismImplemented", "true"),
        Map.entry("gdprPrivacyNoticeProvided", "true"),
        Map.entry("gdprDataSubjectRequestProcedures", "true"),
        Map.entry("gdprRightToErasureCapability", "true"),
        Map.entry("gdprDataPortabilityCapability", "true"),
        Map.entry("gdprDpiaCompleted", "true"),
        Map.entry("gdprPrivacyByDesignImplemented", "true"),
        Map.entry("gdprInternationalTransferSafeguards", "true"),
        Map.entry("gdprDataLocalizationEnforced", "true"),
        Map.entry("gdprDataRetentionPolicyDefined", "true"),
        Map.entry("gdprRecordsOfProcessingActivities", "true"));

    private TestInfrastructureBuilder builderFor(String stackName, SecurityProfile profile, String region,
                                                 Map<String, Object> attestations) {
        Map<String, Object> context = new HashMap<>(attestations);
        context.put("complianceFrameworks", "GDPR");
        if (region != null) {
            context.put("region", region);
        }
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder(stackName, profile, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new GdprOrganizationalRules().install(builder.getSystemContext());
        return builder;
    }

    @Test
    void productionSynthesizesCleanlyWhenEveryOrganizationalAttestationIsTrue() {
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgAllTrue", SecurityProfile.PRODUCTION, "eu-west-1", ALL_ATTESTATIONS_TRUE);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void productionFailsSynthesisWhenNoOrganizationalAttestationIsSet() {
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgAllFalse", SecurityProfile.PRODUCTION, "eu-west-1", Map.of());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void devNeverFailsSynthesisEvenWithNoAttestations() {
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgDev", SecurityProfile.DEV, "eu-west-1", Map.of());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void euRegionSatisfiesInternationalTransfersWithoutAnExplicitSafeguardFlag() {
        Map<String, Object> attestations = new HashMap<>(ALL_ATTESTATIONS_TRUE);
        attestations.remove("gdprInternationalTransferSafeguards");
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgEuRegion", SecurityProfile.PRODUCTION, "eu-central-1", attestations);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void nonEuRegionWithoutSafeguardsFailsInternationalTransfers() {
        Map<String, Object> attestations = new HashMap<>(ALL_ATTESTATIONS_TRUE);
        attestations.remove("gdprInternationalTransferSafeguards");
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgNoSafeguards", SecurityProfile.PRODUCTION, "us-east-1", attestations);

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void nonEuRegionWithExplicitSafeguardsPasses() {
        TestInfrastructureBuilder builder =
            builderFor("GdprOrgWithSafeguards", SecurityProfile.PRODUCTION, "us-east-1", ALL_ATTESTATIONS_TRUE);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }
}
