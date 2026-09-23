package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.ComplianceMode;
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
 * {@code FedRampRules} is not registered in {@code META-INF/services} and {@code
 * ComplianceFrameworkType} has no FEDRAMP entry, so nothing in a real deployment ever calls {@code
 * install}. These tests call it directly, exercising the validator logic itself, which is otherwise
 * completely untested. See the compliance audit notes on FedRAMP registration.
 */
class FedRampRulesSynthesisTest {

    /** Everything each {@code validate*} method's pass branch needs: IAM, security groups, flow
     *  logs, a certificate, private networking and authentication all present. */
    private TestInfrastructureBuilder passingProductionBuilder(String stackName, ComplianceMode mode) {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "alb-oidc");
        context.put("enableSsl", "true");
        context.put("fqdn", "fedramp.example.com");
        context.put("networkMode", "private-with-nat");
        context.put("complianceMode", mode.name());
        context.put("cloudTrailEnabled", "true");
        context.put("enableFlowlogs", "true");
        context.put("guardDutyEnabled", "true");
        context.put("awsConfigEnabled", "true");
        context.put("securityMonitoringEnabled", "true");
        context.put("logRetentionDays", "1096"); // FEDRAMP-AU-11 requires 3+ years
        context.put("albAccessLogging", "true");
        context.put("wafEnabled", "true");
        context.put("cognitoAutoProvision", "true");
        context.put("cognitoMfaEnabled", "true");
        context.put("cloudWatchLogsKmsEncryptionEnabled", "true");
        context.put("s3ObjectLockEnabled", "true");
        context.put("httpsStrictEnabled", "true");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder(stackName, SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        builder.createMockCertificate();
        builder.createMockHttpsListener();
        new SecurityRules().install(builder.getSystemContext());
        new FedRampRules().install(builder.getSystemContext());
        return builder;
    }

    @Test
    void productionWithEveryControlSatisfiedSynthesizesCleanly() {
        TestInfrastructureBuilder builder = passingProductionBuilder("FedRampAllPass", ComplianceMode.ENFORCE);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void productionEnforceModeBlocksSynthesisWhenControlsAreMissing() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("complianceMode", "ENFORCE");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder("FedRampAllFail", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampRules().install(builder.getSystemContext());

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void advisoryModeNeverBlocksSynthesisEvenWithMissingControls() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("complianceMode", "ADVISORY");
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder("FedRampAdvisory", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new FedRampRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void devProfileSkipsFedRampValidationEntirely() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "FedRampDevSkip", SecurityProfile.DEV, RuntimeType.FARGATE, Map.of("authMode", "none"));
        builder.createMinimalInfrastructure();
        new FedRampRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void stagingIsHeldToTheSameControlsAsProduction() {
        TestInfrastructureBuilder builder = passingProductionBuilder("FedRampStagingPass", ComplianceMode.ENFORCE);
        // Re-derive a STAGING variant of the same passing configuration.
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "alb-oidc");
        context.put("enableSsl", "true");
        context.put("fqdn", "fedramp-staging.example.com");
        context.put("networkMode", "private-with-nat");
        context.put("complianceMode", "ENFORCE");
        context.put("cloudTrailEnabled", "true");
        context.put("enableFlowlogs", "true");
        context.put("guardDutyEnabled", "true");
        context.put("awsConfigEnabled", "true");
        context.put("securityMonitoringEnabled", "true");
        context.put("logRetentionDays", "1096"); // FEDRAMP-AU-11 requires 3+ years
        context.put("albAccessLogging", "true");
        context.put("wafEnabled", "true");
        context.put("cognitoAutoProvision", "true");
        context.put("cognitoMfaEnabled", "true");
        context.put("cloudWatchLogsKmsEncryptionEnabled", "true");
        context.put("s3ObjectLockEnabled", "true");
        context.put("httpsStrictEnabled", "true");
        TestInfrastructureBuilder staging =
            new TestInfrastructureBuilder("FedRampStaging", SecurityProfile.STAGING, RuntimeType.FARGATE, context);
        staging.createMinimalInfrastructure();
        staging.createMockCertificate();
        staging.createMockHttpsListener();
        new FedRampRules().install(staging.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(staging.getStack()));
    }
}
