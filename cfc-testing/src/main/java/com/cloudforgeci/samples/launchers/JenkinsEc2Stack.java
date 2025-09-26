package com.cloudforgeci.samples.launchers;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.community.compute.jenkins.Jenkins;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class JenkinsEc2Stack extends Stack {
    public JenkinsEc2Stack(final Construct scope, final String id) { 
        this(scope, id, null, SecurityProfile.DEV, IAMProfile.EXTENDED); 
    }
    
    public JenkinsEc2Stack(final Construct scope, final String id, final StackProps props) { 
        this(scope, id, props, SecurityProfile.DEV, IAMProfile.EXTENDED); 
    }
    
    public JenkinsEc2Stack(final Construct scope, final String id, final StackProps props, 
                          final SecurityProfile security, final IAMProfile iamProfile) {
        super(scope, id, props);
        DeploymentContext cfc = DeploymentContext.from(scope);
        Jenkins.ec2(this, id, cfc, security, iamProfile);
    }
}
