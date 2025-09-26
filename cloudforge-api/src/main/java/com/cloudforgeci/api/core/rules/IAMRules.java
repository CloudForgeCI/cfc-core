package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.iam.ExtendedIAMConfiguration;
import com.cloudforgeci.api.core.iam.MinimalIAMConfiguration;
import com.cloudforgeci.api.core.iam.StandardIAMConfiguration;
import com.cloudforgeci.api.interfaces.IAMConfiguration;
import com.cloudforgeci.api.interfaces.Rule;

import java.util.ArrayList;
import java.util.List;

public final class IAMRules {
  private IAMRules() {}

  public static void install(SystemContext ctx) {
    final IAMConfiguration p = switch (ctx.iamProfile) {
      case MINIMAL  -> new MinimalIAMConfiguration();
      case STANDARD -> new StandardIAMConfiguration();
      case EXTENDED -> new ExtendedIAMConfiguration();
    };

    ctx.getNode().addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    // Create IAM roles immediately instead of deferring - runtime factories need them
    p.wire(ctx);
  }
}
