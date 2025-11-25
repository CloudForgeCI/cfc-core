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

    // Create and set the SecurityProfileConfiguration in SystemContext
    // Pass deployment context so security profiles can override defaults
    final SecurityProfileConfiguration profileConfig = switch (ctx.security) {
      case DEV        -> new DevSecurityProfileConfiguration(ctx.cfc);
      case STAGING    -> new StagingSecurityProfileConfiguration(ctx.cfc);
      case PRODUCTION -> new ProductionSecurityProfileConfiguration(ctx.cfc);
    };

    ctx.securityProfileConfig.set(profileConfig);

    final SecurityConfiguration p = switch (ctx.security) {
      case DEV        -> new DevSecurityConfiguration();
      case STAGING    -> new StagingSecurityConfiguration();
      case PRODUCTION -> new ProductionSecurityConfiguration();
    };


    ctx.getNode().addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    ctx.once("ProfileWiring:Security:" + p.kind(), () -> {
      p.wire(ctx);
    });

    // Install multi-framework compliance validation rules
    // Only run compliance validation if auditManagerEnabled is true
    if (!ctx.cfc.auditManagerEnabled()) {
      LOG.info("Skipping compliance validation (auditManagerEnabled = false)");
      return;
    }

    // Get enabled compliance frameworks (comma-separated list)
    String frameworks = ctx.cfc.complianceFrameworks();
    if (frameworks == null || frameworks.trim().isEmpty()) {
      LOG.info("No compliance frameworks specified - skipping validation");
      return;
    }

    LOG.info("Installing compliance validation for: " + frameworks);

    // Install framework-specific validators
    if (frameworks.contains("PCI-DSS")) {
      PciDssRules.install(ctx);
      LOG.info("  - PCI-DSS v3.2.1 validator enabled");
    }
    if (frameworks.contains("HIPAA")) {
      HipaaRules.install(ctx);
      HipaaOrganizationalRules.install(ctx);
      LOG.info("  - HIPAA Security Rule validator enabled");
    }
    if (frameworks.contains("SOC2")) {
      Soc2Rules.install(ctx);
      LOG.info("  - SOC 2 Trust Services Criteria validator enabled");
    }
    if (frameworks.contains("GDPR")) {
      GdprRules.install(ctx);
      GdprOrganizationalRules.install(ctx);
      LOG.info("  - GDPR Technical Safeguards validator enabled");
    }

    // Install cross-framework validators (apply to all frameworks)
    KeyManagementRules.install(ctx);
    LOG.info("  - Key Management validator enabled");

    AdvancedMonitoringRules.install(ctx);
    LOG.info("  - Advanced Monitoring validator enabled");

    IncidentResponseRules.install(ctx);
    LOG.info("  - Incident Response & DR validator enabled");

    ThreatProtectionRules.install(ctx);
    LOG.info("  - Threat Protection validator enabled");

    DatabaseSecurityRules.install(ctx);
    LOG.info("  - Database Security validator enabled");

    LOG.info("Multi-framework compliance validation enabled");
  }
}
