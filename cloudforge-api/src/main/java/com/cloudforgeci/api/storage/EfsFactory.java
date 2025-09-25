package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.interfaces.SecurityProfile;

import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.efs.*;
import software.constructs.Construct;

public class EfsFactory extends BaseFactory {

  @com.cloudforgeci.api.core.annotation.SystemContext
  private SystemContext ctx;

  public EfsFactory(Construct scope, String id) {
    super(scope, id);
  }

  @Override
  public void create() {
    // Create security group
    SecurityGroup efsSg = createSecurityGroup();
    ctx.efsSg.set(efsSg);

    // Create EFS file system
    FileSystem fs = createFileSystem(efsSg);
    ctx.efs.set(fs);
  }

  private SecurityGroup createSecurityGroup() {
    return SecurityGroup.Builder.create(this, getNode().getId() + "EfsSg")
            .vpc(ctx.vpc.get().orElseThrow())
            .description("EFS Security Group")
            .allowAllOutbound(true)
            .build();
  }

  private FileSystem createFileSystem(SecurityGroup efsSg) {
    return FileSystem.Builder.create(this, "Efs")
            .securityGroup(efsSg)
            .vpc(ctx.vpc.get().orElseThrow())
            .encrypted(true)
            .performanceMode(PerformanceMode.GENERAL_PURPOSE)
            .throughputMode(ThroughputMode.BURSTING)
            .build();
  }
}
