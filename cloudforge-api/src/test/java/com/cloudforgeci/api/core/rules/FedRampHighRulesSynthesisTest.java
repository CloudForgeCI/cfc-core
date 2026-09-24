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
 * {@code FedRampHighRules} is registered in {@code META-INF/services} and {@code
 * ComplianceFrameworkType} has a FEDRAMP_HIGH entry, so a real deployment can reach {@code
 * install}. These tests call it directly, exercising the validator logic itself. Mirrors {@code
 * FedRampRulesSynthesisTest} for the Moderate baseline.
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
        // DeploymentConfig.securityProfile defaults to DEV (which resolves complianceMode to
        // ADVISORY) unless "securityProfile" is set in the raw context -- the PRODUCTION passed
        // to TestInfrastructureBuilder below only drives ctx.security, a separate field. Set both
        // explicitly so this test actually exercises ENFORCE-mode blocking.
        context.put("securityProfile", "PRODUCTION");
        context.put("complianceMode", "ENFORCE");
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
        context.put("securityProfile", "PRODUCTION");
        context.put("complianceMode", "ENFORCE");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighNoCrossRegion", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void advisoryModeDoesNotBlockSynthesisOnFailingControls() {
        // A failing control produces a visible finding in ADVISORY mode, same as FedRampRules,
        // but must not block synthesis.
        Map<String, Object> context = new HashMap<>();
        context.put("networkMode", "public");
        context.put("complianceMode", "ADVISORY");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampHighAdvisoryDoesNotBlock", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampHighRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }
}
