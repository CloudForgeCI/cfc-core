package com.cloudforge.core.oidc;

import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.OidcConfiguration;
import com.cloudforge.core.interfaces.OidcIntegration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC integration for Jenkins using the OpenID Connect Authentication Plugin.
 *
 * <p>Jenkins requires the "oic-auth" plugin for OIDC support. This integration
 * configures Jenkins to authenticate users against AWS Cognito or IAM Identity Center.</p>
 *
 * <p><strong>Supported OIDC Providers:</strong></p>
 * <ul>
 *   <li>Amazon Cognito</li>
 *   <li>IAM Identity Center</li>
 *   <li>Any OIDC-compliant provider</li>
 * </ul>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Auto-create users on first login</li>
 *   <li>Group/role mapping from OIDC claims</li>
 *   <li>Full user information synchronization</li>
 *   <li>Token-based session management</li>
 * </ul>
 *
 * <p><strong>Required Plugin:</strong></p>
 * <ul>
 *   <li>OpenID Connect Authentication Plugin (oic-auth)</li>
 * </ul>
 *
 * @see <a href="https://plugins.jenkins.io/oic-auth/">Jenkins OIDC Plugin Documentation</a>
 */
public class JenkinsOidcIntegration implements OidcIntegration {

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getIntegrationMethod() {
        return "OpenID Connect Authentication Plugin (oic-auth)";
    }

    @Override
    public Map<String, String> getEnvironmentVariables(OidcConfiguration config) {
        // Jenkins OIDC is configured via Jenkins Configuration as Code (JCasC)
        Map<String, String> env = new HashMap<>();

        // Tell Jenkins Configuration as Code where to find our OIDC config file
        env.put("CASC_JENKINS_CONFIG", "/var/jenkins_home/casc_configs");

        // Note: JENKINS_OIDC_CLIENT_SECRET is provided by ContainerFactory as an ECS secret
        // mounted from AWS Secrets Manager at runtime. No need to set it here.

        return env;
    }

