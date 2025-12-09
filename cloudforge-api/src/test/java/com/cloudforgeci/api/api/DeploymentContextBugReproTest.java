package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This captures the current bug where from(App) reflects Map internals.
 * Enable after applying the PATCH-NOTES fix.
 */
public class DeploymentContextBugReproTest {
    @Disabled("Enable after fixing from(App)/from(Construct) to pass a Map directly")
    @Test
    void fromAppReadsContextKeys() {
        App app = new App();
        Stack stack = new Stack(app, "Stack");
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);
        SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE, SecurityProfile.DEV, iamProfile, null);
        app.getNode().setContext("cfc.lbType", "alb");
        DeploymentContext cfc = DeploymentContext.from(stack);
        assertEquals("alb", cfc.lbType());
    }
}
