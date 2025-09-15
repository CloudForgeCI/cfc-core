package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.RuntimeConfiguration;
import com.cloudforgeci.api.core.runtime.Ec2RuntimeConfiguration;
import com.cloudforgeci.api.core.runtime.FargateRuntimeConfiguration;
import software.constructs.Node;

import java.util.ArrayList;
import java.util.List;


public final class RuntimeRules {
  private RuntimeRules() {}

  public static void install(SystemContext ctx) {
    final RuntimeConfiguration p = switch (ctx.runtime) {
      case EC2     -> new Ec2RuntimeConfiguration();
      case FARGATE -> new FargateRuntimeConfiguration();
    };

    Node.of(ctx).addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    ctx.once("ProfileWiring:Runtime:" + p.kind(), () -> p.wire(ctx));
  }
}