    @Override
    public String getConfigurationFile(OidcConfiguration config) {
        // Check if group-based access control is enabled
        boolean groupsEnabled = config.isGroupBasedAccessEnabled();

        // Get group names from configuration
        String adminGroup = (config.getAdminGroupName() != null && !config.getAdminGroupName().isEmpty())
            ? config.getAdminGroupName()
            : "admins";
        String developerGroup = (config.getDeveloperGroupName() != null && !config.getDeveloperGroupName().isEmpty())
            ? config.getDeveloperGroupName()
            : "developers";
        String viewerGroup = (config.getViewerGroupName() != null && !config.getViewerGroupName().isEmpty())
            ? config.getViewerGroupName()
            : "viewers";

        // Get Jenkins URL from OidcConfiguration (supports both custom domain and ALB URL)
        String jenkinsUrl = config.getApplicationUrl();

        // Build Jenkins Configuration as Code (JCasC) YAML
        // Reference: https://github.com/jenkinsci/oic-auth-plugin/blob/master/docs/configuration/README.md
        // serverConfiguration contains ONLY wellKnown (or manual) - nothing else
        // clientId, clientSecret, userNameField etc. go at the oic level
        StringBuilder jcascConfig = new StringBuilder();
        jcascConfig.append("# Jenkins OIDC Configuration\n");
        jcascConfig.append("# Jenkins Configuration as Code (JCasC)\n");
        if (!groupsEnabled) {
            jcascConfig.append("# Group-based access control: DISABLED\n");
            jcascConfig.append("# All authenticated users will have full admin access\n");
        }
        jcascConfig.append("# Added by CloudForge\n");
        jcascConfig.append("\n");
        jcascConfig.append("jenkins:\n");
        jcascConfig.append("  securityRealm:\n");
        jcascConfig.append("    oic:\n");
        jcascConfig.append("      serverConfiguration:\n");
        // Use manual configuration to support Cognito's custom logout URL format
        // Reference: https://github.com/jenkinsci/oic-auth-plugin/issues/95
        jcascConfig.append("        manual:\n");
        jcascConfig.append(String.format("          authorizationServerUrl: \"%s\"\n", config.getAuthorizationEndpoint()));
        jcascConfig.append(String.format("          tokenServerUrl: \"%s\"\n", config.getTokenEndpoint()));
        jcascConfig.append(String.format("          userInfoServerUrl: \"%s\"\n", config.getUserInfoEndpoint()));
        jcascConfig.append(String.format("          jwksServerUrl: \"%s\"\n", config.getJwksUri()));
        jcascConfig.append(String.format("          issuer: \"%s\"\n", config.getIssuerUrl()));
        jcascConfig.append(String.format("          scopes: \"%s\"\n", config.getScopes()));
        // Configure Cognito logout with pre-formatted URL including required parameters
        String logoutEndpoint = config.getLogoutEndpoint();
        if (logoutEndpoint != null && !logoutEndpoint.isEmpty() && jenkinsUrl != null) {
            String cognitoLogoutUrl = logoutEndpoint + "?client_id=" + config.getClientId()
                + "&logout_uri=" + jenkinsUrl + "/";
            jcascConfig.append(String.format("          endSessionUrl: \"%s\"\n", cognitoLogoutUrl));
        }
        jcascConfig.append(String.format("      clientId: \"%s\"\n", config.getClientId()));
        jcascConfig.append("      clientSecret: \"${JENKINS_OIDC_CLIENT_SECRET}\"\n");
        jcascConfig.append(String.format("      userNameField: %s\n", sanitizeJmesPath(config.getUsernameClaim())));
        jcascConfig.append("      fullNameFieldName: \"name\"\n");
        jcascConfig.append("      emailFieldName: \"email\"\n");
        jcascConfig.append(String.format("      groupsFieldName: %s\n", sanitizeJmesPath(config.getGroupsClaim())));
        jcascConfig.append("      disableSslVerification: false\n");
        jcascConfig.append("      logoutFromOpenidProvider: true\n");
        if (jenkinsUrl != null) {
            jcascConfig.append(String.format("      postLogoutRedirectUrl: \"%s/\"\n", jenkinsUrl));
        } else {
            jcascConfig.append("      postLogoutRedirectUrl: \"\"\n");
        }
        jcascConfig.append("\n");
        jcascConfig.append("  authorizationStrategy:\n");

        if (groupsEnabled) {
            // Group-based authorization: use project matrix with group permissions
            jcascConfig.append("    projectMatrix:\n");
            jcascConfig.append("      permissions:\n");
            jcascConfig.append("        # Admin group gets full permissions (from OIDC configuration)\n");
            jcascConfig.append(String.format("        - \"Overall/Administer:%s\"\n", adminGroup));
            jcascConfig.append(String.format("        - \"Overall/Read:%s\"\n", adminGroup));
            jcascConfig.append("        # Developer group gets build and configure permissions\n");
            jcascConfig.append(String.format("        - \"Overall/Read:%s\"\n", developerGroup));
            jcascConfig.append(String.format("        - \"Job/Build:%s\"\n", developerGroup));
            jcascConfig.append(String.format("        - \"Job/Configure:%s\"\n", developerGroup));
            jcascConfig.append(String.format("        - \"Job/Create:%s\"\n", developerGroup));
            jcascConfig.append(String.format("        - \"Job/Read:%s\"\n", developerGroup));
            jcascConfig.append(String.format("        - \"Job/Workspace:%s\"\n", developerGroup));
            jcascConfig.append("        # Viewer group gets read-only permissions\n");
            jcascConfig.append(String.format("        - \"Overall/Read:%s\"\n", viewerGroup));
            jcascConfig.append(String.format("        - \"Job/Read:%s\"\n", viewerGroup));
            jcascConfig.append("        # Deny anonymous access\n");
            jcascConfig.append("        - \"Overall/Read:authenticated\"\n");
        } else {
            // Groups disabled: grant full admin access to all authenticated users
            jcascConfig.append("    loggedInUsersCanDoAnything:\n");
            jcascConfig.append("      allowAnonymousRead: false\n");
        }
        jcascConfig.append("\n");
        jcascConfig.append("# Configure Jenkins URL and audit trail\n");
        jcascConfig.append("unclassified:\n");

        // Add Jenkins URL configuration if available (required for email notifications, PR status, BUILD_URL)
        if (jenkinsUrl != null && !jenkinsUrl.isEmpty()) {
            jcascConfig.append("  location:\n");
            jcascConfig.append(String.format("    url: \"%s\"\n", jenkinsUrl));
        }

        jcascConfig.append("  audit-trail:\n");
        jcascConfig.append("    logBuildCause: true\n");
        jcascConfig.append("    pattern: \".*/.*\"\n");
        jcascConfig.append("    loggers:\n");
        jcascConfig.append("      - logFile:\n");
        jcascConfig.append("          log: /var/jenkins_home/logs/audit.log\n");
        jcascConfig.append("          limit: 10\n");
        jcascConfig.append("          count: 5\n");

        return jcascConfig.toString();
    }

