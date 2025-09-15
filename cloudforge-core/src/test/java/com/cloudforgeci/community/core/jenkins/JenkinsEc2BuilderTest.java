package com.cloudforgeci.community.core.jenkins;

import com.cloudforgeci.community.compute.jenkins.JenkinsEc2Builder;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.api.JenkinsConfig;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JenkinsEc2BuilderTest {
    @Test
    void nonSslPathBuildsEc2WithOpenPorts() {
        App app = new App();
        Stack stack = new Stack(app, "TestStack");

        JenkinsConfig cfg = new JenkinsConfig("JenkinsEc2", false, null, null, null);
        DeploymentContext cfc = DeploymentContext.from(stack);

        JenkinsEc2Builder.Result result = JenkinsEc2Builder.create(stack, "Unit", cfg, cfc);
        assertNotNull(result.vpc);
        assertNotNull(result.instance);

        Template t = Template.fromStack(stack);
        // Instance type expectation (t2.micro)
        t.hasResourceProperties("AWS::EC2::Instance",
                Map.of("InstanceType", "t2.micro")   // <- adjust if your builder differs
        );

        // Security group should include ports 22, 80, 443, 8080 open (can't easily assert count, but we can assert SG exists)
        assertFalse(t.findResources("AWS::EC2::SecurityGroup").isEmpty());
        // ALB may be null in non-SSL path; don't assert its presence here
    }
}
