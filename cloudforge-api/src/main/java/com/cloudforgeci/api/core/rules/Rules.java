package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import java.util.logging.Logger;

public final class Rules {
  private static final Logger LOG = Logger.getLogger(Rules.class.getName());
  
  private Rules() {}
  
  public static void installAll(SystemContext ctx) {
    LOG.info("Installing all rules");
    RuntimeRules.install(ctx);
    TopologyRules.install(ctx);
    SecurityRules.install(ctx);
    IAMRules.install(ctx);
    LOG.info("All rules installed successfully");
  }
}