    @Override
    public String getConfigurationFilePath() {
        return "/var/jenkins_home/casc_configs/oidc.yaml";
    }

    @Override
    public List<String> getUserDataCommands(OidcConfiguration config, Ec2Context context) {
        List<String> commands = new ArrayList<>();

        commands.add("# Configure Jenkins OIDC integration");
        commands.add("# Retrieve client secret from AWS Secrets Manager");
        commands.add("export JENKINS_OIDC_CLIENT_SECRET=$(aws secretsmanager get-secret-value \\");
        commands.add("  --secret-id " + config.getClientSecretArn() + " \\");
        commands.add("  --query SecretString --output text)");
        commands.add("");

        // Install OIDC plugin and compliance plugins
        commands.add("# Install Jenkins OIDC and compliance plugins");
        commands.add("# This will be done via Jenkins plugin installation script");
        commands.add("cat > /tmp/install-oidc-plugin.sh <<'EOFPLUGIN'");
        commands.add("#!/bin/bash");
        commands.add("# Wait for Jenkins to be ready");
        commands.add("until curl -s http://localhost:8080/login >/dev/null; do");
        commands.add("  echo 'Waiting for Jenkins to start...'");
        commands.add("  sleep 10");
        commands.add("done");
        commands.add("");
        commands.add("# Install OIDC plugin and compliance plugins using Jenkins CLI");
        commands.add("JENKINS_URL=http://localhost:8080");
        commands.add("JENKINS_CLI=\"java -jar /var/jenkins_home/jenkins-cli.jar -s ${JENKINS_URL}\"");
        commands.add("");
        commands.add("# Download Jenkins CLI");
        commands.add("curl -s ${JENKINS_URL}/jnlpJars/jenkins-cli.jar -o /var/jenkins_home/jenkins-cli.jar");
        commands.add("");
        commands.add("# Install OIDC authentication plugin");
        commands.add("echo 'Installing OIDC authentication plugin...'");
        commands.add("${JENKINS_CLI} install-plugin oic-auth");
        commands.add("");
        commands.add("# Install compliance and security plugins");
        commands.add("echo 'Installing compliance and security plugins...'");
        commands.add("${JENKINS_CLI} install-plugin audit-trail");
        commands.add("${JENKINS_CLI} install-plugin job-dsl");
        commands.add("${JENKINS_CLI} install-plugin configuration-as-code");
        commands.add("${JENKINS_CLI} install-plugin role-strategy");
        commands.add("${JENKINS_CLI} install-plugin credentials-binding");
        commands.add("${JENKINS_CLI} install-plugin matrix-auth");
        commands.add("");
        commands.add("# Restart Jenkins to activate plugins");
        commands.add("echo 'Restarting Jenkins to activate plugins...'");
        commands.add("${JENKINS_CLI} safe-restart");
        commands.add("EOFPLUGIN");
        commands.add("chmod +x /tmp/install-oidc-plugin.sh");
        commands.add("");

        // Create JCasC configuration directory
        commands.add("# Create Jenkins Configuration as Code directory");
        commands.add("mkdir -p /var/jenkins_home/casc_configs");
        commands.add("");

        // Create OIDC configuration file
        commands.add("# Create OIDC configuration file");
        commands.add("cat > /var/jenkins_home/casc_configs/oidc.yaml <<'EOFJENKINS'");
        commands.add("# Jenkins OIDC Configuration");
        commands.add("# Jenkins Configuration as Code (JCasC)");
        commands.add("# Added by CloudForge");
        commands.add("");
        commands.add("jenkins:");

        // Add Jenkins URL configuration if available (required for email notifications, PR status, BUILD_URL)
        String jenkinsUrl = config.getApplicationUrl();
        if (jenkinsUrl != null && !jenkinsUrl.isEmpty()) {
            commands.add("  # Configure Jenkins root URL for reverse proxy");
            commands.add("  location:");
            commands.add("    url: \"" + jenkinsUrl + "\"");
            commands.add("");
        }

        // Use manual configuration to support Cognito's custom logout URL format
        // Reference: https://github.com/jenkinsci/oic-auth-plugin/issues/95
        commands.add("  securityRealm:");
        commands.add("    oic:");
        commands.add("      serverConfiguration:");
        commands.add("        manual:");
        commands.add("          authorizationServerUrl: \"" + config.getAuthorizationEndpoint() + "\"");
        commands.add("          tokenServerUrl: \"" + config.getTokenEndpoint() + "\"");
        commands.add("          userInfoServerUrl: \"" + config.getUserInfoEndpoint() + "\"");
        commands.add("          jwksServerUrl: \"" + config.getJwksUri() + "\"");
        commands.add("          issuer: \"" + config.getIssuerUrl() + "\"");
        commands.add("          scopes: \"" + config.getScopes() + "\"");
        // Configure Cognito logout with pre-formatted URL including required parameters
        String logoutEndpointEc2 = config.getLogoutEndpoint();
        if (logoutEndpointEc2 != null && !logoutEndpointEc2.isEmpty() && jenkinsUrl != null) {
            String cognitoLogoutUrlEc2 = logoutEndpointEc2 + "?client_id=" + config.getClientId() + "&logout_uri=" + jenkinsUrl + "/";
            commands.add("          endSessionUrl: \"" + cognitoLogoutUrlEc2 + "\"");
        }
        commands.add("      clientId: \"" + config.getClientId() + "\"");
        commands.add("      clientSecret: \"${JENKINS_OIDC_CLIENT_SECRET}\"");
        commands.add("      userNameField: \"" + config.getUsernameClaim() + "\"");
        commands.add("      fullNameFieldName: \"name\"");
        commands.add("      emailFieldName: \"email\"");
        commands.add("      groupsFieldName: \"" + config.getGroupsClaim() + "\"");
        commands.add("      disableSslVerification: false");
        commands.add("      logoutFromOpenidProvider: true");
        if (jenkinsUrl != null) {
            commands.add("      postLogoutRedirectUrl: \"" + jenkinsUrl + "/\"");
        } else {
            commands.add("      postLogoutRedirectUrl: \"\"");
        }
        commands.add("      escapeHatchEnabled: true");
        commands.add("      escapeHatchUsername: \"admin\"");
        commands.add("      escapeHatchSecret: \"admin\"");
        commands.add("");

        // Check if group-based access control is enabled
        boolean groupsEnabled = config.isGroupBasedAccessEnabled();

        // Get group names from configuration
        String adminGroup = (config.getAdminGroupName() != null && !config.getAdminGroupName().isEmpty())
            ? config.getAdminGroupName()
            : "admins";
        String developerGroup = (config.getDeveloperGroupName() != null && !config.getDeveloperGroupName().isEmpty())
            ? config.getDeveloperGroupName()
            : "developers";
        String viewerGroup = (config.getViewerGroupName() != null && !config.getViewerGroupName().isEmpty())
            ? config.getViewerGroupName()
            : "viewers";

        commands.add("  authorizationStrategy:");

        if (groupsEnabled) {
            // Group-based authorization: use project matrix with group permissions
            commands.add("    projectMatrix:");
            commands.add("      permissions:");
            commands.add("        # Admin group gets full permissions (from OIDC configuration)");
            commands.add("        - \"Overall/Administer:" + adminGroup + "\"");
            commands.add("        - \"Overall/Read:" + adminGroup + "\"");
            commands.add("        # Developer group gets build and configure permissions");
            commands.add("        - \"Overall/Read:" + developerGroup + "\"");
            commands.add("        - \"Job/Build:" + developerGroup + "\"");
            commands.add("        - \"Job/Configure:" + developerGroup + "\"");
            commands.add("        - \"Job/Create:" + developerGroup + "\"");
            commands.add("        - \"Job/Read:" + developerGroup + "\"");
            commands.add("        - \"Job/Workspace:" + developerGroup + "\"");
            commands.add("        # Viewer group gets read-only permissions");
            commands.add("        - \"Overall/Read:" + viewerGroup + "\"");
            commands.add("        - \"Job/Read:" + viewerGroup + "\"");
            commands.add("        # Deny anonymous access");
            commands.add("        - \"Overall/Read:authenticated\"");
        } else {
            // Groups disabled: grant full admin access to all authenticated users
            commands.add("    loggedInUsersCanDoAnything:");
            commands.add("      allowAnonymousRead: false");
        }
        commands.add("");
        commands.add("# Configure audit trail for compliance");
        commands.add("unclassified:");
        commands.add("  auditTrail:");
        commands.add("    logBuildCause: true");
        commands.add("    pattern: \".*/.*\"");
        commands.add("    loggers:");
        commands.add("      - logFile:");
        commands.add("          log: /var/jenkins_home/logs/audit.log");
        commands.add("          limit: 10");
        commands.add("          count: 5");
        commands.add("EOFJENKINS");
        commands.add("");

        // Replace secret placeholder with actual value
        commands.add("# Replace secret placeholder with actual value");
        commands.add("sed -i \"s|\\${JENKINS_OIDC_CLIENT_SECRET}|$JENKINS_OIDC_CLIENT_SECRET|g\" /var/jenkins_home/casc_configs/oidc.yaml");
        commands.add("");

        // Set proper ownership
        commands.add("# Set proper ownership for Jenkins files");
        commands.add("chown -R 1000:1000 /var/jenkins_home/casc_configs");
        commands.add("");

        // Note about plugin installation
        commands.add("# Note: Run /tmp/install-oidc-plugin.sh after Jenkins starts");
        commands.add("echo 'Jenkins OIDC configuration created' >> /var/log/userdata.log");
        commands.add("echo 'Run /tmp/install-oidc-plugin.sh to install OIDC plugin' >> /var/log/userdata.log");

        return commands;
    }

