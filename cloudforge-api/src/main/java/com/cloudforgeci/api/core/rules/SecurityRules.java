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

import io.github.cdklabs.cdknag.AwsSolutionsChecks;
import io.github.cdklabs.cdknag.HIPAASecurityChecks;
import io.github.cdklabs.cdknag.NagReportFormat;
import io.github.cdklabs.cdknag.PCIDSS321Checks;
import io.github.cdklabs.cdknag.NagPack;
import software.amazon.awscdk.Aspects;

import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;

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

    // Get enabled compliance frameworks (comma-separated list)
    String frameworksConfig = ctx.cfc.complianceFrameworks();
    if (frameworksConfig == null || frameworksConfig.trim().isEmpty()) {
      LOG.info("No compliance frameworks specified - skipping all compliance validation");
      return;
    }

    // Parse enabled frameworks into a set for fast lookup
    Set<String> enabledFrameworks = Arrays.stream(frameworksConfig.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .collect(java.util.stream.Collectors.toSet());

    // CDK-nag validation only runs for PRODUCTION
    if (ctx.security == SecurityProfile.PRODUCTION) {
      applyCdkNagValidation(ctx, enabledFrameworks);
    }

    // CloudForge FrameworkRules validation requires auditManagerEnabled
    if (!Boolean.TRUE.equals(ctx.cfc.auditManagerEnabled())) {
      LOG.info("Skipping CloudForge FrameworkRules validation (auditManagerEnabled = false)");
      return;
    }

    LOG.info("Installing CloudForge FrameworkRules validation for: " + frameworksConfig);

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

    LOG.info("Successfully installed " + installedCount + " CloudForge FrameworkRules validators");
  }

  /**
   * Auto-apply cdk-nag validation packs based on enabled compliance frameworks.
   *
   * <p>Maps framework names to appropriate cdk-nag rule packs:</p>
   * <ul>
   *   <li><strong>HIPAA:</strong> HIPAASecurityChecks</li>
   *   <li><strong>PCI-DSS:</strong> PCIDSS321Checks</li>
   *   <li><strong>SOC2:</strong> AwsSolutionsChecks</li>
   *   <li><strong>Custom:</strong> AwsSolutionsChecks (fallback)</li>
   * </ul>
   *
   * <p>The enforcement mode is determined by {@code complianceMode}:</p>
   * <ul>
   *   <li><strong>enforce:</strong> Blocks synthesis on violations</li>
   *   <li><strong>advisory:</strong> Logs warnings only</li>
   * </ul>
   *
   * @param ctx the system context containing deployment configuration
   * @param enabledFrameworks set of enabled framework IDs (uppercase)
   * @since 3.1.0
   */
  private static void applyCdkNagValidation(SystemContext ctx, Set<String> enabledFrameworks) {
    if (enabledFrameworks.isEmpty()) {
      LOG.info("No frameworks enabled - skipping cdk-nag validation");
      return;
    }

    boolean enforce = ctx.cfc.complianceMode() == ComplianceMode.ENFORCE;
    LOG.info("Applying cdk-nag validation (mode=" + (enforce ? "enforce" : "advisory") + ")");

    int appliedCount = 0;
    for (String framework : enabledFrameworks) {
      NagPack pack = mapFrameworkToNagPack(framework, enforce);
      if (pack != null) {
        Aspects.of(ctx.getNode().getRoot()).add(pack);
        appliedCount++;
        LOG.info("  ✓ Applied cdk-nag pack for " + framework);
      }
    }

    LOG.info("Applied " + appliedCount + " cdk-nag validation packs");
  }

  /**
   * Maps a compliance framework to its corresponding cdk-nag rule pack.
   *
   * @param framework the framework ID (uppercase)
   * @param enforce true to enforce violations, false to log only
   * @return the corresponding NagPack, or null if no mapping exists
   * @since 3.1.0
   */
  private static NagPack mapFrameworkToNagPack(String framework, boolean enforce) {
    // Report formats for compliance auditing
    var reportFormats = List.of(NagReportFormat.JSON, NagReportFormat.CSV);

    return switch (framework) {
      case "HIPAA" -> HIPAASecurityChecks.Builder.create()
          .logIgnores(!enforce)
          .reports(true)
          .reportFormats(reportFormats)
          .build();
      case "PCI-DSS" -> PCIDSS321Checks.Builder.create()
          .logIgnores(!enforce)
          .reports(true)
          .reportFormats(reportFormats)
          .build();
      case "SOC2" -> AwsSolutionsChecks.Builder.create()
          .logIgnores(!enforce)
          .reports(true)
          .reportFormats(reportFormats)
          .build();
      // FEDRAMP: Handled by existing FedRampRules.java plugin only
      // Not integrated with cdk-nag to avoid conflicts (future epic)
      case "FEDRAMP", "FEDRAMPHIGH" -> {
        LOG.info("  - Skipping cdk-nag for " + framework + " (uses existing FedRampRules.java)");
        yield null;
      }
      // Custom frameworks: fallback to AWS Solutions best practices
      default -> {
        LOG.info("  - Applying AwsSolutionsChecks (fallback) for custom framework: " + framework);
        yield AwsSolutionsChecks.Builder.create()
            .logIgnores(!enforce)
            .reports(true)
            .reportFormats(reportFormats)
            .build();
      }
    };
  }
}
