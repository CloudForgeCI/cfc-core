package com.cloudforge.core.config;

import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import com.cloudforge.core.local.DeploymentTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentConfigTest {

    private DeploymentConfig config;

    @BeforeEach
    void setUp() {
        config = new DeploymentConfig();
    }

    // ========== Default Values Tests ==========

    @Test
    void testDefaultMinInstanceCapacity() {
        assertEquals(1, config.minInstanceCapacity);
    }

    @Test
    void testDefaultMaxInstanceCapacity() {
        assertEquals(1, config.maxInstanceCapacity);
    }

    @Test
    void testDefaultCpuTargetUtilization() {
        assertEquals(60, config.cpuTargetUtilization);
    }

    @Test
    void testDefaultCpu() {
        assertEquals(1024, config.cpu);
    }

    @Test
    void testDefaultMemory() {
        assertEquals(2048, config.memory);
    }

    @Test
    void testDefaultInstanceType() {
        assertEquals("t3.micro", config.instanceType);
    }

    @Test
    void testDefaultOidcProvider() {
        assertEquals("none", config.oidcProvider);
    }

    @Test
    void testDefaultAuthMode() {
        assertEquals(AuthMode.NONE, config.authMode);
    }

    @Test
    void testDefaultDatabaseEngine() {
        // Deliberately null, not a static "postgres" default — DeploymentContextPreparer only
        // resolves a field's defaultFrom (here, ApplicationSpec.databaseRequirement().engine())
        // when the field is currently null/blank. A non-null static default here previously
        // blocked that resolution for every app, silently forcing engine=postgres regardless of
        // what the deployed application actually required (broke MySQL-only apps like WordPress
        // with an invalid "mysql15" RDS parameter group family). See the field's javadoc.
        assertNull(config.databaseEngine);
    }

    @Test
    void testDefaultDatabaseVersion() {
        assertNull(config.databaseVersion);
    }

    @Test
    void testDefaultDatabaseInstanceClass() {
        assertNull(config.databaseInstanceClass);
    }

    @Test
    void testDefaultRegion() {
        assertEquals("us-east-1", config.region);
    }

    @Test
    void testDefaultHealthCheckGracePeriod() {
        assertEquals(300, config.healthCheckGracePeriod);
    }

    @Test
    void testDefaultHealthCheckInterval() {
        assertEquals(30, config.healthCheckInterval);
    }

    @Test
    void testDefaultHealthCheckTimeout() {
        assertEquals(5, config.healthCheckTimeout);
    }

    @Test
    void testDefaultHealthyThreshold() {
        assertEquals(2, config.healthyThreshold);
    }

    @Test
    void testDefaultUnhealthyThreshold() {
        assertEquals(3, config.unhealthyThreshold);
    }

    @Test
    void deserializesDeploymentContextFields() throws Exception {
        DeploymentConfig loaded = DeploymentConfig.fromJson("""
            {
              "stackName": "jtest",
              "applicationId": "jenkins",
              "domain": "cloudforge.localhost",
              "subdomain": "jenkins",
              "runtime": "FARGATE"
            }
            """);

        assertEquals("jtest", loaded.stackName);
        assertEquals("jenkins", loaded.applicationId);
        assertEquals("cloudforge.localhost", loaded.domain);
        assertEquals("jenkins", loaded.subdomain);
        assertEquals(RuntimeType.FARGATE, loaded.runtime);
    }

    /** managerTarget round-trips through its own DeploymentTargetConverter (lower-case wire
     *  format, not the enum's own upper-case name()) -- real deploy submissions carry this
     *  field, so a broken converter here is a broken deploy, not just a style nit. */
    @Test
    void deserializesAndSerializesManagerTargetThroughItsConverter() throws Exception {
        DeploymentConfig loaded = DeploymentConfig.fromJson("""
            {
              "stackName": "jtest",
              "applicationId": "jenkins",
              "runtime": "FARGATE",
              "managerTarget": "localstack"
            }
            """);

        assertEquals(DeploymentTarget.LOCALSTACK, loaded.managerTarget);
        assertTrue(loaded.toJson().contains("\"managerTarget\" : \"localstack\""));
    }

    @Test
    void deserializesSavedDeploymentContextFile(@TempDir Path tempDir) throws Exception {
        // Mirrors cfc-testing/deployment-context.json's shape — that file is gitignored (a local
        // interactive-deployer artifact), so this writes its own fixture rather than depending on
        // a path outside the module that won't exist on a clean checkout.
        Path fixture = tempDir.resolve("deployment-context.json");
        Files.writeString(fixture, """
            {
              "stackName": "jtest",
              "applicationId": "jenkins",
              "applicationName": "Jenkins",
              "environment": "prod",
              "runtime": "FARGATE",
              "authMode": "application-oidc",
              "domain": "cloudforge.localhost",
              "subdomain": "jenkins",
              "fqdn": "jenkins.cloudforge.localhost",
              "enableSsl": false,
              "complianceFrameworks": ["soc2"]
            }
            """);

        DeploymentConfig loaded = DeploymentConfig.fromFile(fixture);

        assertEquals("jtest", loaded.stackName);
        assertEquals("cloudforge.localhost", loaded.domain);
        assertEquals("jenkins", loaded.subdomain);
        assertEquals("jenkins", loaded.applicationId);
        assertEquals(List.of(ComplianceFrameworkType.SOC2), loaded.complianceFrameworks);
    }

    // ========== Boolean Default Tests ==========

    @Test
    void testDefaultCognitoAutoProvision() {
        assertFalse(config.cognitoAutoProvision);
    }

    @Test
    void testDefaultCognitoMfaEnabled() {
        assertFalse(config.cognitoMfaEnabled);
    }

    @Test
    void testDefaultCognitoCreateGroups() {
        assertTrue(config.cognitoCreateGroups);
    }

    @Test
    void testDefaultProvisionDatabase() {
        assertFalse(config.provisionDatabase);
    }

    @Test
    void testDefaultEnableAutoScaling() {
        assertFalse(config.enableAutoScaling);
    }

    @Test
    void testDefaultEnableAgents() {
        assertFalse(config.enableAgents);
    }

    @Test
    void testDefaultEnableSsh() {
        assertFalse(config.enableSsh);
    }

    @Test
    void testDefaultEnableSmtp() {
        assertFalse(config.enableSmtp);
    }

    @Test
    void testDefaultWafEnabled() {
        assertNull(config.wafEnabled);
    }

    @Test
    void testDefaultCloudfrontEnabled() {
        assertNull(config.cloudfrontEnabled);
    }

    @Test
    void testDefaultRestrictSecurityGroupEgress() {
        assertFalse(config.restrictSecurityGroupEgress);
    }

    @Test
    void testDefaultCloudWatchLogsKmsEncryptionEnabled() {
        assertFalse(config.cloudWatchLogsKmsEncryptionEnabled);
    }

    // ========== Field Assignment Tests ==========

    @Test
    void testSetBasicFields() {
        config.stackName = "test-stack";
        config.environment = "production";
        config.applicationId = "jenkins";
        config.applicationName = "Jenkins CI";

        assertEquals("test-stack", config.stackName);
        assertEquals("production", config.environment);
        assertEquals("jenkins", config.applicationId);
        assertEquals("Jenkins CI", config.applicationName);
    }

    @Test
    void testSetDomainFields() {
        config.domain = "example.com";
        config.subdomain = "ci";
        config.enableSsl = true;

        assertEquals("example.com", config.domain);
        assertEquals("ci", config.subdomain);
        assertTrue(config.enableSsl);
    }

    @Test
    void testSetRuntimeFields() {
        config.runtime = RuntimeType.FARGATE;
        config.topology = TopologyType.APPLICATION_SERVICE;
        config.securityProfile = SecurityProfile.PRODUCTION;

        assertEquals(RuntimeType.FARGATE, config.runtime);
        assertEquals(TopologyType.APPLICATION_SERVICE, config.topology);
        assertEquals(SecurityProfile.PRODUCTION, config.securityProfile);
    }

    @Test
    void testSetDatabaseFields() {
        config.provisionDatabase = true;
        config.databaseEngine = "aurora-postgresql";
        config.databaseVersion = "15.4";
        config.databaseInstanceClass = "db.r5.large";
        config.databaseAllocatedStorageGB = 200;
        config.databaseMultiAz = true;
        config.databaseBackupRetentionDays = 90;

        assertTrue(config.provisionDatabase);
        assertEquals("aurora-postgresql", config.databaseEngine);
        assertEquals("15.4", config.databaseVersion);
        assertEquals("db.r5.large", config.databaseInstanceClass);
        assertEquals(200, config.databaseAllocatedStorageGB);
        assertTrue(config.databaseMultiAz);
        assertEquals(90, config.databaseBackupRetentionDays);
    }

    @Test
    void testSetCognitoFields() {
        config.cognitoAutoProvision = true;
        config.cognitoUserPoolName = "my-pool";
        config.cognitoDomainPrefix = "my-app";
        config.cognitoMfaEnabled = true;
        config.cognitoCreateGroups = true;
        config.cognitoAdminGroupName = "Admins";
        config.cognitoUserGroupName = "Users";

        assertTrue(config.cognitoAutoProvision);
        assertEquals("my-pool", config.cognitoUserPoolName);
        assertEquals("my-app", config.cognitoDomainPrefix);
        assertTrue(config.cognitoMfaEnabled);
        assertTrue(config.cognitoCreateGroups);
        assertEquals("Admins", config.cognitoAdminGroupName);
        assertEquals("Users", config.cognitoUserGroupName);
    }

    @Test
    void testSetResourceFields() {
        config.minInstanceCapacity = 2;
        config.maxInstanceCapacity = 10;
        config.cpuTargetUtilization = 70;
        config.cpu = 2048;
        config.memory = 4096;
        config.instanceType = "t3.large";

        assertEquals(2, config.minInstanceCapacity);
        assertEquals(10, config.maxInstanceCapacity);
        assertEquals(70, config.cpuTargetUtilization);
        assertEquals(2048, config.cpu);
        assertEquals(4096, config.memory);
        assertEquals("t3.large", config.instanceType);
    }

    @Test
    void testSetOptionalPorts() {
        config.enableAgents = true;
        config.enableSsh = true;
        config.enableSmtp = true;
        config.enableSmtps = true;
        config.enableClustering = true;
        config.enableDockerRegistry = true;
        config.enableMetrics = true;
        config.enableNotary = true;
        config.enableTrivy = true;
        config.enableSentinel = true;
        config.enableCluster = true;

        assertTrue(config.enableAgents);
        assertTrue(config.enableSsh);
        assertTrue(config.enableSmtp);
        assertTrue(config.enableSmtps);
        assertTrue(config.enableClustering);
        assertTrue(config.enableDockerRegistry);
        assertTrue(config.enableMetrics);
        assertTrue(config.enableNotary);
        assertTrue(config.enableTrivy);
        assertTrue(config.enableSentinel);
        assertTrue(config.enableCluster);
    }

    @Test
    void testSetOidcFields() {
        config.oidcIssuer = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxx";
        config.oidcAuthorizationEndpoint = "https://auth.example.com/oauth2/authorize";
        config.oidcTokenEndpoint = "https://auth.example.com/oauth2/token";
        config.oidcUserInfoEndpoint = "https://auth.example.com/oauth2/userInfo";
        config.oidcClientId = "client123";
        config.oidcClientSecretName = "oidc/client-secret";

        assertEquals("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxx", config.oidcIssuer);
        assertEquals("https://auth.example.com/oauth2/authorize", config.oidcAuthorizationEndpoint);
        assertEquals("https://auth.example.com/oauth2/token", config.oidcTokenEndpoint);
        assertEquals("https://auth.example.com/oauth2/userInfo", config.oidcUserInfoEndpoint);
        assertEquals("client123", config.oidcClientId);
        assertEquals("oidc/client-secret", config.oidcClientSecretName);
    }

    @Test
    void testSetIdentityCenterFields() {
        config.autoProvisionIdentityCenter = true;
        config.ssoInstanceArn = "arn:aws:sso:::instance/ssoins-xxxxx";
        config.identityCenterGroupName = "DeveloperGroup";

        assertTrue(config.autoProvisionIdentityCenter);
        assertEquals("arn:aws:sso:::instance/ssoins-xxxxx", config.ssoInstanceArn);
        assertEquals("DeveloperGroup", config.identityCenterGroupName);
    }

    // ========== Null Value Tests ==========

    @Test
    void testNullableFieldsAreNull() {
        assertNull(config.stackName);
        assertNull(config.environment);
        assertNull(config.applicationId);
        assertNull(config.applicationName);
        assertNull(config.domain);
        assertNull(config.subdomain);
        assertNull(config.cognitoUserPoolName);
        assertNull(config.cognitoDomainPrefix);
        assertNull(config.oidcIssuer);
    }

    @Test
    void testAvailabilityZonesNullByDefault() {
        assertNull(config.availabilityZones);
    }

    @Test
    void testSetAvailabilityZones() {
        config.availabilityZones = new String[]{"us-east-1a", "us-east-1b"};
        assertEquals(2, config.availabilityZones.length);
        assertEquals("us-east-1a", config.availabilityZones[0]);
        assertEquals("us-east-1b", config.availabilityZones[1]);
    }

    @Test
    void testApplicationSpecNullByDefault() {
        assertNull(config.applicationSpec);
    }

    // ========== Runtime Type Tests ==========

    @Test
    void testSetRuntimeTypeFargate() {
        config.runtime = RuntimeType.FARGATE;
        assertEquals(RuntimeType.FARGATE, config.runtime);
    }

    @Test
    void testSetRuntimeTypeEc2() {
        config.runtime = RuntimeType.EC2;
        assertEquals(RuntimeType.EC2, config.runtime);
    }

    // ========== Security Profile Tests ==========

    @Test
    void testSetSecurityProfileDev() {
        config.securityProfile = SecurityProfile.DEV;
        assertEquals(SecurityProfile.DEV, config.securityProfile);
    }

    @Test
    void testSetSecurityProfileStaging() {
        config.securityProfile = SecurityProfile.STAGING;
        assertEquals(SecurityProfile.STAGING, config.securityProfile);
    }

    @Test
    void testSetSecurityProfileProduction() {
        config.securityProfile = SecurityProfile.PRODUCTION;
        assertEquals(SecurityProfile.PRODUCTION, config.securityProfile);
    }

    // ========== Complete Configuration Test ==========

    @Test
    void testCompleteProductionConfiguration() {
        // Set up a complete production-ready configuration
        config.stackName = "myapp-production";
        config.environment = "production";
        config.applicationId = "jenkins";
        config.applicationName = "Jenkins CI/CD";

        config.runtime = RuntimeType.EC2;
        config.topology = TopologyType.APPLICATION_SERVICE;
        config.securityProfile = SecurityProfile.PRODUCTION;

        config.domain = "ci.example.com";
        config.subdomain = "jenkins";
        config.enableSsl = true;

        config.networkMode = NetworkMode.PRIVATE_WITH_NAT;
        config.wafEnabled = true;

        config.minInstanceCapacity = 2;
        config.maxInstanceCapacity = 10;
        config.enableAutoScaling = true;
        config.cpuTargetUtilization = 70;
        config.instanceType = "t3.large";

        config.authMode = AuthMode.APPLICATION_OIDC;
        config.cognitoAutoProvision = true;
        config.cognitoDomainPrefix = "jenkins-prod";
        config.cognitoMfaEnabled = true;

        config.region = "us-west-2";

        // Security hardening flags
        config.restrictSecurityGroupEgress = true;
        config.cloudWatchLogsKmsEncryptionEnabled = true;

        // Verify all values are set correctly
        assertEquals("myapp-production", config.stackName);
        assertEquals(RuntimeType.EC2, config.runtime);
        assertEquals(SecurityProfile.PRODUCTION, config.securityProfile);
        assertTrue(config.enableSsl);
        assertTrue(config.wafEnabled);
        assertEquals(2, config.minInstanceCapacity);
        assertEquals(10, config.maxInstanceCapacity);
        assertTrue(config.enableAutoScaling);
        assertEquals(AuthMode.APPLICATION_OIDC, config.authMode);
        assertTrue(config.cognitoAutoProvision);
        assertTrue(config.cognitoMfaEnabled);
        assertEquals("us-west-2", config.region);
        assertTrue(config.restrictSecurityGroupEgress);
        assertTrue(config.cloudWatchLogsKmsEncryptionEnabled);
    }

    @Test
    void testCompleteFargateConfiguration() {
        config.stackName = "fargate-app";
        config.environment = "staging";
        config.applicationId = "grafana";

        config.runtime = RuntimeType.FARGATE;
        config.securityProfile = SecurityProfile.STAGING;

        config.cpu = 2048;
        config.memory = 4096;
        config.minInstanceCapacity = 1;
        config.maxInstanceCapacity = 4;

        config.healthCheckGracePeriod = 120;
        config.healthCheckInterval = 15;
        config.healthCheckTimeout = 10;

        assertEquals("fargate-app", config.stackName);
        assertEquals(RuntimeType.FARGATE, config.runtime);
        assertEquals(2048, config.cpu);
        assertEquals(4096, config.memory);
        assertEquals(120, config.healthCheckGracePeriod);
    }

    // ========== Security Hardening Tests ==========

    @Test
    void testSetRestrictSecurityGroupEgress() {
        config.restrictSecurityGroupEgress = true;
        assertTrue(config.restrictSecurityGroupEgress);
    }

    @Test
    void testSetCloudWatchLogsKmsEncryptionEnabled() {
        config.cloudWatchLogsKmsEncryptionEnabled = true;
        assertTrue(config.cloudWatchLogsKmsEncryptionEnabled);
    }

    @Test
    void testSecurityHardeningConfiguration() {
        // Configure a security-hardened production deployment
        config.securityProfile = SecurityProfile.PRODUCTION;
        config.networkMode = NetworkMode.PRIVATE_WITH_NAT;
        config.restrictSecurityGroupEgress = true;
        config.cloudWatchLogsKmsEncryptionEnabled = true;
        config.wafEnabled = true;

        assertEquals(SecurityProfile.PRODUCTION, config.securityProfile);
        assertEquals(NetworkMode.PRIVATE_WITH_NAT, config.networkMode);
        assertTrue(config.restrictSecurityGroupEgress);
        assertTrue(config.cloudWatchLogsKmsEncryptionEnabled);
        assertTrue(config.wafEnabled);
    }

    @Test
    void testCompleteDatabaseConfiguration() {
        config.provisionDatabase = true;
        config.databaseEngine = "aurora-postgresql";
        config.databaseVersion = "15.4";
        config.databaseInstanceClass = "db.r5.xlarge";
        config.databaseAllocatedStorageGB = 500;
        config.databaseMultiAz = true;
        config.databaseBackupRetentionDays = 35;

        assertTrue(config.provisionDatabase);
        assertEquals("aurora-postgresql", config.databaseEngine);
        assertEquals("15.4", config.databaseVersion);
        assertEquals("db.r5.xlarge", config.databaseInstanceClass);
        assertEquals(500, config.databaseAllocatedStorageGB);
        assertTrue(config.databaseMultiAz);
        assertEquals(35, config.databaseBackupRetentionDays);
    }

    @Test
    void toHistoryContextMapIncludesNonSensitiveFieldsAndRedactsSensitive() {
        config.stackName = "CloudForgeManager-Dev";
        config.applicationId = "cloudforge-manager";
        config.environment = "development";
        config.region = "us-east-1";
        config.oidcClientSecretName = "my/oidc/secret";
        config.managerHistoryToken = "super-secret-token";
        config.managerUrl = "http://127.0.0.1:1958";

        var history = config.toHistoryContextMap();

        assertEquals("CloudForgeManager-Dev", history.get("stackName"));
        assertEquals("cloudforge-manager", history.get("applicationId"));
        assertEquals("development", history.get("env"));
        assertEquals("us-east-1", history.get("region"));
        assertEquals("http://127.0.0.1:1958", history.get("managerUrl"));
        assertFalse(history.containsKey("oidcClientSecretName"));
        assertFalse(history.containsKey("managerHistoryToken"));
        assertFalse(history.containsKey("environment"));
    }
}
