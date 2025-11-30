package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.actions.*;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.List;
import java.util.logging.Logger;

/**
 * Manages AWS Cognito User Pools for OIDC authentication.
 *
 * <p><b>Quick Start:</b></p>
 * <pre>
 * {
 *   "authMode": "alb-oidc",
 *   "cognitoAutoProvision": true,
 *   "cognitoDomainPrefix": "myapp-auth",
 *   "cognitoMfaEnabled": true,
 *   "cognitoMfaMethod": "both"  // TOTP + SMS
 * }
 * </pre>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Auto-provision User Pools with security best practices</li>
 *   <li>OAuth 2.0 App Client for ALB OIDC integration</li>
 *   <li>MFA support: TOTP (authenticator apps) and SMS</li>
 *   <li>User groups with role-based access control</li>
 *   <li>Compliance-ready (PCI-DSS, HIPAA, SOC 2, GDPR)</li>
 * </ul>
 *
 * <p><b>MFA Configuration:</b></p>
 * <ul>
 *   <li>"totp" - Authenticator apps only (Google Authenticator, Authy)</li>
 *   <li>"sms" - Text message codes (requires AWS SMS spending limit &gt; $0)</li>
 *   <li>"both" - Users choose their preferred method (default)</li>
 * </ul>
 *
 * <p><b>SMS Requirements:</b> AWS accounts default to $0/month SMS spending limit.
 * To enable SMS MFA: AWS Console → Service Quotas → Amazon SNS →
 * "Account spending limit for SMS" → Request increase to $1-$10/month</p>
 *
 * <p><b>Removal Policy:</b> Production User Pools are RETAINED on stack deletion
 * to prevent data loss. Reuse with cognitoUserPoolId in deployment context.</p>
 */
