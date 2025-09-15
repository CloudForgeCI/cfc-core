package com.cloudforgeci.samples.launchers;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.community.compute.jenkins.Jenkins;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class JenkinsEc2Stack extends Stack {
    public JenkinsEc2Stack(final Construct scope, final String id) { this(scope, id, null); }
    public JenkinsEc2Stack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        DeploymentContext cfc = DeploymentContext.from(scope);
        Jenkins.ec2(this, id, cfc);
    }

}
