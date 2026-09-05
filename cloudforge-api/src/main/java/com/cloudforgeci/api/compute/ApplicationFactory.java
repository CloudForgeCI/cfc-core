package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.*;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforgeci.api.interfaces.SecurityProfileConfiguration;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforgeci.api.network.DomainFactory;
import com.cloudforgeci.api.network.VpcFactory;
import com.cloudforgeci.api.ingress.AlbFactory;
import com.cloudforgeci.api.observability.FlowLogFactory;
import com.cloudforgeci.api.storage.EfsFactory;
import com.cloudforgeci.api.observability.AlarmFactory;
import com.cloudforgeci.api.database.RdsFactory;
import com.cloudforgeci.api.storage.BackupFactory;
import software.constructs.Construct;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Universal factory class for deploying any application using ApplicationSpec.
 *
 * <p>This factory supports deployment of any application that implements the ApplicationSpec
 * interface, including:</p>
 * <ul>
 *   <li>CI/CD: Jenkins, GitLab, Drone, Gitea</li>
 *   <li>Monitoring: Grafana, Prometheus</li>
 *   <li>Databases: PostgreSQL, Redis</li>
 *   <li>Secrets Management: HashiCorp Vault</li>
 *   <li>Artifact Registry: Nexus, Harbor</li>
 *   <li>Collaboration: Mattermost</li>
 *   <li>Analytics: Metabase, Apache Superset</li>
 * </ul>
 *
 * <p>The factory automatically configures:</p>
 * <ul>
 *   <li>VPC and networking (respects networkMode: public-no-nat, private-with-nat)</li>
 *   <li>Application Load Balancer (ALB) with SSL/TLS termination</li>
 *   <li>Security groups and IAM roles</li>
 *   <li>Storage (EFS or EBS based on application needs)</li>
 *   <li>Observability (CloudWatch logs, flow logs, alarms)</li>
 *   <li>Auto-scaling (for service deployments)</li>
 * </ul>
 *
 * @author CloudForgeCI
 * @since 3.0.0
 * @see ApplicationSpec
 * @see DeploymentContext
 * @see SystemContext
 */
