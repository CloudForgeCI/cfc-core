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
import com.cloudforge.core.interfaces.FrameworkRules;

import java.util.*;
import java.util.logging.Logger;

/**
 * Security rules installation and compliance framework orchestration.
 *
 * <p>This class coordinates security profile configuration and compliance framework
 * validation. Starting in v3.0.0, it uses auto-discovery to load compliance frameworks,
 * enabling plugin-based extensibility.</p>
 *
 * <h2>Version History:</h2>
 * <ul>
 *   <li><strong>v3.0.0:</strong> Hardcoded framework loading</li>
 *   <li><strong>v3.0.0:</strong> Auto-discovery via {@link FrameworkLoader} with backward compatibility</li>
 *   <li><strong>v4.0.0 (future):</strong> Pure plugin-based architecture</li>
 * </ul>
 *
 * @since 3.0.0
 */
public final class SecurityRules {
  private static final Logger LOG = Logger.getLogger(SecurityRules.class.getName());


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
    String frameworksConfig = ctx.cfc.complianceFrameworks();
    if (frameworksConfig == null || frameworksConfig.trim().isEmpty()) {
      LOG.info("No compliance frameworks specified - skipping validation");
      return;
    }

    LOG.info("Installing compliance validation for: " + frameworksConfig);

    // Parse enabled frameworks into a set for fast lookup
    Set<String> enabledFrameworks = Arrays.stream(frameworksConfig.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .collect(java.util.stream.Collectors.toSet());

    // Discover all available compliance frameworks (v3.0.0 plugin architecture)
    List<FrameworkRules<SystemContext>> allFrameworks = FrameworkLoader.discover();

    LOG.info("Discovered " + allFrameworks.size() + " compliance frameworks");

    // Install frameworks in priority order
    int installedCount = 0;
    for (FrameworkRules<SystemContext> framework : allFrameworks) {
      boolean shouldInstall = framework.alwaysLoad() ||
                             enabledFrameworks.contains(framework.frameworkId().toUpperCase());

      if (shouldInstall) {
        try {
          framework.install(ctx);
          installedCount++;
          LOG.info("  ✓ " + framework.displayName() + " (priority=" + framework.priority() + ")");
        } catch (Exception e) {
          LOG.severe("  ✗ Failed to install " + framework.frameworkId() + ": " + e.getMessage());
          throw new RuntimeException("Failed to install compliance framework: " + framework.frameworkId(), e);
        }
      } else {
        LOG.fine("  - Skipping " + framework.frameworkId() + " (not enabled)");
      }
    }

    LOG.info("Successfully installed " + installedCount + " compliance framework validators");
  }
}
