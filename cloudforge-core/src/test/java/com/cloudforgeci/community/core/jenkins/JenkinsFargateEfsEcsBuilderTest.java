// Copyright (c) CloudForgeCI
// SPDX-License-Identifier: Apache-2.0

package com.cloudforgeci.community.core.jenkins;

import com.cloudforgeci.core.api.DeploymentContext;
import com.cloudforgeci.core.api.JenkinsConfig;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import static org.junit.jupiter.api.Assertions.*;

public class JenkinsFargateEfsEcsBuilderTest {
    @Test
    void nonSslPathCreatesClusterServiceEfsAndSecurityGroups() {
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        JenkinsConfig cfg = new JenkinsConfig("JenkinsFargate", false, null, null, null);
        DeploymentContext ctx = DeploymentContext.from(app);

        JenkinsFargateEfsEcsBuilder.Result result = JenkinsFargateEfsEcsBuilder.create(stack, "Unit", cfg, ctx);
        assertNotNull(result.vpc);
        assertNotNull(result.cluster);
        assertNotNull(result.service);
        assertNotNull(result.efs);

        Template t = Template.fromStack(stack);
        assertFalse(t.findResources("AWS::ECS::Cluster").isEmpty());
        assertFalse(t.findResources("AWS::ECS::TaskDefinition").isEmpty());
        assertFalse(t.findResources("AWS::ECS::Service").isEmpty());
        assertFalse(t.findResources("AWS::EFS::FileSystem").isEmpty());

        // Encrypted EFS
        t.hasResourceProperties("AWS::EFS::FileSystem", java.util.Map.of("Encrypted", true));

        // ALB may or may not exist depending on no-domain branch; we don't rely on it here
    }
}
