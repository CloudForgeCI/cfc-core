package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.topology.JenkinsServiceTopologyConfiguration;
import com.cloudforgeci.api.core.topology.JenkinsSingleNodeTopologyConfiguration;
import com.cloudforgeci.api.core.topology.S3WebsiteTopologyConfiguration;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.TopologyConfiguration;
import software.constructs.Node;

import java.util.ArrayList;
import java.util.List;

public final class TopologyRules {
  private TopologyRules() {}

  public static void install(SystemContext ctx) {
    final TopologyConfiguration p = switch (ctx.topology) {
      case JENKINS_SINGLE_NODE -> new JenkinsSingleNodeTopologyConfiguration();
      case JENKINS_SERVICE     -> new JenkinsServiceTopologyConfiguration();
      case S3_WEBSITE          -> new S3WebsiteTopologyConfiguration();
    };

    Node.of(ctx).addValidation(() -> {
      List<String> errs = new ArrayList<>();
      for (Rule r : p.rules(ctx)) errs.addAll(r.check(ctx));
      return errs;
    });

    ctx.once("ProfileWiring:Topology:" + p.kind(), () -> p.wire(ctx));
  }
}