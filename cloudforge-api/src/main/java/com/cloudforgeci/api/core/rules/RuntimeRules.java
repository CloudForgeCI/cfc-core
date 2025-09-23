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
    LOG.info("*** RuntimeRules.install() called for runtime: " + ctx.runtime + " ***");
    
    try {
      LOG.info("*** Installing runtime rules for runtime: " + ctx.runtime + " ***");
      
      final RuntimeConfiguration p = switch (ctx.runtime) {
        case EC2     -> new Ec2RuntimeConfiguration();
        case FARGATE -> new FargateRuntimeConfiguration();
      };
      
      LOG.info("*** Runtime configuration created: " + p.getClass().getSimpleName() + " ***");

    Node.of(ctx).addValidation(() -> {
        List<String> errs = new ArrayList<>();
        for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
        if (!errs.isEmpty()) {
        }
        return errs;
      });

      // Call wire() using ctx.once() to ensure it runs after all factories are created
      LOG.info("*** Scheduling runtime wiring for: " + ctx.runtime + " ***");
      ctx.once("ProfileWiring:Runtime:" + ctx.runtime, () -> {
        LOG.info("*** Executing runtime wiring for: " + ctx.runtime + " ***");
        p.wire(ctx);
        LOG.info("*** Runtime wiring completed successfully for: " + ctx.runtime + " ***");
      });
      
    } catch (Exception e) {
      throw e;
    }
  }
}