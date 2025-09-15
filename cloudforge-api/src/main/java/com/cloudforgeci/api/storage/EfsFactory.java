package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.efs.*;
import software.constructs.Construct;


public class EfsFactory extends Construct {

  private final Props p;

  public record Props(DeploymentContext cfc) { }

  public EfsFactory(Construct scope, String id, Props props) {
    super(scope, id);
    this.p = props;
    SystemContext ctx = SystemContext.of(this);

    SecurityGroup efsSg = SecurityGroup.Builder.create(this, id + "EfsSg")
            .vpc(ctx.vpc.get().orElseThrow()).description("EFS SG").allowAllOutbound(true).build();
    ctx.efsSg.set(efsSg);
    FileSystem fs = FileSystem.Builder.create(this, "Efs").securityGroup(efsSg).vpc(ctx.vpc.get().orElseThrow()).encrypted(true).build();
    ctx.efs.set(fs);

  }

}
