package com.cloudforgeci.api.network;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;

import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class VpcFactory extends Construct {

  private final Props p;

  public record Props(DeploymentContext cfc) { }

  public VpcFactory(Construct scope, String id, Props props) {
    super(scope, id);
    this.p = props;
    SystemContext ctx = SystemContext.of(this);

    Vpc vpc = Vpc.Builder.create(this, "Vpc")
            .maxAzs(2)
            .vpcName(id + "Vpc")
            .natGateways(0).subnetConfiguration(List.of(SubnetConfiguration.builder().name("public").subnetType(SubnetType.PUBLIC).build())).build();
    if(ctx.flowlogs.get().isPresent())
      vpc.addFlowLog("VpcFlowlog", ctx.flowlogs.get().orElseThrow());

    ctx.vpc.set(vpc);
  }


}
