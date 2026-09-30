package com.cloudforgeci.api.core.annotation;

import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.iam.IAMProfileMapper;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real getter that legitimately returns {@code null} (the field was never set) must not be
 * logged the same way as a genuinely missing getter -- otherwise every deploy logs "Failed to
 * inject" for every unset optional field, drowning out real {@code @DeploymentContext} gaps.
 */
class ContextInjectorTest {

    /** Has a real forwarding getter on DeploymentContext (albAccessLogging()) but no value set. */
    static class HasRealGetterButUnset {
        @DeploymentContext("albAccessLogging")
        Boolean albAccessLogging;
    }

    /** No forwarding getter exists on DeploymentContext for this property name. */
    static class HasNoGetterAtAll {
        @DeploymentContext("thisPropertyDoesNotExistAnywhere")
        String thisPropertyDoesNotExistAnywhere;
    }

    private com.cloudforgeci.api.core.DeploymentContext deploymentContext;
    private com.cloudforgeci.api.core.SystemContext systemContext;
    private ByteArrayOutputStream capturedErr;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        App app = new App();
        Stack stack = new Stack(app, "Test");
        deploymentContext = com.cloudforgeci.api.core.DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        systemContext = com.cloudforgeci.api.core.SystemContext.start(
            stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, deploymentContext);

        originalErr = System.err;
        capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    @Test
    void aLegitimatelyUnsetFieldWithARealGetterInjectsNullWithoutLogging() {
        HasRealGetterButUnset target = new HasRealGetterButUnset();
        ContextInjector.inject(target, systemContext, deploymentContext);

        assertNull(target.albAccessLogging);
        assertFalse(capturedErr.toString().contains("Failed to inject"),
            "a real getter returning null should not be logged as a missing getter: " + capturedErr);
    }

    @Test
    void aPropertyWithNoGetterAtAllIsStillLoggedAsAGap() {
        HasNoGetterAtAll target = new HasNoGetterAtAll();
        ContextInjector.inject(target, systemContext, deploymentContext);

        assertNull(target.thisPropertyDoesNotExistAnywhere);
        assertTrue(capturedErr.toString().contains("Failed to inject field thisPropertyDoesNotExistAnywhere"),
            "a property with no matching field or getter anywhere must still be logged: " + capturedErr);
    }
}
