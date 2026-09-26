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

/**
 * Covers the deployment-context override branch each of {@code rdsAutoMinorVersionUpgrade}/
 * {@code performanceInsightsEnabled}/{@code rdsEnhancedMonitoringEnabled} now reads in
 * {@code RdsFactory}: override set true, override set false, and override left unset (falls
 * through to the PRODUCTION-only default). Same shape as
 * {@code SecurityProfileConfigurationOverrideTest}, but exercised end to end through
 * {@code ApplicationFactory}/{@code RdsFactory} synthesis rather than the config-layer getters
 * directly, since these three are read straight off {@code ctx.cfc} in the factory rather than
 * through a {@code SecurityProfileConfiguration} method.
 */
class RdsFactoryOverrideTest {

    /** Synthesizes a WordPress/RDS stack with the given context overrides applied. */
    private Template synthWordPress(String stackName, Map<String, Object> overrides) {
        App app = new App();
        Stack stack = new Stack(app, stackName);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", stackName);
        ctx.put("securityProfile", SecurityProfile.PRODUCTION.name());
        ctx.put("applicationId", "wordpress");
        // Non-micro: RdsFactory#supportsPerformanceInsights gates Performance Insights on this.
        ctx.put("databaseInstanceClass", "db.t3.large");
        ctx.putAll(overrides);
        stack.getNode().setContext("cfc", ctx);

        DeploymentContext cfc = DeploymentContext.from(stack);
        ApplicationFactory.createFargate(stack, stackName, cfc, SecurityProfile.PRODUCTION, new WordPressApplicationSpec());

        return Template.fromStack(stack);
    }

    @Test
    void autoMinorVersionUpgradeHonorsOverride() {
        Template overrideTrue = synthWordPress("RdsOverrideAutoUpgradeTrue",
            Map.of("rdsAutoMinorVersionUpgrade", "true"));
        overrideTrue.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("AutoMinorVersionUpgrade", true));

        Template overrideFalse = synthWordPress("RdsOverrideAutoUpgradeFalse",
            Map.of("rdsAutoMinorVersionUpgrade", "false"));
        overrideFalse.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("AutoMinorVersionUpgrade", false));

        // Unset: falls through to the PRODUCTION-only default (true).
        Template unset = synthWordPress("RdsOverrideAutoUpgradeUnset", Map.of());
        unset.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("AutoMinorVersionUpgrade", true));
    }

    @Test
    void performanceInsightsHonorsOverride() {
        Template overrideTrue = synthWordPress("RdsOverridePiTrue",
            Map.of("performanceInsightsEnabled", "true"));
        overrideTrue.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("EnablePerformanceInsights", true));

        Template overrideFalse = synthWordPress("RdsOverridePiFalse",
            Map.of("performanceInsightsEnabled", "false"));
        overrideFalse.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("EnablePerformanceInsights", false));

        // Unset: falls through to the PRODUCTION-only default (true, instance class supports it).
        Template unset = synthWordPress("RdsOverridePiUnset", Map.of());
        unset.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("EnablePerformanceInsights", true));
    }

    @Test
    void enhancedMonitoringHonorsOverride() {
        Template overrideTrue = synthWordPress("RdsOverrideMonitorTrue",
            Map.of("rdsEnhancedMonitoringEnabled", "true"));
        overrideTrue.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("MonitoringInterval", 60));

        Template overrideFalse = synthWordPress("RdsOverrideMonitorFalse",
            Map.of("rdsEnhancedMonitoringEnabled", "false"));
        overrideFalse.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("MonitoringInterval", 0));

        // Unset: falls through to the PRODUCTION-only default (enabled, interval 60s).
        Template unset = synthWordPress("RdsOverrideMonitorUnset", Map.of());
        unset.hasResourceProperties("AWS::RDS::DBInstance",
            Map.of("MonitoringInterval", 60));
    }
}
