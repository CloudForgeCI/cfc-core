package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.rules.AwsConfigRule;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.interfaces.ApplicationSpec;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
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
 *   <li>Creating Access Points with application-specific ownership and permissions</li>
 * </ul>
 *
 * <p><strong>Compliance Coverage:</strong></p>
 * <ul>
 *   <li>SOC2-C1.1-EFS: Encryption at rest for confidentiality</li>
 *   <li>HIPAA §164.312(a)(2)(iv): Encryption of ePHI at rest</li>
 *   <li>PCI-DSS Req 3.4: Protect stored cardholder data</li>
 *   <li>GDPR Art. 32: Security of processing (encryption)</li>
 * </ul>
 *
 * <p>CloudForge 3.0.0: Uses ApplicationSpec to determine Access Point configuration</p>
 */
public class EfsFactory extends BaseFactory {

  private static final Logger LOG = Logger.getLogger(EfsFactory.class.getName());

  @DeploymentContext("existingFileSystemId")
  private String existingFileSystemId;

  @DeploymentContext("retainStorage")
  private Boolean retainStorage;

  @SystemContext("vpc")
  private Vpc vpc;

  @SystemContext("applicationSpec")
  private ApplicationSpec applicationSpec;

  @DeploymentContext("networkMode")
  private NetworkMode networkMode;

  public EfsFactory(Construct scope, String id) {
    super(scope, id);
    // Fields are automatically injected by BaseFactory via @SystemContext and @DeploymentContext annotations
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

    // Create Access Point for application-specific access
    AccessPoint ap = createAccessPoint(fs);
    ctx.ap.set(ap);
  }

  private SecurityGroup createSecurityGroup() {
    // Check if egress should be restricted to VPC CIDR only (only for private subnets)
    boolean restrictEgress = config.isRestrictSecurityGroupEgressEnabled()
        && networkMode != NetworkMode.PUBLIC;

    SecurityGroup sg = SecurityGroup.Builder.create(this, getNode().getId() + "EfsSg")
            .vpc(vpc)
            .description("EFS Security Group")
            .allowAllOutbound(!restrictEgress)
            .build();

    // If egress is restricted, add explicit egress rule for VPC CIDR only
    if (restrictEgress) {
      sg.addEgressRule(
          Peer.ipv4(vpc.getVpcCidrBlock()),
          Port.allTraffic(),
          "Allow egress to VPC CIDR only"
      );
    }

    return sg;
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

    // Register AWS Config rule for EFS encryption compliance
    ctx.requireConfigRule(AwsConfigRule.EFS_ENCRYPTED);

    return FileSystem.Builder.create(this, "Efs")
            .securityGroup(efsSg)
            .vpc(vpc)
            .encrypted(true)
            .performanceMode(PerformanceMode.GENERAL_PURPOSE)
            .throughputMode(ThroughputMode.BURSTING)
            .removalPolicy(removalPolicy)
            .build();
  }

  private AccessPoint createAccessPoint(FileSystem fs) {
    // ApplicationSpec is injected via @SystemContext annotation
    if (applicationSpec == null) {
      throw new IllegalStateException("ApplicationSpec not available - required for EFS Access Point creation");
    }

    String efsPath = applicationSpec.efsDataPath();

    // Some applications (like GitLab) need to run as root - use default 0:0 for those
    String containerUser = applicationSpec.containerUser();
    String uid = "0";  // default to root
    String gid = "0";  // default to root

    if (containerUser != null && !containerUser.isBlank()) {
      String[] userParts = containerUser.split(":");
      uid = userParts[0];
      gid = userParts[1];
    }

    String permissions = applicationSpec.efsPermissions();

    LOG.info("Creating EFS Access Point:");
    LOG.info("  Path: " + efsPath);
    LOG.info("  Owner: " + uid + ":" + gid + (containerUser == null ? " (root - application requires root access)" : ""));
    LOG.info("  Permissions: " + permissions);

    return AccessPoint.Builder.create(this, "AccessPoint")
            .fileSystem(fs)
            .path(efsPath)
            .createAcl(Acl.builder()
                    .ownerUid(uid)
                    .ownerGid(gid)
                    .permissions(permissions)
                    .build())
            .posixUser(PosixUser.builder()
                    .uid(uid)
                    .gid(gid)
                    .build())
            .build();
  }
}
