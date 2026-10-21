package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.services.cognito.IUserPool;
import software.amazon.awscdk.services.cognito.UserPool;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the COGNITO-AUDIT-TRAIL check added to {@link IamSecurityRules#validateAuthentication}:
 * an app using Cognito/OIDC auth without CloudTrail enabled has no audit trail for its own
 * sign-ins.
 */
class IamSecurityRulesTest {

    private TestInfrastructureBuilder builderFor(String stackName, SecurityProfile profile, String complianceFrameworks) {
        Map<String, Object> customContext = new HashMap<>();
        customContext.put("stackName", stackName);
        if (complianceFrameworks != null) {
            customContext.put("complianceFrameworks", complianceFrameworks);
            // ComplianceMode's profile-based auto-default (ENFORCE for PRODUCTION) does not take
            // effect through TestInfrastructureBuilder's context-map path, so blocking-failure
            // tests set it explicitly, matching FedRampHighRulesSynthesisTest/AdvancedMonitoringRulesTest.
            customContext.put("complianceMode", "ENFORCE");
        }
        return new TestInfrastructureBuilder(stackName, profile, RuntimeType.FARGATE, customContext);
    }

    @Test
    void appsWithoutCognitoAreUnaffectedByTheNewCheck() {
        TestInfrastructureBuilder builder = builderFor("TestNoCognito", SecurityProfile.PRODUCTION, "PCI-DSS");
        builder.createMinimalInfrastructure();

        new IamSecurityRules().install(builder.getSystemContext());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "no COGNITO-AUDIT-TRAIL finding should fire when the app never set cognitoUserPoolId");
    }

    @Test
    void cognitoWithoutCloudTrailFailsWhenAFrameworkRequiresAuditLogging() {
        TestInfrastructureBuilder builder = builderFor("TestCognitoNoTrailPci", SecurityProfile.PRODUCTION, "PCI-DSS");
        builder.createMinimalInfrastructure();
        SystemContext ctx = builder.getSystemContext();
        ctx.cognitoUserPoolId.set("us-east-1_testpool");
        // ctx.cloudTrail intentionally left unset -- simulates CloudTrail not having attached.

        new IamSecurityRules().install(ctx);

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "Cognito in use without CloudTrail should block synthesis when PCI-DSS requires AUDIT_LOGGING");
    }

    @Test
    void cognitoWithoutCloudTrailDoesNotBlockWhenNoFrameworkIsSelected() {
        TestInfrastructureBuilder builder = builderFor("TestCognitoNoTrailNoFramework", SecurityProfile.PRODUCTION, null);
        builder.createMinimalInfrastructure();
        SystemContext ctx = builder.getSystemContext();
        ctx.cognitoUserPoolId.set("us-east-1_testpool");

        new IamSecurityRules().install(ctx);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "with no compliance framework selected, nothing requires AUDIT_LOGGING, so this is advisory at most");
    }

    @Test
    void cognitoUserPoolObjectAloneAlsoTriggersTheCheck() {
        // cognitoUserPool (the CDK IUserPool slot) is checked in addition to cognitoUserPoolId --
        // either one present means "this app uses Cognito."
        TestInfrastructureBuilder builder = builderFor("TestCognitoPoolObject", SecurityProfile.PRODUCTION, "HIPAA");
        builder.createMinimalInfrastructure();
        SystemContext ctx = builder.getSystemContext();
        IUserPool pool = UserPool.Builder.create(builder.getStack(), "TestPool").build();
        ctx.cognitoUserPool.set(pool);

        new IamSecurityRules().install(ctx);

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()),
            "cognitoUserPool presence alone (no cognitoUserPoolId) should still trigger the check");
    }

    @Test
    void devProfileNeverRunsTheCheckAtAll() {
        // validateAuthentication() as a whole only runs for PRODUCTION/STAGING -- DEV returns
        // immediately, so COGNITO-AUDIT-TRAIL (like every other check in that method) never fires.
        TestInfrastructureBuilder builder = builderFor("TestDevSkipped", SecurityProfile.DEV, "PCI-DSS");
        builder.createMinimalInfrastructure();
        SystemContext ctx = builder.getSystemContext();
        ctx.cognitoUserPoolId.set("us-east-1_testpool");

        new IamSecurityRules().install(ctx);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()),
            "DEV profile should never block on COGNITO-AUDIT-TRAIL regardless of CloudTrail state");
    }
}
