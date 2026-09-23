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
 * {@code FedRampHighRules} is not registered in {@code META-INF/services} and {@code
 * ComplianceFrameworkType} has no FEDRAMP-HIGH entry, so nothing in a real deployment ever calls
 * {@code install}. These tests call it directly, exercising the validator logic itself, which is
 * otherwise completely untested. Mirrors {@code FedRampRulesSynthesisTest} for the Moderate baseline.
 */
class FedRampHighRulesSynthesisTest {

    /** Everything each {@code validate*} method's pass branch needs: AWS Config, GuardDuty,
     *  cross-region backup, Multi-AZ, security monitoring and private networking all present. */
    private TestInfrastructureBuilder passingProductionBuilder(String stackName) {
        Map<String, Object> context = new HashMap<>();
        context.put("networkMode", "private-with-nat");
        context.put("awsConfigEnabled", "true");
        context.put("guardDutyEnabled", "true");
        context.put("securityMonitoringEnabled", "true");
        context.put("crossRegionBackupEnabled", "true");
        context.put("backupCrossRegionVaultArn", "arn:aws:backup:us-west-2:123456789012:backup-vault:cross-region");
        context.put("multiAzEnforced", "true");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder(stackName, SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());
        return builder;
    }

    @Test
    void productionWithEveryControlSatisfiedSynthesizesCleanly() {
        TestInfrastructureBuilder builder = passingProductionBuilder("FedRampHighAllPass");

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void productionBlocksSynthesisWhenControlsAreMissing() {
        Map<String, Object> context = new HashMap<>();
        context.put("networkMode", "public");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighAllFail", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void devProfileSkipsFedRampHighValidationEntirely() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighDevSkip", SecurityProfile.DEV, RuntimeType.FARGATE, Map.of("networkMode", "public"));
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void stagingSkipsFedRampHighValidationEntirely() {
        // Unlike Moderate, High only applies to PRODUCTION -- see FedRampHighRules#install.
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighStagingSkip", SecurityProfile.STAGING, RuntimeType.FARGATE, Map.of("networkMode", "public"));
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void missingCrossRegionBackupAloneFailsSynthesis() {
        Map<String, Object> context = new HashMap<>();
        context.put("networkMode", "private-with-nat");
        context.put("awsConfigEnabled", "true");
        context.put("guardDutyEnabled", "true");
        context.put("securityMonitoringEnabled", "true");
        context.put("multiAzEnforced", "true");
        context.put("crossRegionBackupEnabled", "false");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighNoCrossRegion", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void enforcementIgnoresComplianceModeUnlikeTheModerateBaseline() {
        // Unlike FedRampRules, FedRampHighRules never reads ctx.cfc.complianceMode() -- a failing
        // control always blocks synthesis here, even when the deployment context requests ADVISORY.
        // This documents that behavior rather than asserting it is correct; see the compliance
        // audit notes on FedRAMP High before changing it.
        Map<String, Object> context = new HashMap<>();
        context.put("networkMode", "public");
        context.put("complianceMode", "ADVISORY");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighIgnoresAdvisory", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }
}
