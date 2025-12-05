package com.cloudforgeci.api.storage;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.OidcIntegration;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
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

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @DeploymentContext("authMode")
    private String authMode;

    @SystemContext("fargateTaskDef")
    private TaskDefinition fargateTaskDef;

    @SystemContext("logs")
    private LogGroup logs;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

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
            if (applicationSpec instanceof DatabaseSpec) {
                ctx.dbConnection.get().ifPresentOrElse(
                    dbConn -> {
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
                                applicationSpec, fqdn, sslEnabled, authMode, dbConn
                            );
                            environment.putAll(dbEnv);
                        } catch (NoSuchMethodException e) {
                            // Application doesn't have 4-parameter method, use standard 3-parameter
                            LOG.info("Application " + applicationSpec.applicationId() + " doesn't support database connection parameter");
                            environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode));
                        } catch (Exception e) {
                            LOG.warning("Error calling containerEnvironmentVariables with database connection: " + e.getMessage());
                            environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode));
                        }
                    },
                    () -> {
                        // No database connection - use embedded database fallback
                        LOG.info("No database connection - " + applicationSpec.applicationId() + " will use embedded database");
                        environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode));
                    }
                );
            } else {
                // Standard applications without database support
                environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode));
            }
        }

        // Collect ECS secrets (from Secrets Manager) to be mounted as environment variables
        Map<String, software.amazon.awscdk.services.ecs.Secret> ecsSecrets = new HashMap<>();

        // Add database password from Secrets Manager for applications with external database
        if (applicationSpec instanceof DatabaseSpec) {
            ctx.dbConnection.get().ifPresent(dbConn -> {
                LOG.info("Adding database password secret for " + applicationSpec.applicationId());

                // Extract secret name from ARN
                // ARN format: arn:aws:secretsmanager:region:account:secret:name-randomsuffix
                String secretArn = dbConn.passwordSecretArn();
                ISecret dbSecret = Secret.fromSecretCompleteArn(this, "DatabasePasswordSecret", secretArn);

                // Grant task execution role permission to read the secret
                if (fargateTaskDef.getExecutionRole() != null) {
                    fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                        software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                            .sid("AllowReadDatabasePassword")
                            .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                            .actions(java.util.List.of(
                                "secretsmanager:GetSecretValue",
                                "secretsmanager:DescribeSecret"
                            ))
                            .resources(java.util.List.of(secretArn))
                            .build()
                    );
                    LOG.info("  ✅ Added IAM policy for database password secret access");
                }

                // Map password to application-specific environment variable names
                // Different applications expect different env var names for the database password
                String appId = applicationSpec.applicationId();
                switch (appId) {
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
                        ecsSecrets.put("DATABASE_PASSWORD",
                                      software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                        LOG.info("  ✅ Database password mapped to DATABASE_PASSWORD");
                        break;
                    case "mattermost":
                        // Mattermost uses connection string with password embedded
                        ecsSecrets.put("GITLAB_DATABASE_PASSWORD",
                                      software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                        LOG.info("  ✅ Database password mapped to GITLAB_DATABASE_PASSWORD (for connection string)");
                        break;
                    default:
                        // Fallback - use generic name
                        ecsSecrets.put("DATABASE_PASSWORD",
                                      software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"));
                        LOG.info("  ✅ Database password mapped to DATABASE_PASSWORD (default)");
                }
            });
        }

        // Add OIDC environment variables if application-oidc mode is enabled
        if ("application-oidc".equals(authMode) && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            LOG.info("ContainerFactory: application-oidc mode detected for " + applicationSpec.applicationId());
            LOG.info("  Checking if applicationOidcConfig is available...");

            ctx.applicationOidcConfig.get().ifPresent(oidcConfig -> {
                LOG.info("  ✅ applicationOidcConfig found!");
                OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
                if (oidcIntegration != null) {
                    Map<String, String> oidcEnv = oidcIntegration.getEnvironmentVariables(oidcConfig);
                    environment.putAll(oidcEnv);
                    LOG.info("  Added " + oidcEnv.size() + " OIDC environment variables for " + applicationSpec.applicationId());

                    // Add OIDC client secret from Secrets Manager if available
                    String clientSecretArn = oidcConfig.getClientSecretArn();
                    if (clientSecretArn != null && !clientSecretArn.isEmpty()) {
                        LOG.info("  Mounting OIDC client secret from Secrets Manager for application: " + applicationSpec.applicationId());

                        // clientSecretArn contains COMPLETE ARN with suffix (same as RDS pattern)
                        // Example: arn:aws:secretsmanager:region:account:secret:name-AbCd12

                        // Import secret using complete ARN (same pattern as database password)
                        ISecret clientSecret = Secret.fromSecretCompleteArn(this, "OidcClientSecret", clientSecretArn);

                        // Grant the ECS task execution role permission to read the secret
                        if (fargateTaskDef.getExecutionRole() != null) {
                            fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                                software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                                    .sid("AllowReadOidcClientSecret")
                                    .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                                    .actions(java.util.List.of(
                                        "secretsmanager:GetSecretValue",
                                        "secretsmanager:DescribeSecret"
                                    ))
                                    .resources(java.util.List.of(clientSecretArn))
                                    .build()
                            );

                            LOG.info("  ✅ Added IAM policy for secret access");
                        } else {
                            LOG.warning("  ⚠️  Task execution role not found - cannot grant secret read permission");
                        }

                        // Add as ECS secret (mounted as environment variable at runtime)
                        // Use application-specific naming for the secret environment variable
                        String secretEnvVar = applicationSpec.applicationId().toUpperCase() + "_OIDC_CLIENT_SECRET";
                        ecsSecrets.put(secretEnvVar,
                                      software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));

                        LOG.info("  ✅ OIDC client secret will be available as " + secretEnvVar + " environment variable");
                    } else {
                        LOG.warning("  ⚠️  Client secret ARN not found in OIDC config - secret will not be mounted");
                    }
                } else {
                    LOG.warning("  ⚠️  OidcIntegration is null!");
                }
            });

            if (!ctx.applicationOidcConfig.get().isPresent()) {
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

        // Only set user if specified (some apps like GitLab need to run as root)
        if (containerUser != null) {
            containerOptionsBuilder.user(containerUser);
        }

        // Log container configuration
        if (!ecsSecrets.isEmpty()) {
            LOG.info("Container will have " + ecsSecrets.size() + " secret(s) mounted as environment variables");
        }

        // Add entrypoint/command override for application-oidc mode
        // This creates the OIDC config file before starting the application
        if ("application-oidc".equals(authMode) && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            LOG.info("ContainerFactory: Configuring OIDC entrypoint wrapper...");

            ctx.applicationOidcConfig.get().ifPresent(oidcConfig -> {
                OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
                if (oidcIntegration != null) {
                    String configFileContent = oidcIntegration.getConfigurationFile(oidcConfig);
                    String configFilePath = oidcIntegration.getConfigurationFilePath();
                    String startupCommand = oidcIntegration.getContainerStartupCommand();

                    LOG.info("  Config file path: " + configFilePath);
                    LOG.info("  Startup command: " + startupCommand);
                    LOG.info("  Config file length: " + (configFileContent != null ? configFileContent.length() + " chars" : "NULL"));

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
                    LOG.info("✅ Configured OIDC entrypoint wrapper for " + applicationSpec.applicationId());
                } else {
                    LOG.severe("❌ OidcIntegration is NULL - cannot configure entrypoint!");
                }
            });

            if (!ctx.applicationOidcConfig.get().isPresent()) {
                LOG.severe("❌ applicationOidcConfig NOT PRESENT - entrypoint wrapper NOT configured!");
            }
        }

        ContainerDefinition container = fargateTaskDef.addContainer(getNode().getId() + "Container",
                containerOptionsBuilder.build());

        container.addPortMappings(PortMapping
                .builder()
                .containerPort(appPort)
                .build());

        container.addMountPoints(MountPoint
                .builder()
                .containerPath(containerPath)
                .sourceVolume(volumeName)
                .readOnly(false)
                .build());
        ctx.container.set(container);
    }

}
