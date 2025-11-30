package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.topology.ApplicationServiceTopologyConfiguration;
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
    LOG.severe("=== TopologyRules.install() called for topology: " + ctx.topology + " ===");
    final TopologyConfiguration p = switch (ctx.topology) {
      case JENKINS_SERVICE     -> new JenkinsServiceTopologyConfiguration();
      case S3_WEBSITE          -> new S3WebsiteTopologyConfiguration();
      case APPLICATION_SERVICE -> new ApplicationServiceTopologyConfiguration();
    };
    LOG.severe("Topology configuration created: " + p.getClass().getSimpleName());

    // Install ApplicationSpec for JENKINS_SERVICE topology (hardcoded for backward compatibility)
    if (ctx.topology == com.cloudforge.core.enums.TopologyType.JENKINS_SERVICE) {
      ApplicationSpec appSpec = new JenkinsApplicationSpec();
      ctx.applicationSpec.set(appSpec);
    }

    // For APPLICATION_SERVICE topology, applicationSpec must be provided by caller
    // It will be set in ApplicationFactory before calling createInfrastructureFactories()

    Node.of(ctx).addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    LOG.severe("Registering topology wire() callback with ctx.once() for: " + p.kind());
    ctx.once("ProfileWiring:Topology:" + p.kind(), () -> {
      LOG.severe("=== EXECUTING topology.wire() for " + p.kind() + " ===");
      p.wire(ctx);
      LOG.severe("=== COMPLETED topology.wire() for " + p.kind() + " ===");
    });
    LOG.severe("TopologyRules.install() completed");
  }
}
