package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.core.runtime.Ec2RuntimeConfiguration;
import com.cloudforgeci.api.core.runtime.FargateRuntimeConfiguration;
import software.constructs.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public final class RuntimeRules {
    private static final Logger LOG = Logger.getLogger(RuntimeRules.class.getName());
  
  private RuntimeRules() {}

  public static void install(SystemContext ctx) {
    
    try {
      
      final RuntimeConfiguration p = switch (ctx.runtime) {
        case EC2     -> new Ec2RuntimeConfiguration();
        case FARGATE -> new FargateRuntimeConfiguration();
      };
      

    Node.of(ctx).addValidation(() -> {
        List<String> errs = new ArrayList<>();
        for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
        if (!errs.isEmpty()) {
        }
        return errs;
      });

      // Call wire() using ctx.once() to ensure it runs after all factories are created
      ctx.once("ProfileWiring:Runtime:" + ctx.runtime, () -> {
        p.wire(ctx);
      });
      
    } catch (Exception e) {
      throw e;
    }
  }
}