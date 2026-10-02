package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforgeci.api.database.RdsFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Match;
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
     * the infrastructure that rule checks. This synthesizes {@code
     * ComplianceFactory#createCloudTrail} (PRODUCTION/STAGING profiles) and asserts the resource
     * each of the four CloudTrail-mapped rules points at is present, not just the Config rules.
     * {@code complianceFrameworks=HIPAA} enables KMS encryption, which is what gates
     * CLOUDTRAIL_ENCRYPTION_ENABLED's own registration. The same synthesis also exercises {@code
     * getOrCreateBucket}, which creates the CloudTrail logs bucket and registers the two mapped S3
     * rules.
     */
    @Test
    void cloudTrailAndS3RulesHaveTheirResourcesInTheSynthesizedTemplate() {
        Map<String, Object> context = new HashMap<>();
        context.put("complianceFrameworks", "HIPAA");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestCloudTrailEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, context);

        builder.createMinimalInfrastructure().createCompliance();

        assertMappedResourceExists(AwsConfigRule.CLOUDTRAIL_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.CLOUDTRAIL_LOG_FILE_VALIDATION, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.MULTI_REGION_CLOUDTRAIL, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.CLOUDTRAIL_ENCRYPTION_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.S3_BUCKET_ENCRYPTION, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.S3_BUCKET_VERSIONING_ENABLED, builder.getStack());
    }

    /**
     * Exercises {@link RdsFactory#createDatabase}, the one place all six mapped RDS rules are
     * registered, and asserts {@code AWS::RDS::DBInstance} is present for each.
     */
    @Test
    void rdsRulesHaveADbInstanceInTheSynthesizedTemplate() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestRdsEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, new HashMap<>());
        builder.createMinimalInfrastructure();

        DatabaseRequirement requirement = DatabaseRequirement.required("postgres", "16")
            .withInstanceClass("db.t3.medium")
            .withStorage(20)
            .withDatabaseName("testdb");
        RdsFactory.createDatabase(
            builder.getSystemContext(), requirement, builder.getSystemContext().vpc.get().orElseThrow(), "TestDb");

        assertMappedResourceExists(AwsConfigRule.RDS_STORAGE_ENCRYPTED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.DB_INSTANCE_BACKUP_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.RDS_INSTANCE_PUBLIC_ACCESS_CHECK, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.RDS_INSTANCE_DELETION_PROTECTION_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.RDS_LOGGING_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.RDS_MULTI_AZ, builder.getStack());
    }

    /**
     * {@code createMinimalInfrastructure()} already builds VPC + ALB + EFS + Fargate, registering
     * ELB_LOGGING_ENABLED (always) and EFS_ENCRYPTED (always) directly in the same methods that
     * build the load balancer and file system. ELB_DELETION_PROTECTION only registers when the
     * security profile enables it, which PRODUCTION does.
     */
    @Test
    void albAndEfsRulesHaveTheirResourcesInTheSynthesizedTemplate() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestAlbEfsEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, new HashMap<>());
        builder.createMinimalInfrastructure();

        assertMappedResourceExists(AwsConfigRule.ELB_LOGGING_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.ELB_DELETION_PROTECTION, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.EFS_ENCRYPTED, builder.getStack());
    }

    /**
     * WafFactory requires an ALB to already exist (it associates the WebACL with one), so this
     * builds on {@code createMinimalInfrastructure()} rather than a bare VPC.
     */
    @Test
    void wafRulesHaveTheirResourcesInTheSynthesizedTemplate() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestWafEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, new HashMap<>());
        builder.withWafEnabled(true).createMinimalInfrastructure().createWaf();

        assertMappedResourceExists(AwsConfigRule.ALB_WAF_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.WAFV2_LOGGING_ENABLED, builder.getStack());
    }

    /**
     * FlowLogFactory only builds {@code FlowLogOptions} and sets {@code ctx.flowlogs} -- the
     * {@code AWS::EC2::FlowLog} resource is created by VpcFactory consuming that slot. VpcFactory
     * checks it during its own {@code create()}, so FlowLogFactory must run first or the option
     * is set too late to be picked up (matching the order in {@code ApplicationFactory}, which
     * creates FlowLogFactory before the infrastructure factories that include VpcFactory).
     */
    @Test
    void flowLogRulesHaveTheirResourcesInTheSynthesizedTemplate() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestFlowLogEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, new HashMap<>());

        builder.getSystemContext();
        new FlowLogFactory(builder.getStack(), "Flowlog").create();
        builder.createMinimalInfrastructure();

        assertMappedResourceExists(AwsConfigRule.VPC_FLOW_LOGS_ENABLED, builder.getStack());
        assertMappedResourceExists(AwsConfigRule.CLOUDWATCH_LOG_GROUP_ENCRYPTED, builder.getStack());
    }

    private static void assertMappedResourceExists(AwsConfigRule rule, Stack stack) {
        String expectedType = rule.getExpectedCfnResourceType().orElseThrow();
        Template.fromStack(stack).hasResource(expectedType, Match.anyValue());
    }
}
