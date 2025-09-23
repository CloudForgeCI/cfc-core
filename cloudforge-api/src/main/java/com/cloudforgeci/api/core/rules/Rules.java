package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import java.util.logging.Logger;

public final class Rules {
  private static final Logger LOG = Logger.getLogger(Rules.class.getName());
  
  private Rules() {}
  
  public static void installAll(SystemContext ctx) {
    LOG.info("*** Rules.installAll() called ***");
    System.out.println("*** DEBUG: Rules.installAll() called ***");
    
    LOG.info("*** Installing RuntimeRules ***");
    System.out.println("*** DEBUG: Installing RuntimeRules ***");
    RuntimeRules.install(ctx);
    
    LOG.info("*** Installing TopologyRules ***");
    System.out.println("*** DEBUG: Installing TopologyRules ***");
    TopologyRules.install(ctx);
    
    LOG.info("*** Installing SecurityRules ***");
    System.out.println("*** DEBUG: Installing SecurityRules ***");
    SecurityRules.install(ctx);
    
    LOG.info("*** Installing IAMRules ***");
    System.out.println("*** DEBUG: Installing IAMRules ***");
    IAMRules.install(ctx);
    
    LOG.info("*** All rules installed successfully ***");
    System.out.println("*** DEBUG: All rules installed successfully ***");
  }
}