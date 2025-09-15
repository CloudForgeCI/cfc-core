package com.cloudforgeci.samples.launchers;


import com.cloudforgeci.community.compute.jenkins.Jenkins;
import com.cloudforgeci.api.core.DeploymentContext;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;


public class JenkinsFargateStack extends Stack {
    public JenkinsFargateStack(final Construct scope, final String id) { this(scope, id, null); }
    public JenkinsFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        var cfc = DeploymentContext.from(scope);
        Jenkins.fargate(this, id, cfc);
    }

}