public class CognitoAuthenticationFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(CognitoAuthenticationFactory.class.getName());

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("stackName")
    private String stackName;

    @DeploymentContext("region")
    private String region;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("fqdn")
    private String fqdn;

    // Auto-provision new Cognito User Pool
    @DeploymentContext("cognitoAutoProvision")
    private Boolean cognitoAutoProvision;

    @DeploymentContext("cognitoUserPoolName")
    private String cognitoUserPoolName;

    @DeploymentContext("cognitoDomainPrefix")
    private String cognitoDomainPrefix;

    @DeploymentContext("cognitoMfaEnabled")
    private Boolean cognitoMfaEnabled;

    @DeploymentContext("cognitoMfaMethod")
    private String cognitoMfaMethod;

    // User groups configuration
    @DeploymentContext("cognitoCreateGroups")
    private Boolean cognitoCreateGroups;

    @DeploymentContext("cognitoAdminGroupName")
    private String cognitoAdminGroupName;

    @DeploymentContext("cognitoUserGroupName")
    private String cognitoUserGroupName;

    @DeploymentContext("cognitoInitialAdminEmail")
    private String cognitoInitialAdminEmail;

    @DeploymentContext("cognitoInitialAdminPhone")
    private String cognitoInitialAdminPhone;

    // Use existing Cognito User Pool
    @DeploymentContext("cognitoUserPoolId")
    private String cognitoUserPoolId;

    @DeploymentContext("cognitoAppClientId")
    private String cognitoAppClientId;

    // Client secret configuration
    @DeploymentContext("oidcClientSecretName")
    private String oidcClientSecretName;

    @com.cloudforge.core.annotation.SystemContext("securityProfileConfig")
    private com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityProfileConfig;

    @com.cloudforge.core.annotation.SystemContext("cognitoUserPool")
    private UserPool cognitoUserPool;

    @com.cloudforge.core.annotation.SystemContext("cognitoUserPoolClient")
    private UserPoolClient cognitoUserPoolClient;

    @com.cloudforge.core.annotation.SystemContext("cognitoUserPoolDomain")
    private UserPoolDomain cognitoUserPoolDomain;

    public CognitoAuthenticationFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only configure Cognito if authMode is OIDC-based
        if (!"alb-oidc".equals(authMode) && !"jenkins-oidc".equals(authMode) && !"application-oidc".equals(authMode)) {
            LOG.info("Cognito authentication not applicable (authMode = " + authMode + ")");
            return;
        }

        // Check if Cognito auto-provisioning is enabled
        if (cognitoAutoProvision != null && cognitoAutoProvision) {
            LOG.info("Auto-provisioning Cognito User Pool for OIDC authentication");
            createCognitoUserPool();
            configureAlbAuthentication();
            return;
        }

        // Check if using existing Cognito User Pool
        if (cognitoUserPoolId != null && !cognitoUserPoolId.isEmpty()) {
            LOG.info("Using existing Cognito User Pool: " + cognitoUserPoolId);
            configureExistingUserPool();
            configureAlbAuthentication();
            return;
        }

        LOG.info("Cognito authentication not configured (use cognitoAutoProvision = true or provide cognitoUserPoolId)");
    }

    /**
     * Creates a new Cognito User Pool with security best practices.
     *
     * <p>Configures:
     * <ul>
     *   <li>Strong password policy (12+ chars, mixed case, numbers, symbols)</li>
     *   <li>Email sign-in with verification</li>
     *   <li>MFA (TOTP and/or SMS based on cognitoMfaMethod)</li>
     *   <li>Custom domain for hosted UI</li>
     *   <li>OAuth 2.0 App Client for ALB OIDC</li>
     *   <li>User groups for role-based access</li>
     * </ul>
     *
     * <p><b>Removal Policy:</b></p>
     * <ul>
     *   <li>Production: RETAIN (prevents data loss, manual cleanup)</li>
     *   <li>Dev/Staging: DESTROY (automatic cleanup)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if cognitoDomainPrefix is missing
     */
    private void createCognitoUserPool() {
        // Validate required configuration
        if (cognitoDomainPrefix == null || cognitoDomainPrefix.isEmpty()) {
            LOG.severe("cognitoDomainPrefix is required for Cognito auto-provisioning");
            throw new IllegalArgumentException("cognitoDomainPrefix is required when cognitoAutoProvision = true");
        }

        // Sanitize domain prefix: lowercase, only alphanumerics and hyphens
        // Cognito requirement: lowercase letters, numbers, and hyphens only
        String sanitizedDomainPrefix = cognitoDomainPrefix
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")  // Replace invalid chars with hyphen
                .replaceAll("-+", "-")          // Collapse multiple hyphens
                .replaceAll("^-|-$", "");       // Remove leading/trailing hyphens

        if (!sanitizedDomainPrefix.equals(cognitoDomainPrefix)) {
            LOG.warning("Cognito domain prefix sanitized: '" + cognitoDomainPrefix + "' -> '" + sanitizedDomainPrefix + "'");
        }

        // Make domain prefix stack-scoped to avoid conflicts between multiple stacks
        String stackScopedPrefix = sanitizedDomainPrefix + "-" + stackName.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // Use stack-scoped prefix
        cognitoDomainPrefix = stackScopedPrefix;

        LOG.info("Cognito domain prefix scoped to stack: '" + sanitizedDomainPrefix + "' -> '" + stackScopedPrefix + "'");

        // Set default user pool name
        String userPoolName = (cognitoUserPoolName != null && !cognitoUserPoolName.isEmpty())
                ? cognitoUserPoolName
                : stackName + "-users";

        // Determine removal policy based on security profile (injected via annotation)
        boolean isProduction = (securityProfileConfig != null && securityProfileConfig.getClass().getSimpleName().contains("Production"));
        RemovalPolicy userPoolRemovalPolicy = isProduction ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;

        LOG.info("Creating/Importing Cognito User Pool: " + userPoolName);
        LOG.info("Domain prefix: " + cognitoDomainPrefix);
        LOG.info("User Pool removal policy: " + userPoolRemovalPolicy + " (isProduction = " + isProduction + ")");

        // Determine which MFA methods to enable based on cognitoMfaMethod
        // Valid values: "totp", "sms", "both" (default: "both")
        String mfaMethod = (cognitoMfaMethod != null && !cognitoMfaMethod.isEmpty())
                ? cognitoMfaMethod.toLowerCase()
                : "both";

        boolean enableTotp = false;
        boolean enableSms = false;

        switch (mfaMethod) {
            case "totp":
                enableTotp = true;
                enableSms = false;
                LOG.info("MFA method: TOTP (authenticator apps)");
                break;
            case "sms":
                enableTotp = false;
                enableSms = true;
                LOG.info("MFA method: SMS");
                break;
            case "both":
                enableTotp = true;
                enableSms = true;
                LOG.info("MFA method: Both TOTP and SMS");
                break;
            default:
                LOG.warning("Invalid cognitoMfaMethod '" + mfaMethod + "', defaulting to TOTP");
                enableTotp = true;
                enableSms = false;
        }

        // SMS MFA Configuration:
        // Cognito handles SMS delivery automatically using AWS-managed SNS integration
        // No explicit IAM role needed for basic SMS MFA functionality
        // For custom SMS configuration (sender ID, etc.), use CfnUserPool with SmsConfiguration
        //
        // IMPORTANT: SMS MFA requires AWS account SMS spending limit to be increased
        // Default limit is $0/month which blocks ALL SMS messages
        // To enable SMS:
        // 1. Open AWS Console -> Service Quotas -> Amazon SNS
        // 2. Request quota increase for "Account spending limit for SMS" to at least $1-$10/month
        // 3. Wait for approval (usually instant for small increases)
        if (enableSms) {
            LOG.warning("SMS MFA is enabled - ensure AWS account SMS spending limit is configured");
            LOG.warning("Check: AWS Console > Service Quotas > Amazon SNS > Account spending limit for SMS");
            LOG.warning("Default is $0/month which blocks all SMS. Increase to at least $1-$10/month.");
        }

        // Configure MFA second factor
        // Note: Both otp and sms must be explicitly set (CDK requirement)
        MfaSecondFactor mfaSecondFactor = MfaSecondFactor.builder()
                .otp(enableTotp)
                .sms(enableSms)
                .build();

        // PRODUCTION REUSE STRATEGY:
        // Production UserPools have RETAIN policy - they are NOT deleted when stack is destroyed
        // This prevents data loss but requires manual reuse on subsequent deployments.
        //
        // To reuse an existing production UserPool:
        // 1. Find the UserPool ID: aws cognito-idp list-user-pools --max-results 60
        // 2. Add to deployment-context.json: "cognitoUserPoolId": "us-east-1_abc123xyz"
        // 3. Set cognitoAutoProvision: false (to skip creation)
        // 4. Deploy - will import and reuse the existing pool
        //
        // Dev/Staging: UserPools have DESTROY policy - automatically deleted on stack deletion

        if (isProduction) {
            LOG.info("Production mode: User Pool will be RETAINED on stack deletion for safety");
            LOG.info("  To reuse existing pool: set 'cognitoUserPoolId' in deployment context");
            LOG.info("  Example: aws cognito-idp list-user-pools --max-results 60 | grep '" + userPoolName + "'");
        }

        // Create SNS role for SMS MFA if SMS is enabled
        Role smsRole = null;
        String externalId = null;
        String snsRegion = region != null ? region : "us-east-1";

        if (enableSms) {
            // Generate a unique external ID for security (prevents confused deputy problem)
            externalId = "cognito-sns-" + stackName;

            // Create least-privilege SNS policy for Cognito SMS
            // Only allow publishing to SNS topics (required for SMS MFA)
            software.amazon.awscdk.services.iam.PolicyStatement snsPublishPolicy =
                    software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                    .sid("CognitoSNSPublish")
                    .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                    .actions(List.of("sns:Publish"))
                    .resources(List.of("*"))  // Cognito needs wildcard for SMS
                    .build();

            smsRole = Role.Builder.create(this, "CognitoSmsRole")
                    .assumedBy(ServicePrincipal.Builder.create("cognito-idp.amazonaws.com")
                            .build())
                    .externalIds(List.of(externalId))
                    .inlinePolicies(java.util.Map.of(
                            "CognitoSNSPolicy",
                            software.amazon.awscdk.services.iam.PolicyDocument.Builder.create()
                                    .statements(List.of(snsPublishPolicy))
                                    .build()
                    ))
                    .build();

            // RETAIN the SMS role when User Pool is retained (production)
            // Without this, stack deletion removes the role but leaves the User Pool orphaned
            smsRole.applyRemovalPolicy(userPoolRemovalPolicy);

            LOG.info("Created SNS role for SMS MFA with least-privilege permissions: " + smsRole.getRoleArn());
            LOG.info("  - External ID: [REDACTED]");
            LOG.info("  - SNS Region: " + snsRegion);
            LOG.info("  - Permissions: sns:Publish only (least privilege)");
            LOG.info("  - Removal policy: " + userPoolRemovalPolicy);
        }

        // Create User Pool with strong security configuration
        UserPool.Builder userPoolBuilder = UserPool.Builder.create(this, "UserPool")
                .userPoolName(userPoolName)
                // Password policy - PCI-DSS and HIPAA compliant
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(12)
                        .requireUppercase(true)
                        .requireLowercase(true)
                        .requireDigits(true)
                        .requireSymbols(true)
                        .tempPasswordValidity(software.amazon.awscdk.Duration.days(3))
                        .build())
                // Email verification required
                .signInAliases(SignInAliases.builder()
                        .email(true)
                        .username(false)
                        .build());

        // Configure auto-verification based on MFA method
        if (enableSms) {
            // SMS MFA requires phone number verification
            userPoolBuilder.autoVerify(AutoVerifiedAttrs.builder()
                    .email(true)
                    .phone(true)  // Required for SMS MFA
                    .build());
            LOG.info("Auto-verification enabled for email and phone (SMS MFA enabled)");
        } else {
            // TOTP only requires email verification
            userPoolBuilder.autoVerify(AutoVerifiedAttrs.builder()
                    .email(true)
                    .build());
            LOG.info("Auto-verification enabled for email only (TOTP MFA)");
        }

        userPoolBuilder
                // MFA configuration
                .mfa(cognitoMfaEnabled != null && cognitoMfaEnabled ? Mfa.REQUIRED : Mfa.OPTIONAL)
                .mfaSecondFactor(mfaSecondFactor)
                // Account recovery
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                // Advanced security features disabled (Plus plan required for AUDIT/ENFORCED modes)
                // .advancedSecurityMode(AdvancedSecurityMode.AUDIT)
                // Self-service account recovery
                .selfSignUpEnabled(false)  // Admins must create users for security
                // Removal policy - RETAIN for production, DESTROY for dev/staging
                .removalPolicy(userPoolRemovalPolicy);

        UserPool userPool = userPoolBuilder.build();

        // Configure SMS role and phone number schema using CloudFormation escape hatch
        CfnUserPool cfnUserPool = (CfnUserPool) userPool.getNode().getDefaultChild();

        // Enable phone number as a standard attribute for SMS MFA
        cfnUserPool.setSchema(List.of(
                CfnUserPool.SchemaAttributeProperty.builder()
                        .name("email")
                        .attributeDataType("String")
                        .mutable(true)
                        .required(true)
                        .build(),
                CfnUserPool.SchemaAttributeProperty.builder()
                        .name("phone_number")
                        .attributeDataType("String")
                        .mutable(true)
                        .required(false)
                        .build()
        ));

        if (enableSms && smsRole != null) {
            cfnUserPool.setSmsConfiguration(CfnUserPool.SmsConfigurationProperty.builder()
                    .snsCallerArn(smsRole.getRoleArn())
                    .externalId(externalId)
                    .snsRegion(snsRegion)
                    .build());
            LOG.info("Configured SMS role for User Pool:");
            LOG.info("  - Role ARN: " + smsRole.getRoleArn());
            LOG.info("  - External ID: [REDACTED]");
            LOG.info("  - SNS Region: " + snsRegion);
        }

        LOG.info("User Pool created: [REDACTED]");
        LOG.info("MFA Configuration Summary:");
        LOG.info("  - MFA Enabled: " + (cognitoMfaEnabled != null && cognitoMfaEnabled ? "REQUIRED" : "OPTIONAL"));
        LOG.info("  - TOTP (Authenticator Apps): " + (enableTotp ? "ENABLED" : "DISABLED"));
        LOG.info("  - SMS (Text Messages): " + (enableSms ? "ENABLED" : "DISABLED"));
        if (enableSms) {
            LOG.info("  - SMS Role ARN: " + (smsRole != null ? smsRole.getRoleArn() : "NOT CONFIGURED"));
            LOG.info("  - Phone Number Attribute: ENABLED");
        }

        // Create user groups if enabled
        createUserGroups(userPool);

        // Create initial admin user if email provided (even if groups are disabled)
        if (cognitoInitialAdminEmail != null && !cognitoInitialAdminEmail.isEmpty() &&
            (cognitoCreateGroups == null || !cognitoCreateGroups)) {
            // Groups disabled but admin email provided - create user without group attachment
            createInitialAdminUser(userPool, null);
        }

        // Create custom domain for hosted UI
        UserPoolDomain userPoolDomain = UserPoolDomain.Builder.create(this, "UserPoolDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix(cognitoDomainPrefix)
                        .build())
                .build();

        LOG.info("User Pool domain created: " + cognitoDomainPrefix + ".auth." + region + ".amazoncognito.com");

        // Construct redirect URL
        String redirectUrl = constructRedirectUrl();
        LOG.info("Redirect URL: " + redirectUrl);

        // Create App Client for ALB OIDC authentication
        UserPoolClient appClient = UserPoolClient.Builder.create(this, "AppClient")
                .userPool(userPool)
                .userPoolClientName(stackName + "-alb-client")
                // Generate client secret (required for ALB OIDC)
                .generateSecret(true)
                // OAuth 2.0 configuration
                .oAuth(OAuthSettings.builder()
                        .flows(OAuthFlows.builder()
                                .authorizationCodeGrant(true)
                                .build())
                        .scopes(List.of(
                                OAuthScope.OPENID,
                                OAuthScope.EMAIL,
                                OAuthScope.PROFILE
                        ))
                        .callbackUrls(List.of(redirectUrl))
                        .build())
                // Token validity
                .idTokenValidity(software.amazon.awscdk.Duration.hours(1))
                .accessTokenValidity(software.amazon.awscdk.Duration.hours(1))
                .refreshTokenValidity(software.amazon.awscdk.Duration.days(30))
                // Prevent user existence errors (security best practice)
                .preventUserExistenceErrors(true)
                .build();

        LOG.info("App Client created: " + appClient.getUserPoolClientId());

        // Note: For ALB-level OIDC (alb-oidc), Cognito manages client secret internally
        // For application-level OIDC (application-oidc), we need to store secret in Secrets Manager
        // so the application container can retrieve it at runtime
        String secretName = null;

        // Check if we need to store the client secret for application-level OIDC
        if ("application-oidc".equals(authMode)) {
            LOG.info("Application-level OIDC detected - storing Cognito client secret in Secrets Manager");
            secretName = storeCognitoClientSecret(userPool, appClient);
        } else {
            LOG.info("ALB-level OIDC - Cognito will manage client secret internally");
            LOG.info("Client secret retrieval command:");
            LOG.info("  aws cognito-idp describe-user-pool-client --user-pool-id [REDACTED] --client-id [REDACTED]");
        }

        // Export OIDC endpoints to SystemContext for OidcAuthenticationFactory to use
        exportOidcEndpoints(userPool.getUserPoolId(), appClient.getUserPoolClientId(), cognitoDomainPrefix, secretName);

        // Export CDK objects for native ALB Cognito authentication
        ctx.cognitoUserPool.set(userPool);
        ctx.cognitoUserPoolClient.set(appClient);
        ctx.cognitoUserPoolDomain.set(userPoolDomain);
        LOG.info("Exported Cognito CDK objects to SystemContext for native ALB Cognito authentication");

        // Store User Pool ARN in SSM Parameter Store for compliance tracking (PRODUCTION only)
        storeUserPoolArnInSSM(userPool);

        LOG.info("Cognito User Pool setup complete");
    }

    /**
     * Configure existing Cognito User Pool by creating/updating app client.
     */
    private void configureExistingUserPool() {
        // Import existing User Pool
        IUserPool userPool = UserPool.fromUserPoolId(this, "ImportedUserPool", cognitoUserPoolId);
        LOG.info("Imported existing User Pool: " + cognitoUserPoolId);

        // Export UserPool to SystemContext (available for both paths)
        ctx.cognitoUserPool.set(userPool);

        // If app client ID is provided, use it; otherwise create a new one
        if (cognitoAppClientId != null && !cognitoAppClientId.isEmpty()) {
            LOG.info("Using existing App Client: " + cognitoAppClientId);

            // Extract region and domain prefix from existing configuration
            // Note: We need domain prefix to construct OIDC endpoints
            if (cognitoDomainPrefix == null || cognitoDomainPrefix.isEmpty()) {
                LOG.warning("cognitoDomainPrefix not provided - cannot auto-configure OIDC endpoints");
                LOG.warning("Please provide cognitoDomainPrefix or configure OIDC endpoints manually");
                return;
            }

            // Import existing User Pool Client
            IUserPoolClient appClient = UserPoolClient.fromUserPoolClientId(this, "ImportedAppClient", cognitoAppClientId);
            ctx.cognitoUserPoolClient.set(appClient);

            // Import existing User Pool Domain (requires full domain name)
            String fullDomainName = cognitoDomainPrefix + ".auth." + region + ".amazoncognito.com";
            IUserPoolDomain userPoolDomain = UserPoolDomain.fromDomainName(this, "ImportedUserPoolDomain", fullDomainName);
            ctx.cognitoUserPoolDomain.set(userPoolDomain);

            LOG.info("Imported and exported Cognito CDK objects to SystemContext");

            // Export OIDC endpoints
            // Pass null for secretName since Cognito manages client secret internally
            exportOidcEndpoints(cognitoUserPoolId, cognitoAppClientId, cognitoDomainPrefix, null);
        } else {
            LOG.info("Creating new App Client for existing User Pool");

            // Validate domain prefix is provided
            if (cognitoDomainPrefix == null || cognitoDomainPrefix.isEmpty()) {
                LOG.severe("cognitoDomainPrefix is required when creating app client");
                throw new IllegalArgumentException("cognitoDomainPrefix is required");
            }

            // Construct redirect URL
            String redirectUrl = constructRedirectUrl();

            // Create new App Client
            UserPoolClient appClient = UserPoolClient.Builder.create(this, "AppClient")
                    .userPool(userPool)
                    .userPoolClientName(stackName + "-alb-client")
                    .generateSecret(true)
                    .oAuth(OAuthSettings.builder()
                            .flows(OAuthFlows.builder()
                                    .authorizationCodeGrant(true)
                                    .build())
                            .scopes(List.of(OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE))
                            .callbackUrls(List.of(redirectUrl))
                            .build())
                    .preventUserExistenceErrors(true)
                    .build();

            LOG.info("App Client created: " + appClient.getUserPoolClientId());

            // Import existing User Pool Domain (requires full domain name)
            String fullDomainName = cognitoDomainPrefix + ".auth." + region + ".amazoncognito.com";
            IUserPoolDomain userPoolDomain = UserPoolDomain.fromDomainName(this, "ImportedUserPoolDomain", fullDomainName);

            // Export CDK objects for native ALB Cognito authentication
            ctx.cognitoUserPoolClient.set(appClient);
            ctx.cognitoUserPoolDomain.set(userPoolDomain);
            LOG.info("Exported Cognito CDK objects to SystemContext");

            // Export OIDC endpoints
            // Pass null for secretName since Cognito manages client secret internally
            exportOidcEndpoints(cognitoUserPoolId, appClient.getUserPoolClientId(), cognitoDomainPrefix, null);
        }
    }

    /**
     * Construct redirect URL based on domain configuration.
     */
    private String constructRedirectUrl() {
        if (fqdn != null && !fqdn.isEmpty()) {
            return "https://" + fqdn + "/oauth2/idpresponse";
        } else if (domain != null && !domain.isEmpty()) {
            if (subdomain != null && !subdomain.isEmpty()) {
                return "https://" + subdomain + "." + domain + "/oauth2/idpresponse";
            } else {
                return "https://" + domain + "/oauth2/idpresponse";
            }
        } else {
            // No domain configured - ALB DNS will be used (must update callback URL after deployment)
            LOG.warning("No domain configured - callback URL will need to be updated after deployment");
            return "https://example.com/oauth2/idpresponse";  // Placeholder
        }
    }

    /**
     * Export Cognito OIDC endpoints to DeploymentContext for OidcAuthenticationFactory.
     * This allows seamless integration between CognitoAuthenticationFactory and OidcAuthenticationFactory.
     */
    private void exportOidcEndpoints(String userPoolId, String clientId, String domainPrefix, String secretName) {
        String issuer = "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
        String authEndpoint = "https://" + domainPrefix + ".auth." + region + ".amazoncognito.com/oauth2/authorize";
        String tokenEndpoint = "https://" + domainPrefix + ".auth." + region + ".amazoncognito.com/oauth2/token";
        String userInfoEndpoint = "https://" + domainPrefix + ".auth." + region + ".amazoncognito.com/oauth2/userInfo";

        LOG.info("Exporting OIDC endpoints to DeploymentContext:");
        LOG.info("  Issuer: " + issuer);
        LOG.info("  Authorization: [REDACTED]");
        LOG.info("  Token: [REDACTED]");
        LOG.info("  UserInfo: [REDACTED]");
        LOG.info("  Client ID: [REDACTED]");
        LOG.info("  Secret Name: " + (secretName != null ? "[REDACTED]" : "null"));

        // Store in SystemContext slots for OidcAuthenticationFactory to use
        ctx.cognitoIssuer.set(issuer);
        ctx.cognitoAuthorizationEndpoint.set(authEndpoint);
        ctx.cognitoTokenEndpoint.set(tokenEndpoint);
        ctx.cognitoUserInfoEndpoint.set(userInfoEndpoint);
        ctx.cognitoClientId.set(clientId);
        ctx.cognitoClientSecretName.set(secretName);
        ctx.cognitoUserPoolId.set(userPoolId);
        ctx.cognitoDomainPrefix.set(domainPrefix);

        LOG.info("OIDC endpoints exported - OidcAuthenticationFactory will use these for ALB configuration");
    }

    /**
     * Create user groups for role-based access control.
     */
    private void createUserGroups(UserPool userPool) {
        if (cognitoCreateGroups == null || !cognitoCreateGroups) {
            LOG.info("User groups creation disabled");
            return;
        }

        String adminGroupName = (cognitoAdminGroupName != null && !cognitoAdminGroupName.isEmpty())
                ? cognitoAdminGroupName
                : "Jenkins-Admins";

        String userGroupName = (cognitoUserGroupName != null && !cognitoUserGroupName.isEmpty())
                ? cognitoUserGroupName
                : "Jenkins-Users";

        LOG.info("Creating user groups: " + adminGroupName + ", " + userGroupName);

        // Create admin group
        CfnUserPoolGroup.Builder.create(this, "AdminGroup")
                .userPoolId(userPool.getUserPoolId())
                .groupName(adminGroupName)
                .description("Jenkins administrators with full access")
                .precedence(1)  // Higher precedence (lower number = higher priority)
                .build();

        // Create user group
        CfnUserPoolGroup.Builder.create(this, "UserGroup")
                .userPoolId(userPool.getUserPoolId())
                .groupName(userGroupName)
                .description("Jenkins users with standard access")
                .precedence(10)  // Lower precedence
                .build();

        LOG.info("User groups created: " + adminGroupName + " (precedence 1), " + userGroupName + " (precedence 10)");

        // Create initial admin user if email provided
        if (cognitoInitialAdminEmail != null && !cognitoInitialAdminEmail.isEmpty()) {
            createInitialAdminUser(userPool, adminGroupName);
        }
    }

    /**
     * Create initial admin user with temporary password.
     * User will be required to change password on first login.
     */
    private void createInitialAdminUser(UserPool userPool, String adminGroupName) {
        LOG.info("Creating initial admin user: " + cognitoInitialAdminEmail);

        // Build user attributes list
        java.util.List<CfnUserPoolUser.AttributeTypeProperty> attributes = new java.util.ArrayList<>();

        // Add email attributes
        attributes.add(CfnUserPoolUser.AttributeTypeProperty.builder()
                .name("email")
                .value(cognitoInitialAdminEmail)
                .build());
        attributes.add(CfnUserPoolUser.AttributeTypeProperty.builder()
                .name("email_verified")
                .value("true")
                .build());

        // Add phone number attributes if provided
        if (cognitoInitialAdminPhone != null && !cognitoInitialAdminPhone.trim().isEmpty()) {
            attributes.add(CfnUserPoolUser.AttributeTypeProperty.builder()
                    .name("phone_number")
                    .value(cognitoInitialAdminPhone)
                    .build());
            attributes.add(CfnUserPoolUser.AttributeTypeProperty.builder()
                    .name("phone_number_verified")
                    .value("true")
                    .build());
            LOG.info("  - Phone number configured: " + cognitoInitialAdminPhone);
        }

        // Create user with email as username
        CfnUserPoolUser adminUser = CfnUserPoolUser.Builder.create(this, "InitialAdminUser")
                .userPoolId(userPool.getUserPoolId())
                .username(cognitoInitialAdminEmail)
                .userAttributes(attributes)
                // FORCE_CHANGE_PASSWORD means user must change password on first login
                .desiredDeliveryMediums(List.of("EMAIL"))
                .build();

        // Add user to admin group if group name provided
        if (adminGroupName != null && !adminGroupName.isEmpty()) {
            CfnUserPoolUserToGroupAttachment groupAttachment = CfnUserPoolUserToGroupAttachment.Builder.create(this, "InitialAdminGroupAttachment")
                    .userPoolId(userPool.getUserPoolId())
                    .username(cognitoInitialAdminEmail)
                    .groupName(adminGroupName)
                    .build();

            // Ensure user is created before adding to group
            groupAttachment.getNode().addDependency(adminUser);

            LOG.info("Initial admin user created: " + cognitoInitialAdminEmail);
            LOG.info("  - User will receive email with temporary password");
            LOG.info("  - User must change password on first login");
            LOG.info("  - User added to admin group: " + adminGroupName);
        } else {
            LOG.info("Initial admin user created: " + cognitoInitialAdminEmail);
            LOG.info("  - User will receive email with temporary password");
            LOG.info("  - User must change password on first login");
            LOG.info("  - No group assignment (groups disabled)");
        }

        // Check if SMS MFA is enabled and warn about phone number requirement
        if (cognitoMfaEnabled != null && cognitoMfaEnabled &&
            cognitoMfaMethod != null && (cognitoMfaMethod.equals("sms") || cognitoMfaMethod.equals("both"))) {
            if (cognitoInitialAdminPhone == null || cognitoInitialAdminPhone.trim().isEmpty()) {
                LOG.warning("  - SMS MFA is enabled but no phone number configured!");
                LOG.warning("  - User must add phone number after first login to enable SMS MFA");
                LOG.warning("  - Add 'cognitoInitialAdminPhone' to cdk.json (E.164 format, e.g., +12025551234)");
            } else {
                LOG.info("  - SMS MFA enabled with phone number: " + cognitoInitialAdminPhone);
            }
        }
    }

    /**
     * Configure native ALB Cognito authentication.
     * This uses ALB's built-in Cognito support which eliminates the need for Secrets Manager.
     * Only activates if authMode is "alb-oidc" (Cognito User Pool authentication).
     */
    private void configureAlbAuthentication() {
        // Only configure ALB authentication if authMode is "alb-oidc"
        if (!"alb-oidc".equals(authMode)) {
            LOG.info("ALB authentication not applicable for authMode: " + authMode);
            return;
        }

        LOG.info("Configuring native ALB Cognito authentication");

        // Wait for HTTPS listener to be available
        ctx.https.onSet(https -> {
            // Also need target group to forward to after authentication
            ctx.albTargetGroup.onSet(targetGroup -> {
                // Check if Cognito CDK objects are available for native ALB Cognito authentication
                if (ctx.cognitoUserPool.get().isPresent() &&
                    ctx.cognitoUserPoolClient.get().isPresent() &&
                    ctx.cognitoUserPoolDomain.get().isPresent()) {

                    LOG.info("Using native ALB Cognito authentication (no Secrets Manager required)");

                    var userPool = ctx.cognitoUserPool.get().orElseThrow();
                    var userPoolClient = ctx.cognitoUserPoolClient.get().orElseThrow();
                    var userPoolDomain = ctx.cognitoUserPoolDomain.get().orElseThrow();

                    // Create native Cognito authentication action
                    https.addAction("CognitoAuth", AddApplicationActionProps.builder()
                        .priority(1)  // High priority to catch all requests before default action
                        .conditions(List.of(
                            ListenerCondition.pathPatterns(List.of("/*"))  // Match all paths
                        ))
                        .action(AuthenticateCognitoAction.Builder.create()
                                .userPool(userPool)
                                .userPoolClient(userPoolClient)
                                .userPoolDomain(userPoolDomain)
                                .scope("openid email profile")
                                .onUnauthenticatedRequest(UnauthenticatedAction.AUTHENTICATE)
                                .next(ListenerAction.forward(List.of(targetGroup)))  // Forward to target group after authentication
                                .build()
                        )
                        .build());

                    LOG.info("✅ Native Cognito authentication configured successfully");
                    LOG.info("  User Pool ID: " + ctx.cognitoUserPoolId.get().orElse("N/A"));
                    LOG.info("  Target Group: " + targetGroup.getTargetGroupName());
                    LOG.info("  Priority: 1 (authenticate then forward to target group)");
                    LOG.info("  Authentication: All requests require Cognito User Pool authentication");
                    LOG.info("  Scopes: openid, email, profile");
                    LOG.info("  Benefits: No Secrets Manager required, simplified configuration");
                } else {
                    LOG.severe("❌ Cognito CDK objects not available in SystemContext");
                    LOG.severe("ALB authentication cannot be configured - check Cognito setup");
                }
            });
        });
    }

    /**
     * Store Cognito User Pool ARN in SSM Parameter Store (deployment-time).
     *
     * This helper method stores the User Pool ARN in SSM for compliance tracking.
     * The parameter persists after stack deletion for audit trail purposes.
     *
     * Only active in PRODUCTION mode.
     *
     * @param userPool The UserPool to track
     */
    private void storeUserPoolArnInSSM(UserPool userPool) {
        // Check if we have access to security profile (injected via annotation)
        if (securityProfileConfig == null || securityProfileConfig.getSecurityProfile() != SecurityProfile.PRODUCTION) {
            LOG.fine("Non-production mode: Skipping SSM tracking for User Pool");
            return;
        }

        if (region == null || region.isEmpty() || region.contains("$")) {
            LOG.warning("Region not available - cannot store User Pool ARN in SSM");
            return;
        }

        LOG.info("Storing User Pool ARN in SSM Parameter Store for compliance tracking");

        String ssmParameterName = "/cloudforge/shared/" + region + "/stack/" + this.stackName + "/cognito/user-pool-arn";

        AwsSdkCall putParameterCall = AwsSdkCall.builder()
                .service("SSM")
                .action("putParameter")
                .parameters(java.util.Map.of(
                        "Name", ssmParameterName,
                        "Value", userPool.getUserPoolArn(),
                        "Type", "String",
                        "Description", "CloudForge retained Cognito User Pool ARN for region " + region,
                        "Overwrite", true
                ))
                .physicalResourceId(PhysicalResourceId.of("UserPoolArn-SSMWriter"))
                .region(region)
                .build();

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, "UserPoolArnSSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        ssmWriter.getNode().addDependency(userPool);

        LOG.info("User Pool ARN will be tracked in SSM: " + ssmParameterName);
    }

    /**
     * Store Cognito User Pool Client Secret in AWS Secrets Manager.
     *
     * <p>This is required for application-level OIDC authentication where the application
     * needs to retrieve the client secret at runtime. For ALB-level OIDC, Cognito manages
     * the secret internally and this method is not called.</p>
     *
     * <p>The client secret is retrieved from the Cognito User Pool Client using a Custom Resource
     * and stored in Secrets Manager for the application container to access.</p>
     *
     * <p>IMPORTANT: We use putSecretValue for BOTH create and update operations.
     * This works because putSecretValue automatically creates the secret if it doesn't exist
     * (when using AWS SDK v3). This avoids the "ResourceExistsException" error entirely.</p>
     *
     * @param userPool The Cognito User Pool
     * @param appClient The Cognito User Pool Client
     * @return The Secrets Manager secret name
     */
    private String storeCognitoClientSecret(UserPool userPool, UserPoolClient appClient) {
        String secretName = stackName + "/" + "jenkins" + "/oidc/client-secret";

        LOG.info("Storing Cognito client secret in Secrets Manager: " + secretName);

        // Use AWS SDK Custom Resource to retrieve the client secret from Cognito
        // AND write it to Secrets Manager in a single Custom Resource
        // The client secret is only available via DescribeUserPoolClient API call
        AwsSdkCall getSecretCall = AwsSdkCall.builder()
                .service("CognitoIdentityServiceProvider")
                .action("describeUserPoolClient")
                .parameters(java.util.Map.of(
                        "UserPoolId", userPool.getUserPoolId(),
                        "ClientId", appClient.getUserPoolClientId()
                ))
                .outputPaths(List.of("UserPoolClient.ClientSecret"))
                .physicalResourceId(PhysicalResourceId.of("CognitoClientSecret-" + stackName))
                .region(region)
                .build();

        AwsCustomResource secretManager = AwsCustomResource.Builder.create(this, "CognitoClientSecretManager")
                .onCreate(getSecretCall)
                .onUpdate(getSecretCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of(
                                    userPool.getUserPoolArn(),
                                    "arn:aws:secretsmanager:" + region + ":" + Stack.of(this).getAccount() + ":secret:" + secretName + "*"
                                ))
                                .build()
                ))
                .build();

        secretManager.getNode().addDependency(appClient);

        // Get the client secret value from the Custom Resource
        String clientSecretValue = secretManager.getResponseField("UserPoolClient.ClientSecret");

        // Write secret using Custom Resource to handle "already exists" gracefully
        // CDK's Secret construct throws AlreadyExists error, but Custom Resource can ignore it
        AwsSdkCall putSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("putSecretValue")
                .parameters(java.util.Map.of(
                        "SecretId", secretName,
                        "SecretString", clientSecretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoClientSecretValue-" + stackName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceNotFoundException")  // Create if doesn't exist
                .build();

        // Create secret on first deployment - ignore if already exists
        AwsSdkCall createSecretFallback = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("createSecret")
                .parameters(java.util.Map.of(
                        "Name", secretName,
                        "Description", "Cognito User Pool Client Secret for application-level OIDC authentication",
                        "SecretString", clientSecretValue
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoClientSecretValue-" + stackName))
                .region(region)
                .ignoreErrorCodesMatching("ResourceExistsException")  // If exists, leave it alone
                .build();

        AwsCustomResource secretWriter = AwsCustomResource.Builder.create(this, "CognitoClientSecretWriter")
                .onCreate(createSecretFallback)
                .onUpdate(putSecretCall)  // Update existing secret value on stack updates
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        software.amazon.awscdk.customresources.SdkCallsPolicyOptions.builder()
                                .resources(List.of("*"))
                                .build()
                ))
                .build();

        secretWriter.getNode().addDependency(secretManager);

        LOG.info("Cognito client secret will be stored in Secrets Manager: " + secretName);

        // Store the Custom Resource in SystemContext for dependency tracking
        // This ensures ECS tasks don't start before the secret is created
        ctx.cognitoClientSecretResourceInternal.set(secretWriter);

        return secretName;
    }
}
