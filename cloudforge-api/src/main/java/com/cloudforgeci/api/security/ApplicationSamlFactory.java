package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.Slot;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Application SAML Factory - configures SAML authentication for applications.
 *
 * <p>This factory handles SAML configuration for application-level authentication,
 * parallel to ApplicationOidcFactory for OIDC.</p>
 *
 * <p><strong>Supported SAML Providers:</strong></p>
 * <ul>
 *   <li><b>cognito-saml:</b> Deploys Keycloak as SAML bridge to Cognito (OIDC federation)</li>
 *   <li><b>identity-center:</b> Uses AWS IAM Identity Center as SAML IdP</li>
 * </ul>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * cognito-saml:
 *   Application ←SAML→ Keycloak ←OIDC→ Cognito User Pool
 *
 * identity-center:
 *   Application ←SAML→ IAM Identity Center
 * </pre>
 *
 * <p><strong>Factory Orchestration:</strong></p>
 * <pre>
 * 1. CognitoAuthenticationFactory (if cognito-saml)
 * 2. IdentityCenterSamlFactory (if identity-center)
 * 3. ApplicationOidcFactory
 * 4. ApplicationSamlFactory ← YOU ARE HERE
 *    └─ KeycloakFactory (if cognito-saml)
 * </pre>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcProvider": "cognito-saml",  // Triggers Keycloak deployment
 *   "cognitoAutoProvision": true
 * }
 * </pre>
 */
public class ApplicationSamlFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(ApplicationSamlFactory.class.getName());

    @DeploymentContext("oidcProvider")
    private String oidcProvider;

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("stackName")
    private String stackName;

    // Application spec to check if it supports SAML
    @SystemContext("applicationSpec")
    private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

    // Track if Keycloak has been deployed (shared across all apps in stack)
    @SystemContext("keycloakDeployed")
    private Slot<Boolean> keycloakDeployed;

    // Keycloak factory (lazy-created when needed)
    private KeycloakFactory keycloakFactory;

    public ApplicationSamlFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only process for application-level authentication
        if (!"application-oidc".equals(authMode)) {
            LOG.info("Application SAML not applicable - authMode is '" + authMode + "' (expected 'application-oidc')");
            return;
        }

        // Check if application supports SAML/OIDC
        if (applicationSpec == null || !applicationSpec.supportsOidcIntegration()) {
            LOG.info("Application does not support OIDC/SAML integration - skipping");
            return;
        }

        var oidcIntegration = applicationSpec.getOidcIntegration();
        if (oidcIntegration == null) {
            LOG.info("Application has no OIDC integration configured - skipping");
            return;
        }

        // Only handle SAML authentication types
        if (!"SAML".equals(oidcIntegration.getAuthenticationType())) {
            LOG.info("Application uses " + oidcIntegration.getAuthenticationType() + " - not SAML, skipping");
            return;
        }

        LOG.info("Configuring Application SAML for: " + applicationSpec.applicationId());
        LOG.info("  OIDC Provider: " + oidcProvider);
        LOG.info("  Auth Mode: " + authMode);

        // Route to appropriate SAML provider
        configureSamlProvider();
    }

    private void configureSamlProvider() {
        if ("cognito-saml".equals(oidcProvider)) {
            configureCognitoSaml();
        } else if ("identity-center".equals(oidcProvider)) {
            configureIdentityCenterSaml();
        } else {
            LOG.warning("Unknown SAML provider: " + oidcProvider);
            LOG.warning("Expected 'cognito-saml' or 'identity-center'");
        }
    }

    /**
     * Configures Cognito SAML by deploying Keycloak as a bridge.
     * Keycloak federates with Cognito via OIDC and provides SAML to the application.
     *
     * <p><b>Singleton Pattern:</b> Keycloak is deployed ONCE per stack and shared by all SAML apps.
     * Multiple applications (Mattermost, GitLab, etc.) will reuse the same Keycloak instance.</p>
     */
    private void configureCognitoSaml() {
        LOG.info("Configuring Cognito SAML via Keycloak bridge for: " + applicationSpec.applicationId());

        // Check if Keycloak has already been deployed by another application in this stack
        if (keycloakDeployed.get().isPresent() && keycloakDeployed.get().orElse(false)) {
            LOG.info("Keycloak already deployed in this stack - reusing existing instance");
            LOG.info("  Application '" + applicationSpec.applicationId() + "' will use shared Keycloak");
            // TODO: Register this application as SAML client in existing Keycloak
            return;
        }

        // Deploy Keycloak for the first time (shared by all SAML apps in this stack)
        LOG.info("Deploying shared Keycloak instance for stack: " + stackName);
        keycloakFactory = new KeycloakFactory(this, "Keycloak");
        keycloakFactory.create();

        // Mark Keycloak as deployed so subsequent SAML apps reuse it
        keycloakDeployed.set(true);

        LOG.info("Keycloak SAML bridge deployed and ready for all SAML applications");
    }

    /**
     * Configures Identity Center SAML.
     * The actual IdP setup is handled by IdentityCenterSamlFactory,
     * this just validates it was configured.
     */
    private void configureIdentityCenterSaml() {
        LOG.info("Identity Center SAML configuration");
        LOG.info("  SAML endpoints should be set by IdentityCenterSamlFactory");
        LOG.info("  Application will use SAML integration from Identity Center");

        // IdentityCenterSamlFactory already ran and configured the SAML app
        // Just validate the configuration is available
        // TODO: Add validation that samlIdpMetadataUrl is set in SystemContext
    }
}
