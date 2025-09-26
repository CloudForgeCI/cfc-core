package com.cloudforgeci.community.compute.jenkins;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.compute.JenkinsFactory;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.IAMProfile;

import software.constructs.Construct;

import java.util.logging.Logger;

public final class Jenkins {
  private Jenkins() {}
  
  private static final Logger LOG = Logger.getLogger(Jenkins.class.getName());

  public static JenkinsFactory.JenkinsSystem ec2(Construct scope, String id, DeploymentContext cfc) {
    //ContextValidator.attach(scope, cfc);
    return JenkinsFactory.createEc2(scope, id, cfc);
  }

  public static JenkinsFactory.JenkinsSystem ec2(Construct scope, String id, DeploymentContext cfc, 
                                                 SecurityProfile security, IAMProfile iamProfile) {
    //ContextValidator.attach(scope, cfc);
    System.err.println("*** DEBUG: Jenkins.ec2 called with topology=" + cfc.topology() + " ***");
    System.out.println("DEBUG: Jenkins.ec2 called with topology=" + cfc.topology());
    // Debug: topology=" + cfc.topology() + ", security=" + security + ", iam=" + iamProfile
    return JenkinsFactory.createEc2(scope, id, cfc, security, iamProfile);
  }

  public static JenkinsFactory.JenkinsSystem fargate(Construct scope, String id, DeploymentContext cfc) {
    //ContextValidator.attach(scope, cfc);
    return JenkinsFactory.createFargate(scope, id, cfc);
  }

  public static JenkinsFactory.JenkinsSystem fargate(Construct scope, String id, DeploymentContext cfc, 
                                                    SecurityProfile security, IAMProfile iamProfile) {
    //ContextValidator.attach(scope, cfc);
    LOG.info("*** DEBUG: Jenkins.fargate called with topology=" + cfc.topology() + ", runtime=" + cfc.runtime() + " ***");
    return JenkinsFactory.createFargate(scope, id, cfc, security, iamProfile);
  }
}
