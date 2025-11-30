package com.cloudforgeci.api.storage;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
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
            // All applications use the standard 3-parameter method
            // Applications implementing DatabaseSpec have overloaded this to check for database connection internally
            environment.putAll(applicationSpec.containerEnvironmentVariables(fqdn, sslEnabled, authMode));
        }

        // Collect ECS secrets (from Secrets Manager) to be mounted as environment variables
        Map<String, software.amazon.awscdk.services.ecs.Secret> ecsSecrets = new HashMap<>();

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
                    String clientSecretName = oidcConfig.getClientSecretArn();
                    if (clientSecretName != null && !clientSecretName.isEmpty()) {
                        LOG.info("  Mounting OIDC client secret from Secrets Manager: " + clientSecretName);

                        // Construct the full secret ARN manually
                        // Format: arn:aws:secretsmanager:region:account:secret:name
                        // Use Stack.of() to get the stack's environment
                        software.amazon.awscdk.Stack stack = software.amazon.awscdk.Stack.of(this);
                        String secretArn = String.format(
                            "arn:aws:secretsmanager:%s:%s:secret:%s*",
                            stack.getRegion(),
                            stack.getAccount(),
                            clientSecretName
                        );

                        LOG.info("  Constructed secret ARN: " + secretArn);

                        // Import the secret by name
                        ISecret clientSecret = Secret.fromSecretNameV2(this, "OidcClientSecret", clientSecretName);

                        // Grant the ECS task execution role permission to read the secret
                        // This is required for ECS to pull the secret value at container startup
                        if (fargateTaskDef.getExecutionRole() != null) {
                            // Add explicit policy statement with manually constructed ARN
                            // This ensures the IAM permission is created in CloudFormation
                            fargateTaskDef.getExecutionRole().addToPrincipalPolicy(
                                software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                                    .sid("AllowReadOidcClientSecret")
                                    .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                                    .actions(java.util.List.of(
                                        "secretsmanager:GetSecretValue",
                                        "secretsmanager:DescribeSecret"
                                    ))
                                    .resources(java.util.List.of(secretArn))
                                    .build()
                            );

                            LOG.info("  ✅ Added explicit IAM policy for secret access with ARN: " + secretArn);
                        } else {
                            LOG.warning("  ⚠️  Task execution role not found - cannot grant secret read permission");
                        }

                        // Add as ECS secret (mounted as environment variable at runtime)
                        ecsSecrets.put("JENKINS_OIDC_CLIENT_SECRET",
                                      software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(clientSecret));

                        LOG.info("  ✅ OIDC client secret will be available as JENKINS_OIDC_CLIENT_SECRET environment variable");
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
                    String fullCommand = String.format(
                        "mkdir -p $(dirname %s) && " +
                        "cat > %s <<'EOFCASC'\n%s\nEOFCASC && " +
                        "exec %s",
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
