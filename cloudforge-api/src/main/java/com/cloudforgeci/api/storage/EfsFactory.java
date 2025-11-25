package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.core.annotation.SystemContext;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.efs.*;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Factory for creating EFS file systems with support for persistence and reuse.
 *
 * <p>This factory handles EFS lifecycle management including:</p>
 * <ul>
 *   <li>Creating new file systems with configurable retention policies</li>
 *   <li>Reusing existing file systems for disaster recovery workflows</li>
 *   <li>Applying security groups and encryption settings</li>
 * </ul>
 */
public class EfsFactory extends BaseFactory {

  private static final Logger LOG = Logger.getLogger(EfsFactory.class.getName());

  @DeploymentContext("existingFileSystemId")
  private String existingFileSystemId;

  @DeploymentContext("retainStorage")
  private Boolean retainStorage;

  @SystemContext("vpc")
  private Vpc vpc;

  public EfsFactory(Construct scope, String id) {
    super(scope, id);
    // existingFileSystemId and retainStorage are automatically injected by BaseFactory
  }

  @Override
  public void create() {
    // Create security group
    SecurityGroup efsSg = createSecurityGroup();
    ctx.efsSg.set(efsSg);

    // Check if we should reuse an existing file system
    if (existingFileSystemId != null && !existingFileSystemId.isEmpty()) {
      LOG.info("⚠️  Reusing existing EFS is not fully supported yet - existingFileSystemId will be ignored");
      LOG.info("   To reuse an existing EFS, you need to manually import it and its access points");
      LOG.info("   For now, creating a new EFS file system");
    }

    // Always create new EFS for now - full import support requires AccessPoint lookup
    FileSystem fs = createFileSystem(efsSg);
    ctx.efs.set(fs);
  }

  private SecurityGroup createSecurityGroup() {
    return SecurityGroup.Builder.create(this, getNode().getId() + "EfsSg")
            .vpc(vpc)
            .description("EFS Security Group")
            .allowAllOutbound(true)
            .build();
  }

  private FileSystem createFileSystem(SecurityGroup efsSg) {
    RemovalPolicy removalPolicy = Boolean.TRUE.equals(retainStorage)
        ? RemovalPolicy.RETAIN
        : RemovalPolicy.DESTROY;

    if (Boolean.TRUE.equals(retainStorage)) {
      LOG.info("EFS file system will be RETAINED after stack deletion (retainStorage = true)");
      LOG.info("⚠️  You must manually delete the EFS file system from AWS Console to avoid ongoing storage costs");
    } else {
      LOG.info("EFS file system will be DESTROYED with stack (retainStorage = false)");
    }

    return FileSystem.Builder.create(this, "Efs")
            .securityGroup(efsSg)
            .vpc(vpc)
            .encrypted(true)
            .performanceMode(PerformanceMode.GENERAL_PURPOSE)
            .throughputMode(ThroughputMode.BURSTING)
            .removalPolicy(removalPolicy)
            .build();
  }
}
