package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Identity Center Factory for AWS IAM Identity Center (formerly AWS SSO) setup.
 *
 * Simplified version that only creates the OIDC client secret in Secrets Manager.
 * The SSO instance ARN must be provided by the user.
 *
 * This factory runs only when authMode is set to "alb-oidc".
 *
 * Configuration:
 * - authMode: "alb-oidc"
 * - ssoInstanceArn: ARN of your IAM Identity Center instance (required)
 *
 * To find your SSO instance ARN:
 * 1. Go to AWS IAM Identity Center console
 * 2. Click on "Settings" in the left navigation
 * 3. Copy the "ARN" value (format: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx)
 *
 * Note: IAM Identity Center requires AWS Organizations to be enabled in your account.
 */
public class IdentityCenterFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(IdentityCenterFactory.class.getName());

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("ssoInstanceArn")
    private String ssoInstanceArn;

    @DeploymentContext("stackName")
    private String stackName;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    // Check if manual OIDC endpoints are configured (means not using Identity Center)
    @DeploymentContext("oidcIssuer")
    private String oidcIssuer;

    public IdentityCenterFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only provision if authMode is alb-oidc
        if (!"alb-oidc".equals(authMode)) {
            LOG.info("ALB-OIDC not enabled - skipping Identity Center setup");
            return;
        }

        // Don't run if manual OIDC endpoints are configured (not using Identity Center)
        if (oidcIssuer != null && !oidcIssuer.isEmpty()) {
            LOG.info("Manual OIDC endpoints configured - skipping Identity Center setup");
            return;
        }

        // Check if SSO instance ARN is provided
        if (ssoInstanceArn == null || ssoInstanceArn.isEmpty()) {
            LOG.info("IAM Identity Center not configured (no ssoInstanceArn) - skipping setup");
            return;
        }

        LOG.info("Setting up AWS IAM Identity Center for OIDC authentication");
        LOG.info("SSO Instance ARN: " + ssoInstanceArn);

        // Create client secret in Secrets Manager
        createClientSecret();
    }

    /**
     * Creates an OIDC client secret in Secrets Manager.
     * This secret will be used by the ALB for OIDC authentication.
     *
     * The secret is created with a placeholder value - you must update it with the actual
     * client secret from your IAM Identity Center application registration.
     */
    private void createClientSecret() {
        LOG.info("Creating OIDC client secret in Secrets Manager");

        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

        // Create secret with placeholder value
        // User must update this with actual client secret from Identity Center
        String secretName = stackName + "/" + appId + "/oidc/client-secret";
        Secret.Builder.create(this, "OidcClientSecret")
                .secretName(secretName)
                .description("OIDC client secret for " + appId + " ALB authentication (update with actual value from IAM Identity Center)")
                .secretObjectValue(java.util.Map.of(
                        "clientSecret", SecretValue.unsafePlainText("PLACEHOLDER-UPDATE-FROM-IDENTITY-CENTER")
                ))
                .removalPolicy(RemovalPolicy.DESTROY)  // Allow deletion when stack is deleted
                .build();

        LOG.info("Client secret created: [REDACTED]");
        LOG.info("IMPORTANT: Update the secret with the actual client secret from your IAM Identity Center application:");
        LOG.info("  1. Register an application in IAM Identity Center console");
        LOG.info("  2. Copy the client secret from the application configuration");
        LOG.info("  3. Update the secret in Secrets Manager with the actual value");
    }
}
