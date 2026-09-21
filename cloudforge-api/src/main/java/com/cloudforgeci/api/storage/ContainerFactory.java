package com.cloudforgeci.api.storage;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ContainerFactory extends BaseFactory {
    private static final Logger LOG = Logger.getLogger(ContainerFactory.class.getName());

    private final ContainerImage image;

    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("certificateArn")
    private String certificateArn;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @DeploymentContext("authMode")
    private AuthMode authMode;

    @DeploymentContext("cognitoInitialAdminEmail")
    private String cognitoInitialAdminEmail;

    // ========== Optional Port Configuration ==========
    // These flags control which optional ports are exposed for applications
    // Ports are NOT exposed by default - must be explicitly enabled

    @DeploymentContext("enableAgents")
    private Boolean enableAgents;

    @DeploymentContext("enableSsh")
    private Boolean enableSsh;

    @DeploymentContext("enableSmtp")
    private Boolean enableSmtp;

    @DeploymentContext("enableSmtps")
    private Boolean enableSmtps;

    @DeploymentContext("enableClustering")
    private Boolean enableClustering;

    @DeploymentContext("enableDockerRegistry")
    private Boolean enableDockerRegistry;

    @DeploymentContext("enableMetrics")
    private Boolean enableMetrics;

    @DeploymentContext("enableNotary")
    private Boolean enableNotary;

    @DeploymentContext("enableTrivy")
    private Boolean enableTrivy;

    @DeploymentContext("enableSentinel")
    private Boolean enableSentinel;

    @DeploymentContext("enableCluster")
    private Boolean enableCluster;

    @SystemContext("fargateTaskDef")
    private TaskDefinition fargateTaskDef;

    @SystemContext("logs")
    private LogGroup logs;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    @SystemContext("dbConnection")
    private DatabaseSpec.DatabaseConnection dbConnection;

    @SystemContext("dbDatasourceParameter")
    private StringParameter dbDatasourceParameter;

    @SystemContext("redisSessionStoreEndpoint")
    private String redisSessionStoreEndpoint;

    @SystemContext("redisSessionStorePort")
    private Integer redisSessionStorePort;

    @SystemContext("accountCipherKeySecretArn")
    private String accountCipherKeySecretArn;

    @SystemContext("licenseKeySecretArn")
    private String licenseKeySecretArn;

    @SystemContext("autoAdminPasswordSecretArn")
    private String autoAdminPasswordSecretArn;

    @SystemContext("applicationOidcConfig")
    private OidcConfiguration applicationOidcConfig;

    @SystemContext("alb")
    private ApplicationLoadBalancer alb;

    @SystemContext("samlIdpMetadataUrl")
    private String samlIdpMetadataUrl;

    public ContainerFactory(Construct scope, String id, ContainerImage image) {
        super(scope, id);
        this.image = image;
        // fqdn, enableSsl, authMode, and applicationSpec are automatically injected by BaseFactory
    }

    @Override
    public void create() {
        // Get configuration values from annotated fields
        boolean sslEnabled = Boolean.TRUE.equals(enableSsl);

        // Get application-specific environment variables from ApplicationSpec
        // Each application can define its own environment configuration
        Map<String, String> environment = new HashMap<>();
        if (applicationSpec != null) {
            // Check if application implements DatabaseSpec and has database connection
            if (applicationSpec instanceof DatabaseSpec && dbConnection != null) {
                // Pass database connection to applications that support it (GitLab, Mattermost, etc.)
                LOG.info("Database connection available - configuring " + applicationSpec.applicationId() + " with RDS");
                // Use reflection to call the 4-parameter method if it exists
                try {
                    java.lang.reflect.Method method = applicationSpec.getClass().getMethod(
                        "containerEnvironmentVariables",
                        String.class, boolean.class, String.class, DatabaseSpec.DatabaseConnection.class
                    );
                    @SuppressWarnings("unchecked")
                    Map<String, String> dbEnv = (Map<String, String>) method.invoke(
                        applicationSpec, fqdn, sslEnabled, authMode.getValue(), dbConnection
                    );
                    environment.putAll(dbEnv);
                } catch (NoSuchMethodException e) {
                    // CMS specifications use the DatabaseSpec databaseEnvVars contract rather
                    // than the newer four-argument ApplicationSpec convenience method.
                    // Preserve their normal application settings and add the database values.
                    LOG.info("Application " + applicationSpec.applicationId()
                        + " uses DatabaseSpec database environment variables");
                    environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode.getValue()));
                    if (applicationSpec instanceof CmsSpec cmsSpec) {
                        environment.putAll(cmsSpec.databaseEnvVars(
                            dbConnection.endpoint(),
                            dbConnection.port(),
                            dbConnection.databaseName(),
                            dbConnection.username()));
                    }
                } catch (Exception e) {
                    LOG.warning("Error calling containerEnvironmentVariables with database connection: " + e.getMessage());
                    environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode.getValue()));
                }
            } else if (applicationSpec instanceof DatabaseSpec) {
                // No database connection - use embedded database fallback
                LOG.info("No database connection - " + applicationSpec.applicationId() + " will use embedded database");
                environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode.getValue()));
            } else {
                // Standard applications without database support
                environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode.getValue()));
            }
        }

        if (applicationSpec != null) {
            String albSignerArnEnvVar = applicationSpec.albSignerArnEnvVar();
            if (albSignerArnEnvVar != null && !albSignerArnEnvVar.isBlank()
                    && authMode == AuthMode.ALB_OIDC && alb != null) {
                environment.put(albSignerArnEnvVar, alb.getLoadBalancerArn());
            }

            String publicTlsTrustedEnvVar = applicationSpec.publicTlsTrustedEnvVar();
            if (publicTlsTrustedEnvVar != null && !publicTlsTrustedEnvVar.isBlank()) {
                boolean publiclyTrusted = com.cloudforgeci.api.core.TlsTrustEvaluator.isPubliclyTrusted(
                    sslEnabled, domain, fqdn, certificateArn);
                environment.put(publicTlsTrustedEnvVar, String.valueOf(publiclyTrusted));
            }

            // With alb-oidc and cognitoInitialAdminEmail set (see CognitoAuthenticationFactory),
            // use that address for the application's first-run admin account instead of the
            // admin@<fqdn> default from containerEnvironmentVariables(). Applies only to specs
            // that expose autoAdminEmailEnvVar().
            String autoAdminEmailEnvVar = applicationSpec.autoAdminEmailEnvVar();
            if (autoAdminEmailEnvVar != null && !autoAdminEmailEnvVar.isBlank()
                    && authMode == AuthMode.ALB_OIDC
                    && cognitoInitialAdminEmail != null && !cognitoInitialAdminEmail.isBlank()) {
                environment.put(autoAdminEmailEnvVar, cognitoInitialAdminEmail);
                LOG.info("  ✅ Admin email set from cognitoInitialAdminEmail for " + applicationSpec.applicationId());
            }
        }

        if (applicationSpec != null) {
            String[] sessionStoreEnvVars = applicationSpec.sessionStoreEnvVars();
            if (sessionStoreEnvVars != null && sessionStoreEnvVars.length == 4
                    && redisSessionStoreEndpoint != null && !redisSessionStoreEndpoint.isBlank()) {
                // See ApplicationFactory's provisionManagerRedisSessions handling for where this
                // cluster gets created. CmsObjectCacheConfiguration.createRedisCluster() builds a
                // single-node CfnCacheCluster, which has no transitEncryptionEnabled property at
                // all (unlike CfnReplicationGroup) — there is no TLS listener for a client to
                // connect to, so the fourth env var is always told "false".
                environment.put(sessionStoreEnvVars[0], "redis");
                environment.put(sessionStoreEnvVars[1], redisSessionStoreEndpoint);
                environment.put(sessionStoreEnvVars[2],
                    String.valueOf(redisSessionStorePort != null ? redisSessionStorePort : 6379));
                environment.put(sessionStoreEnvVars[3], "false");
                LOG.info("✅ Configured session store: mode=redis (" + redisSessionStoreEndpoint + ")");
            }
        }

        // Collect ECS secrets (from Secrets Manager) to be mounted as environment variables
        Map<String, software.amazon.awscdk.services.ecs.Secret> ecsSecrets = new HashMap<>();

        // Bind a provisioned AES cipher-key secret to whichever env var name the app's own
        // ApplicationSpec declares (see ApplicationFactory's provisioning above). Independent of
        // database provisioning — applies whenever the secret was provisioned, embedded storage
        // included.
        String cipherKeySecretEnvVar = applicationSpec != null ? applicationSpec.cipherKeySecretEnvVar() : null;
        if (cipherKeySecretEnvVar != null && !cipherKeySecretEnvVar.isBlank()
                && accountCipherKeySecretArn != null && !accountCipherKeySecretArn.isBlank()) {
            ISecret accountCipherKeySecret =
                Secret.fromSecretCompleteArn(this, "AccountCipherKeySecret", accountCipherKeySecretArn);

            if (fargateTaskDef.getExecutionRole() != null) {
                fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                    PolicyStatement.Builder.create()
                        .sid("AllowReadAccountCipherKey")
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "secretsmanager:GetSecretValue",
                            "secretsmanager:DescribeSecret"
                        ))
                        .resources(List.of(accountCipherKeySecretArn))
                        .build()
                );
                LOG.info("  ✅ Added IAM policy for account cipher key secret access");
            }

            ecsSecrets.put(cipherKeySecretEnvVar,
                          software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(accountCipherKeySecret));
            LOG.info("  ✅ Account cipher key mapped to " + cipherKeySecretEnvVar);
        }

        // Bind a provisioned deploy-time-supplied license key to whichever env var name the app's
        // own ApplicationSpec declares (see ApplicationFactory's provisioning above). Independent
        // of database provisioning, same as the cipher key above.
        String licenseKeySecretEnvVar = applicationSpec != null ? applicationSpec.licenseKeySecretEnvVar() : null;
        if (licenseKeySecretEnvVar != null && !licenseKeySecretEnvVar.isBlank()
                && licenseKeySecretArn != null && !licenseKeySecretArn.isBlank()) {
            ISecret licenseKeySecret =
                Secret.fromSecretCompleteArn(this, "LicenseKeySecret", licenseKeySecretArn);

            if (fargateTaskDef.getExecutionRole() != null) {
                fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                    PolicyStatement.Builder.create()
                        .sid("AllowReadLicenseKey")
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "secretsmanager:GetSecretValue",
                            "secretsmanager:DescribeSecret"
                        ))
                        .resources(List.of(licenseKeySecretArn))
                        .build()
                );
                LOG.info("  ✅ Added IAM policy for license key secret access");
            }

            ecsSecrets.put(licenseKeySecretEnvVar,
                          software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(licenseKeySecret));
            LOG.info("  ✅ License key mapped to " + licenseKeySecretEnvVar);
        }

        // Bind an application's auto-generated initial admin password (see ApplicationFactory's
        // provisioning above) to whichever env var name its own ApplicationSpec declares via
        // autoAdminPasswordEnvVar() (e.g. JOOMLA_ADMIN_PASSWORD). Combined with the plain admin/
        // site-name variables that spec's own containerEnvironmentVariables() already sets, this
        // completes the full set an application's official image needs for a non-interactive
        // install — without it, its web installer's wizard, including the database-connection
        // step, is left for a human.
        String autoAdminPasswordEnvVar = applicationSpec == null ? null : applicationSpec.autoAdminPasswordEnvVar();
        if (autoAdminPasswordEnvVar != null && !autoAdminPasswordEnvVar.isBlank()
                && autoAdminPasswordSecretArn != null && !autoAdminPasswordSecretArn.isBlank()) {
            ISecret autoAdminPasswordSecret =
                Secret.fromSecretCompleteArn(this, "AutoAdminPasswordSecret", autoAdminPasswordSecretArn);

            if (fargateTaskDef.getExecutionRole() != null) {
                fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                    PolicyStatement.Builder.create()
                        .sid("AllowReadAutoAdminPassword")
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "secretsmanager:GetSecretValue",
                            "secretsmanager:DescribeSecret"
                        ))
                        .resources(List.of(autoAdminPasswordSecretArn))
                        .build()
                );
                LOG.info("  ✅ Added IAM policy for auto-admin password secret access");
            }

            ecsSecrets.put(autoAdminPasswordEnvVar,
                          software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(autoAdminPasswordSecret));
            LOG.info("  ✅ Admin password mapped to JOOMLA_ADMIN_PASSWORD");
        }

        // Add database password from Secrets Manager for applications with external database
        if (applicationSpec instanceof DatabaseSpec && dbConnection != null) {
            LOG.info("Adding database password secret for " + applicationSpec.applicationId());

            // Extract secret name from ARN
            // ARN format: arn:aws:secretsmanager:region:account:secret:name-randomsuffix
            String secretArn = dbConnection.passwordSecretArn();
            ISecret dbSecret = Secret.fromSecretCompleteArn(this, "DatabasePasswordSecret", secretArn);

            // Grant task execution role permission to read the secret
            if (fargateTaskDef.getExecutionRole() != null) {
                fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                    PolicyStatement.Builder.create()
                        .sid("AllowReadDatabasePassword")
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "secretsmanager:GetSecretValue",
                            "secretsmanager:DescribeSecret"
                        ))
                        .resources(List.of(secretArn))
                        .build()
                );
                LOG.info("  ✅ Added IAM policy for database password secret access");
            }

            // Map password to application-specific environment variable names
            // Different applications expect different env var names for the database password
            String appId = applicationSpec.applicationId();
            String databasePasswordEnvVar = applicationSpec.databasePasswordEnvVar();
            if (databasePasswordEnvVar != null && !databasePasswordEnvVar.isBlank()) {
                ecsSecrets.put(databasePasswordEnvVar,
                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                LOG.info("  ✅ Database password mapped to " + databasePasswordEnvVar);
            } else switch (appId) {
                case "gitlab":
                    ecsSecrets.put("GITLAB_DATABASE_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to GITLAB_DATABASE_PASSWORD");
                    break;
                case "metabase":
                    ecsSecrets.put("MB_DB_PASS",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to MB_DB_PASS");
                    break;
                case "grafana":
                    ecsSecrets.put("GF_DATABASE_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to GF_DATABASE_PASSWORD");
                    break;
                case "harbor":
                    ecsSecrets.put("POSTGRESQL_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to POSTGRESQL_PASSWORD");
                    break;
                case "superset":
                    ecsSecrets.put("SUPERSET_DATABASE_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to SUPERSET_DATABASE_PASSWORD");
                    break;
                case "joomla":
                    ecsSecrets.put("JOOMLA_DB_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to JOOMLA_DB_PASSWORD");
                    break;
                case "wordpress":
                case "woocommerce":
                    // Same official wordpress:* image bootstrap either way (WooCommerce is a
                    // WordPress plugin on top, sharing WordPressApplicationSpec's env var
                    // conventions — see WooCommerceApplicationSpec). Falling through to the
                    // generic DATABASE_PASSWORD default here left WORDPRESS_DB_PASSWORD unset —
                    // the official image's entrypoint reads that name specifically, not
                    // DATABASE_PASSWORD, so it connected with no password at all and failed.
                    ecsSecrets.put("WORDPRESS_DB_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to WORDPRESS_DB_PASSWORD");
                    break;
                case "mattermost-enterprise":
                case "mattermost-team":
                    // Mattermost is distroless (Go binary, no shell) - cannot use shell variable substitution
                    // RdsFactory creates an SSM Parameter with the complete datasource URL
                    // The SSM parameter value uses CloudFormation dynamic reference to resolve the password
                    if (dbDatasourceParameter != null) {
                        // Grant read permission to the task execution role
                        if (fargateTaskDef.getExecutionRole() != null) {
                            dbDatasourceParameter.grantRead(fargateTaskDef.getExecutionRole());
                        }

                        // Inject as ECS secret from SSM Parameter Store
                        ecsSecrets.put("MM_SQLSETTINGS_DATASOURCE",
                            software.amazon.awscdk.services.ecs.Secret.fromSsmParameter(dbDatasourceParameter));
                        LOG.info("  ✅ Complete datasource URL mapped to MM_SQLSETTINGS_DATASOURCE from SSM Parameter");
                    } else {
                        LOG.warning("  ⚠️  Datasource SSM parameter not found - Mattermost database connection not configured");
                        LOG.warning("  ⚠️  Mattermost REQUIRES a database - deployment will fail without it");
                    }
                    break;
                default:
                    // Fallback - use generic name
                    ecsSecrets.put("DATABASE_PASSWORD",
                                  software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                    LOG.info("  ✅ Database password mapped to DATABASE_PASSWORD (default)");
            }
        }

        // Add OIDC environment variables if APPLICATION_OIDC mode is enabled
        if (authMode == AuthMode.APPLICATION_OIDC && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            LOG.info("ContainerFactory: application-oidc mode detected for " + applicationSpec.applicationId());

            if (applicationOidcConfig != null) {
                LOG.info("  ✅ applicationOidcConfig found!");
                OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
                if (oidcIntegration != null) {
                    Map<String, String> oidcEnv = oidcIntegration.getEnvironmentVariables(applicationOidcConfig);
                    environment.putAll(oidcEnv);
                    LOG.info("  Added " + oidcEnv.size() + " OIDC environment variables for " + applicationSpec.applicationId());

                    // Add OIDC client secret from Secrets Manager if available
                    // Skip for Identity Center SAML - uses IAM authentication, not client secrets
                    String clientSecretArn = applicationOidcConfig.getClientSecretArn();
                    boolean isIdentityCenterSaml = oidcIntegration.supportsIdentityCenterSaml() &&
                                                   "identity-center".equals(applicationOidcConfig.getProviderType());
                    if (clientSecretArn != null && !clientSecretArn.isEmpty() && !isIdentityCenterSaml) {
                        LOG.info("  Mounting OIDC client secret from Secrets Manager for application: " + applicationSpec.applicationId());

                        // OidcConfiguration historically calls this field clientSecretArn, but
                        // external-provider configuration supplies a Secrets Manager *name*.
                        // Support both forms so application OIDC can synthesize and deploy with
                        // managed or pre-existing secrets.
                        //
                        // startsWith("arn:") alone is not enough: the Cognito auto-provision path
                        // (CognitoAuthenticationFactory) passes cognitoSecret.getSecretArn(), an
                        // unresolved CDK Token. Treating it as a bare name would wrap the resolved
                        // ARN in a second "arn:aws:secretsmanager:...:secret:" prefix and fail at
                        // deploy time. Unresolved Tokens always resolve to a full ARN, so they take
                        // the ARN branch; only a literal non-ARN value (a secret name supplied in
                        // the deployment context) uses fromSecretNameV2.
                        boolean isCompleteArn = software.amazon.awscdk.Token.isUnresolved(clientSecretArn)
                            || clientSecretArn.startsWith("arn:");
                        ISecret clientSecret = isCompleteArn
                            ? Secret.fromSecretCompleteArn(this, "OidcClientSecret", clientSecretArn)
                            : Secret.fromSecretNameV2(this, "OidcClientSecret", clientSecretArn);

                        // Grant the ECS task execution role permission to read the secret
                        if (fargateTaskDef.getExecutionRole() != null) {
                            fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                                PolicyStatement.Builder.create()
                                    .sid("AllowReadOidcClientSecret")
                                    .effect(Effect.ALLOW)
                                    .actions(List.of(
                                        "secretsmanager:GetSecretValue",
                                        "secretsmanager:DescribeSecret"
                                    ))
                                    .resources(List.of(clientSecret.getSecretArn()))
                                    .build()
                            );

                            LOG.info("  ✅ Added IAM policy for secret access");
                        } else {
                            LOG.warning("  ⚠️  Task execution role not found - cannot grant secret read permission");
                        }

                        // Add as ECS secret (mounted as environment variable at runtime)
                        // Map to application-specific environment variable names
                        // Different applications expect different env var names for OIDC client secret
                        String appId = applicationSpec.applicationId();
                        String oidcClientSecretEnvVar = applicationSpec.oidcClientSecretEnvVar();
                        if (oidcClientSecretEnvVar != null && !oidcClientSecretEnvVar.isBlank()) {
                            ecsSecrets.put(oidcClientSecretEnvVar,
                                          software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                            LOG.info("  ✅ OIDC client secret mapped to " + oidcClientSecretEnvVar);
                        } else switch (appId) {
                            case "mattermost-enterprise":
                                // Mattermost Enterprise - native OpenID Connect
                                ecsSecrets.put("MM_OPENIDSETTINGS_SECRET",
                                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                                LOG.info("  ✅ OIDC client secret mapped to MM_OPENIDSETTINGS_SECRET");
                                break;
                            case "mattermost-team":
                                // Mattermost Team Edition (free) - GitLab OAuth
                                ecsSecrets.put("MM_GITLABSETTINGS_SECRET",
                                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                                LOG.info("  ✅ OIDC client secret mapped to MM_GITLABSETTINGS_SECRET");
                                break;
                            case "jenkins":
                                // Jenkins CasC expects JENKINS_OIDC_CLIENT_SECRET placeholder
                                ecsSecrets.put("JENKINS_OIDC_CLIENT_SECRET",
                                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                                LOG.info("  ✅ OIDC client secret mapped to JENKINS_OIDC_CLIENT_SECRET");
                                break;
                            case "gitlab":
                                ecsSecrets.put("GITLAB_OIDC_CLIENT_SECRET",
                                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                                LOG.info("  ✅ OIDC client secret mapped to GITLAB_OIDC_CLIENT_SECRET");
                                break;
                            default:
                                // Generic naming: <APP>_OIDC_CLIENT_SECRET
                                String secretEnvVar = appId.toUpperCase() + "_OIDC_CLIENT_SECRET";
                                ecsSecrets.put(secretEnvVar,
                                              software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));
                                LOG.info("  ✅ OIDC client secret mapped to env var (default)");
                        }
                    } else {
                        LOG.warning("  ⚠️  Client secret ARN not found in OIDC config - secret will not be mounted");
                    }
                } else {
                    LOG.warning("  ⚠️  OidcIntegration is null!");
                }
            } else {
                LOG.severe("  ❌ applicationOidcConfig NOT FOUND in SystemContext!");
                LOG.severe("  This means ApplicationOidcFactory did not run or failed to set the config");
            }
        }

        // Get application-specific configuration from ApplicationSpec
        String containerUser = applicationSpec != null ? applicationSpec.containerUser() : "1000:1000";
        String logStreamPrefix = applicationSpec != null ? applicationSpec.applicationId() : "jenkins";
        int appPort = applicationSpec != null ? applicationSpec.applicationPort() : 8080;
        String containerPath = applicationSpec != null ? applicationSpec.containerDataPath() : "/var/jenkins_home";
        String volumeName = applicationSpec != null ? applicationSpec.volumeName() : "jenkinsHome";

        // Build container options - only set user if containerUser is not null
        ContainerDefinitionOptions.Builder containerOptionsBuilder = ContainerDefinitionOptions.builder()
                .containerName(getNode().getId())
                .image(image)
                .environment(environment.isEmpty() ? null : environment)
                .secrets(ecsSecrets.isEmpty() ? null : ecsSecrets)
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(logs)
                        .streamPrefix(logStreamPrefix).build()));

        // Only set user if specified (some apps like GitLab need to run as root), and never for a
        // privileged port (<1024). A container-level user applies from PID 1, bypassing the
        // image's root-then-drop-privileges startup (e.g. Apache binds port 80 as root, then forks
        // www-data workers), so the bind would fail with "Permission denied". Fargate does not
        // allow adding NET_BIND_SERVICE, so for privileged ports the image runs as its Dockerfile
        // intends: start as root, then drop privileges.
        boolean privilegedPort = appPort < 1024;
        if (containerUser != null && !privilegedPort) {
            containerOptionsBuilder.user(containerUser);
        }

        // Log container configuration
        if (!ecsSecrets.isEmpty()) {
            LOG.info("Container will have " + ecsSecrets.size() + " secret(s) mounted as environment variables");
        }

        // Set by the OIDC entrypoint-wrapper block below whenever it configures its own
        // command — guards the general-purpose CmsSpec.containerCommand() hook further down
        // from clobbering it. OIDC's wrapper is app-specific too; the two aren't composed.
        boolean commandConfigured = false;

        // Track whether we need an init container for SAML certificate
        // Uses OidcIntegration interface methods for application-specific paths
        boolean needsSamlCertInit = false;
        String samlMetadataUrl = null;
        String samlCertVolumeName = "saml-cert";
        String samlCertMountPath = null;
        String samlCertPath = null;
        String samlCertEnvVar = null;

        // Add entrypoint/command override for APPLICATION_OIDC mode
        // This creates the OIDC config file before starting the application
        if (authMode == AuthMode.APPLICATION_OIDC && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            LOG.info("ContainerFactory: Configuring OIDC entrypoint wrapper...");

            if (applicationOidcConfig != null) {
                OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
                if (oidcIntegration != null) {
                    String configFileContent = oidcIntegration.getConfigurationFile(applicationOidcConfig);
                    String configFilePath = oidcIntegration.getConfigurationFilePath();
                    String startupCommand = oidcIntegration.getContainerStartupCommand();

                    LOG.info("  Config file path: " + configFilePath);
                    LOG.info("  Startup command: " + startupCommand);
                    LOG.info("  Config file length: " + (configFileContent != null ? configFileContent.length() + " chars" : "NULL"));
                    LOG.info("  Is distroless: " + oidcIntegration.isDistroless());

                    // Check if we need to write a config file at startup
                    // Some apps (Metabase) use environment variables only - no config file needed
                    boolean needsConfigFile = configFileContent != null && configFilePath != null;

                    // Check if the container is distroless (no shell available) OR doesn't need config file
                    if (oidcIntegration.isDistroless() || !needsConfigFile) {
                        // Distroless containers have no /bin/sh - cannot use shell wrapper
                        // Or app uses environment variables only (like Metabase) - no config file to write
                        if (oidcIntegration.isDistroless()) {
                            LOG.info("  ⚠️  Distroless container detected - skipping shell wrapper");
                        } else {
                            LOG.info("  ⚠️  No config file needed - using environment variables only");
                        }
                        LOG.info("  Configuration will be done via environment variables only");

                        // For distroless, just set the startup command directly (no shell wrapper)
                        // All configuration is already in environment variables
                        if (startupCommand != null) {
                            List<String> command = List.of(startupCommand);
                            containerOptionsBuilder.command(command);
                            commandConfigured = true;
                            LOG.info("✅ Configured direct startup command for distroless " + applicationSpec.applicationId());
                        } else {
                            LOG.info("✅ Using default container entrypoint for " + applicationSpec.applicationId());
                        }
                    } else {
                        // Normal container with shell - use wrapper to write config file
                        // Create startup command that writes OIDC config and starts application
                        // Uses sh -c to execute multi-line script
                        // Note: No chown needed - container runs as containerUser so files created are already owned correctly
                        String fullCommand = String.format(
                            "mkdir -p $(dirname %s) && " +
                            "cat > %s <<'EOFCASC'\n%s\nEOFCASC\n" +
                            "%s",
                            configFilePath,
                            configFilePath,
                            configFileContent,
                            startupCommand
                        );

                        LOG.info("  Full command length: " + fullCommand.length() + " chars");
                        LOG.info("  Command preview (first 800 chars):");
                        LOG.info(fullCommand.substring(0, Math.min(800, fullCommand.length())));

                        List<String> command = List.of(
                            "/bin/sh",
                            "-c",
                            fullCommand
                        );

                        containerOptionsBuilder.command(command);
                        commandConfigured = true;
                        LOG.info("✅ Configured OIDC entrypoint wrapper for " + applicationSpec.applicationId());
                    }
                } else {
                    LOG.severe("❌ OidcIntegration is NULL - cannot configure entrypoint!");
                }
            } else {
                LOG.severe("❌ applicationOidcConfig NOT PRESENT - entrypoint wrapper NOT configured!");
            }
        }

        // CmsSpec startup customization (e.g. phpBB reconfiguring Apache's listen port and
        // downloading its source on first run). Applies regardless of authMode, unless the OIDC
        // branch above already set the container command.
        if (!commandConfigured && applicationSpec instanceof CmsSpec cmsSpec) {
            List<String> command = cmsSpec.containerCommand();
            if (command != null && !command.isEmpty()) {
                containerOptionsBuilder.command(command);
                commandConfigured = true;
                LOG.info("✅ Configured containerCommand() startup script for " + applicationSpec.applicationId());
            }
        }

        // Print the generated initial admin password once at container start, before the image's
        // default process takes over, so it can be retrieved from CloudWatch Logs. Applies only
        // when the spec declares its image's default CMD (see
        // ApplicationSpec#defaultContainerCommand for why this is opt-in) and no other command
        // has been configured.
        if (!commandConfigured && applicationSpec != null
                && applicationSpec.autoAdminPasswordEnvVar() != null
                && applicationSpec.defaultContainerCommand() != null
                && !applicationSpec.defaultContainerCommand().isEmpty()) {
            String passwordEnvVar = applicationSpec.autoAdminPasswordEnvVar();
            String defaultCommand = String.join(" ", applicationSpec.defaultContainerCommand());
            // Only CMD is replaced, so the image's entrypoint still runs first with $1 set to
            // /bin/sh. Entrypoints that gate first-run setup on $1 matching the default command
            // (see defaultContainerEntrypoint()) would skip it, so re-invoke the entrypoint here
            // with the default command as its argument.
            String entrypointPath = applicationSpec.defaultContainerEntrypoint();
            String exec = entrypointPath != null && !entrypointPath.isBlank()
                ? "exec " + entrypointPath + " " + defaultCommand
                : "exec " + defaultCommand;
            String banner = "echo '----------------------------------------------------------------' && "
                + "echo 'CloudForge: initial admin password (printed once at container start)' && "
                + "echo \"  " + passwordEnvVar + "=$" + passwordEnvVar + "\" && "
                + "echo '----------------------------------------------------------------'";
            containerOptionsBuilder.command(List.of("/bin/sh", "-c", banner + " && " + exec));
            commandConfigured = true;
            LOG.info("✅ Configured initial-admin-password startup banner for " + applicationSpec.applicationId());
        }

        // Check if we need SAML certificate init container
        // Uses OidcIntegration.needsSamlCertificate() to determine if app needs SAML cert
        if (authMode == AuthMode.APPLICATION_OIDC && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();

            if (oidcIntegration != null && oidcIntegration.needsSamlCertificate()) {
                // Get the SAML metadata URL from annotated field (set by CognitoSamlFactory)
                if (samlIdpMetadataUrl != null) {
                    samlMetadataUrl = samlIdpMetadataUrl;
                    needsSamlCertInit = true;

                    // Get application-specific SAML certificate paths from OidcIntegration
                    samlCertMountPath = oidcIntegration.getSamlCertificateMountPath();
                    samlCertPath = oidcIntegration.getSamlCertificateFilePath();
                    samlCertEnvVar = oidcIntegration.getSamlCertificateEnvVar();

                    LOG.info(applicationSpec.applicationId() + " SAML: Will create init container to fetch IdP certificate");
                    LOG.info("  Metadata URL: " + samlMetadataUrl);
                    LOG.info("  Certificate mount path: " + samlCertMountPath);
                    LOG.info("  Certificate file path: " + samlCertPath);

                    // Add the certificate file path to environment using app-specific env var
                    if (samlCertEnvVar != null) {
                        environment.put(samlCertEnvVar, samlCertPath);
                        LOG.info("  Certificate env var: " + samlCertEnvVar + "=" + samlCertPath);
                    }

                    // Add a volume for the SAML certificate
                    fargateTaskDef.addVolume(software.amazon.awscdk.services.ecs.Volume.builder()
                        .name(samlCertVolumeName)
                        .build());
                    LOG.info("  ✅ Added shared volume '" + samlCertVolumeName + "' for SAML certificate");
                } else {
                    LOG.warning(applicationSpec.applicationId() + " SAML: No metadata URL available - certificate cannot be fetched");
                }
            }
        }

        // Create init container for SAML certificate if needed
        final String finalSamlMetadataUrl = samlMetadataUrl;
        if (needsSamlCertInit && finalSamlMetadataUrl != null) {
            // Create init container that fetches SAML IdP certificate from metadata URL
            // Uses alpine/curl image with xmllint to parse the metadata XML
            String initCommand = String.format(
                "set -e; " +
                "echo 'Fetching SAML IdP certificate from metadata URL...'; " +
                "curl -s '%s' > /tmp/metadata.xml; " +
                "echo 'Extracting X509Certificate from metadata...'; " +
                // Extract the certificate using grep and sed (simpler than xmllint)
                "grep -oP '(?<=<ds:X509Certificate>)[^<]+' /tmp/metadata.xml | head -1 > /tmp/cert.b64; " +
                "echo '-----BEGIN CERTIFICATE-----' > %s; " +
                "cat /tmp/cert.b64 >> %s; " +
                "echo '' >> %s; " +  // Ensure newline before END
                "echo '-----END CERTIFICATE-----' >> %s; " +
                "chmod 644 %s; " +
                "echo 'SAML IdP certificate saved to %s'; " +
                "cat %s",
                finalSamlMetadataUrl,
                samlCertPath, samlCertPath, samlCertPath, samlCertPath, samlCertPath,
                samlCertPath, samlCertPath
            );

            ContainerDefinition initContainer = fargateTaskDef.addContainer("SamlCertInit",
                ContainerDefinitionOptions.builder()
                    .containerName("saml-cert-init")
                    .image(ContainerImage.fromRegistry("alpine/curl:latest"))
                    .essential(false)  // Init container - not essential after completion
                    .command(List.of("/bin/sh", "-c", initCommand))
                    .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(logs)
                        .streamPrefix("saml-init")
                        .build()))
                    .build());

            // Mount the shared volume for the certificate
            initContainer.addMountPoints(MountPoint.builder()
                .containerPath(samlCertMountPath)
                .sourceVolume(samlCertVolumeName)
                .readOnly(false)
                .build());

            LOG.info("  ✅ Created SAML certificate init container");
        }

        ContainerDefinition container = fargateTaskDef.addContainer(getNode().getId() + "Container",
                containerOptionsBuilder.build());

        // Add dependency on init container after main container is created
        final String finalSamlCertMountPath = samlCertMountPath;
        if (needsSamlCertInit && finalSamlMetadataUrl != null) {
            // Get the init container we created earlier
            ContainerDefinition initContainer = fargateTaskDef.findContainer("saml-cert-init");
            if (initContainer != null) {
                container.addContainerDependencies(ContainerDependency.builder()
                    .container(initContainer)
                    .condition(ContainerDependencyCondition.SUCCESS)
                    .build());
                LOG.info("  ✅ Configured main container to depend on SAML init container");

                // Also mount the shared volume on the main container
                // Use the same mount path as init container
                container.addMountPoints(MountPoint.builder()
                    .containerPath(finalSamlCertMountPath)
                    .sourceVolume(samlCertVolumeName)
                    .readOnly(true)  // Main container only reads the certificate
                    .build());
                LOG.info("  ✅ Mounted SAML certificate volume at " + finalSamlCertMountPath);
            }
        }

        container.addPortMappings(PortMapping
                .builder()
                .containerPort(appPort)
                .build());

        // Add optional port mappings based on deployment configuration
        // These ports are NOT exposed by default - must be explicitly enabled
        if (applicationSpec != null) {
            for (ApplicationSpec.OptionalPort optionalPort : applicationSpec.optionalPorts()) {
                if (isOptionalPortEnabled(optionalPort.configKey())) {
                    container.addPortMappings(PortMapping.builder()
                        .containerPort(optionalPort.port())
                        .protocol(optionalPort.protocol().equals("udp") ? Protocol.UDP : Protocol.TCP)
                        .build());
                    LOG.info("  ✅ Added optional port mapping: " + optionalPort.port() + "/" +
                             optionalPort.protocol() + " (" + optionalPort.service() + ")");
                }
            }
        }

        container.addMountPoints(MountPoint
                .builder()
                .containerPath(containerPath)
                .sourceVolume(volumeName)
                .readOnly(false)
                .build());

        // Same-task sidecar containers (see ApplicationSpec.SidecarContainer) —
        // empty by default, opt-in per application. Always-running, essential=true:
        // unlike the SAML init container above, a sidecar has no exit condition, so its own
        // death is treated the same as the main container's — ECS replaces the whole task.
        if (applicationSpec != null) {
            for (ApplicationSpec.SidecarContainer sidecar : applicationSpec.sidecarContainers()) {
                ContainerDefinitionOptions.Builder sidecarOptions = ContainerDefinitionOptions.builder()
                        .containerName(sidecar.containerName())
                        .image(ContainerImage.fromRegistry(sidecar.image()))
                        .essential(true)
                        .environment(sidecar.environment())
                        .healthCheck(HealthCheck.builder()
                            .command(sidecar.healthCheckCommand())
                            .interval(Duration.seconds(30))
                            .timeout(Duration.seconds(5))
                            .retries(3)
                            .startPeriod(Duration.seconds(10))
                            .build())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                            .logGroup(logs)
                            .streamPrefix(sidecar.containerName())
                            .build()));
                // A hard memory ceiling, when the sidecar asks for one -- see
                // ApplicationSpec.SidecarContainer#memoryLimitMiB's own javadoc for why a sidecar
                // running its own JVM (or one that spawns a further child process, like
                // cloudforge-synth-service's jsii/Node) needs a guaranteed floor rather than
                // sharing the task's memory pool with no minimum.
                if (sidecar.memoryLimitMiB() != null) {
                    sidecarOptions.memoryLimitMiB(sidecar.memoryLimitMiB());
                }
                ContainerDefinition sidecarContainer =
                    fargateTaskDef.addContainer(sidecar.containerName(), sidecarOptions.build());
                sidecarContainer.addPortMappings(PortMapping.builder()
                    .containerPort(sidecar.containerPort())
                    .build());

                // Main container waits for the sidecar to report HEALTHY (not just running)
                // before ECS starts it, since the main container's first request to it would
                // otherwise race the sidecar's own JVM/HTTP-server startup.
                container.addContainerDependencies(ContainerDependency.builder()
                    .container(sidecarContainer)
                    .condition(ContainerDependencyCondition.HEALTHY)
                    .build());
                LOG.info("  ✅ Added sidecar container '" + sidecar.containerName() + "' on port " + sidecar.containerPort());
            }
        }

        ctx.container.set(container);
    }

    /**
     * Check if an optional port is enabled based on the config key.
     * Maps config keys like "enableSmtp" to the corresponding annotated field.
     */
    private boolean isOptionalPortEnabled(String configKey) {
        return switch (configKey) {
            case "enableAgents" -> Boolean.TRUE.equals(enableAgents);
            case "enableSsh" -> Boolean.TRUE.equals(enableSsh);
            case "enableSmtp" -> Boolean.TRUE.equals(enableSmtp);
            case "enableSmtps" -> Boolean.TRUE.equals(enableSmtps);
            case "enableClustering" -> Boolean.TRUE.equals(enableClustering);
            case "enableDockerRegistry" -> Boolean.TRUE.equals(enableDockerRegistry);
            case "enableMetrics" -> Boolean.TRUE.equals(enableMetrics);
            case "enableNotary" -> Boolean.TRUE.equals(enableNotary);
            case "enableTrivy" -> Boolean.TRUE.equals(enableTrivy);
            case "enableSentinel" -> Boolean.TRUE.equals(enableSentinel);
            case "enableCluster" -> Boolean.TRUE.equals(enableCluster);
            default -> {
                LOG.warning("Unknown optional port config key: " + configKey);
                yield false;
            }
        };
    }

}
