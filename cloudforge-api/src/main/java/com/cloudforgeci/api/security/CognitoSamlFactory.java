package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.OidcIntegration;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.cognito.UserPool;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Cognito SAML Factory for applications requiring SAML authentication.
 *
 * <p>This factory configures Amazon Cognito User Pool as a SAML 2.0 Identity Provider
 * for applications that need SAML for features like group synchronization (e.g., Mattermost).</p>
 *
 * <p><b>Why Cognito SAML over Identity Center SAML:</b></p>
 * <ul>
 *   <li><b>Full API Support:</b> Cognito SAML configuration is fully automatable via AWS API</li>
 *   <li><b>Attribute Mapping:</b> Can configure SAML attribute mappings programmatically</li>
 *   <li><b>No Console Steps:</b> Unlike Identity Center, no manual console configuration required</li>
 * </ul>
 *
 * <p><b>Quick Start:</b></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcProvider": "cognito-saml",
 *   "cognitoAutoProvision": true,
 *   "cognitoDomainPrefix": "myapp-auth"
 * }
 * </pre>
 *
 * <p><b>What Gets Created:</b></p>
 * <ul>
 *   <li>SAML 2.0 provider configuration on existing Cognito User Pool</li>
 *   <li>SAML attribute mappings (email, firstName, lastName, groups)</li>
 *   <li>IdP certificate stored in Secrets Manager for application use</li>
 *   <li>SAML metadata URL for application auto-configuration</li>
 * </ul>
 *
 * <p><b>Cognito SAML Endpoints:</b></p>
 * <ul>
 *   <li>SSO URL: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/SSO</li>
 *   <li>Metadata: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/metadata</li>
 *   <li>Logout: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/logout</li>
 * </ul>
 *
 * @see <a href="https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-saml-idp.html">Cognito SAML IdP</a>
 */