    @Override
    public String getContainerStartupCommand() {
        // Install required plugins using jenkins-plugin-cli, then start Jenkins
        // This ensures plugins are installed even if Jenkins home already exists
        return "jenkins-plugin-cli --plugins configuration-as-code oic-auth matrix-auth audit-trail && /usr/local/bin/jenkins.sh";
    }

    @Override
    public String getPostDeploymentInstructions() {
        return """
                Jenkins OIDC Integration Setup
                ===============================

                IMPORTANT: Manual steps required!

                1. Install the OIDC plugin:
                   - SSH into the Jenkins EC2 instance
                   - Run: /tmp/install-oidc-plugin.sh
                   - Wait for Jenkins to restart

                2. Verify OIDC configuration:
                   - Access Jenkins at: https://{your-domain}:8080
                   - You should see "Login with OpenID Connect" option

                3. First login:
                   - Click "Login with OpenID Connect"
                   - Authenticate with your OIDC provider (Cognito or Identity Center)
                   - Jenkins will auto-create your user account

                4. Escape Hatch (Emergency Access):
                   - If OIDC fails, you can still login with:
                     Username: admin
                     Password: admin
                   - Change these credentials immediately!

                5. Configure Authorization:
                   - Go to Manage Jenkins > Configure Global Security
                   - Adjust matrix-based security as needed
                   - Map OIDC groups to Jenkins roles

                Configuration Files:
                - OIDC Config: /var/jenkins_home/casc_configs/oidc.yaml
                - Plugin Install Script: /tmp/install-oidc-plugin.sh

                Troubleshooting:
                - Check logs: docker logs jenkins
                - Verify OIDC plugin installed: Manage Jenkins > Manage Plugins
                - Test OIDC endpoints are accessible from Jenkins container
                """;
    }

    /**
     * Sanitizes OIDC claim names to be valid JMESPath expressions.
     *
     * <p>The Jenkins OIDC plugin uses JMESPath to extract claim values from OIDC tokens.
     * However, Cognito uses claim names with colons (e.g., "cognito:username", "cognito:groups")
     * which are invalid in bare JMESPath identifier syntax.</p>
     *
     * <p>JMESPath requires quoted identifiers for names with special characters.
     * The syntax is: "identifier-with-special-chars"</p>
     *
     * @param claimName the claim name from OIDC configuration
     * @return sanitized claim name that's valid JMESPath, properly quoted for YAML
     */
    private String sanitizeJmesPath(String claimName) {
        if (claimName == null || claimName.isEmpty()) {
            return claimName;
        }

        // JMESPath quoted identifier syntax: "name" for names with special chars like colons
        // In YAML, we need to wrap in single quotes to preserve the double quotes
        // Result in YAML: '"cognito:groups"' which JMESPath parses as identifier "cognito:groups"
        if (claimName.contains(":") || claimName.contains("-") || claimName.contains(" ")) {
            return "'\"" + claimName + "\"'";
        }

        // For simple claim names, just quote for YAML
        return "\"" + claimName + "\"";
    }
}
