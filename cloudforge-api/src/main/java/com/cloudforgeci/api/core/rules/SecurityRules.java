package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.security.DevSecurityConfiguration;
import com.cloudforgeci.api.core.security.ProductionSecurityConfiguration;
import com.cloudforgeci.api.core.security.StagingSecurityConfiguration;
import com.cloudforgeci.api.core.security.DevSecurityProfileConfiguration;
import com.cloudforgeci.api.core.security.StagingSecurityProfileConfiguration;
import com.cloudforgeci.api.core.security.ProductionSecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.SecurityConfiguration;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforgeci.api.interfaces.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SecurityRules {
  private static final Logger LOG = Logger.getLogger(SecurityRules.class.getName());
  
  private SecurityRules() {}

  public static void install(SystemContext ctx) {
    System.out.println("*** DEBUG: SecurityRules.install() called ***");
    LOG.info("*** SecurityRules.install() called for security: " + ctx.security + " ***");
    
    // Create and set the SecurityProfileConfiguration in SystemContext
    final SecurityProfileConfiguration profileConfig = switch (ctx.security) {
      case DEV        -> new DevSecurityProfileConfiguration();
      case STAGING    -> new StagingSecurityProfileConfiguration();
      case PRODUCTION -> new ProductionSecurityProfileConfiguration();
    };
    
    ctx.securityProfileConfig.set(profileConfig);
    LOG.info("*** SecurityProfileConfiguration set in SystemContext: " + profileConfig.getClass().getSimpleName() + " ***");
    
    final SecurityConfiguration p = switch (ctx.security) {
      case DEV        -> new DevSecurityConfiguration();
      case STAGING    -> new StagingSecurityConfiguration();
      case PRODUCTION -> new ProductionSecurityConfiguration();
    };

    System.out.println("*** DEBUG: SecurityConfiguration created: " + p.getClass().getSimpleName() + " ***");
    LOG.info("*** SecurityConfiguration created: " + p.getClass().getSimpleName() + " ***");

    ctx.getNode().addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    System.out.println("*** DEBUG: About to call ctx.once() for SecurityProfile wiring ***");
    LOG.info("*** About to call ctx.once() for SecurityProfile wiring ***");
    ctx.once("ProfileWiring:Security:" + p.kind(), () -> {
      System.out.println("*** DEBUG: SecurityProfile wiring callback executed ***");
      LOG.info("*** SecurityProfile wiring callback executed ***");
      p.wire(ctx);
      LOG.info("*** SecurityProfile wiring completed successfully ***");
    });
    System.out.println("*** DEBUG: SecurityRules.install() completed ***");
    LOG.info("*** SecurityRules.install() completed ***");
  }
}
