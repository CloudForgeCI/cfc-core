package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwsConfigRuleTest {

    @Test
    void expectedCfnResourceTypeIsEmptyForRulesWithoutOneMapped() {
        assertTrue(AwsConfigRule.RESTRICTED_SSH.getExpectedCfnResourceType().isEmpty());
        assertTrue(AwsConfigRule.IAM_USER_MFA_ENABLED.getExpectedCfnResourceType().isEmpty());
    }

    @Test
    void expectedCfnResourceTypeReturnsTheMappedValue() {
        assertEquals("AWS::CloudTrail::Trail",
            AwsConfigRule.CLOUDTRAIL_ENABLED.getExpectedCfnResourceType().orElseThrow());
        assertEquals("AWS::RDS::DBInstance",
            AwsConfigRule.RDS_STORAGE_ENCRYPTED.getExpectedCfnResourceType().orElseThrow());
        assertEquals("AWS::EFS::FileSystem",
            AwsConfigRule.EFS_ENCRYPTED.getExpectedCfnResourceType().orElseThrow());
    }

    @Test
    void guardDutyEnabledHasNoResourceTypeMapped() {
        // GuardDutyFactory#enableGuardDuty registers this rule unconditionally but only builds
        // CfnDetector when createGuardDutyDetector is true -- see AwsConfigRule's own comment on
        // this constant. Asserted explicitly so a future re-add doesn't slip back in unnoticed.
        assertTrue(AwsConfigRule.GUARDDUTY_ENABLED.getExpectedCfnResourceType().isEmpty());
    }

    /**
     * Enforcement test: a rule being REQUIRED only proves a {@code CfnConfigRule} was deployed
     * asking AWS Config to evaluate something -- it proves nothing about whether this stack built
     * the infrastructure that rule checks. This synthesizes the same CloudTrail creation path
     * {@code ctx.requireConfigRule(AwsConfigRule.CLOUDTRAIL_ENABLED)} sits inside ({@code
     * ComplianceFactory#createCloudTrail}, PRODUCTION/STAGING profiles) and asserts the resource
     * -- not just the Config rule -- is present, and that its type matches what {@code
     * getExpectedCfnResourceType()} claims.
     */
    @Test
    void cloudTrailEnabledRuleHasACloudTrailTrailInTheSynthesizedTemplate() {
        Map<String, Object> context = new HashMap<>();
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestCloudTrailEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);

        builder.createMinimalInfrastructure().createCompliance();

        Template template = Template.fromStack(builder.getStack());

        String expectedType = AwsConfigRule.CLOUDTRAIL_ENABLED.getExpectedCfnResourceType().orElseThrow();
        template.resourceCountIs(expectedType, 1);
    }
}
