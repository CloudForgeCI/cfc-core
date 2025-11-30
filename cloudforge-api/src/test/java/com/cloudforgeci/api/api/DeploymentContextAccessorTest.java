package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all DeploymentContext accessor methods.
 * Tests all 48 public accessor methods to ensure proper value retrieval.
 */
@DisplayName("DeploymentContext Accessor Tests")
class DeploymentContextAccessorTest {

    private DeploymentContext createContext(Map<String, Object> config) {
        App app = new App();
        app.getNode().setContext("cfc", config);
        Stack stack = new Stack(app, "TestStack");
        return DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("String Accessor Tests")
    class StringAccessorTests {

        @Test
        @DisplayName("tier() returns configured tier value")
        void tierAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "enterprise");
            DeploymentContext ctx = createContext(config);

            assertEquals("enterprise", ctx.tier());
        }

        @Test
        @DisplayName("tier() returns default 'public' when not configured")
        void tierAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("public", ctx.tier());
        }

        @Test
        @DisplayName("env() returns configured environment value")
        void envAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("env", "prod");
            DeploymentContext ctx = createContext(config);

            assertEquals("prod", ctx.env());
        }

        @Test
        @DisplayName("env() returns default 'dev' when not configured")
        void envAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("dev", ctx.env());
        }

        @Test
        @DisplayName("region() returns configured region value")
        void regionAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("region", "eu-west-1");
            DeploymentContext ctx = createContext(config);

            assertEquals("eu-west-1", ctx.region());
        }

        @Test
        @DisplayName("region() returns default 'us-east-1' when not configured")
        void regionAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("us-east-1", ctx.region());
        }

        @Test
        @DisplayName("domain() returns configured domain value")
        void domainAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("example.com", ctx.domain());
        }

        @Test
        @DisplayName("domain() returns null when not configured")
        void domainAccessorNull() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertNull(ctx.domain());
        }

        @Test
        @DisplayName("subdomain() returns configured subdomain value")
        void subdomainAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("subdomain", "jenkins");
            DeploymentContext ctx = createContext(config);

            assertEquals("jenkins", ctx.subdomain());
        }

        @Test
        @DisplayName("subdomain() returns null when not configured")
        void subdomainAccessorNull() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertNull(ctx.subdomain());
        }

        @Test
        @DisplayName("fqdn() returns configured fqdn value")
        void fqdnAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("fqdn", "jenkins.example.com");
            DeploymentContext ctx = createContext(config);

            assertEquals("jenkins.example.com", ctx.fqdn());
        }

        @Test
        @DisplayName("fqdn() returns composed value from domain and subdomain")
        void fqdnAccessorComposed() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("domain", "example.com");
            config.put("subdomain", "ci");
            DeploymentContext ctx = createContext(config);

            assertEquals("ci.example.com", ctx.fqdn());
        }

        @Test
        @DisplayName("networkMode() returns configured network mode value")
        void networkModeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("networkMode", "private-with-nat");
            DeploymentContext ctx = createContext(config);

            assertEquals("private-with-nat", ctx.networkMode());
        }

        @Test
        @DisplayName("networkMode() returns default 'public-no-nat' when not configured")
        void networkModeAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("public-no-nat", ctx.networkMode());
        }

        @Test
        @DisplayName("lbType() returns configured load balancer type")
        void lbTypeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("lbType", "nlb");
            DeploymentContext ctx = createContext(config);

            assertEquals("nlb", ctx.lbType());
        }

        @Test
        @DisplayName("lbType() returns default 'alb' when not configured")
        void lbTypeAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("alb", ctx.lbType());
        }

        @Test
        @DisplayName("authMode() returns configured auth mode")
        void authModeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("authMode", "jenkins-oidc");
            DeploymentContext ctx = createContext(config);

            assertEquals("jenkins-oidc", ctx.authMode());
        }

        @Test
        @DisplayName("authMode() returns default 'none' when not configured")
        void authModeAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals("none", ctx.authMode());
        }

        @Test
        @DisplayName("ssoInstanceArn() returns configured SSO instance ARN")
        void ssoInstanceArnAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("ssoInstanceArn", "arn:aws:sso:::instance/ssoins-1234567890abcdef");
            DeploymentContext ctx = createContext(config);

            assertEquals("arn:aws:sso:::instance/ssoins-1234567890abcdef", ctx.ssoInstanceArn());
        }

        @Test
        @DisplayName("ssoGroupId() returns configured SSO group ID")
        void ssoGroupIdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("ssoGroupId", "12345678-1234-1234-1234-123456789012");
            DeploymentContext ctx = createContext(config);

            assertEquals("12345678-1234-1234-1234-123456789012", ctx.ssoGroupId());
        }

        @Test
        @DisplayName("ssoTargetAccountId() returns configured SSO target account")
        void ssoTargetAccountIdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("ssoTargetAccountId", "123456789012");
            DeploymentContext ctx = createContext(config);

            assertEquals("123456789012", ctx.ssoTargetAccountId());
        }

        @Test
        @DisplayName("artifactsBucket() returns configured artifacts bucket")
        void artifactsBucketAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("artifactsBucket", "my-artifacts-bucket");
            DeploymentContext ctx = createContext(config);

            assertEquals("my-artifacts-bucket", ctx.artifactsBucket());
        }

        @Test
        @DisplayName("artifactsPrefix() returns configured artifacts prefix")
        void artifactsPrefixAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("artifactsPrefix", "custom/prefix/${JOB_NAME}");
            DeploymentContext ctx = createContext(config);

            assertEquals("custom/prefix/${JOB_NAME}", ctx.artifactsPrefix());
        }

        @Test
        @DisplayName("bastionCidr() returns configured bastion CIDR")
        void bastionCidrAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("bastionCidr", "10.0.0.0/24");
            DeploymentContext ctx = createContext(config);

            assertEquals("10.0.0.0/24", ctx.bastionCidr());
        }

        @Test
        @DisplayName("existingFileSystemId() returns configured file system ID")
        void existingFileSystemIdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("existingFileSystemId", "fs-1234567890abcdef");
            DeploymentContext ctx = createContext(config);

            assertEquals("fs-1234567890abcdef", ctx.existingFileSystemId());
        }

        @Test
        @DisplayName("instanceType() returns configured EC2 instance type")
        void instanceTypeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("instanceType", "t3.large");
            DeploymentContext ctx = createContext(config);

            assertEquals("t3.large", ctx.instanceType());
        }

        @Test
        @DisplayName("deploymentId() returns configured deployment ID")
        void deploymentIdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("deploymentId", "deploy-123456");
            DeploymentContext ctx = createContext(config);

            assertEquals("deploy-123456", ctx.deploymentId());
        }

        @Test
        @DisplayName("deploymentVersion() returns configured deployment version")
        void deploymentVersionAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("deploymentVersion", "v1.2.3");
            DeploymentContext ctx = createContext(config);

            assertEquals("v1.2.3", ctx.deploymentVersion());
        }

        @Test
        @DisplayName("tags() returns configured tags as JSON string")
        void tagsAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tags", "{\"env\":\"prod\",\"team\":\"platform\"}");
            DeploymentContext ctx = createContext(config);

            assertEquals("{\"env\":\"prod\",\"team\":\"platform\"}", ctx.tags());
        }

        @Test
        @DisplayName("stackName() returns configured stack name")
        void stackNameAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("stackName", "MyCustomStack");
            DeploymentContext ctx = createContext(config);

            assertEquals("MyCustomStack", ctx.stackName());
        }
    }

    @Nested
    @DisplayName("Integer Accessor Tests")
    class IntegerAccessorTests {

        @Test
        @DisplayName("cpu() returns configured CPU value")
        void cpuAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpu", 2048);
            DeploymentContext ctx = createContext(config);

            assertEquals(2048, ctx.cpu());
        }

        @Test
        @DisplayName("cpu() returns default 1024 when not configured")
        void cpuAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(1024, ctx.cpu());
        }

        @Test
        @DisplayName("memory() returns configured memory value")
        void memoryAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("memory", 4096);
            DeploymentContext ctx = createContext(config);

            assertEquals(4096, ctx.memory());
        }

        @Test
        @DisplayName("memory() returns default 2048 when not configured")
        void memoryAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(2048, ctx.memory());
        }

        @Test
        @DisplayName("minInstanceCapacity() returns configured min capacity")
        void minInstanceCapacityAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("minInstanceCapacity", 2);
            DeploymentContext ctx = createContext(config);

            assertEquals(2, ctx.minInstanceCapacity());
        }

        @Test
        @DisplayName("minInstanceCapacity() returns default 1 when not configured")
        void minInstanceCapacityAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(1, ctx.minInstanceCapacity());
        }

        @Test
        @DisplayName("maxInstanceCapacity() returns configured max capacity")
        void maxInstanceCapacityAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxInstanceCapacity", 10);
            DeploymentContext ctx = createContext(config);

            assertEquals(10, ctx.maxInstanceCapacity());
        }

        @Test
        @DisplayName("maxInstanceCapacity() returns default 1 when not configured")
        void maxInstanceCapacityAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(1, ctx.maxInstanceCapacity());
        }

        @Test
        @DisplayName("cpuTargetUtilization() returns configured CPU target")
        void cpuTargetUtilizationAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cpuTargetUtilization", 75);
            DeploymentContext ctx = createContext(config);

            assertEquals(75, ctx.cpuTargetUtilization());
        }

        @Test
        @DisplayName("cpuTargetUtilization() returns default 60 when not configured")
        void cpuTargetUtilizationAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(60, ctx.cpuTargetUtilization());
        }

        @Test
        @DisplayName("logRetentionDays() returns configured log retention")
        void logRetentionDaysAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("logRetentionDays", 30);
            DeploymentContext ctx = createContext(config);

            assertEquals(30, ctx.logRetentionDays());
        }

        @Test
        @DisplayName("logRetentionDays() returns default 7 when not configured")
        void logRetentionDaysAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(7, ctx.logRetentionDays());
        }

        @Test
        @DisplayName("healthCheckGracePeriod() returns configured grace period")
        void healthCheckGracePeriodAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("healthCheckGracePeriod", 600);
            DeploymentContext ctx = createContext(config);

            assertEquals(600, ctx.healthCheckGracePeriod());
        }

        @Test
        @DisplayName("healthCheckInterval() returns configured interval")
        void healthCheckIntervalAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("healthCheckInterval", 60);
            DeploymentContext ctx = createContext(config);

            assertEquals(60, ctx.healthCheckInterval());
        }

        @Test
        @DisplayName("healthCheckTimeout() returns configured timeout")
        void healthCheckTimeoutAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("healthCheckTimeout", 10);
            DeploymentContext ctx = createContext(config);

            assertEquals(10, ctx.healthCheckTimeout());
        }

        @Test
        @DisplayName("healthyThreshold() returns configured healthy threshold")
        void healthyThresholdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("healthyThreshold", 3);
            DeploymentContext ctx = createContext(config);

            assertEquals(3, ctx.healthyThreshold());
        }

        @Test
        @DisplayName("unhealthyThreshold() returns configured unhealthy threshold")
        void unhealthyThresholdAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("unhealthyThreshold", 5);
            DeploymentContext ctx = createContext(config);

            assertEquals(5, ctx.unhealthyThreshold());
        }
    }

    @Nested
    @DisplayName("Boolean Accessor Tests")
    class BooleanAccessorTests {

        @Test
        @DisplayName("wafEnabled() returns configured WAF setting")
        void wafEnabledAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("wafEnabled", true);
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.wafEnabled());
        }

        @Test
        @DisplayName("wafEnabled() returns default false when not configured")
        void wafEnabledAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.wafEnabled());
        }

        @Test
        @DisplayName("cloudfrontEnabled() returns configured CloudFront setting")
        void cloudfrontEnabledAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("cloudfront", true);
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.cloudfrontEnabled());
        }

        @Test
        @DisplayName("cloudfrontEnabled() returns default false when not configured")
        void cloudfrontEnabledAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.cloudfrontEnabled());
        }

        @Test
        @DisplayName("enableSsl() returns true when explicitly enabled")
        void enableSslAccessorEnabled() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableSsl", true);
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableSsl());
        }

        @Test
        @DisplayName("enableSsl() returns false by default")
        void enableSslAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.enableSsl());
        }

        @Test
        @DisplayName("createZone() returns true when explicitly enabled")
        void createZoneAccessorEnabled() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("createZone", true);
            config.put("domain", "example.com");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.createZone());
        }

        @Test
        @DisplayName("createZone() returns false by default")
        void createZoneAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.createZone());
        }

        @Test
        @DisplayName("enableFlowlogs() returns configured flow logs setting")
        void enableFlowlogsAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableFlowlogs", true);
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.enableFlowlogs());
        }

        @Test
        @DisplayName("enableFlowlogs() returns default false when not configured")
        void enableFlowlogsAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.enableFlowlogs());
        }

        @Test
        @DisplayName("retainStorage() returns configured storage retention setting")
        void retainStorageAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("retainStorage", true);
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.retainStorage());
        }

        @Test
        @DisplayName("retainStorage() returns default false when not configured")
        void retainStorageAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertFalse(ctx.retainStorage());
        }

        @Test
        @DisplayName("enableMonitoring() returns configured monitoring setting")
        void enableMonitoringAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableMonitoring", false);
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.enableMonitoring());
        }

        @Test
        @DisplayName("enableMonitoring() returns default true when not configured")
        void enableMonitoringAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertTrue(ctx.enableMonitoring());
        }

        @Test
        @DisplayName("enableEncryption() returns configured encryption setting")
        void enableEncryptionAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enableEncryption", false);
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.enableEncryption());
        }

        @Test
        @DisplayName("enableEncryption() returns default true when not configured")
        void enableEncryptionAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertTrue(ctx.enableEncryption());
        }

        @Test
        @DisplayName("isEnterprise() returns true for enterprise tier")
        void isEnterpriseAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "enterprise");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.isEnterprise());
        }

        @Test
        @DisplayName("isEnterprise() returns false for public tier")
        void isEnterpriseAccessorPublic() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("tier", "public");
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.isEnterprise());
        }

        @Test
        @DisplayName("isPrivateWithNat() returns true for private network mode")
        void isPrivateWithNatAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("networkMode", "private-with-nat");
            DeploymentContext ctx = createContext(config);

            assertTrue(ctx.isPrivateWithNat());
        }

        @Test
        @DisplayName("isPrivateWithNat() returns false for public network mode")
        void isPrivateWithNatAccessorPublic() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("networkMode", "public-no-nat");
            DeploymentContext ctx = createContext(config);

            assertFalse(ctx.isPrivateWithNat());
        }
    }

    @Nested
    @DisplayName("Enum Accessor Tests")
    class EnumAccessorTests {

        @Test
        @DisplayName("securityProfile() returns configured security profile")
        void securityProfileAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("securityProfile", "production");
            DeploymentContext ctx = createContext(config);

            assertEquals(SecurityProfile.PRODUCTION, ctx.securityProfile());
        }

        @Test
        @DisplayName("securityProfile() returns default DEV when not configured")
        void securityProfileAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(SecurityProfile.DEV, ctx.securityProfile());
        }

        @Test
        @DisplayName("runtime() returns configured runtime type")
        void runtimeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "ec2");
            DeploymentContext ctx = createContext(config);

            assertEquals(RuntimeType.EC2, ctx.runtime());
        }

        @Test
        @DisplayName("runtime() returns default FARGATE when not configured")
        void runtimeAccessorDefault() {
            DeploymentContext ctx = createContext(new LinkedHashMap<>());
            assertEquals(RuntimeType.FARGATE, ctx.runtime());
        }

        @Test
        @DisplayName("topology() returns JENKINS_SERVICE for fargate runtime")
        void topologyAccessorJenkinsService() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "fargate");
            DeploymentContext ctx = createContext(config);

            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology());
        }

        @Test
        @DisplayName("getRuntime() is same as runtime()")
        void getRuntimeAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "ec2");
            DeploymentContext ctx = createContext(config);

            assertEquals(ctx.runtime(), ctx.getRuntime());
            assertEquals(RuntimeType.EC2, ctx.getRuntime());
        }

        @Test
        @DisplayName("getTopology() is same as topology()")
        void getTopologyAccessor() {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("runtime", "fargate");
            DeploymentContext ctx = createContext(config);

            assertEquals(ctx.topology(), ctx.getTopology());
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.getTopology());
        }
    }
}
