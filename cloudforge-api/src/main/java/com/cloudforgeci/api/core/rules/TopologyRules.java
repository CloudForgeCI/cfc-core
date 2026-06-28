package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.topology.ApplicationServiceTopologyConfiguration;
import com.cloudforgeci.api.core.topology.CmsServiceTopologyConfiguration;
import com.cloudforgeci.api.core.topology.JenkinsServiceTopologyConfiguration;
import com.cloudforgeci.api.core.topology.S3WebsiteTopologyConfiguration;
import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.constructs.Node;

import java.util.ArrayList;
import java.util.List;

public final class TopologyRules {

  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(TopologyRules.class.getName());

  public static void install(SystemContext ctx) {
    LOG.fine("=== TopologyRules.install() called for topology: " + ctx.topology + " ===");
    final TopologyConfiguration p = switch (ctx.topology) {
      case JENKINS_SERVICE     -> new JenkinsServiceTopologyConfiguration();
      case S3_WEBSITE          -> new S3WebsiteTopologyConfiguration();
      case APPLICATION_SERVICE -> new ApplicationServiceTopologyConfiguration();
      case CMS_SERVICE         -> new CmsServiceTopologyConfiguration();
    };
    LOG.fine("Topology configuration created: " + p.getClass().getSimpleName());

    // Install ApplicationSpec for JENKINS_SERVICE topology (hardcoded for backward compatibility)
    if (ctx.topology == com.cloudforge.core.enums.TopologyType.JENKINS_SERVICE) {
      ApplicationSpec appSpec = new JenkinsApplicationSpec();
      ctx.applicationSpec.set(appSpec);
    }

    // For CMS_SERVICE topology, resolve the CmsSpec from the deployment context and set it
    // as the applicationSpec so other factories (FargateFactory, etc.) can read it.
    if (ctx.topology == com.cloudforge.core.enums.TopologyType.CMS_SERVICE) {
      ctx.cfc.cmsSpec().ifPresent(ctx.applicationSpec::set);
    }

    // For APPLICATION_SERVICE topology, applicationSpec must be provided by caller
    // It will be set in ApplicationFactory before calling createInfrastructureFactories()

    Node.of(ctx).addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    LOG.fine("Registering topology wire() callback with ctx.once() for: " + p.kind());
    ctx.once("ProfileWiring:Topology:" + p.kind(), () -> {
      LOG.fine("=== EXECUTING topology.wire() for " + p.kind() + " ===");
      p.wire(ctx);
      LOG.fine("=== COMPLETED topology.wire() for " + p.kind() + " ===");
    });
    LOG.fine("TopologyRules.install() completed");
  }
}
