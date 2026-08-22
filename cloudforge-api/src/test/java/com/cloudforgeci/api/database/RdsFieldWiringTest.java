package com.cloudforgeci.api.database;

import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.application.cms.WordPressApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the 6 RDS/scaling {@code @DeploymentContext} fields on {@link ApplicationFactory}
 * reach their real CloudFormation property, via real CDK synth ({@link ApplicationFactory#createFargate},
 * the same entry point production uses) rather than mocks. Uses {@code wordpress} since its
 * {@code databaseRequirement()} is {@code REQUIRED}, so RDS provisioning fires automatically.
 */
class RdsFieldWiringTest {

    private Template synthWordPress(SecurityProfile profile, Map<String, Object> extra) {
        App app = new App();
        String stackName = "RdsFieldWiring" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Stack stack = new Stack(app, stackName);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", stackName);
        ctx.put("securityProfile", profile.name());
        ctx.put("applicationId", "wordpress");
        if (extra != null) ctx.putAll(extra);
        stack.getNode().setContext("cfc", ctx);

        DeploymentContext cfc = DeploymentContext.from(stack);
        ApplicationFactory.createFargate(stack, stackName, cfc, profile, new WordPressApplicationSpec());

        return Template.fromStack(stack);
    }

    @Test
    void databaseInstanceClassOverrideReachesRdsInstance() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of("databaseInstanceClass", "db.t3.large"));
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("DBInstanceClass", "db.t3.large"));
    }

    @Test
    void databaseAllocatedStorageGBOverrideReachesRdsInstance() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of("databaseAllocatedStorageGB", 250));
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("AllocatedStorage", "250"));
    }

    @Test
    void databaseNameOverrideReachesRdsInstance() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of("databaseName", "customwpdb"));
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("DBName", "customwpdb"));
    }

    @Test
    void databaseMultiAzExplicitTrueOverridesDevProfileDefault() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of("databaseMultiAz", true));
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("MultiAZ", true));
    }

    @Test
    void databaseMultiAzUnsetOnDevUsesProfileDefaultFalse() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of());
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("MultiAZ", false));
    }

    @Test
    void databaseMultiAzUnsetOnProductionDefaultsToProfileValue() {
        Template t = synthWordPress(SecurityProfile.PRODUCTION, Map.of());
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("MultiAZ", true));
    }

    @Test
    void databaseBackupRetentionDaysExplicitOverrideReachesRdsInstance() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of("databaseBackupRetentionDays", 21));
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("BackupRetentionPeriod", 21));
    }

    @Test
    void databaseBackupRetentionDaysUnsetOnDevUsesProfileDefaultSeven() {
        Template t = synthWordPress(SecurityProfile.DEV, Map.of());
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("BackupRetentionPeriod", 7));
    }

    @Test
    void databaseBackupRetentionDaysUnsetOnProductionUsesProfileDefaultThirty() {
        Template t = synthWordPress(SecurityProfile.PRODUCTION, Map.of());
        t.hasResourceProperties("AWS::RDS::DBInstance", Map.of("BackupRetentionPeriod", 30));
    }

    @Test
    void enableAutoScalingExplicitFalseSkipsScalingEvenWithACapacityRange() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("enableAutoScaling", false);
        extra.put("minInstanceCapacity", 1);
        extra.put("maxInstanceCapacity", 3);
        Template t = synthWordPress(SecurityProfile.DEV, extra);
        t.resourceCountIs("AWS::ApplicationAutoScaling::ScalableTarget", 0);
    }

    @Test
    void enableAutoScalingExplicitTrueEnablesScalingResources() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("enableAutoScaling", true);
        extra.put("minInstanceCapacity", 1);
        extra.put("maxInstanceCapacity", 3);
        Template t = synthWordPress(SecurityProfile.DEV, extra);
        assertTrue(!t.findResources("AWS::ApplicationAutoScaling::ScalableTarget").isEmpty());
    }
}
