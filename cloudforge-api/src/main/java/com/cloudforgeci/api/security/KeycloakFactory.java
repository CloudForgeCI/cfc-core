package com.cloudforgeci.api.security;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.rds.IDatabaseInstance;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Keycloak SAML Bridge Factory.
 *
 * <p>Deploys Keycloak as a SAML Identity Provider bridge between AWS Cognito (OIDC)
 * and applications that require SAML authentication (e.g., Mattermost with group sync).</p>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * Mattermost ←SAML→ Keycloak ←OIDC→ Cognito User Pool
 *            (groups)        (federation)   (users)
 * </pre>
 *
 * <p><strong>When to Use:</strong></p>
 * <ul>
 *   <li>Application requires SAML (not OIDC)</li>
 *   <li>Need group synchronization / team management</li>
 *   <li>Want to use Cognito but app doesn't support OIDC directly</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong></p>
 * <pre>
 * {
 *   "oidcProvider": "cognito-saml",
 *   "authMode": "application-oidc",
 *   "cognitoAutoProvision": true
 * }
 * </pre>
 *
 * <p><strong>What Gets Created:</strong></p>
 * <ul>
 *   <li>Keycloak ECS Fargate service</li>
 *   <li>Keycloak schema in existing PostgreSQL database</li>
 *   <li>ALB target group for Keycloak (auth.{domain})</li>
 *   <li>Cognito OIDC federation in Keycloak</li>
 *   <li>SAML client configuration for Mattermost</li>
 * </ul>
 *
 * <p><strong>What Gets Reused:</strong></p>
 * <ul>
 *   <li>Existing Cognito User Pool (created by CognitoAuthenticationFactory)</li>
 *   <li>Existing RDS PostgreSQL database</li>
 *   <li>Existing VPC, subnets, security groups</li>
 *   <li>Existing ECS cluster</li>
 *   <li>Existing ALB</li>
 * </ul>
 *
 * <p><strong>Security:</strong></p>
 * <ul>
 *   <li>Follows security profile settings (DEV/STAGING/PRODUCTION)</li>
 *   <li>Database credentials in Secrets Manager</li>
 *   <li>Encrypted database connections</li>
 *   <li>HTTPS only</li>
 * </ul>
 */
public class KeycloakFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(KeycloakFactory.class.getName());

    @DeploymentContext("oidcProvider")
    private String oidcProvider;

    @DeploymentContext("authMode")
    private String authMode;

    @DeploymentContext("stackName")
    private String stackName;

    @DeploymentContext("domain")
    private String domain;

    @DeploymentContext("subdomain")
    private String subdomain;

    @DeploymentContext("region")
    private String region;

    // Reuse existing infrastructure
    @SystemContext("vpc")
    private IVpc vpc;

    @SystemContext("cluster")
    private ICluster cluster;

    @SystemContext("databaseInstance")
    private IDatabaseInstance database;

    @SystemContext("databaseCredentialsSecret")
    private ISecret databaseSecret;

    @SystemContext("applicationSecurityGroup")
    private SecurityGroup applicationSecurityGroup;

    // Cognito endpoints (set by CognitoAuthenticationFactory)
    @SystemContext("cognitoIssuer")
    private String cognitoIssuer;

    @SystemContext("cognitoClientId")
    private String cognitoClientId;

    @SystemContext("cognitoClientSecretName")
    private String cognitoClientSecretName;

    public KeycloakFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Only deploy Keycloak when oidcProvider is "cognito-saml"
        if (!"cognito-saml".equals(oidcProvider)) {
            LOG.info("Keycloak not needed - oidcProvider is '" + oidcProvider + "' (expected 'cognito-saml')");
            return;
        }

        // Only deploy for application-level authentication
        if (!"application-oidc".equals(authMode)) {
            LOG.info("Keycloak not needed - authMode is '" + authMode + "' (expected 'application-oidc')");
            return;
        }

        LOG.info("Deploying Keycloak SAML bridge for Cognito federation");
        LOG.info("  Provider: cognito-saml");
        LOG.info("  Auth Mode: application-oidc");
        LOG.info("  Stack: " + stackName);

        // Validate required infrastructure exists
        validateInfrastructure();

        // Deploy Keycloak
        deployKeycloak();
    }

    private void validateInfrastructure() {
        if (vpc == null) {
            throw new IllegalStateException("VPC not found - ensure NetworkFactory runs before KeycloakFactory");
        }

        if (cluster == null) {
            throw new IllegalStateException("ECS Cluster not found - ensure ComputeFactory runs before KeycloakFactory");
        }

        if (database == null) {
            throw new IllegalStateException("Database not found - ensure DatabaseFactory runs before KeycloakFactory");
        }

        if (cognitoIssuer == null) {
            throw new IllegalStateException("Cognito not configured - ensure CognitoAuthenticationFactory runs before KeycloakFactory");
        }

        LOG.info("Infrastructure validation passed:");
        LOG.info("  ✓ VPC: " + vpc.getVpcId());
        LOG.info("  ✓ ECS Cluster: " + cluster.getClusterName());
        LOG.info("  ✓ Database: " + database.getInstanceIdentifier());
        LOG.info("  ✓ Cognito Issuer: " + cognitoIssuer);
    }

    private void deployKeycloak() {
        LOG.info("Deploying Keycloak ECS service...");

        // TODO: Implementation in next step
        // 1. Create Keycloak database schema
        // 2. Create ECS task definition with Keycloak container
        // 3. Configure environment variables for:
        //    - Database connection (reuse existing RDS)
        //    - Cognito OIDC federation
        //    - SAML client for Mattermost
        // 4. Create Fargate service
        // 5. Create ALB target group for auth.{domain}
        // 6. Export SAML endpoints to SystemContext

        LOG.info("Keycloak deployment placeholder - implementation continues in next iteration");
    }
}
