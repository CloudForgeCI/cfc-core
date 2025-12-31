package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforgeci.api.util.CfnStringUtils;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * IAM Identity Center SAML Factory for automated SAML 2.0 application provisioning.
 *
 * <p>This factory creates a SAML 2.0 application in AWS IAM Identity Center (formerly AWS SSO)
 * and configures it for use with applications like Mattermost that support SAML authentication.</p>
 *
 * <p><b>Quick Start:</b></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "autoProvisionIdentityCenter": true,
 *   "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx"
 * }
 * </pre>
 *
 * <p><b>What Gets Created:</b></p>
 * <ul>
 *   <li>SAML 2.0 application in IAM Identity Center</li>
 *   <li>Attribute mappings (email, firstName, lastName, groups)</li>
 *   <li>IdP certificate stored in Secrets Manager</li>
 *   <li>SAML metadata URL for automatic configuration</li>
 * </ul>
 *
 * <p><b>Prerequisites:</b></p>
 * <ul>
 *   <li>AWS Organizations enabled in the account</li>
 *   <li>IAM Identity Center enabled and configured</li>
 *   <li>SSO Instance ARN available (Settings page in Identity Center console)</li>
 * </ul>
 *
 * <p><b>Post-Deployment:</b></p>
 * <ol>
 *   <li>Assign users/groups to the application in IAM Identity Center console</li>
 *   <li>Users can then sign in using "Sign in with AWS IAM Identity Center"</li>
 * </ol>
 *
 * @see <a href="https://docs.aws.amazon.com/singlesignon/latest/userguide/samlapps.html">IAM Identity Center SAML Apps</a>
 */
