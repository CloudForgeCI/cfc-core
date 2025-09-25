package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import java.util.logging.Logger;

public final class Rules {
  private static final Logger LOG = Logger.getLogger(Rules.class.getName());
  
  private Rules() {}
  
  public static void installAll(SystemContext ctx) {
    LOG.info("Installing all rules");
    // Install IAM rules first so roles are available for runtime factories
    IAMRules.install(ctx);
    RuntimeRules.install(ctx);
    TopologyRules.install(ctx);
    SecurityRules.install(ctx);
    LOG.info("All rules installed successfully");
  }
}