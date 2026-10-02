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
     * getOrCreateBucket}, which creates the CloudTrail logs bucket (logical id {@code
     * CloudTrailBucket}, from {@code getOrCreateBucketWithSSM("CloudTrailBucket", ...)}) and
     * registers the two mapped S3 rules. S3_BUCKET_ENCRYPTION/VERSIONING are asserted by logical
     * id, not bare type -- {@code AWS::S3::Bucket} is a type other factories can also create (e.g.
     * AlbFactory's access-log bucket), so a type-only match would pass even if this specific
     * bucket were never built.
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
        assertMappedResourceWithLogicalIdContains(
            AwsConfigRule.S3_BUCKET_ENCRYPTION, builder.getStack(), "CloudTrailBucket");
        assertMappedResourceWithLogicalIdContains(
            AwsConfigRule.S3_BUCKET_VERSIONING_ENABLED, builder.getStack(), "CloudTrailBucket");
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
     *
     * <p>CLOUDWATCH_LOG_GROUP_ENCRYPTED is asserted by logical id, not bare type -- {@code
     * AWS::Logs::LogGroup} is a type other factories also create (e.g. LoggingCwFactory's
     * SecurityProfileLogs group, built by {@code createMinimalInfrastructure()}'s own Fargate
     * step), so a type-only match would pass even with FlowLogFactory's own log group
     * ({@code VpcFlowLogsGroup}) never built.
     */
    @Test
    void flowLogRulesHaveTheirResourcesInTheSynthesizedTemplate() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "TestFlowLogEnforcement", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, new HashMap<>());

        builder.getSystemContext();
        new FlowLogFactory(builder.getStack(), "Flowlog").create();
        builder.createMinimalInfrastructure();

        assertMappedResourceExists(AwsConfigRule.VPC_FLOW_LOGS_ENABLED, builder.getStack());
        assertMappedResourceWithLogicalIdContains(
            AwsConfigRule.CLOUDWATCH_LOG_GROUP_ENCRYPTED, builder.getStack(), "VpcFlowLogsGroup");
    }

    private static void assertMappedResourceExists(AwsConfigRule rule, Stack stack) {
        String expectedType = rule.getExpectedCfnResourceType().orElseThrow();
        Template.fromStack(stack).hasResource(expectedType, Match.anyValue());
    }

    /**
     * Like {@link #assertMappedResourceExists}, but also requires a resource of the mapped type
     * whose logical id contains {@code logicalIdSubstring} -- CDK appends a hash suffix to
     * logical ids, so this checks a substring rather than an exact match. Use this instead of the
     * type-only assertion whenever the mapped resource type isn't unique to the factory under
     * test, or the assertion would pass regardless of whether that factory built anything.
     */
    private static void assertMappedResourceWithLogicalIdContains(
            AwsConfigRule rule, Stack stack, String logicalIdSubstring) {
        String expectedType = rule.getExpectedCfnResourceType().orElseThrow();
        Map<String, Map<String, Object>> matches = Template.fromStack(stack).findResources(expectedType);
        boolean found = matches.keySet().stream().anyMatch(id -> id.contains(logicalIdSubstring));
        assertTrue(found, "Expected a " + expectedType + " resource with logical id containing \""
            + logicalIdSubstring + "\", found: " + matches.keySet());
    }
}
