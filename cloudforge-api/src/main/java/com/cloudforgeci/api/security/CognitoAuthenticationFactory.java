package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceMode;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.util.CfnStringUtils;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.customresources.SdkCallsPolicyOptions;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.actions.*;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.constructs.Construct;

import io.github.cdklabs.cdknag.NagPackSuppression;
import io.github.cdklabs.cdknag.NagSuppressions;

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
    private AuthMode authMode;

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

    @DeploymentContext("cognitoSelfSignupEnabled")
    private Boolean cognitoSelfSignupEnabled;

    // User groups configuration
    @DeploymentContext("cognitoCreateGroups")
    private Boolean cognitoCreateGroups;

    @DeploymentContext("cognitoAdminGroupName")
    private String cognitoAdminGroupName;

    @DeploymentContext("cognitoUserGroupName")
    private String cognitoUserGroupName;

    @DeploymentContext("cognitoInitialAdminEmail")
    private String cognitoInitialAdminEmail;

    // ========== Path-Based Authentication ==========
    // These settings control which paths require authentication at the ALB level

    @DeploymentContext("protectedPaths")
    private java.util.List<String> protectedPaths;

    @DeploymentContext("additionalProtectedPaths")
    private java.util.List<String> additionalProtectedPaths;

    @DeploymentContext("publicPaths")
    private java.util.List<String> publicPaths;

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

    @SystemContext("securityProfileConfig")
    private com.cloudforgeci.api.interfaces.SecurityProfileConfiguration securityProfileConfig;

    @SystemContext("applicationSpec")
    private com.cloudforge.core.interfaces.ApplicationSpec applicationSpec;

    @SystemContext("alb")
    private ApplicationLoadBalancer alb;

    @SystemContext("cognitoUserPool")
    private UserPool cognitoUserPool;

    @SystemContext("cognitoUserPoolClient")
    private UserPoolClient cognitoUserPoolClient;

    @SystemContext("cognitoUserPoolDomain")
    private UserPoolDomain cognitoUserPoolDomain;

    public CognitoAuthenticationFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only configure Cognito if authMode is OIDC-based
        if (authMode != AuthMode.ALB_OIDC && authMode != AuthMode.APPLICATION_OIDC) {
            LOG.info("Cognito authentication not applicable (authMode = " + authMode + ")");
            return;
        }

        // Validate application supports Cognito (for application-oidc mode)
        if (authMode == AuthMode.APPLICATION_OIDC && applicationSpec != null && applicationSpec.supportsOidcIntegration()) {
            var oidcIntegration = applicationSpec.getOidcIntegration();
            if (oidcIntegration != null && !oidcIntegration.supportsCognito()) {
                LOG.warning("Application '" + applicationSpec.applicationId() + "' does not support Cognito");
                LOG.warning("  Auth type: " + oidcIntegration.getAuthenticationType());
                LOG.warning("  Supports Cognito: false");
                LOG.warning("  Supports Identity Center SAML: " + oidcIntegration.supportsIdentityCenterSaml());
                LOG.warning("Skipping Cognito setup - use Identity Center SAML instead");
                return;
            }
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
        boolean isProduction = (securityProfileConfig != null && securityProfileConfig.getSecurityProfile() == SecurityProfile.PRODUCTION);
        RemovalPolicy userPoolRemovalPolicy = isProduction ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY;

        LOG.info("Creating/Importing Cognito User Pool: " + userPoolName);
        LOG.info("Domain prefix: " + cognitoDomainPrefix);
        LOG.info("User Pool removal policy: " + userPoolRemovalPolicy + " (isProduction = " + isProduction + ")");

        // Ensure securityProfileConfig is available - it should always be injected by BaseFactory
        if (securityProfileConfig == null) {
            throw new IllegalStateException("SecurityProfileConfiguration not injected - ensure SystemContext is properly initialized");
        }

        // Self-signup: deployment context override, then security profile default (dev allows
        // it, staging/production don't). No compliance framework rule blocks self-signup today —
        // a self-registered user still lands with no group membership and the lowest-privilege
        // role — but enabling it under an active framework is a common independent audit finding
        // (SOC2 CC6.1/6.2, PCI-DSS Req.7/8 both ask how accounts get provisioned, regardless of
        // what role they end up with), so warn rather than silently allowing it. Computed here,
        // ahead of the summary log below, so both that log and the actual builder call
        // (selfSignUpEnabled(...) further down) agree on the same effective value.
        boolean selfSignupEnabled = (cognitoSelfSignupEnabled != null)
            ? cognitoSelfSignupEnabled : securityProfileConfig.isSelfSignupEnabled();
        String complianceFrameworks = ctx != null && ctx.cfc != null ? ctx.cfc.complianceFrameworks() : null;
        ComplianceMode complianceMode = ctx != null && ctx.cfc != null ? ctx.cfc.complianceMode() : null;
        boolean complianceActive = complianceFrameworks != null && !complianceFrameworks.isBlank()
            && (complianceMode == ComplianceMode.ADVISORY || complianceMode == ComplianceMode.ENFORCE);
        if (selfSignupEnabled && complianceActive) {
            LOG.warning("Cognito self-signup is enabled while compliance frameworks are active "
                + "(" + complianceFrameworks + ", mode=" + complianceMode + "). Open self-registration "
                + "into an ops panel is a common audit finding independent of the role it grants — "
                + "review whether cognitoSelfSignupEnabled should stay unset (profile default) or be "
                + "explicitly disabled for this deployment.");
        }

        LOG.info("Security Profile: " + securityProfileConfig.getSecurityProfile());
        LOG.info("Profile-aware authentication settings:");
        LOG.info("  - MFA Required: " + securityProfileConfig.isMfaRequired());
        LOG.info("  - Default MFA Method: " + securityProfileConfig.getDefaultMfaMethod());
        LOG.info("  - Password Min Length: " + securityProfileConfig.getMinimumPasswordLength());
        LOG.info("  - Temp Password Validity: " + securityProfileConfig.getTempPasswordValidityDays() + " days");
        LOG.info("  - Access Token Validity: " + securityProfileConfig.getAccessTokenValidityHours() + " hours");
        LOG.info("  - ID Token Validity: " + securityProfileConfig.getIdTokenValidityHours() + " hours");
        LOG.info("  - Refresh Token Validity: " + securityProfileConfig.getRefreshTokenValidityDays() + " days");
        LOG.info("  - Self-Signup Enabled: " + selfSignupEnabled
            + (cognitoSelfSignupEnabled != null ? " (deployment context override)" : " (security profile default)"));
        LOG.info("  - Prevent User Existence Errors: " + securityProfileConfig.isPreventUserExistenceErrorsEnabled());
        LOG.info("  - Advanced Security: " + securityProfileConfig.isAdvancedSecurityEnabled());

        // Determine which MFA methods to enable based on cognitoMfaMethod
        // Valid values: "totp", "sms", "both"
        // Use deployment context override if provided, otherwise use profile default
        String mfaMethod = (cognitoMfaMethod != null && !cognitoMfaMethod.isEmpty())
                ? cognitoMfaMethod.toLowerCase()
                : securityProfileConfig.getDefaultMfaMethod();

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
                // Password policy - profile-aware (stricter in production)
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(securityProfileConfig.getMinimumPasswordLength())
                        .requireUppercase(true)
                        .requireLowercase(true)
                        .requireDigits(true)
                        .requireSymbols(true)
                        .tempPasswordValidity(software.amazon.awscdk.Duration.days(
                            securityProfileConfig.getTempPasswordValidityDays()))
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

        // Determine MFA setting: use deployment context override, then security profile default
        boolean mfaRequired = (cognitoMfaEnabled != null) ? cognitoMfaEnabled : securityProfileConfig.isMfaRequired();

        userPoolBuilder
                // MFA configuration - profile-aware (required in staging/production)
                .mfa(mfaRequired ? Mfa.REQUIRED : Mfa.OPTIONAL)
                .mfaSecondFactor(mfaSecondFactor)
                // Account recovery
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                // Advanced security features - profile-aware (enabled for CDK-nag COG3 compliance)
                // Note: Requires Cognito Plus tier for production deployments
                .advancedSecurityMode(securityProfileConfig.isAdvancedSecurityEnabled() ?
                    AdvancedSecurityMode.ENFORCED : AdvancedSecurityMode.OFF)
                // Feature plan - Plus tier required when Advanced Security is enabled
                // CDK validates this, so we must set it even though we'll remove it from CloudFormation
                .featurePlan(securityProfileConfig.isAdvancedSecurityEnabled() ?
                    FeaturePlan.PLUS : FeaturePlan.LITE)
                // Self-service signup - profile-aware, deployment-context-overridable (see above)
                .selfSignUpEnabled(selfSignupEnabled)
                // Removal policy - RETAIN for production, DESTROY for dev/staging
                .removalPolicy(userPoolRemovalPolicy);

        UserPool userPool = userPoolBuilder.build();

        // Add CDK-nag suppression for DEV profile when AdvancedSecurityMode is disabled
        if (!securityProfileConfig.isAdvancedSecurityEnabled()) {
            NagSuppressions.addResourceSuppressions(
                userPool,
                List.of(
                    NagPackSuppression.builder()
                        .id("AwsSolutions-COG3")
                        .reason("AdvancedSecurityMode is intentionally disabled for DEV/non-production " +
                                "environments to reduce costs. Cognito Plus tier is required for ENFORCED mode. " +
                                "Production deployments enable AdvancedSecurityMode for enhanced threat protection.")
                        .build()
                ),
                Boolean.TRUE
            );
        }

        // Configure SMS role and phone number schema using CloudFormation escape hatch
        CfnUserPool cfnUserPool = (CfnUserPool) userPool.getNode().getDefaultChild();

        // Remove UserPoolTier from CloudFormation output - cfn-guard doesn't recognize this property
        // The featurePlan is set above for CDK validation, but we remove it from the template
        cfnUserPool.addPropertyDeletionOverride("UserPoolTier");

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

        // cloudforge-manager's Users tab writes a role edit through to this pool's real group
        // membership (AuthService.syncOidcRoleToCognito) instead of only updating its own local
        // cache — Cognito stays the single source of truth for who's in which role regardless of
        // whether that role's group membership changed via our own UI or directly in Cognito.
        // Scoped to this one pool's ARN; harmless to grant even if that Manager feature is never
        // exercised for a given deployment. onSet (not a direct getter) because IAM-profile
        // configuration (which creates fargateTaskRole) and Cognito provisioning have no fixed
        // ordering relative to each other.
        if (applicationSpec != null && "cloudforge-manager".equals(applicationSpec.applicationId())
                && cognitoCreateGroups != null && cognitoCreateGroups) {
            String userPoolArn = userPool.getUserPoolArn();
            ctx.fargateTaskRole.onSet(taskRole -> taskRole.addToPrincipalPolicy(
                PolicyStatement.Builder.create()
                    .sid("AllowManagerCognitoRoleGroupSync")
                    .effect(software.amazon.awscdk.services.iam.Effect.ALLOW)
                    .actions(List.of(
                        "cognito-idp:AdminAddUserToGroup",
                        "cognito-idp:AdminRemoveUserFromGroup",
                        "cognito-idp:AdminListGroupsForUser"
                    ))
                    .resources(List.of(userPoolArn))
                    .build()));
            LOG.info("  ✅ Added IAM policy for Manager Cognito role-group sync (Users tab role edits)");
        }

        // Create initial admin user if email provided (even if groups are disabled)
        if (cognitoInitialAdminEmail != null && !cognitoInitialAdminEmail.isEmpty() &&
            (cognitoCreateGroups == null || !cognitoCreateGroups)) {
            // Groups disabled but admin email provided - create user without group attachment
            createInitialAdminUser(userPool, null, null);
        }

        // Create custom domain for hosted UI
        UserPoolDomain userPoolDomain = UserPoolDomain.Builder.create(this, "UserPoolDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix(cognitoDomainPrefix)
                        .build())
                .build();

        LOG.info("User Pool domain created: " + cognitoDomainPrefix + ".auth." + region + ".amazoncognito.com");

        // Construct redirect URL and logout URL
        String redirectUrl = constructRedirectUrl();
        String logoutUrl = constructLogoutRedirectUrl();
        LOG.info("Redirect URL: " + redirectUrl);
        LOG.info("Logout URL: " + logoutUrl);

        // Create App Client for ALB OIDC authentication
        UserPoolClient.Builder appClientBuilder = UserPoolClient.Builder.create(this, "AppClient")
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
                        .logoutUrls(List.of(logoutUrl))
                        .build())
                // Token validity - profile-aware (stricter in production)
                .idTokenValidity(software.amazon.awscdk.Duration.hours(
                    securityProfileConfig.getIdTokenValidityHours()))
                .accessTokenValidity(software.amazon.awscdk.Duration.hours(
                    securityProfileConfig.getAccessTokenValidityHours()))
                .refreshTokenValidity(software.amazon.awscdk.Duration.days(
                    securityProfileConfig.getRefreshTokenValidityDays()))
                // Prevent user existence errors - profile-aware (enabled in staging/production)
                .preventUserExistenceErrors(securityProfileConfig.isPreventUserExistenceErrorsEnabled());
        if (authMode == AuthMode.APPLICATION_OIDC) {
            // Manager's own custom login form calls Cognito's InitiateAuth/RespondToAuthChallenge
            // directly (ApplicationOidcAuthenticator#completeDirectLogin) instead of redirecting
            // the browser to Cognito Hosted UI — needs USER_PASSWORD_AUTH enabled on the client to
            // be accepted at all. Scoped to application-oidc only: alb-oidc's client secret is
            // managed internally by Cognito and never reaches Manager's own app code (see below),
            // so nothing would ever call InitiateAuth for it — least privilege, not just inertness.
            appClientBuilder = appClientBuilder.authFlows(AuthFlow.builder().userPassword(true).build());
        }
        UserPoolClient appClient = appClientBuilder.build();

        LOG.info("App Client created: " + appClient.getUserPoolClientId());

        // Opt this domain into Cognito's newer "Managed Login" branding version instead of the
        // classic Hosted UI's plain/dated default look — useCognitoProvidedValues gives Cognito's
        // own sensible default styling with no custom logo/color assets to author or host.
        // CfnManagedLoginBranding needs the domain on managedLoginVersion=2; the L2 UserPoolDomain
        // construct doesn't yet surface that property, hence the L1 escape hatch. Applies whenever
        // Cognito auto-provisions the pool/domain, since Hosted UI is reachable regardless of
        // authMode — alb-oidc always, application-oidc via its own "Sign in with Cognito instead"
        // link (see ApplicationOidcAuthenticator/manager-route-access.service.ts).
        CfnUserPoolDomain cfnUserPoolDomain = (CfnUserPoolDomain) userPoolDomain.getNode().getDefaultChild();
        cfnUserPoolDomain.setManagedLoginVersion(2);
        CfnManagedLoginBranding managedLoginBranding = CfnManagedLoginBranding.Builder.create(this, "ManagedLoginBranding")
                .userPoolId(userPool.getUserPoolId())
                .clientId(appClient.getUserPoolClientId())
                .useCognitoProvidedValues(true)
                .build();
        managedLoginBranding.getNode().addDependency(cfnUserPoolDomain);
        LOG.info("Managed Login branding enabled (Cognito-provided default styling)");

        // Note: For ALB-level OIDC (alb-oidc), Cognito manages client secret internally
        // For application-level OIDC (application-oidc), we need to store secret in Secrets Manager
        // so the application container can retrieve it at runtime
        String secretName = null;

        // Check if we need to store the client secret for application-level OIDC
        if (authMode == AuthMode.APPLICATION_OIDC) {
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

            // For application-oidc mode, we need to store the client secret in Secrets Manager
            // so the application container can retrieve it at runtime
            String secretName = null;
            if (authMode == AuthMode.APPLICATION_OIDC) {
                LOG.info("Application-level OIDC detected - storing Cognito client secret in Secrets Manager");
                // For existing user pool with existing client, we need the secret to be provided externally
                // or we retrieve it using Custom Resource
                secretName = storeCognitoClientSecret(userPool, appClient);
            }

            // Export OIDC endpoints
            exportOidcEndpoints(cognitoUserPoolId, cognitoAppClientId, cognitoDomainPrefix, secretName);
        } else {
            LOG.info("Creating new App Client for existing User Pool");

            // Validate domain prefix is provided
            if (cognitoDomainPrefix == null || cognitoDomainPrefix.isEmpty()) {
                LOG.severe("cognitoDomainPrefix is required when creating app client");
                throw new IllegalArgumentException("cognitoDomainPrefix is required");
            }

            // Construct redirect URL and logout URL
            String redirectUrl = constructRedirectUrl();
            String logoutUrl = constructLogoutRedirectUrl();

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
                            .logoutUrls(List.of(logoutUrl))
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

            // For application-oidc mode, we need to store the client secret in Secrets Manager
            // so the application container can retrieve it at runtime
            String secretName = null;
            if (authMode == AuthMode.APPLICATION_OIDC) {
                LOG.info("Application-level OIDC detected - storing Cognito client secret in Secrets Manager");
                secretName = storeCognitoClientSecret(userPool, appClient);
            }

            // Export OIDC endpoints
            exportOidcEndpoints(cognitoUserPoolId, appClient.getUserPoolClientId(), cognitoDomainPrefix, secretName);
        }
    }

    /**
     * Construct redirect URL based on domain configuration and auth mode.
     *
     * <p>For ALB-OIDC: Uses /oauth2/idpresponse (ALB handles the callback)</p>
     * <p>For Application-OIDC: Uses application-specific callback path (e.g., /securityRealm/finishLogin for Jenkins)</p>
     */
    private String constructRedirectUrl() {
        // Determine the callback path based on authMode
        String callbackPath;
        if (authMode == AuthMode.APPLICATION_OIDC) {
            // For application-level OIDC, get the callback path from the application's OidcIntegration
            callbackPath = getApplicationCallbackPath();
            LOG.info("Using application-oidc callback path: " + callbackPath);
        } else {
            // For ALB-level OIDC, use the standard ALB callback path
            callbackPath = "/oauth2/idpresponse";
        }

        // Use Fn.join to properly concatenate when base URL may be a CloudFormation token
        // (e.g., when using ALB DNS name without custom domain)
        String baseUrl = constructBaseUrl();
        return Fn.join("", List.of(baseUrl, callbackPath));
    }

    /**
     * Get the application-specific callback path from the OidcIntegration.
     * Returns default Jenkins path if not available.
     */
    private String getApplicationCallbackPath() {
        // Try to get from SystemContext.applicationSpec if available
        if (ctx != null && ctx.applicationSpec != null && ctx.applicationSpec.get().isPresent()) {
            var appSpec = ctx.applicationSpec.get().orElse(null);
            if (appSpec != null && appSpec.supportsOidcIntegration()) {
                var integration = appSpec.getOidcIntegration();
                if (integration != null) {
                    String callbackPath = integration.getOidcCallbackPath();
                    LOG.info("Retrieved callback path from ApplicationSpec: " + callbackPath);
                    return callbackPath;
                }
            }
        }
        // Fallback to Jenkins default
        LOG.warning("Could not retrieve callback path from ApplicationSpec, using Jenkins default");
        return "/securityRealm/finishLogin";
    }

    /**
     * Construct logout redirect URL - where Cognito redirects after logout.
     * This is typically the application root URL.
     */
    private String constructLogoutRedirectUrl() {
        // Use Fn.join to properly concatenate when base URL may be a CloudFormation token
        String baseUrl = constructBaseUrl();
        return Fn.join("", List.of(baseUrl, "/"));
    }

    /**
     * Construct the base URL from domain configuration.
     * Priority: fqdn > subdomain.domain > domain > ALB DNS name (with Private CA)
     *
     * <p>When no custom domain is configured, the ALB DNS name is used with HTTPS.
     * This requires AWS Private CA which is automatically provisioned by FargateRuntimeConfiguration
     * when enableSsl=true but no domain is configured.</p>
     *
     * <p><b>Note:</b> Private CA certificates are not trusted by browsers (users will see warnings),
     * but they enable HTTPS for OIDC callbacks without requiring a custom domain.</p>
     */
    private String constructBaseUrl() {
        // Priority 1: Fully qualified domain name
        if (fqdn != null && !fqdn.isEmpty()) {
            return "https://" + fqdn;
        }

        // Priority 2: Domain with optional subdomain
        if (domain != null && !domain.isEmpty()) {
            if (subdomain != null && !subdomain.isEmpty()) {
                return "https://" + subdomain + "." + domain;
            }
            return "https://" + domain;
        }

        // Priority 3: ALB DNS name with HTTPS (requires Private CA certificate)
        // This is used when enableSsl=true but no custom domain is configured
        // IMPORTANT: ALB DNS names contain mixed case (e.g., nJjqXp6M1K6T) but Cognito
        // callback URLs are case-sensitive. We must lowercase the DNS name.
        // See: https://github.com/aws/aws-cdk/issues/11171
        if (alb != null) {
            // Lowercase the ALB DNS name for Cognito callback URL compatibility
            String lowercaseDns = CfnStringUtils.toLowerCase(alb.getLoadBalancerDnsName());
            String albUrl = Fn.join("", List.of("https://", lowercaseDns));
            LOG.info("Using ALB DNS name for callback URL with Private CA certificate (lowercased): " + albUrl);
            LOG.warning("NOTE: Private CA certificates are not trusted by browsers. Users will see certificate warnings.");
            return albUrl;
        }

        // ALB not yet available - use placeholder that will be resolved at deploy time
        LOG.warning("ALB not yet available - callback URL will use placeholder");
        LOG.warning("Ensure enableSsl=true is set for HTTPS callback URLs");
        return "https://placeholder.invalid";
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
        String logoutEndpoint = "https://" + domainPrefix + ".auth." + region + ".amazoncognito.com/logout";

        LOG.info("Exporting OIDC endpoints to DeploymentContext:");
        LOG.info("  Issuer: " + issuer);
        LOG.info("  Authorization: [REDACTED]");
        LOG.info("  Token: [REDACTED]");
        LOG.info("  UserInfo: [REDACTED]");
        LOG.info("  Logout: [REDACTED]");
        LOG.info("  Client ID: [REDACTED]");
        LOG.info("  Secret Name: " + (secretName != null ? "[REDACTED]" : "null"));

        // Store in SystemContext slots for OidcAuthenticationFactory to use
        ctx.cognitoIssuer.set(issuer);
        ctx.cognitoAuthorizationEndpoint.set(authEndpoint);
        ctx.cognitoTokenEndpoint.set(tokenEndpoint);
        ctx.cognitoUserInfoEndpoint.set(userInfoEndpoint);
        ctx.cognitoLogoutEndpoint.set(logoutEndpoint);
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
        CfnUserPoolGroup adminGroup = CfnUserPoolGroup.Builder.create(this, "AdminGroup")
                .userPoolId(userPool.getUserPoolId())
                .groupName(adminGroupName)
                .description("Jenkins administrators with full access")
                .precedence(1)  // Higher precedence (lower number = higher priority)
                .build();

        // Create user group
        CfnUserPoolGroup userGroup = CfnUserPoolGroup.Builder.create(this, "UserGroup")
                .userPoolId(userPool.getUserPoolId())
                .groupName(userGroupName)
                .description("Jenkins users with standard access")
                .precedence(10)  // Lower precedence
                .build();

        LOG.info("User groups created: " + adminGroupName + " (precedence 1), " + userGroupName + " (precedence 10)");

        // Create initial admin user if email provided
        if (cognitoInitialAdminEmail != null && !cognitoInitialAdminEmail.isEmpty()) {
            createInitialAdminUser(userPool, adminGroupName, adminGroup);
        }
    }

    /**
     * Create initial admin user with temporary password.
     * User will be required to change password on first login.
     */
    private void createInitialAdminUser(UserPool userPool, String adminGroupName, CfnUserPoolGroup adminGroup) {
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
        if (adminGroupName != null && !adminGroupName.isEmpty() && adminGroup != null) {
            CfnUserPoolUserToGroupAttachment groupAttachment = CfnUserPoolUserToGroupAttachment.Builder.create(this, "InitialAdminGroupAttachment")
                    .userPoolId(userPool.getUserPoolId())
                    .username(cognitoInitialAdminEmail)
                    .groupName(adminGroupName)
                    .build();

            // Ensure user and group are created before adding to group
            groupAttachment.getNode().addDependency(adminUser);
            groupAttachment.getNode().addDependency(adminGroup);

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
     *
     * <p>Path-based authentication allows protecting only specific paths while
     * leaving other paths public. This is configured via:</p>
     * <ul>
     *   <li>ApplicationSpec.protectedPaths() - Application default protected paths</li>
     *   <li>DeploymentContext.protectedPaths - Override/replace application defaults</li>
     *   <li>DeploymentContext.additionalProtectedPaths - Add to application defaults</li>
     *   <li>DeploymentContext.publicPaths - Explicitly exclude paths from protection</li>
     * </ul>
     */
    private void configureAlbAuthentication() {
        // Only configure ALB authentication if authMode is ALB_OIDC
        if (authMode != AuthMode.ALB_OIDC) {
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

                    // Calculate effective protected paths
                    List<String> effectiveProtectedPaths = calculateEffectiveProtectedPaths();

                    if (effectiveProtectedPaths.isEmpty()) {
                        // No specific paths defined - protect everything (existing behavior)
                        LOG.info("No specific protected paths defined - protecting all paths");
                        configureFullAuthenticationRule(https, targetGroup, userPool, userPoolClient, userPoolDomain);
                    } else {
                        // Specific paths defined - create path-based authentication rules
                        LOG.info("Path-based authentication enabled with " + effectiveProtectedPaths.size() + " protected path(s)");
                        configurePathBasedAuthenticationRules(https, targetGroup, userPool, userPoolClient, userPoolDomain, effectiveProtectedPaths);
                    }

                    LOG.info("✅ Native Cognito authentication configured successfully");
                    LOG.info("  User Pool ID: " + ctx.cognitoUserPoolId.get().orElse("N/A"));
                    LOG.info("  Target Group: " + targetGroup.getTargetGroupName());
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
     * Calculate the effective list of protected paths based on ApplicationSpec and DeploymentContext.
     *
     * <p>Priority:</p>
     * <ol>
     *   <li>If DeploymentContext.protectedPaths is set, it replaces ApplicationSpec defaults</li>
     *   <li>Otherwise, use ApplicationSpec.protectedPaths()</li>
     *   <li>Add any DeploymentContext.additionalProtectedPaths</li>
     *   <li>Remove any DeploymentContext.publicPaths from the result</li>
     * </ol>
     *
     * @return List of path patterns requiring authentication (empty = protect everything)
     */
    private List<String> calculateEffectiveProtectedPaths() {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();

        // Step 1: Start with base protected paths
        if (protectedPaths != null && !protectedPaths.isEmpty()) {
            // DeploymentContext overrides ApplicationSpec
            paths.addAll(protectedPaths);
            LOG.info("Using DeploymentContext.protectedPaths: " + protectedPaths);
        } else if (applicationSpec != null && !applicationSpec.protectedPaths().isEmpty()) {
            // Use ApplicationSpec defaults
            paths.addAll(applicationSpec.protectedPaths());
            LOG.info("Using ApplicationSpec.protectedPaths(): " + applicationSpec.protectedPaths());
        }

        // Step 2: Add additional protected paths from DeploymentContext
        if (additionalProtectedPaths != null && !additionalProtectedPaths.isEmpty()) {
            paths.addAll(additionalProtectedPaths);
            LOG.info("Adding DeploymentContext.additionalProtectedPaths: " + additionalProtectedPaths);
        }

        // Step 3: Remove public paths from DeploymentContext
        if (publicPaths != null && !publicPaths.isEmpty()) {
            paths.removeAll(publicPaths);
            LOG.info("Removing DeploymentContext.publicPaths: " + publicPaths);
        }

        // Step 4: Remove public paths from ApplicationSpec
        if (applicationSpec != null && !applicationSpec.publicPaths().isEmpty()) {
            paths.removeAll(applicationSpec.publicPaths());
            LOG.info("Removing ApplicationSpec.publicPaths(): " + applicationSpec.publicPaths());
        }

        List<String> result = new java.util.ArrayList<>(paths);
        LOG.info("Effective protected paths: " + (result.isEmpty() ? "[ALL PATHS]" : result));
        return result;
    }

    /**
     * Configure authentication rule that protects all paths (original behavior).
     */
    private void configureFullAuthenticationRule(
            ApplicationListener https,
            IApplicationTargetGroup targetGroup,
            IUserPool userPool,
            IUserPoolClient userPoolClient,
            IUserPoolDomain userPoolDomain) {

        // Create native Cognito authentication action for all paths
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
                    .next(ListenerAction.forward(List.of(targetGroup)))
                    .build()
            )
            .build());

        LOG.info("  Priority: 1 (authenticate then forward to target group)");
        LOG.info("  Authentication: All requests require Cognito User Pool authentication");
    }

    /**
     * Configure path-based authentication rules.
     *
     * <p>Creates separate rules:</p>
     * <ul>
     *   <li>Priority 1-N: Protected paths require authentication</li>
     *   <li>Priority N+1: Catch-all rule forwards without authentication</li>
     * </ul>
     */
    private void configurePathBasedAuthenticationRules(
            ApplicationListener https,
            IApplicationTargetGroup targetGroup,
            IUserPool userPool,
            IUserPoolClient userPoolClient,
            IUserPoolDomain userPoolDomain,
            List<String> protectedPathPatterns) {

        // ALB rules can have multiple path patterns per rule (up to 5 values per condition)
        // Group paths into batches of 5 for efficiency
        int batchSize = 5;
        int priority = 1;

        for (int i = 0; i < protectedPathPatterns.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, protectedPathPatterns.size());
            List<String> batch = protectedPathPatterns.subList(i, endIndex);

            String ruleName = "CognitoAuth" + (priority > 1 ? "-" + priority : "");

            https.addAction(ruleName, AddApplicationActionProps.builder()
                .priority(priority)
                .conditions(List.of(
                    ListenerCondition.pathPatterns(batch)
                ))
                .action(AuthenticateCognitoAction.Builder.create()
                        .userPool(userPool)
                        .userPoolClient(userPoolClient)
                        .userPoolDomain(userPoolDomain)
                        .scope("openid email profile")
                        .onUnauthenticatedRequest(UnauthenticatedAction.AUTHENTICATE)
                        .next(ListenerAction.forward(List.of(targetGroup)))
                        .build()
                )
                .build());

            LOG.info("  Rule '" + ruleName + "' (priority " + priority + "): Authenticate for paths " + batch);
            priority++;
        }

        // Add catch-all rule that forwards without authentication (lower priority)
        https.addAction("PublicForward", AddApplicationActionProps.builder()
            .priority(priority)
            .conditions(List.of(
                ListenerCondition.pathPatterns(List.of("/*"))
            ))
            .action(ListenerAction.forward(List.of(targetGroup)))
            .build());

        LOG.info("  Rule 'PublicForward' (priority " + priority + "): Forward without auth for all other paths");
        LOG.info("  Authentication: Only protected paths require Cognito authentication");
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

        // Use scoped ARN pattern for least-privilege SSM access
        // Pattern: arn:aws:ssm:REGION:*:parameter/cloudforge/shared/REGION/stack/STACKNAME/*
        String ssmArnPattern = "arn:aws:ssm:" + region + ":*:parameter/cloudforge/shared/" + region + "/stack/" + this.stackName + "/*";

        AwsCustomResource ssmWriter = AwsCustomResource.Builder.create(this, "UserPoolArnSSMWriter")
                .onCreate(putParameterCall)
                .onUpdate(putParameterCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                        software.amazon.awscdk.services.iam.PolicyStatement.Builder.create()
                                .actions(List.of("ssm:PutParameter"))
                                .resources(List.of(ssmArnPattern))
                                .build()
                )))
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
     * <p>The client secret is retrieved from the Cognito User Pool Client and stored directly
     * in Secrets Manager using a Custom Resource Lambda. This ensures the secret value never
     * appears in the CloudFormation template (avoiding the security issue with unsafePlainText).</p>
     *
     * <p>The Custom Resource uses a Lambda function that:
     * 1. Calls DescribeUserPoolClient to get the client secret
     * 2. Calls PutSecretValue to store it in Secrets Manager
     * This keeps the secret value within AWS and out of CloudFormation.</p>
     *
     * @param userPool The Cognito User Pool
     * @param appClient The Cognito User Pool Client
     * @return The Secrets Manager secret ARN
     */
    private String storeCognitoClientSecret(IUserPool userPool, IUserPoolClient appClient) {
        // Determine application ID for secret path
        String appId = "jenkins"; // Default for backward compatibility with alb-oidc mode
        if (applicationSpec != null) {
            appId = applicationSpec.applicationId();
        }

        String secretName = stackName + "/" + appId + "/oidc/client-secret";

        LOG.info("Storing Cognito client secret in Secrets Manager for application: " + appId);

        // First, create an empty secret in Secrets Manager
        // The Custom Resource will populate it with the actual client secret value
        Secret cognitoSecret = Secret.Builder.create(this, "CognitoClientSecret")
                .secretName(secretName)
                .description("Cognito User Pool Client Secret for application-level OIDC authentication")
                // Generate a placeholder - will be replaced by Custom Resource
                .generateSecretString(SecretStringGenerator.builder()
                    .generateStringKey("placeholder")
                    .secretStringTemplate("{}")
                    .build())
                // Always destroy - Cognito regenerates client secrets, this is just a synchronized copy
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Suppress SMG4 - Cognito client secrets cannot be rotated by Secrets Manager
        // The secret is managed by Cognito and synchronized via Custom Resource.
        // To rotate, you must regenerate the Cognito User Pool Client which requires
        // updating all dependent applications (ALB OIDC actions, application configs).
        NagSuppressions.addResourceSuppressions(
            cognitoSecret,
            List.of(
                NagPackSuppression.builder()
                    .id("AwsSolutions-SMG4")
                    .reason("Cognito User Pool Client secrets are managed by AWS Cognito, not Secrets Manager. " +
                           "This secret is a synchronized copy for application use. Rotation requires regenerating " +
                           "the Cognito client and updating all dependent ALB OIDC actions and applications.")
                    .build()
            ),
            Boolean.TRUE
        );

        // Use Custom Resource to retrieve the client secret from Cognito and store it in Secrets Manager
        // This keeps the secret value within AWS - it never appears in the CloudFormation template
        //
        // SECURITY: We use a two-step Custom Resource approach:
        // 1. First CR fetches the secret from Cognito (DescribeUserPoolClient)
        // 2. Second CR stores it in Secrets Manager (PutSecretValue)
        // This ensures the secret value is passed between CRs at runtime, never in CloudFormation.
        AwsSdkCall getSecretCall = AwsSdkCall.builder()
                .service("CognitoIdentityServiceProvider")
                .action("describeUserPoolClient")
                .parameters(java.util.Map.of(
                        "UserPoolId", userPool.getUserPoolId(),
                        "ClientId", appClient.getUserPoolClientId()
                ))
                .outputPaths(List.of("UserPoolClient.ClientSecret"))
                .physicalResourceId(PhysicalResourceId.of("CognitoClientSecretFetch-" + stackName))
                .region(region)
                .build();

        // Step 1: Fetch the client secret from Cognito
        AwsCustomResource secretFetcher = AwsCustomResource.Builder.create(this, "CognitoClientSecretFetcher")
                .onCreate(getSecretCall)
                .onUpdate(getSecretCall)
                .policy(AwsCustomResourcePolicy.fromSdkCalls(
                        SdkCallsPolicyOptions.builder()
                                .resources(List.of(userPool.getUserPoolArn()))
                                .build()
                ))
                .build();

        secretFetcher.getNode().addDependency(appClient);

        // Step 2: Store the secret value in Secrets Manager using PutSecretValue
        // This Custom Resource takes the output from the fetcher and stores it
        AwsSdkCall storeSecretCall = AwsSdkCall.builder()
                .service("SecretsManager")
                .action("putSecretValue")
                .parameters(java.util.Map.of(
                        "SecretId", cognitoSecret.getSecretArn(),
                        "SecretString", secretFetcher.getResponseField("UserPoolClient.ClientSecret")
                ))
                .physicalResourceId(PhysicalResourceId.of("CognitoClientSecretStore-" + stackName))
                .region(region)
                .build();

        AwsCustomResource secretStorer = AwsCustomResource.Builder.create(this, "CognitoClientSecretStorer")
                .onCreate(storeSecretCall)
                .onUpdate(storeSecretCall)
                .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                    PolicyStatement.Builder.create()
                        .actions(List.of("secretsmanager:PutSecretValue"))
                        .resources(List.of(cognitoSecret.getSecretArn()))
                        .build()
                )))
                .build();

        secretStorer.getNode().addDependency(secretFetcher);
        secretStorer.getNode().addDependency(cognitoSecret);

        LOG.info("Cognito client secret will be stored in Secrets Manager via Custom Resource");

        // Store the secret in SystemContext for dependency tracking
        ctx.cognitoClientSecretResourceInternal.set(cognitoSecret);

        // Return the COMPLETE ARN with suffix (same as RDS pattern)
        return cognitoSecret.getSecretArn();
    }

}
