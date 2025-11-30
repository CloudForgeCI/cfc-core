package com.cloudforge.core.interfaces;

/**
 * Application-level OIDC integration interface.
 *
 * <p>This interface defines how applications integrate with OIDC providers
 * for authentication. Each application implements this to configure its
 * specific OIDC plugin/module.</p>
 *
 * <p><strong>CloudForge supports two separate authentication systems:</strong></p>
 * <ul>
 *   <li><strong>Amazon Cognito</strong> - Standalone user directory with OIDC</li>
 *   <li><strong>IAM Identity Center</strong> - Enterprise SSO with SAML/OIDC</li>
 * </ul>
 *
 * <p>These are completely separate systems and cannot be mixed.</p>
 *
 * @see ApplicationSpec#supportsOidcIntegration()
 */
public interface OidcIntegration {

    /**
     * Returns whether this application supports OIDC integration.
     *
     * @return true if application has OIDC support
     */
    boolean isSupported();

    /**
     * Returns the OIDC integration method for this application.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>jenkins: OIDC Plugin</li>
     *   <li>gitlab: Built-in OmniAuth</li>
     *   <li>grafana: Built-in generic_oauth</li>
     *   <li>sonarqube: OIDC Plugin</li>
     * </ul>
     *
     * @return integration method description
     */
    String getIntegrationMethod();

    /**
     * Returns environment variables needed for OIDC configuration.
     *
     * <p>These are passed to the container or EC2 userdata script.</p>
     *
     * <p>Example for Grafana:</p>
     * <pre>
     * GF_AUTH_GENERIC_OAUTH_ENABLED=true
     * GF_AUTH_GENERIC_OAUTH_NAME=Cognito
     * GF_AUTH_GENERIC_OAUTH_CLIENT_ID=${clientId}
     * GF_AUTH_GENERIC_OAUTH_AUTH_URL=${authUrl}
     * </pre>
     *
     * @param config OIDC configuration from provider
     * @return map of environment variable name to value
     */
    java.util.Map<String, String> getEnvironmentVariables(OidcConfiguration config);

    /**
     * Returns configuration file content for OIDC setup.
     *
     * <p>Some applications require configuration files instead of environment variables.</p>
     *
     * <p>Example for GitLab gitlab.rb:</p>
     * <pre>
     * gitlab_rails['omniauth_enabled'] = true
     * gitlab_rails['omniauth_providers'] = [
     *   {
     *     name: 'openid_connect',
     *     args: { ... }
     *   }
     * ]
     * </pre>
     *
     * @param config OIDC configuration from provider
     * @return configuration file content (optional)
     */
    default String getConfigurationFile(OidcConfiguration config) {
        return null;
    }

    /**
     * Returns the file path where configuration should be written.
     *
     * <p>Only used if getConfigurationFile() returns non-null.</p>
     *
     * @return configuration file path (optional)
     */
    default String getConfigurationFilePath() {
        return null;
    }

    /**
     * Returns UserData commands for setting up OIDC integration.
     *
     * <p>These commands are added to the EC2 userdata script to configure
     * OIDC integration during instance initialization.</p>
     *
     * @param config OIDC configuration from provider
     * @param context EC2 context with stack information
     * @return list of shell commands
     */
    java.util.List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context);

    /**
     * Returns post-deployment instructions for completing OIDC setup.
     *
     * <p>Some applications require manual steps after deployment (e.g., installing plugins).</p>
     *
     * @return human-readable instructions (optional)
     */
    default String getPostDeploymentInstructions() {
        return null;
    }

    /**
     * Returns the application startup command for Fargate containers.
     *
     * <p>This command is used to start the application after the OIDC configuration
     * file has been created. Each application has a different startup script.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Jenkins: /usr/local/bin/jenkins.sh</li>
     *   <li>GitLab: /assets/wrapper</li>
     *   <li>Grafana: /run.sh</li>
     * </ul>
     *
     * @return startup command path
     */
    default String getContainerStartupCommand() {
        // Default: assume standard Unix convention
        return "/usr/local/bin/start.sh";
    }
}
