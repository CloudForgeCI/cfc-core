package com.cloudforgeci.samples.launchers;

import com.cloudforgeci.community.compute.jenkins.Jenkins;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class JenkinsFargateStack extends Stack {
    public JenkinsFargateStack(final Construct scope, final String id) { 
        this(scope, id, null, SecurityProfile.DEV, IAMProfile.EXTENDED); 
    }
    
    public JenkinsFargateStack(final Construct scope, final String id, final StackProps props) { 
        this(scope, id, props, SecurityProfile.DEV, IAMProfile.EXTENDED); 
    }
    
    public JenkinsFargateStack(final Construct scope, final String id, final StackProps props, 
                              final SecurityProfile security, final IAMProfile iamProfile) {
        super(scope, id, props);
        System.out.println("JenkinsFargateStack constructor called with id: " + id);
        var cfc = DeploymentContext.from(scope);
        System.out.println("JenkinsFargateStack: Domain: " + cfc.domain() + ", Subdomain: " + cfc.subdomain());
        try {
            System.out.println("JenkinsFargateStack: About to call Jenkins.fargate()");
            Jenkins.fargate(this, id, cfc, security, iamProfile);
            System.out.println("JenkinsFargateStack: Jenkins.fargate() completed successfully");
        } catch (Exception e) {
            System.out.println("JenkinsFargateStack: Exception in Jenkins.fargate(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
