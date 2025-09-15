package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;

public final class Rules {
  private Rules() {}
  public static void installAll(SystemContext ctx) {
    RuntimeRules.install(ctx);
    TopologyRules.install(ctx);
  }
}