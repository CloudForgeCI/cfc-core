// Copyright (c) CloudForgeCI
// SPDX-License-Identifier: Apache-2.0

package com.cloudforgeci.core.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This captures the current bug where from(App) reflects Map internals.
 * Enable after applying the PATCH-NOTES fix.
 */
public class DeploymentContextBugReproTest {
    @Disabled("Enable after fixing from(App)/from(Construct) to pass a Map directly")
    @Test
    void fromAppReadsContextKeys() {
        App app = new App();
        app.getNode().setContext("lbType", "nlb");
        DeploymentContext ctx = DeploymentContext.from(app);
        assertEquals("nlb", ctx.lbType());
    }
}