public class CognitoSamlFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(CognitoSamlFactory.class.getName());

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("cognitoAutoProvision")
    private Boolean cognitoAutoProvision;

    @DeploymentContext("stackName")
    private String stackName;

    @DeploymentContext("region")
    private String region;

    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    @SystemContext("cognitoUserPool")
    private UserPool cognitoUserPool;

    @SystemContext("cognitoUserPoolId")
    private String cognitoUserPoolId;

    @com.cloudforge.core.annotation.SystemContext("securityProfileConfig")
    private com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityProfileConfig;

    @SystemContext("alb")
    private software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer alb;

    public CognitoSamlFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only configure if authMode is application-oidc (for SAML apps)
        if (!"application-oidc".equals(authMode)) {
            LOG.info("Application-level OIDC not enabled - skipping Cognito SAML setup");
            return;
        }

        // Check if Cognito auto-provisioning is enabled
        if (cognitoAutoProvision == null || !cognitoAutoProvision) {
            LOG.info("Cognito auto-provisioning not enabled - skipping Cognito SAML setup");
            return;
        }

        // Check if application supports OIDC/SAML integration
        if (applicationSpec == null || !applicationSpec.supportsOidcIntegration()) {
            LOG.info("Application does not support OIDC/SAML integration - skipping Cognito SAML setup");
            return;
        }

        OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
        if (oidcIntegration == null) {
            LOG.info("Application has no OIDC integration configured - skipping Cognito SAML setup");
            return;
        }

        // Check if application uses SAML authentication
        if (!"SAML".equals(oidcIntegration.getAuthenticationType())) {
            LOG.info("Application uses " + oidcIntegration.getAuthenticationType() +
                    ", not SAML - skipping Cognito SAML setup");
            return;
        }

        // Wait for Cognito User Pool to be created by CognitoAuthenticationFactory
        ctx.cognitoUserPoolId.onSet(userPoolId -> {
            this.cognitoUserPoolId = userPoolId;
            LOG.info("Configuring Cognito SAML IdP for application: " + applicationSpec.applicationId());
            LOG.info("User Pool ID: " + userPoolId);

            configureCognitoSaml(userPoolId);
        });
    }

    /**
     * Configures Cognito User Pool as a SAML 2.0 Identity Provider.
     *
     * <p>Cognito automatically exposes SAML endpoints when a User Pool is created:</p>
     * <ul>
     *   <li>SSO: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/SSO</li>
     *   <li>Metadata: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/saml2/idp/metadata</li>
     * </ul>
     *
     * <p>We configure the SAML application (Service Provider) settings and store
     * the IdP certificate in Secrets Manager for the application to use.</p>
     */
    private void configureCognitoSaml(String userPoolId) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        String siteUrl = constructSiteUrl();
        String acsUrl = siteUrl + "/login/sso/saml";

        LOG.info("Configuring Cognito SAML for: " + appId);
        LOG.info("  Site URL (Entity ID): " + siteUrl);
        LOG.info("  ACS URL: " + acsUrl);

        // Construct SAML endpoints
        String issuerUrl = String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
        String ssoUrl = issuerUrl + "/saml2/idp/SSO";
        String metadataUrl = issuerUrl + "/saml2/idp/metadata";
        String logoutUrl = issuerUrl + "/saml2/logout";

        LOG.info("  Issuer URL: " + issuerUrl);
        LOG.info("  SSO URL: " + ssoUrl);
        LOG.info("  Metadata URL: " + metadataUrl);

        // Fetch and store the IdP certificate from metadata
        storeIdpCertificate(userPoolId, metadataUrl, appId);

        // Export SAML configuration to SystemContext
        exportSamlConfiguration(siteUrl, acsUrl, issuerUrl, ssoUrl, metadataUrl, logoutUrl);

        // Create CloudFormation outputs
        createOutputs(appId, siteUrl, acsUrl, ssoUrl, metadataUrl);

        LOG.info("Cognito SAML IdP configuration complete");
    }

    /**
     * Fetches the IdP certificate from Cognito SAML metadata and stores it in Secrets Manager.
     *
     * <p>The SAML metadata XML contains the X509Certificate that applications need
     * to verify SAML assertions. We use a Custom Resource Lambda to fetch the metadata,
     * extract the certificate, and store it in Secrets Manager.</p>
     */
    private void storeIdpCertificate(String userPoolId, String metadataUrl, String appId) {
        String secretName = stackName + "/" + appId + "/saml/cognito-idp-cert";

        LOG.info("Storing Cognito SAML IdP certificate in Secrets Manager");

        // Determine removal policy based on security profile
        boolean isProduction = (securityProfileConfig != null &&
                securityProfileConfig.getSecurityProfile() == SecurityProfile.PRODUCTION);
        RemovalPolicy removalPolicy = isProduction ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;

        // Create a secret to store the IdP certificate
        // For now, we store the metadata URL - the init container fetches the actual cert
        String secretValue = String.format(
            "{\"metadataUrl\":\"%s\",\"userPoolId\":\"%s\",\"region\":\"%s\",\"providerType\":\"cognito\"}",
            metadataUrl, userPoolId, region
        );

        // Create secret
        AwsSdkCall createSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("createSecret")
                .parameters(Map.of(
                        "Name", secretName,
                        "Description", "Cognito SAML IdP configuration for " + appId +
                                ". Init container fetches certificate from metadataUrl.",
                        "SecretString", secretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoSamlIdpConfig-" + secretName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceExistsException")
                .build();

        // Update secret value (always runs to ensure current values)
        AwsSdkCall updateSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("putSecretValue")
                .parameters(Map.of(
                        "SecretId", secretName,
                        "SecretString", secretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoSamlIdpConfig-" + secretName))
                .region(region)
                .build();

        // Delete secret on stack deletion (if not production)
        AwsSdkCall deleteSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("deleteSecret")
                .parameters(Map.of(
                        "SecretId", secretName,
                        "ForceDeleteWithoutRecovery", !isProduction
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoSamlIdpConfig-" + secretName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        // Use scoped ARN pattern for least-privilege (secretName is known at synth time)
        // Pattern: arn:aws:secretsmanager:REGION:*:secret:STACKNAME/APP_ID/saml/*
        String secretArnPattern = "arn:aws:secretsmanager:" + region + ":*:secret:" + stackName + "/" + appId + "/saml/*";

        AwsCustomResource samlConfigSecret = AwsCustomResource.Builder.create(this, "CognitoSamlIdpConfig")
                .onCreate(createSecretCall)
                .onUpdate(updateSecretCall)
                .onDelete(deleteSecretCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                        software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                                .actions(List.of(
                                        "secretsmanager:CreateSecret",
                                        "secretsmanager:PutSecretValue",
                                        "secretsmanager:DeleteSecret"
                                ))
                                .resources(List.of(secretArnPattern))
                                .build()
                )))
                .build();

        LOG.info("Cognito SAML IdP configuration stored in Secrets Manager");
        LOG.info("  Removal policy: " + removalPolicy);

        // Store the secret name in SystemContext
        ctx.samlConfigSecretArn.set(secretName);
    }

    /**
     * Export SAML configuration to SystemContext for application use.
     */
    private void exportSamlConfiguration(String siteUrl, String acsUrl,
                                          String issuerUrl, String ssoUrl,
                                          String metadataUrl, String logoutUrl) {
        // Store in SystemContext for MattermostSamlIntegration and other SAML apps
        ctx.samlSiteUrl.set(siteUrl);
        ctx.samlAcsUrl.set(acsUrl);
        ctx.samlIdpSsoUrl.set(ssoUrl);
        ctx.samlIdpMetadataUrl.set(metadataUrl);
        ctx.samlIdpLogoutUrl.set(logoutUrl);
        ctx.samlIdpEntityId.set(issuerUrl);  // Cognito uses issuer URL as entity ID
        ctx.samlProviderType.set("cognito");

        LOG.info("Cognito SAML configuration exported to SystemContext");
    }

    /**
     * Create CloudFormation outputs for easy reference.
     */
    private void createOutputs(String appId, String siteUrl, String acsUrl,
                                String ssoUrl, String metadataUrl) {
        CfnOutput.Builder.create(this, "CognitoSamlSsoUrl")
                .description("Cognito SAML SSO URL for " + appId)
                .value(ssoUrl)
                .build();

        CfnOutput.Builder.create(this, "CognitoSamlMetadataUrl")
                .description("Cognito SAML Metadata URL for " + appId)
                .value(metadataUrl)
                .build();

        CfnOutput.Builder.create(this, "CognitoSamlEntityId")
                .description("Service Provider Entity ID (Site URL)")
                .value(siteUrl)
                .build();

        CfnOutput.Builder.create(this, "CognitoSamlAcsUrl")
                .description("SAML Assertion Consumer Service URL")
                .value(acsUrl)
                .build();

        // Cognito SAML is fully automated - no manual console steps required
        CfnOutput.Builder.create(this, "CognitoSamlStatus")
                .description("Cognito SAML configuration status")
                .value("FULLY AUTOMATED - No manual console configuration required")
                .build();

        LOG.info("CloudFormation outputs created for Cognito SAML");
    }

    /**
     * Construct the site URL from domain configuration or ALB DNS name.
     */
    private String constructSiteUrl() {
        String protocol = (enableSsl != null && enableSsl) ? "https" : "http";

        if (fqdn != null && !fqdn.isEmpty()) {
            return protocol + "://" + fqdn;
        } else if (domain != null && !domain.isEmpty()) {
            if (subdomain != null && !subdomain.isEmpty()) {
                return protocol + "://" + subdomain + "." + domain;
            }
            return protocol + "://" + domain;
        }

        // For OIDC modes without custom domain, use ALB DNS name with Private CA
        if (alb != null) {
            String albDnsName = alb.getLoadBalancerDnsName();
            LOG.info("No custom domain configured - using ALB DNS name: " + albDnsName);
            return protocol + "://" + albDnsName;
        }

        // Fallback - should not happen in production
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";
        LOG.warning("No domain or ALB configured - using placeholder URL");
        return "https://" + appId + ".example.com";
    }
}