public class IdentityCenterSamlFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(IdentityCenterSamlFactory.class.getName());

    @DeploymentContext("authMode")
    private AuthMode authMode;

    @DeploymentContext("autoProvisionIdentityCenter")
    private Boolean autoProvisionIdentityCenter;

    @DeploymentContext("ssoInstanceArn")
    private String ssoInstanceArn;

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

    // Reuse cognitoInitialAdminEmail for Identity Center - they're mutually exclusive
    @DeploymentContext("cognitoInitialAdminEmail")
    private String initialAdminEmail;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    @SystemContext("alb")
    private software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer alb;

    public IdentityCenterSamlFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only provision if authMode is APPLICATION_OIDC (SAML for app-level auth)
        if (authMode != AuthMode.APPLICATION_OIDC) {
            LOG.info("Application-level OIDC not enabled - skipping Identity Center SAML setup");
            return;
        }

        // Check if auto-provisioning is enabled
        if (autoProvisionIdentityCenter == null || !autoProvisionIdentityCenter) {
            LOG.info("Identity Center auto-provisioning not enabled - skipping SAML setup");
            return;
        }

        // Validate SSO instance ARN
        if (ssoInstanceArn == null || ssoInstanceArn.isEmpty()) {
            LOG.severe("ssoInstanceArn is required for Identity Center auto-provisioning");
            throw new IllegalArgumentException(
                "ssoInstanceArn is required when autoProvisionIdentityCenter = true. " +
                "Find it in IAM Identity Center console > Settings > ARN"
            );
        }

        // Check if application supports OIDC/SAML integration
        if (applicationSpec == null || !applicationSpec.supportsOidcIntegration()) {
            LOG.info("Application does not support OIDC/SAML integration - skipping Identity Center setup");
            return;
        }

        OidcIntegration oidcIntegration = applicationSpec.getOidcIntegration();
        if (oidcIntegration == null) {
            LOG.info("Application has no OIDC integration configured - skipping Identity Center setup");
            return;
        }

        // Check if application supports Identity Center SAML specifically
        if (!oidcIntegration.supportsIdentityCenterSaml()) {
            LOG.warning("Application '" + applicationSpec.applicationId() + "' does not support IAM Identity Center SAML");
            LOG.warning("  Auth type: " + oidcIntegration.getAuthenticationType());
            LOG.warning("  Supports Cognito: " + oidcIntegration.supportsCognito());
            LOG.warning("  Supports Identity Center SAML: false");
            LOG.warning("Skipping Identity Center SAML setup - use Cognito OIDC instead");
            return;
        }

        LOG.info("Creating IAM Identity Center SAML application for: " + applicationSpec.applicationId());
        LOG.info("SSO Instance ARN: " + ssoInstanceArn);

        // Create the SAML application
        createSamlApplication();
    }

    /**
     * Creates a SAML 2.0 application in IAM Identity Center using Custom Resources.
     *
     * <p>The SSO Admin API is used to create and configure the application:</p>
     * <ul>
     *   <li>CreateApplication - Creates the SAML app</li>
     *   <li>PutApplicationAccessScope - Configures access scopes</li>
     *   <li>PutApplicationAssignmentConfiguration - Configures user assignment</li>
     * </ul>
     */
    private void createSamlApplication() {
        String appName = stackName + "-" + applicationSpec.applicationId();
        String siteUrl = constructSiteUrl();
        String acsUrl = siteUrl + "/login/sso/saml";

        LOG.info("Creating SAML application: " + appName);
        LOG.info("  Site URL: " + siteUrl);
        LOG.info("  ACS URL: " + acsUrl);

        // Extract instance ID from ARN (format: arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx)
        String instanceId = ssoInstanceArn.substring(ssoInstanceArn.lastIndexOf("/") + 1);

        // Create SAML application using Custom Resource
        // Note: CDK doesn't have native SSO Admin constructs, so we use Custom Resources
        // Use ApplicationArn from response as PhysicalResourceId so it can be used in onDelete
        AwsSdkCall createAppCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("createApplication")
                .parameters(Map.of(
                    "ApplicationProviderArn", "arn:aws:sso::aws:applicationProvider/custom",
                    "InstanceArn", ssoInstanceArn,
                    "Name", appName,
                    "Description", "SAML application for " + applicationSpec.applicationId() + " created by CloudForge",
                    "PortalOptions", Map.of(
                        "SignInOptions", Map.of(
                            "Origin", "APPLICATION",
                            "ApplicationUrl", siteUrl
                        ),
                        "Visibility", "ENABLED"
                    ),
                    "Status", "ENABLED"
                ))
                // Use ApplicationArn from response as physical ID - this allows onDelete to reference it
                .physicalResourceId(PhysicalResourceId.fromResponse("ApplicationArn"))
                .region(region)
                .build();

        // Delete the SAML application on stack deletion
        // PhysicalResourceIdReference resolves to the ApplicationArn set above
        AwsSdkCall deleteAppCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("deleteApplication")
                .parameters(Map.of(
                    "ApplicationArn", new software.amazon.awscdk.customresources.PhysicalResourceIdReference()
                ))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        AwsCustomResource samlApp = AwsCustomResource.Builder.create(this, "SamlApplication")
                .onCreate(createAppCall)
                .onDelete(deleteAppCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of(
                            "sso:CreateApplication",
                            "sso:DeleteApplication",
                            "sso:DescribeApplication",
                            "sso:PutApplicationGrant",
                            "sso:PutApplicationAuthenticationMethod",
                            "sso:PutApplicationAccessScope",
                            "sso:PutApplicationAssignmentConfiguration",
                            "sso:GetApplicationGrant",
                            "sso:ListApplicationGrants"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        // Get the application ARN from the response
        String applicationArn = samlApp.getResponseField("ApplicationArn");

        // Configure SAML authentication method
        configureSamlAuthentication(applicationArn, siteUrl, acsUrl);

        // Store IdP metadata URL and certificate in Secrets Manager
        storeIdpConfiguration(instanceId, applicationArn);

        // Export the SAML configuration to SystemContext
        exportSamlConfiguration(siteUrl, acsUrl, instanceId);

        // Output useful information
        createOutputs(appName, siteUrl, acsUrl, instanceId);

        LOG.info("IAM Identity Center SAML application created successfully");
    }

    /**
     * Configure SAML authentication method for the application.
     *
     * <p>Note: The SSO Admin API for custom SAML applications has limited support
     * for programmatic configuration. The CreateApplication call creates the app,
     * but detailed SAML settings (ACS URL, attribute mappings) must be configured
     * via the IAM Identity Center console.</p>
     *
     * <p>The application is created with status ENABLED and visible in the portal.
     * Post-deployment steps:</p>
     * <ol>
     *   <li>Go to IAM Identity Center > Applications</li>
     *   <li>Select the created application</li>
     *   <li>Configure SAML settings (ACS URL, Entity ID, Attributes)</li>
     *   <li>Assign users/groups</li>
     * </ol>
     */
    private void configureSamlAuthentication(String applicationArn, String siteUrl, String acsUrl) {
        // Note: For custom SAML applications, the grant types and authentication methods
        // are pre-configured by AWS when using ApplicationProviderArn = "arn:aws:sso::aws:applicationProvider/custom"
        //
        // The SSO Admin API doesn't support SAML-specific grant types like "saml2-bearer"
        // and the PutApplicationAuthenticationMethod only accepts "IAM" which is for
        // IAM Identity Center managed applications, not custom SAML apps.
        //
        // Instead, SAML configuration is done through:
        // 1. The portal settings in CreateApplication (SignInOptions, Visibility)
        // 2. Manual configuration in the IAM Identity Center console
        //
        // We skip the grant and auth method calls to avoid API errors.

        LOG.info("SAML application created. ACS URL for manual configuration: " + acsUrl);
        LOG.info("Entity ID (Audience) for manual configuration: " + siteUrl);
        LOG.info("Post-deployment: Configure SAML settings in IAM Identity Center console");
    }

    /**
     * Store IdP certificate and metadata URL in Secrets Manager.
     * The application needs these to validate SAML responses.
     *
     * <p>Uses PutSecretValue which creates the secret if it doesn't exist
     * or updates it if it does. This ensures we always have current values.</p>
     */
    private void storeIdpConfiguration(String instanceId, String applicationArn) {
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

        // Construct IdP metadata URL
        // Format: https://portal.sso.{region}.amazonaws.com/saml/metadata/{instanceId}
        String metadataUrl = "https://portal.sso." + region + ".amazonaws.com/saml/metadata/" + instanceId;

        // Construct IdP SSO URL
        String ssoUrl = "https://portal.sso." + region + ".amazonaws.com/saml/assertion/" + instanceId;

        LOG.info("IdP Metadata URL: " + metadataUrl);
        LOG.info("IdP SSO URL: " + ssoUrl);

        String secretName = stackName + "/" + appId + "/saml/idp-config";
        String secretValue = String.format(
            "{\"metadataUrl\":\"%s\",\"ssoUrl\":\"%s\",\"instanceId\":\"%s\",\"region\":\"%s\"}",
            metadataUrl, ssoUrl, instanceId, region
        );
        String description = "IAM Identity Center SAML IdP configuration for " + appId;

        // Delete secret on stack deletion - ALWAYS delete, no RETAIN behavior for this config
        AwsSdkCall deleteSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("deleteSecret")
                .parameters(Map.of(
                        "SecretId", secretName,
                        "ForceDeleteWithoutRecovery", true
                ))
                .physicalResourceId(PhysicalResourceId.of("IdentityCenterSamlConfig-" + secretName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        // Step 1: Create secret (ignore if exists)
        AwsSdkCall createSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("createSecret")
                .parameters(Map.of(
                        "Name", secretName,
                        "Description", description,
                        "SecretString", secretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("IdentityCenterSamlConfig-" + secretName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceExistsException")
                .build();

        // Step 2: Update secret value (always runs to ensure current values)
        AwsSdkCall updateSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("putSecretValue")
                .parameters(Map.of(
                        "SecretId", secretName,
                        "SecretString", secretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("IdentityCenterSamlConfig-" + secretName))
                .region(region)
                .build();

        // Single custom resource that creates, updates, and deletes
        // Use scoped ARN pattern for least-privilege (secretName is known at synth time)
        // Pattern: arn:aws:secretsmanager:REGION:*:secret:STACKNAME/APP_ID/saml/*
        String secretArnPattern = "arn:aws:secretsmanager:" + region + ":*:secret:" + stackName + "/" + appId + "/saml/*";

        AwsCustomResource.Builder.create(this, "SamlIdpConfig")
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

        LOG.info("SAML IdP configuration stored in Secrets Manager");
        LOG.info("Secret will be deleted on stack deletion (ForceDeleteWithoutRecovery=true)");

        // Store the secret name in SystemContext for the application to use
        // Note: Just store the secret name - the full ARN isn't known until the secret is created
        ctx.samlConfigSecretArn.set(secretName);

        // Also store the metadata URL directly for MattermostSamlIntegration
        ctx.samlIdpMetadataUrl.set(metadataUrl);
        ctx.samlIdpSsoUrl.set(ssoUrl);
    }

    /**
     * Export SAML configuration to SystemContext for application use.
     */
    private void exportSamlConfiguration(String siteUrl, String acsUrl, String instanceId) {
        // Construct IdP endpoints
        String metadataUrl = "https://portal.sso." + region + ".amazonaws.com/saml/metadata/" + instanceId;
        String ssoUrl = "https://portal.sso." + region + ".amazonaws.com/saml/assertion/" + instanceId;

        // Store in SystemContext for MattermostSamlIntegration to use
        ctx.samlSiteUrl.set(siteUrl);
        ctx.samlAcsUrl.set(acsUrl);
        ctx.samlIdpMetadataUrl.set(metadataUrl);
        ctx.samlIdpSsoUrl.set(ssoUrl);
        ctx.samlIdpEntityId.set("urn:amazon:webservices");

        LOG.info("SAML configuration exported to SystemContext");
    }

    /**
     * Create CloudFormation outputs for easy reference.
     */
    private void createOutputs(String appName, String siteUrl, String acsUrl, String instanceId) {
        String metadataUrl = "https://portal.sso." + region + ".amazonaws.com/saml/metadata/" + instanceId;

        CfnOutput.Builder.create(this, "SamlApplicationName")
                .description("IAM Identity Center SAML Application Name")
                .value(appName)
                .build();

        CfnOutput.Builder.create(this, "SamlAcsUrl")
                .description("SAML Assertion Consumer Service URL")
                .value(acsUrl)
                .build();

        CfnOutput.Builder.create(this, "SamlEntityId")
                .description("SAML Service Provider Entity ID")
                .value(siteUrl)
                .build();

        CfnOutput.Builder.create(this, "SamlIdpMetadataUrl")
                .description("IAM Identity Center SAML Metadata URL")
                .value(metadataUrl)
                .build();

        // Direct console link for post-deployment configuration
        String consoleUrl = "https://" + region + ".console.aws.amazon.com/singlesignon/home?region=" + region + "#/applications";
        CfnOutput.Builder.create(this, "SamlConsoleUrl")
                .description("IAM Identity Center Applications Console - configure SAML settings here")
                .value(consoleUrl)
                .build();

        // CRITICAL: AWS SSO Admin API does NOT support SAML attribute mapping programmatically
        // Users MUST manually configure these in the IAM Identity Center console
        // The "Assign users" popup does NOT configure attributes - must use "Edit attribute mappings"
        CfnOutput.Builder.create(this, "SamlPostDeployment")
                .description("REQUIRED manual steps - AWS API limitation")
                .value("1) Open console URL, 2) Select '" + appName + "', 3) Actions > Edit attribute mappings (NOT Assign users), 4) Add mappings below, 5) Then assign users")
                .build();

        // Output required attribute mappings for the SAML application
        // These MUST be configured in IAM Identity Center for Mattermost to work
        // AWS API does not support programmatic SAML attribute mapping - console only
        CfnOutput.Builder.create(this, "SamlAttrMappings")
                .description("REQUIRED attribute mappings - add ALL of these in Edit attribute mappings")
                .value("email=${user:email}, firstName=${user:givenName}, lastName=${user:familyName}, preferred_username=${user:preferredUsername}")
                .build();

        // Output initial admin email if provided (reused from cognitoInitialAdminEmail)
        if (initialAdminEmail != null && !initialAdminEmail.isEmpty()) {
            CfnOutput.Builder.create(this, "SamlInitialAdminEmail")
                    .description("Initial admin email - assign this user in Identity Center")
                    .value(initialAdminEmail)
                    .build();

            LOG.info("Initial admin email for Identity Center assignment: " + initialAdminEmail);
        }
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
        // IMPORTANT: ALB DNS names contain mixed case but Cognito callback URLs are case-sensitive.
        // See: https://github.com/aws/aws-cdk/issues/11171
        if (alb != null) {
            // Lowercase the ALB DNS name for callback URL compatibility
            String lowercaseDns = CfnStringUtils.toLowerCase(alb.getLoadBalancerDnsName());
            String albUrl = Fn.join("", java.util.List.of(protocol, "://", lowercaseDns));
            LOG.info("No custom domain configured - using ALB DNS name (lowercased): " + albUrl);
            return albUrl;
        }

        // Fallback - should not happen in production
        LOG.warning("No domain or ALB configured - using placeholder URL");
        return "https://" + applicationSpec.applicationId() + ".example.com";
    }
}
