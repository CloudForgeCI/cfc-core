package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.OidcIntegration;
import com.cloudforgeci.api.util.CfnStringUtils;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * IAM Identity Center SAML Factory for automated SAML 2.0 application provisioning.
 *
 * <p>This factory creates a SAML 2.0 application in AWS IAM Identity Center (formerly AWS SSO)
 * and configures it for use with applications like Metabase Enterprise that support SAML authentication.</p>
 *
 * <p><b>Hybrid Architecture - Cognito + Identity Center:</b></p>
 * <p>This factory supports a hybrid architecture where Cognito manages users/groups
 * and Identity Center provides SAML assertions to applications:</p>
 * <pre>
 * User → Cognito (authentication, user/group management)
 *      → Identity Center (trusted token issuer, SAML provider)
 *      → Application (SAML consumer)
 * </pre>
 *
 * <p><b>Benefits of Hybrid Architecture:</b></p>
 * <ul>
 *   <li>Fully automated - Cognito API supports complete user/group management</li>
 *   <li>No manual console steps for user provisioning</li>
 *   <li>Users and groups managed exclusively in Cognito</li>
 *   <li>Identity Center acts as SAML provider only (no Identity Store users)</li>
 *   <li>Works with applications requiring SAML (like Metabase Enterprise)</li>
 * </ul>
 *
 * <p><b>Quick Start (Hybrid Mode with Cognito):</b></p>
 * <pre>
 * {
 *   "authMode": "application-oidc",
 *   "oidcProvider": "cognito-saml",
 *   "autoProvisionIdentityCenter": true,
 *   "cognitoAutoProvision": true,
 *   "cognitoCreateGroups": true,
 *   "cognitoGroups": ["Administrators", "Analysts", "Viewers"],
 *   "ssoInstanceArn": "arn:aws:sso:::instance/ssoins-xxxxxxxxxxxx"
 * }
 * </pre>
 *
 * <p><b>IMPORTANT:</b> Set <code>cognitoAutoProvision=true</code>,
 * <code>cognitoCreateGroups=true</code>, and specify <code>cognitoGroups</code>
 * to enable group synchronization from Cognito to SAML.</p>
 *
 * <p><b>What Gets Created:</b></p>
 * <ul>
 *   <li>Cognito User Pool (via CognitoAuthenticationFactory, if cognitoAutoProvision=true)</li>
 *   <li>SAML 2.0 application in IAM Identity Center</li>
 *   <li>Trusted Token Issuer configuration (Cognito → Identity Center)</li>
 *   <li>IdP metadata URL and certificate in Secrets Manager</li>
 *   <li>CloudFormation outputs with SAML configuration</li>
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
 *   <li>Users are managed in Cognito User Pool (fully automated)</li>
 *   <li>Identity Center trusts Cognito tokens via trusted token issuer</li>
 *   <li>Identity Center issues SAML assertions to the application</li>
 *   <li>Application receives SAML with user attributes from Cognito</li>
 * </ol>
 *
 * @see <a href="https://docs.aws.amazon.com/singlesignon/latest/userguide/samlapps.html">IAM Identity Center SAML Apps</a>
 * @see <a href="https://docs.aws.amazon.com/singlesignon/latest/userguide/trustedidentitypropagation.html">Trusted Token Issuers</a>
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

    @DeploymentContext("securityProfile")
    private SecurityProfile securityProfile;

    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    // Reuse cognitoInitialAdminEmail for Identity Center - they're mutually exclusive
    @DeploymentContext("cognitoAutoProvision")
    private Boolean cognitoAutoProvision;

    @DeploymentContext("cognitoInitialAdminEmail")
    private String initialAdminEmail;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    @SystemContext("alb")
    private software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer alb;

    @SystemContext("cognitoUserPool")
    private software.amazon.awscdk.services.cognito.UserPool cognitoUserPool;

    @SystemContext("cognitoUserPoolId")
    private String cognitoUserPoolId;

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

        // Get application-specific SAML ACS path from OidcIntegration
        String acsPath = "/login/sso/saml";  // Default for Mattermost
        if (applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            var oidcIntegration = applicationSpec.getOidcIntegration();
            if (oidcIntegration != null && oidcIntegration.getSamlAcsPath() != null) {
                acsPath = oidcIntegration.getSamlAcsPath();
                LOG.info("Using application-specific SAML ACS path: " + acsPath);
            }
        }
        String acsUrl = siteUrl + acsPath;

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
                            "sso:PutApplicationAccessScope",
                            "sso:PutApplicationAssignmentConfiguration",
                            "sso:GetApplicationGrant",
                            "sso:ListApplicationGrants",
                            // Add CreateApplicationAssignment here so it's included when SSOAdmin Lambda provider is created
                            "sso:CreateApplicationAssignment",
                            "sso:ListApplicationAssignments",
                            // Add TrustedTokenIssuer permissions here to avoid Lambda provider caching issues
                            "sso:CreateTrustedTokenIssuer",
                            "sso:DeleteTrustedTokenIssuer",
                            "sso:DescribeTrustedTokenIssuer",
                            "sso:UpdateTrustedTokenIssuer"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        // Get the application ARN from the response
        String applicationArn = samlApp.getResponseField("ApplicationArn");

        // Configure application assignment - allow all users without explicit assignment
        configureApplicationAssignment(applicationArn);

        // Create IAM role for trusted identity propagation (uses IAMProfileMapper)
        String roleArn = createApplicationIAMRole(applicationArn);
        LOG.info("IAM role created for application: " + roleArn);

        // Configure SAML authentication method
        configureSamlAuthentication(applicationArn, siteUrl, acsUrl);

        // Store IdP metadata URL and certificate in Secrets Manager
        storeIdpConfiguration(instanceId, applicationArn);

        // Wait for Cognito User Pool to be created, then configure as trusted token issuer
        if (cognitoAutoProvision != null && cognitoAutoProvision) {
            ctx.cognitoUserPoolId.onSet(userPoolId -> {
                LOG.info("Configuring Cognito User Pool as trusted token issuer for Identity Center");
                configureCognitoAsExternalIdP(applicationArn, userPoolId);
            });
        } else {
            LOG.warning("Cognito auto-provisioning not enabled - Identity Center application created but no IdP configured");
            LOG.warning("Configure an external IdP manually in the Identity Center console");
        }

        // Export the SAML configuration to SystemContext
        exportSamlConfiguration(siteUrl, acsUrl, instanceId);

        // Output useful information
        createOutputs(appName, siteUrl, acsUrl, instanceId, roleArn);

        LOG.info("IAM Identity Center SAML application created successfully");
    }

    /**
     * Configure application assignment to allow all Identity Center users without explicit assignment.
     *
     * <p>This sets AssignmentRequired=false, which enables the "Do not require assignments" option
     * in the Identity Center console. When disabled, all authenticated Identity Center users
     * can access the application without needing explicit user/group assignments.</p>
     *
     * @param applicationArn the Identity Center application ARN
     */
    private void configureApplicationAssignment(String applicationArn) {
        LOG.info("Configuring application assignment: AssignmentRequired=false (allow all users)");

        AwsSdkCall putAssignmentConfigCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("putApplicationAssignmentConfiguration")
                .parameters(Map.of(
                    "ApplicationArn", applicationArn,
                    "AssignmentRequired", false
                ))
                .physicalResourceId(PhysicalResourceId.of("AppAssignmentConfig-" + stackName))
                .region(region)
                .build();

        // Note: No need for onDelete - assignment config is deleted when application is deleted
        AwsCustomResource.Builder.create(this, "ApplicationAssignmentConfig")
                .onCreate(putAssignmentConfigCall)
                .onUpdate(putAssignmentConfigCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of("sso:PutApplicationAssignmentConfiguration"))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        LOG.info("Application assignment configured: All Identity Center users can access without explicit assignment");
    }

    /**
     * Configure application grant with audience claim for Cognito JWT token validation.
     *
     * <p>This configures the JWT Bearer grant type for the application, associating it with
     * the Cognito trusted token issuer and specifying the expected audience claim (App Client ID).</p>
     *
     * <p>This enables Identity Center to:</p>
     * <ul>
     *   <li>Accept JWT tokens from Cognito (via trusted token issuer)</li>
     *   <li>Validate the audience claim matches the Cognito App Client ID</li>
     *   <li>Exchange validated JWT tokens for SAML assertions</li>
     * </ul>
     *
     * @param applicationArn the Identity Center application ARN
     * @param trustedTokenIssuerArn the trusted token issuer ARN (Cognito)
     * @param audienceClaim the expected audience claim value (Cognito App Client ID)
     */
    private void configureApplicationGrant(String applicationArn, String trustedTokenIssuerArn, String audienceClaim) {
        LOG.info("Configuring application grant for JWT bearer token exchange");
        LOG.info("  Application ARN: " + applicationArn);
        LOG.info("  Trusted Token Issuer ARN: " + trustedTokenIssuerArn);
        LOG.info("  Audience Claim (Cognito App Client ID): " + audienceClaim);

        // Configure JWT Bearer grant with authorized token issuer and audience
        // This tells Identity Center to accept JWT tokens from Cognito with the specified audience
        AwsSdkCall putGrantCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("putApplicationGrant")
                .parameters(Map.of(
                    "ApplicationArn", applicationArn,
                    "GrantType", "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "Grant", Map.of(
                        "JwtBearer", Map.of(
                            "AuthorizedTokenIssuers", List.of(
                                Map.of(
                                    "TrustedTokenIssuerArn", trustedTokenIssuerArn,
                                    "AuthorizedAudiences", List.of(audienceClaim)
                                )
                            )
                        )
                    )
                ))
                .physicalResourceId(PhysicalResourceId.of("AppGrant-" + stackName))
                .region(region)
                .build();

        // Note: No need for onDelete - grant is deleted when application is deleted
        AwsCustomResource.Builder.create(this, "ApplicationGrant")
                .onCreate(putGrantCall)
                .onUpdate(putGrantCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of("sso:PutApplicationGrant"))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        LOG.info("Application grant configured successfully");
        LOG.info("Identity Center will now accept JWT tokens from Cognito with audience: " + audienceClaim);
    }

    /**
     * Create an IAM role for the Identity Center application with permissions based on security profile.
     *
     * <p>This role enables <b>trusted identity propagation</b> - allowing the application to access
     * AWS services like S3, Athena, Redshift, etc. on behalf of authenticated users.</p>
     *
     * <p>The IAM permissions are determined using {@link IAMProfileMapper} based on the security profile:</p>
     * <ul>
     *   <li><b>PRODUCTION</b> → MINIMAL IAM profile (least privilege)</li>
     *   <li><b>STAGING</b> → STANDARD IAM profile (balanced permissions)</li>
     *   <li><b>DEV</b> → EXTENDED IAM profile (broader permissions)</li>
     * </ul>
     *
     * <p><b>Example Use Cases:</b></p>
     * <ul>
     *   <li>Metabase querying data from AWS Athena</li>
     *   <li>Metabase reading S3 buckets for data analysis</li>
     *   <li>Application accessing Redshift databases</li>
     * </ul>
     *
     * <p><b>Note:</b> For SAML SSO-only applications (no AWS resource access), this role is optional.</p>
     *
     * @param applicationArn the Identity Center application ARN
     * @return the IAM role ARN for trusted identity propagation
     */
    private String createApplicationIAMRole(String applicationArn) {
        LOG.info("Creating IAM role for Identity Center application with trusted identity propagation");

        // Determine IAM profile based on security profile using IAMProfileMapper
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
        LOG.info("  Security Profile: " + securityProfile);
        LOG.info("  IAM Profile (via IAMProfileMapper): " + iamProfile);

        String roleName = stackName + "-metabase-app-role";

        // Build permissions based on IAM profile
        List<PolicyStatement> permissions = new ArrayList<>();

        // Base permissions for all profiles
        permissions.add(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                    "s3:GetObject",
                    "s3:ListBucket"
                ))
                .resources(List.of("arn:aws:s3:::*"))
                .build());

        // Add additional permissions based on IAM profile
        switch (iamProfile) {
            case EXTENDED:
                // Extended permissions for development - includes write access
                permissions.add(PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "s3:PutObject",
                            "s3:DeleteObject",
                            "athena:*",
                            "glue:GetDatabase",
                            "glue:GetTable",
                            "glue:GetPartitions"
                        ))
                        .resources(List.of("*"))
                        .build());
                break;
            case STANDARD:
                // Standard permissions for staging - read-only data access
                permissions.add(PolicyStatement.Builder.create()
                        .effect(Effect.ALLOW)
                        .actions(List.of(
                            "athena:GetQueryExecution",
                            "athena:GetQueryResults",
                            "athena:StartQueryExecution",
                            "glue:GetDatabase",
                            "glue:GetTable"
                        ))
                        .resources(List.of("*"))
                        .build());
                break;
            case MINIMAL:
            default:
                // Minimal permissions for production - very restrictive
                LOG.info("  Using MINIMAL IAM profile - only basic S3 read access");
                break;
        }

        PolicyDocument permissionsPolicy = PolicyDocument.Builder.create()
                .statements(permissions)
                .build();

        // Create IAM role
        Role appRole = Role.Builder.create(this, "ApplicationRole")
                .roleName(roleName)
                .description("IAM role for " + applicationSpec.applicationId() + " with trusted identity propagation (IAM Profile: " + iamProfile + ")")
                .assumedBy(new ServicePrincipal("sso.amazonaws.com"))
                .inlinePolicies(Map.of("ApplicationPermissions", permissionsPolicy))
                .build();

        String roleArn = appRole.getRoleArn();
        LOG.info("IAM role created: " + roleArn);
        LOG.info("  Role Name: " + roleName);
        LOG.info("  IAM Profile: " + iamProfile);
        LOG.info("  Permissions: " + permissions.size() + " policy statements");

        return roleArn;
    }

    /**
     * Log SAML configuration details for manual setup.
     *
     * <p><b>CRITICAL AWS API LIMITATION:</b> The AWS SSO Admin API does NOT support
     * programmatic configuration of custom SAML applications. The API only supports
     * OAuth/OIDC grant types, not SAML grant types.</p>
     *
     * <p>ALL SAML-specific configuration must be done manually in the Identity Center console:</p>
     * <ol>
     *   <li>Go to IAM Identity Center > Applications</li>
     *   <li>Select the created application</li>
     *   <li>Configure SAML settings:
     *     <ul>
     *       <li>Application ACS URL: {acsUrl}</li>
     *       <li>Application SAML audience/Entity ID: {siteUrl}</li>
     *     </ul>
     *   </li>
     *   <li>Configure attribute mappings (email, firstName, lastName, etc.)</li>
     *   <li>Assign users/groups</li>
     * </ol>
     *
     * <p><b>Why this limitation exists:</b></p>
     * <ul>
     *   <li>PutApplicationGrant only accepts OAuth grant types: jwt-bearer, token-exchange, authorization_code, refresh_token</li>
     *   <li>PutApplicationGrant does NOT accept SAML grant type: urn:ietf:params:oauth:grant-type:saml2-bearer</li>
     *   <li>PutApplicationAuthenticationMethod only accepts "IAM" as AuthenticationMethodType, not "SAML"</li>
     *   <li>AWS SSO Admin API is designed for OAuth/OIDC applications, not SAML applications</li>
     * </ul>
     */
    private void configureSamlAuthentication(String applicationArn, String siteUrl, String acsUrl) {
        LOG.warning("AWS SSO Admin API LIMITATION: SAML configuration cannot be automated");
        LOG.warning("The AWS API only supports OAuth/OIDC grant types, NOT SAML grant types");
        LOG.warning("All SAML settings must be configured manually in the Identity Center console");
        LOG.info("SAML configuration values (use these in the console):");
        LOG.info("  Application ACS URL: " + acsUrl);
        LOG.info("  Application SAML audience (Entity ID): " + siteUrl);
        LOG.info("  Application ARN: " + applicationArn);

        // IMPORTANT: Do NOT attempt to use PutApplicationGrant or PutApplicationAuthenticationMethod
        // These APIs do not support SAML applications - they only support OAuth/OIDC applications
        // The CreateApplication call creates the SAML app shell, but all SAML-specific configuration
        // (ACS URL, Entity ID, attribute mappings) MUST be done manually via the console
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

        // Extract application instance ID from ARN using CloudFormation intrinsic functions
        // ARN format: arn:aws:sso:region:account-id:application/ssoins-instance-id/apl-application-id
        // Split by "/" and get the last element (index 2 of the split result)
        String applicationInstanceId = software.amazon.awscdk.Fn.select(2,
            software.amazon.awscdk.Fn.split("/", applicationArn));

        // Construct IdP metadata URL (uses SSO instance ID)
        // Format: https://portal.sso.{region}.amazonaws.com/saml/metadata/{ssoInstanceId}
        String metadataUrl = "https://portal.sso." + region + ".amazonaws.com/saml/metadata/" + instanceId;

        // Construct IdP SSO URL for SP-initiated SAML (uses APPLICATION instance ID)
        // Format: https://portal.sso.{region}.amazonaws.com/saml/assertion/{applicationInstanceId}
        String ssoUrl = "https://portal.sso." + region + ".amazonaws.com/saml/assertion/" + applicationInstanceId;

        LOG.info("IdP Metadata URL: " + metadataUrl);
        LOG.info("IdP SSO URL (SP-initiated): " + ssoUrl);
        LOG.info("Application Instance ID: (extracted from ARN at deploy time)");

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

        // Store SAML configuration in SystemContext for ApplicationOidcFactory to use
        ctx.samlIdpMetadataUrl.set(metadataUrl);
        ctx.samlIdpSsoUrl.set(ssoUrl);  // SP-initiated SAML URL with application instance ID
    }

    /**
     * Export SAML configuration to SystemContext for application use.
     */
    private void exportSamlConfiguration(String siteUrl, String acsUrl, String instanceId) {
        // Store site URL and ACS URL in SystemContext
        // Note: samlIdpSsoUrl is already set by storeIdpConfiguration() with the correct
        // SP-initiated SAML URL using the application instance ID
        ctx.samlSiteUrl.set(siteUrl);
        ctx.samlAcsUrl.set(acsUrl);
        ctx.samlIdpEntityId.set("urn:amazon:webservices");

        LOG.info("SAML configuration exported to SystemContext");
        LOG.info("  Site URL: " + siteUrl);
        LOG.info("  ACS URL: " + acsUrl);
        LOG.info("  Entity ID: urn:amazon:webservices");
    }

    /**
     * Create CloudFormation outputs for easy reference.
     */
    private void createOutputs(String appName, String siteUrl, String acsUrl, String instanceId, String roleArn) {
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

        // CRITICAL: AWS SSO Admin API LIMITATION - ALL SAML config must be done manually
        // The API only supports OAuth/OIDC applications, NOT SAML applications
        String postDeploymentSteps = initialAdminEmail != null && !initialAdminEmail.isEmpty()
                ? "MANUAL CONFIGURATION REQUIRED: 1) Open console URL below, 2) Select '" + appName + "', 3) Actions > Edit configuration, 4) Set ACS URL (see output), 5) Set Entity ID (see output), 6) Add attribute mappings"
                : "MANUAL CONFIGURATION REQUIRED: 1) Open console URL below, 2) Select '" + appName + "', 3) Actions > Edit configuration, 4) Set ACS URL (see output), 5) Set Entity ID (see output), 6) Add attribute mappings, 7) Assign users/groups";

        CfnOutput.Builder.create(this, "SamlPostDeployment")
                .description("AWS API LIMITATION: SAML apps require manual configuration")
                .value(postDeploymentSteps)
                .build();

        // Output required attribute mappings for the SAML application
        // AWS API does not support programmatic SAML attribute mapping - console only
        CfnOutput.Builder.create(this, "SamlAttrMappings")
                .description("REQUIRED attribute mappings - add ALL of these in Edit attribute mappings")
                .value("email=${user:email}, firstName=${user:givenName}, lastName=${user:familyName}, preferred_username=${user:preferredUsername}")
                .build();

        // Explain the AWS API limitation
        CfnOutput.Builder.create(this, "SamlApiLimitation")
                .description("Why manual configuration is required")
                .value("AWS SSO Admin API only supports OAuth/OIDC grant types (jwt-bearer, token-exchange, authorization_code, refresh_token). SAML grant type (saml2-bearer) is NOT supported. All SAML configuration must be done via console.")
                .build();

        // Output initial admin email and confirmation
        if (initialAdminEmail != null && !initialAdminEmail.isEmpty()) {
            CfnOutput.Builder.create(this, "SamlInitialAdminEmail")
                    .description("Initial admin user (auto-created and assigned)")
                    .value(initialAdminEmail + " - check email for password setup link")
                    .build();

            CfnOutput.Builder.create(this, "SamlUserCreationStatus")
                    .description("User provisioning status")
                    .value("AUTOMATED - User '" + initialAdminEmail + "' created and assigned automatically")
                    .build();

            LOG.info("Initial admin email for Identity Center assignment: " + initialAdminEmail);
        }

        // Output IAM role ARN for trusted identity propagation
        CfnOutput.Builder.create(this, "ApplicationRoleArn")
                .description("IAM role ARN for trusted identity propagation (uses IAMProfileMapper)")
                .value(roleArn)
                .build();

        // Get IAM profile for documentation
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(securityProfile);
        CfnOutput.Builder.create(this, "ApplicationRoleIAMProfile")
                .description("IAM profile applied to application role (based on security profile)")
                .value(iamProfile.toString() + " (Security Profile: " + securityProfile + ")")
                .build();
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

    /**
     * Configure Cognito User Pool as the external IdP (trusted token issuer) for Identity Center.
     *
     * <p>This enables the hybrid architecture where:</p>
     * <ul>
     *   <li>Users authenticate with Cognito (user/group management)</li>
     *   <li>Cognito issues OIDC JWT tokens</li>
     *   <li>Identity Center trusts Cognito tokens via trusted token issuer</li>
     *   <li>Identity Center issues SAML assertions to applications</li>
     *   <li>Applications receive SAML with user attributes from Cognito</li>
     * </ul>
     *
     * <p><b>Benefits of this approach:</b></p>
     * <ul>
     *   <li>Fully automated - Cognito API supports complete configuration</li>
     *   <li>User/group management stays in Cognito only</li>
     *   <li>Identity Center provides SAML to applications</li>
     *   <li>No manual console steps for user provisioning</li>
     * </ul>
     *
     * @param applicationArn Identity Center application ARN (not used currently, for future application-specific config)
     * @param userPoolId Cognito User Pool ID
     */
    private void configureCognitoAsExternalIdP(String applicationArn, String userPoolId) {
        LOG.info("Configuring Cognito as trusted token issuer for Identity Center");
        LOG.info("  Application ARN: " + applicationArn);
        LOG.info("  Cognito User Pool ID: " + userPoolId);

        // Construct Cognito OIDC issuer URL
        // Format: https://cognito-idp.{region}.amazonaws.com/{userPoolId}
        String issuerUrl = String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
        String trustedTokenIssuerName = stackName + "-cognito-idp";

        LOG.info("  Issuer URL: " + issuerUrl);
        LOG.info("  Trusted Token Issuer Name: " + trustedTokenIssuerName);

        // Create trusted token issuer in Identity Center
        // This tells Identity Center to trust OIDC JWT tokens from Cognito
        AwsSdkCall createTrustedTokenIssuerCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("createTrustedTokenIssuer")
                .parameters(Map.of(
                    "Name", trustedTokenIssuerName,
                    "InstanceArn", ssoInstanceArn,
                    "TrustedTokenIssuerType", "OIDC_JWT",
                    "TrustedTokenIssuerConfiguration", Map.of(
                        "OidcJwtConfiguration", Map.of(
                            "IssuerUrl", issuerUrl,
                            // Map Cognito email claim to Identity Center userId
                            "ClaimAttributePath", "email",
                            "IdentityStoreAttributePath", "emails.value",
                            // Retrieve JWKS from Cognito's well-known endpoint
                            "JwksRetrievalOption", "OPEN_ID_DISCOVERY"
                        )
                    )
                ))
                .physicalResourceId(PhysicalResourceId.fromResponse("TrustedTokenIssuerArn"))
                .region(region)
                .build();

        // Delete trusted token issuer on stack deletion
        AwsSdkCall deleteTrustedTokenIssuerCall = AwsSdkCall.builder()
                .service("SSOAdmin")
                .action("deleteTrustedTokenIssuer")
                .parameters(Map.of(
                    "TrustedTokenIssuerArn", new software.amazon.awscdk.customresources.PhysicalResourceIdReference()
                ))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")
                .build();

        // Create Custom Resource for trusted token issuer
        AwsCustomResource trustedTokenIssuer = AwsCustomResource.Builder.create(this, "CognitoTrustedTokenIssuer")
                .onCreate(createTrustedTokenIssuerCall)
                .onDelete(deleteTrustedTokenIssuerCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                        .actions(List.of(
                            "sso:CreateTrustedTokenIssuer",
                            "sso:DeleteTrustedTokenIssuer",
                            "sso:DescribeTrustedTokenIssuer",
                            "sso:UpdateTrustedTokenIssuer"
                        ))
                        .resources(List.of("*"))
                        .build()
                )))
                .build();

        String trustedTokenIssuerArn = trustedTokenIssuer.getResponseField("TrustedTokenIssuerArn");

        LOG.info("Cognito configured as trusted token issuer for Identity Center");
        LOG.info("  Trusted Token Issuer ARN: " + trustedTokenIssuerArn);

        // Configure application grant with audience claim for Cognito token validation
        // Wait for Cognito Client ID to be available, then configure the grant
        ctx.cognitoClientId.onSet(clientId -> {
            LOG.info("Configuring application grant with Aud claim: " + clientId);
            configureApplicationGrant(applicationArn, trustedTokenIssuerArn, clientId);
        });

        // Create CloudFormation output for trusted token issuer ARN
        CfnOutput.Builder.create(this, "TrustedTokenIssuerArn")
                .description("Cognito Trusted Token Issuer ARN for Identity Center")
                .value(trustedTokenIssuerArn)
                .build();

        CfnOutput.Builder.create(this, "CognitoIdpIntegration")
                .description("Cognito integration status")
                .value("FULLY AUTOMATED - Cognito acts as IdP for Identity Center, users managed in Cognito only")
                .build();

        // Document group claim mapping requirements
        LOG.info("Hybrid architecture configured: Cognito (users/groups) -> Identity Center (SAML) -> Application");
        LOG.info("IMPORTANT: For group synchronization to work:");
        LOG.info("  1. Set cognitoAutoProvision=true to create Cognito User Pool");
        LOG.info("  2. Set cognitoCreateGroups=true to create groups in Cognito");
        LOG.info("  3. Cognito includes groups in 'cognito:groups' claim");
        LOG.info("  4. Configure SAML attribute mapping in Identity Center console:");
        LOG.info("     - Attribute: groups");
        LOG.info("     - Maps to: ${path:cognito:groups}");

        // Create CloudFormation output with group mapping instructions
        CfnOutput.Builder.create(this, "CognitoGroupMapping")
                .description("Group attribute mapping - configure in Identity Center console")
                .value("Add attribute mapping: groups -> ${path:cognito:groups} (requires cognitoCreateGroups=true)")
                .build();

        CfnOutput.Builder.create(this, "CognitoGroupsClaim")
                .description("Cognito groups claim name")
                .value("cognito:groups - automatically included in Cognito JWT when user is in groups")
                .build();

        CfnOutput.Builder.create(this, "ApplicationAssignmentConfig")
                .description("Application assignment configuration status")
                .value("AUTOMATED - AssignmentRequired=false (all Identity Center users can access)")
                .build();

        CfnOutput.Builder.create(this, "ApplicationGrantConfig")
                .description("Application grant configuration status")
                .value("AUTOMATED - JWT Bearer grant configured with Cognito as trusted token issuer")
                .build();

        CfnOutput.Builder.create(this, "AudienceClaimConfig")
                .description("Audience claim validation")
                .value("AUTOMATED - Aud claim set to Cognito App Client ID for token validation")
                .build();
    }
}
