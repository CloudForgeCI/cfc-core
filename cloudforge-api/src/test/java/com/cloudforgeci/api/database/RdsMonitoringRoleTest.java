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
 * Live incident: a real cross-account deploy failed with {@code iam:CreateRole} denied on
 * {@code Intermediate-MySQL-RDS-IntermediateMySQLRDSApplicat-SWqnXHLIKH7c} — CloudFormation's
 * auto-generated name for the RDS Enhanced Monitoring role {@code DatabaseInstance} creates by
 * default, truncated by CloudFormation's own 64-character IAM role-name limit down to a fragment
 * with no stable substring left for {@code OperatorProvisioningPermissionMatrix}'s operator-role
 * grant to have matched in the first place. {@link RdsFactory#createDatabase} now builds this role
 * itself with an explicit {@code roleName} ending in a fixed suffix, specifically so it can never
 * be truncated unpredictably — these tests confirm that holds for both a short and a pathologically
 * long stack/app name.
 */
class RdsMonitoringRoleTest {

    private Template synthWordPress(String stackName) {
        App app = new App();
        Stack stack = new Stack(app, stackName);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", stackName);
        ctx.put("securityProfile", SecurityProfile.PRODUCTION.name());
        ctx.put("applicationId", "wordpress");
        // Non-micro: RdsFactory#supportsPerformanceInsights gates the monitoringRole path on this.
        ctx.put("databaseInstanceClass", "db.t3.large");
        stack.getNode().setContext("cfc", ctx);

        DeploymentContext cfc = DeploymentContext.from(stack);
        ApplicationFactory.createFargate(stack, stackName, cfc, SecurityProfile.PRODUCTION, new WordPressApplicationSpec());

        return Template.fromStack(stack);
    }

    private String monitoringRoleName(Template t) {
        Map<String, Map<String, Object>> roles = t.findResources("AWS::IAM::Role");
        for (Map<String, Object> role : roles.values()) {
            Object properties = role.get("Properties");
            if (properties instanceof Map<?, ?> props) {
                Object roleName = props.get("RoleName");
                if (roleName instanceof String name && name.endsWith("-CfcRdsMonitor")) {
                    return name;
                }
            }
        }
        return null;
    }

    @Test
    void monitoringRoleNameCarriesTheFixedSuffixOperatorPolicyMatchesOn() {
        Template t = synthWordPress("RdsMonitoringRoleTestShort");
        String roleName = monitoringRoleName(t);
        assertTrue(roleName != null, "expected a monitoring role ending in -CfcRdsMonitor");
        assertTrue(roleName.length() <= 64, "IAM role names cap at 64 characters: " + roleName);
    }

    @Test
    void monitoringRoleNameStaysUnder64CharsAndKeepsItsSuffixForAPathologicallyLongStackName() {
        // Long enough that CloudFormation's own auto-generated name (stack name + construct path)
        // would have truncated away any identifying suffix entirely -- the exact failure mode this
        // class exists to prevent.
        String longStackName = "IntermediateMySQLRDSApplicationForAVeryLongCustomerChosenStackName";
        Template t = synthWordPress(longStackName);
        String roleName = monitoringRoleName(t);
        assertTrue(roleName != null, "expected a monitoring role ending in -CfcRdsMonitor even for a long stack name");
        assertTrue(roleName.length() <= 64, "IAM role names cap at 64 characters: " + roleName);
        assertTrue(roleName.endsWith("-CfcRdsMonitor"));
    }
}