public class ApplicationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ApplicationFactory.class.getName());

    private final RuntimeType runtime;
    private final ApplicationSpec applicationSpec;
    private SystemContext.InfrastructureFactories infrastructure;

    @com.cloudforge.core.annotation.DeploymentContext("domain")
    private String domain;

    @com.cloudforge.core.annotation.DeploymentContext("subdomain")
    private String subdomain;

    @com.cloudforge.core.annotation.DeploymentContext("fqdn")
    private String fqdn;

    @com.cloudforge.core.annotation.DeploymentContext("maxInstanceCapacity")
    private Integer maxInstanceCapacity;

    @com.cloudforge.core.annotation.DeploymentContext("provisionDatabase")
    private Boolean provisionDatabase;

    @com.cloudforge.core.annotation.DeploymentContext("databaseEngine")
    private String databaseEngine;

    @com.cloudforge.core.annotation.DeploymentContext("databaseVersion")
    private String databaseVersion;

    @com.cloudforge.core.annotation.DeploymentContext("databaseInstanceClass")
    private String databaseInstanceClass;

    @com.cloudforge.core.annotation.DeploymentContext("databaseAllocatedStorageGB")
    private Integer databaseAllocatedStorageGB;

    @com.cloudforge.core.annotation.DeploymentContext("databaseName")
    private String databaseName;

    @com.cloudforge.core.annotation.DeploymentContext("databaseBackupRetentionDays")
    private Integer databaseBackupRetentionDays;

    @com.cloudforge.core.annotation.DeploymentContext("databaseMultiAz")
    private Boolean databaseMultiAz;

    @com.cloudforge.core.annotation.DeploymentContext("databaseReadReplicaCount")
    private Integer databaseReadReplicaCount;

    @com.cloudforge.core.annotation.DeploymentContext("enableEncryption")
    private Boolean enableEncryption;

    @com.cloudforge.core.annotation.DeploymentContext("provisionManagerRedisSessions")
    private Boolean provisionManagerRedisSessions;

    @com.cloudforge.core.annotation.DeploymentContext("provisionManagerAccountCipherKey")
    private Boolean provisionManagerAccountCipherKey;

    @com.cloudforge.core.annotation.DeploymentContext("managerLicenseKey")
    private String managerLicenseKey;

    @com.cloudforge.core.annotation.DeploymentContext("enableAutoScaling")
    private Boolean enableAutoScaling;

    public ApplicationFactory(Construct scope, String id, RuntimeType runtime, ApplicationSpec applicationSpec) {
        super(scope, id);
        this.runtime = runtime;
        this.applicationSpec = applicationSpec;
    }

    /**
     * Gets the infrastructure factories created during deployment.
     * @return Infrastructure factories containing VPC, ALB, EFS, and logging components
     */
    public SystemContext.InfrastructureFactories getInfrastructure() {
        return infrastructure;
    }

    /**
     * Container for application system components created by the factory.
     *
     * @param vpc the VPC factory that created the network infrastructure
     * @param alb the ALB factory that created the load balancer
     * @param efs the EFS factory that created the shared file system
     */
    public record ApplicationSystem(
        VpcFactory vpc,
        AlbFactory alb,
        EfsFactory efs
    ) {}

    /**
     * Creates the application deployment infrastructure.
     * This method is called after SystemContext has been started and ApplicationFactory has been instantiated.
     */
    @Override
    public void create() {
        try {
            LOG.info("Creating " + runtime + " deployment for application: " + applicationSpec.applicationId());

            // Set the ApplicationSpec for this deployment (must be done before createInfrastructureFactories)
            ctx.applicationSpec.set(applicationSpec);

            // Set DNS configuration early for SSL certificate creation
            ctx.domain.set(domain);
            ctx.subdomain.set(subdomain);
            ctx.fqdn.set(fqdn);

            // Get the unique ID for this factory (without the "Application" prefix to avoid duplication)
            String id = getNode().getId().replace("Application", "");

            // Create FlowLogFactory early and configure flow logs
            try {
                FlowLogFactory flowLogFactory = new FlowLogFactory(this, id + "Flowlog");
                flowLogFactory.create();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "*** CRITICAL: Exception in FlowLogFactory ***", e);
                throw e;
            }

            // Create domain factory if domain is provided (must run before security factories for cert validation)
            if (domain != null && !domain.isBlank()) {
                DomainFactory domainFactory;
                try {
                    domainFactory = new DomainFactory(this, id + "Domain");
                    domainFactory.create();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "*** CRITICAL: Exception in DomainFactory ***", e);
                    throw e;
                }
            }

            // Create infrastructure factories FIRST (VPC, ALB, EFS, Logging)
            // This ensures the ALB exists before security factories try to reference it
            // for OIDC callback URLs (CognitoAuthenticationFactory needs ALB DNS name)
            infrastructure = ctx.createInfrastructureFactories(this, id);

            // Create security factories AFTER infrastructure factories
            // CognitoAuthenticationFactory needs the ALB to be created first so it can
            // use the ALB DNS name in callback URLs when no custom domain is configured
            try {
                ctx.createSecurityFactories(this, id);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "*** CRITICAL: Exception in createSecurityFactories() ***", e);
                throw e;
            }

            // Provision database if required by the application
            // MUST run AFTER createInfrastructureFactories() so VPC is available
            if (applicationSpec instanceof DatabaseSpec) {
                DatabaseSpec dbSpec = (DatabaseSpec) applicationSpec;
                DatabaseRequirement dbReq = dbSpec.databaseRequirement();

                boolean shouldProvisionDatabase = false;

                // Determine if we should provision the database
                if (dbReq.type() == DatabaseRequirement.RequirementType.REQUIRED) {
                    // Application MUST have a database
                    shouldProvisionDatabase = true;
                    LOG.info("Database is REQUIRED for " + applicationSpec.applicationId());
                } else if (dbReq.type() == DatabaseRequirement.RequirementType.OPTIONAL) {
                    // Application CAN use database if provisionDatabase flag is set. Some security
                    // profiles (PRODUCTION) require that choice to be explicit rather than silently
                    // defaulting to false — see SecurityProfileConfiguration#isDatabaseProvisioningChoiceRequired's
                    // own javadoc for why.
                    if (provisionDatabase == null && ctx.securityProfileConfig.get()
                            .map(SecurityProfileConfiguration::isDatabaseProvisioningChoiceRequired)
                            .orElse(false)) {
                        throw new IllegalStateException(
                            "securityProfile=" + ctx.security + " requires an explicit "
                                + "provisionDatabase choice for " + applicationSpec.applicationId()
                                + " (true: provision a real RDS " + dbReq.engine() + " instance; "
                                + "false: accept its embedded fallback storage on purpose). Set "
                                + "provisionDatabase in the deployment context rather than leaving "
                                + "it unset.");
                    }
                    shouldProvisionDatabase = Boolean.TRUE.equals(provisionDatabase);
                    LOG.info("Database is OPTIONAL for " + applicationSpec.applicationId() +
                            " (provisionDatabase=" + provisionDatabase + ")");
                }

                if (shouldProvisionDatabase) {
                    try {
                        LOG.info("Provisioning RDS database for " + applicationSpec.applicationId());

                        // Get VPC from context (created by FlowLogFactory -> VpcFactory)
                        software.amazon.awscdk.services.ec2.IVpc vpc = ctx.vpc.get()
                            .orElseThrow(() -> new IllegalStateException("VPC not available for database provisioning"));

                        // Merge DeploymentConfig values with ApplicationSpec defaults
                        // Priority: DeploymentConfig > ApplicationSpec
                        DatabaseRequirement mergedReq = DatabaseRequirement.required(
                            databaseEngine != null ? databaseEngine : dbReq.engine(),
                            databaseVersion != null ? databaseVersion : dbReq.version()
                        )
                        .withInstanceClass(databaseInstanceClass != null ? databaseInstanceClass : dbReq.instanceClass())
                        .withStorage(databaseAllocatedStorageGB != null ? databaseAllocatedStorageGB : dbReq.allocatedStorageGB())
                        .withDatabaseName(databaseName != null ? databaseName : dbReq.databaseName());

                        LOG.info("Database configuration (DeploymentConfig overrides applied):");
                        LOG.info("  Engine: " + mergedReq.engine() + " " + mergedReq.version());
                        LOG.info("  Instance: " + mergedReq.instanceClass());
                        LOG.info("  Storage: " + mergedReq.allocatedStorageGB() + " GB");
                        LOG.info("  Database: " + mergedReq.databaseName());

                        // Create RDS database instance with merged configuration
                        DatabaseConnection dbConnection = RdsFactory.createDatabase(
                            ctx,
                            mergedReq,
                            vpc,
                            applicationSpec.applicationId() + "-db",
                            databaseBackupRetentionDays,  // DeploymentConfig override
                            databaseMultiAz,              // DeploymentConfig override
                            enableEncryption,             // DeploymentConfig override
                            resolveReadReplicaCount(dbSpec)
                        );

                        // Store database connection in SystemContext for use by ContainerFactory
                        ctx.dbConnection.set(dbConnection);

                        LOG.info("Successfully provisioned RDS database: " + dbConnection.endpoint());

                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "*** CRITICAL: Exception provisioning database for " +
                               applicationSpec.applicationId() + " ***", e);
                        throw e;
                    }
                } else {
                    LOG.info("No database provisioning for " + applicationSpec.applicationId());
                }
            }

            // Provision an ElastiCache Redis cluster for any application that declares it needs a
            // session store (see ApplicationSpec#requiresSessionStore's own javadoc). MUST run
            // AFTER createInfrastructureFactories() so VPC exists.
            if (applicationSpec.requiresSessionStore()
                    && Boolean.TRUE.equals(provisionManagerRedisSessions)) {
                try {
                    LOG.info("Provisioning ElastiCache Redis session store for " + applicationSpec.applicationId());
                    software.amazon.awscdk.services.elasticache.CfnCacheCluster redisCluster =
                        com.cloudforgeci.api.core.topology.CmsObjectCacheConfiguration.createRedisCluster(
                            ctx, applicationSpec);
                    ctx.redisSessionStoreEndpoint.set(redisCluster.getAttrRedisEndpointAddress());
                    ctx.redisSessionStorePort.set(6379);
                    LOG.info("Successfully provisioned Redis session store: "
                        + redisCluster.getAttrRedisEndpointAddress());
                } catch (IllegalStateException e) {
                    // VPC not yet available — same tolerance CmsServiceTopologyConfiguration
                    // applies to CMS object-cache provisioning; log and skip rather than fail
                    // the whole synth, since a single-instance app works fine without this.
                    LOG.warning("Skipping Redis session store — VPC not yet available: "
                        + e.getMessage());
                }
            }

            // Provision an AES cipher-key secret for any application that declares one (see
            // ApplicationSpec#cipherKeySecretEnvVar's own javadoc). Delivered as an ECS Secret
            // bound to that env var (ContainerFactory), mirroring CFC_MANAGER_DATABASE_PASSWORD's
            // delivery. No VPC dependency, unlike Redis above, so this can run regardless of
            // network readiness. Defaults to true (see DeploymentConfig.provisionManagerAccountCipherKey
            // javadoc) — an application declaring this env var but not receiving the secret would
            // otherwise silently fall back to whatever its own no-cipher-key default is.
            String cipherKeySecretEnvVar = applicationSpec.cipherKeySecretEnvVar();
            if (cipherKeySecretEnvVar != null && !cipherKeySecretEnvVar.isBlank()
                    && !Boolean.FALSE.equals(provisionManagerAccountCipherKey)) {
                LOG.info("Provisioning cipher key secret for " + applicationSpec.applicationId());
                // 32 alphanumeric characters is valid, padding-free Base64 (32 % 4 == 0) that
                // decodes to exactly 24 raw bytes — an accepted AES-192 key length for
                // AesGcmSecretCipher (16/24/32). excludePunctuation keeps the generated string to
                // the alphanumeric subset of the Base64 alphabet, so it is always decodable as-is.
                software.amazon.awscdk.services.secretsmanager.Secret accountCipherKeySecret =
                    software.amazon.awscdk.services.secretsmanager.Secret.Builder.create(
                            this, "AccountCipherKeySecret")
                        .description("AES cipher key for "
                            + software.amazon.awscdk.Stack.of(this).getStackName()
                            + " cross-account connection secrets (CFC_MANAGER_ACCOUNT_SECRET_KEY)")
                        .generateSecretString(
                            software.amazon.awscdk.services.secretsmanager.SecretStringGenerator.builder()
                                .passwordLength(32)
                                .excludePunctuation(true)
                                .build())
                        .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                        .build();
                ctx.accountCipherKeySecretArn.set(accountCipherKeySecret.getSecretArn());
                LOG.info("Successfully provisioned cipher key secret: "
                    + accountCipherKeySecret.getSecretName());
            }

            // Provision a Secrets Manager entry for a deploy-time-supplied license key, for any
            // application that declares one (see ApplicationSpec#licenseKeySecretEnvVar's own
            // javadoc). This one is a fixed value the user supplies rather than a generated one,
            // so it uses secretStringValue/unsafePlainText instead of generateSecretString —
            // "unsafe" here just means the literal value round-trips through the CDK template the
            // way any other deploy-time input does, same as a database name or stack name; it is
            // still never a plaintext ECS task-definition environment variable. Opt-in: no
            // license key configured, nothing is provisioned.
            String licenseKeySecretEnvVar = applicationSpec.licenseKeySecretEnvVar();
            if (licenseKeySecretEnvVar != null && !licenseKeySecretEnvVar.isBlank()
                    && managerLicenseKey != null && !managerLicenseKey.isBlank()) {
                LOG.info("Provisioning license key secret for " + applicationSpec.applicationId());
                software.amazon.awscdk.services.secretsmanager.Secret licenseKeySecret =
                    software.amazon.awscdk.services.secretsmanager.Secret.Builder.create(
                            this, "LicenseKeySecret")
                        .description("License key for "
                            + software.amazon.awscdk.Stack.of(this).getStackName()
                            + " (" + licenseKeySecretEnvVar + ")")
                        .secretStringValue(
                            software.amazon.awscdk.SecretValue.unsafePlainText(managerLicenseKey))
                        .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                        .build();
                ctx.licenseKeySecretArn.set(licenseKeySecret.getSecretArn());
                LOG.info("Successfully provisioned license key secret: "
                    + licenseKeySecret.getSecretName());
            }

            // Provision a random initial admin password for any application spec declaring
            // ApplicationSpec.autoAdminPasswordEnvVar() -- delivered as an ECS Secret bound to
            // that env var name (ContainerFactory). Without it, an app that otherwise supports a
            // fully non-interactive first-run install (site name/admin account already present
            // via containerEnvironmentVariables) still leaves its web installer's wizard --
            // including the database-connection step -- for a human to complete by hand, even
            // though every value it asks for is already known to CDK.
            String autoAdminPasswordEnvVar = applicationSpec.autoAdminPasswordEnvVar();
            if (autoAdminPasswordEnvVar != null && !autoAdminPasswordEnvVar.isBlank()) {
                LOG.info("Provisioning initial admin password secret for "
                    + applicationSpec.applicationId() + " (" + autoAdminPasswordEnvVar + ")");
                software.amazon.awscdk.services.secretsmanager.Secret adminPasswordSecret =
                    software.amazon.awscdk.services.secretsmanager.Secret.Builder.create(
                            this, "AutoAdminPasswordSecret")
                        .description("Initial admin password for "
                            + software.amazon.awscdk.Stack.of(this).getStackName()
                            + " (" + autoAdminPasswordEnvVar + ")")
                        .generateSecretString(
                            software.amazon.awscdk.services.secretsmanager.SecretStringGenerator.builder()
                                .passwordLength(24)
                                .excludePunctuation(true)
                                .build())
                        .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                        .build();
                ctx.autoAdminPasswordSecretArn.set(adminPasswordSecret.getSecretArn());
                LOG.info("Successfully provisioned admin password secret: "
                    + adminPasswordSecret.getSecretName());
            }

            // NOTE: WAF is created by security profile configurations (ProductionSecurityConfiguration, StagingSecurityConfiguration)
            // to ensure proper integration with security profile settings and avoid duplicates

            // Create compute resources based on runtime type
            if (runtime == RuntimeType.FARGATE) {
                FargateFactory fargate;
                try {
                    fargate = new FargateFactory(this, id + "Fargate");
                    fargate.create();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "*** CRITICAL: Exception in FargateFactory ***", e);
                    throw e;
                }
            } else if (runtime == RuntimeType.EC2) {
                try {
                    Ec2Factory ec2 = new Ec2Factory(this, id + "Ec2");
                    ec2.create();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "*** CRITICAL: Exception in Ec2Factory ***", e);
                    throw e;
                }
            }

            // Create AWS Backup infrastructure (after EFS and RDS are available)
            try {
                BackupFactory backupFactory = new BackupFactory(this, id + "Backup");
                backupFactory.create();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "*** CRITICAL: Exception in BackupFactory ***", e);
                throw e;
            }

            try {
                new AlarmFactory(this, id + "Alarms", null);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "*** CRITICAL: Exception in AlarmFactory ***", e);
                throw e;
            }

            // Execute deferred actions
            try {
                ctx.executeDeferredActions();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "*** CRITICAL: Exception in executeDeferredActions() ***", e);
                throw e;
            }

            LOG.info("Successfully created " + runtime + " deployment for: " + applicationSpec.applicationId());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "*** CRITICAL: Exception in ApplicationFactory.create() for " +
                   applicationSpec.applicationId() + " ***", e);
            throw e;
        }
    }

    private int resolveReadReplicaCount(DatabaseSpec dbSpec) {
        if (databaseReadReplicaCount != null) {
            return Math.max(0, databaseReadReplicaCount);
        }
        return dbSpec.requiresReadReplicas() ? Math.max(0, dbSpec.readReplicaCount()) : 0;
    }

    /**
     * Static helper method for creating a Fargate-based application deployment.
     * This method ensures SystemContext is started before creating the ApplicationFactory.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createFargate(Construct scope, String id, DeploymentContext cfc, ApplicationSpec applicationSpec) {
        return createFargate(scope, id, cfc, cfc.securityProfile(), applicationSpec);
    }

    /**
     * Static helper method for creating a Fargate-based application deployment with specific security profile.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param security Security profile determining security hardening level
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createFargate(Construct scope, String id, DeploymentContext cfc,
                                                 SecurityProfile security, ApplicationSpec applicationSpec) {
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);
        return createFargate(scope, id, cfc, security, iamProfile, applicationSpec);
    }

    /**
     * Static helper method for creating a Fargate-based application deployment with explicit IAM profile.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param security Security profile determining security hardening level
     * @param iamProfile IAM profile for access control
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createFargate(Construct scope, String id, DeploymentContext cfc,
                                                 SecurityProfile security, IAMProfile iamProfile,
                                                 ApplicationSpec applicationSpec) {
        try {
            // Use APPLICATION_SERVICE topology for universal application deployment
            TopologyType topology = TopologyType.APPLICATION_SERVICE;

            // Start SystemContext (or get existing one)
            SystemContext.start(scope, topology, RuntimeType.FARGATE, security, iamProfile, cfc);

            // Create ApplicationFactory and invoke create()
            ApplicationFactory factory = new ApplicationFactory(scope, id + "Application", RuntimeType.FARGATE, applicationSpec);
            factory.create();

            // Return the infrastructure components from the factory
            SystemContext.InfrastructureFactories infra = factory.getInfrastructure();
            return new ApplicationSystem(infra.vpc(), infra.alb(), infra.efs());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "*** CRITICAL: Exception in createFargate() for " +
                   applicationSpec.applicationId() + " ***", e);
            throw e;
        }
    }

    /**
     * Creates an EC2-based application deployment.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createEc2(Construct scope, String id, DeploymentContext cfc, ApplicationSpec applicationSpec) {
        return createEc2(scope, id, cfc, cfc.securityProfile(), applicationSpec);
    }

    /**
     * Creates an EC2-based application deployment with specific security profile.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param security Security profile determining security hardening level
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createEc2(Construct scope, String id, DeploymentContext cfc,
                                             SecurityProfile security, ApplicationSpec applicationSpec) {
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(security);
        return createEc2(scope, id, cfc, security, iamProfile, applicationSpec);
    }

    /**
     * Static helper method for creating an EC2-based application deployment with explicit IAM profile.
     *
     * @param scope The CDK construct scope
     * @param id Unique identifier for the deployment
     * @param cfc Deployment context containing configuration parameters
     * @param security Security profile determining security hardening level
     * @param iamProfile IAM profile for access control
     * @param applicationSpec The ApplicationSpec defining the application
     * @return ApplicationSystem containing references to created infrastructure components
     */
    public static ApplicationSystem createEc2(Construct scope, String id, DeploymentContext cfc,
                                             SecurityProfile security, IAMProfile iamProfile,
                                             ApplicationSpec applicationSpec) {
        try {
            // Use APPLICATION_SERVICE topology for universal application deployment
            TopologyType topology = TopologyType.APPLICATION_SERVICE;

            // Start SystemContext (or get existing one)
            SystemContext.start(scope, topology, RuntimeType.EC2, security, iamProfile, cfc);

            // Create ApplicationFactory and invoke create()
            ApplicationFactory factory = new ApplicationFactory(scope, id + "Application", RuntimeType.EC2, applicationSpec);
            factory.create();

            // Return the infrastructure components from the factory
            SystemContext.InfrastructureFactories infra = factory.getInfrastructure();
            return new ApplicationSystem(infra.vpc(), infra.alb(), infra.efs());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "*** CRITICAL: Exception in createEc2() for " +
                   applicationSpec.applicationId() + " ***", e);
            throw e;
        }
    }
}
