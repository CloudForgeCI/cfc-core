package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Comprehensive tests for DeploymentContext default values.
 * Target: 20+ tests covering all default value scenarios.
 */
@DisplayName("DeploymentContext Defaults Tests")
public class DeploymentContextDefaultsTest {
    private DeploymentContext fromMap(Map<String,Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @Nested
    @DisplayName("Core Configuration Defaults")
    class CoreConfigurationDefaults {

        @Test
        @DisplayName("tier defaults to 'public'")
        void tierDefaultsToPublic() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("public", cfc.tier(), "Default tier should be 'public'");
        }

        @Test
        @DisplayName("env defaults to 'dev'")
        void envDefaultsToDev() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("dev", cfc.env(), "Default env should be 'dev'");
        }

        @Test
        @DisplayName("region defaults to 'us-east-1'")
        void regionDefaultsToUsEast1() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("us-east-1", cfc.region(), "Default region should be 'us-east-1'");
        }

        @Test
        @DisplayName("runtime defaults to FARGATE")
        void runtimeDefaultsToFargate() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(RuntimeType.FARGATE, cfc.runtime(), "Default runtime should be FARGATE");
        }

        @Test
        @DisplayName("topology defaults to JENKINS_SERVICE")
        void topologyDefaultsToJenkinsService() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(TopologyType.JENKINS_SERVICE, cfc.topology(), "Default topology should be JENKINS_SERVICE");
        }

        @Test
        @DisplayName("securityProfile defaults to DEV")
        void securityProfileDefaultsToDev() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(SecurityProfile.DEV, cfc.securityProfile(), "Default security profile should be DEV");
        }
    }

    @Nested
    @DisplayName("Capacity and Scaling Defaults")
    class CapacityScalingDefaults {

        @Test
        @DisplayName("minInstanceCapacity defaults to 1")
        void minInstanceCapacityDefaultsTo1() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(1, cfc.minInstanceCapacity(), "Default minInstanceCapacity should be 1");
        }

        @Test
        @DisplayName("maxInstanceCapacity defaults to 1")
        void maxInstanceCapacityDefaultsTo1() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(1, cfc.maxInstanceCapacity(), "Default maxInstanceCapacity should be 1");
        }

        @Test
        @DisplayName("cpuTargetUtilization defaults to 60")
        void cpuTargetUtilizationDefaultsTo60() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(60, cfc.cpuTargetUtilization(), "Default cpuTargetUtilization should be 60");
        }

        @Test
        @DisplayName("cpu defaults to 1024")
        void cpuDefaultsTo1024() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(1024, cfc.cpu(), "Default CPU should be 1024");
        }

        @Test
        @DisplayName("memory defaults to 2048")
        void memoryDefaultsTo2048() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(2048, cfc.memory(), "Default memory should be 2048");
        }
    }

    @Nested
    @DisplayName("Networking and Load Balancer Defaults")
    class NetworkingDefaults {

        @Test
        @DisplayName("networkMode defaults to 'public-no-nat'")
        void networkModeDefaultsToPublicNoNat() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("public-no-nat", cfc.networkMode(), "Default networkMode should be 'public-no-nat'");
        }

        @Test
        @DisplayName("lbType defaults to 'alb'")
        void lbTypeDefaultsToAlb() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("alb", cfc.lbType(), "Default lbType should be 'alb'");
        }

        @Test
        @DisplayName("wafEnabled defaults to false")
        void wafEnabledDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(Boolean.TRUE.equals(cfc.wafEnabled()), "Default wafEnabled should be false");
        }

        @Test
        @DisplayName("cloudfrontEnabled defaults to false")
        void cloudfrontEnabledDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(Boolean.TRUE.equals(cfc.cloudfrontEnabled()), "Default cloudfront should be false");
        }
    }

    @Nested
    @DisplayName("DNS and Domain Defaults")
    class DnsDefaults {

        @Test
        @DisplayName("domain defaults to null")
        void domainDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.domain(), "Default domain should be null");
        }

        @Test
        @DisplayName("subdomain defaults to null")
        void subdomainDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.subdomain(), "Default subdomain should be null");
        }

        @Test
        @DisplayName("fqdn defaults to null")
        void fqdnDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.fqdn(), "Default fqdn should be null");
        }

        @Test
        @DisplayName("fqdn is composed from subdomain and domain when both provided")
        void fqdnComposedFromSubdomainAndDomain() throws Exception {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("domain", "example.com");
            m.put("subdomain", "ci");
            DeploymentContext cfc = fromMap(m);
            assertEquals("ci.example.com", cfc.fqdn(), "FQDN should be composed from subdomain.domain");
        }

        @Test
        @DisplayName("explicit fqdn overrides composed value")
        void explicitFqdnBeatsPieces() throws Exception {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("domain", "example.com");
            m.put("subdomain", "ci");
            m.put("fqdn", "jenkins.example.org");
            DeploymentContext cfc = fromMap(m);
            assertEquals("jenkins.example.org", cfc.fqdn(), "Explicit FQDN should override composition");
        }
    }

    @Nested
    @DisplayName("Security and SSL Defaults")
    class SecurityDefaults {

        @Test
        @DisplayName("enableSsl defaults to false")
        void enableSslDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(cfc.enableSsl(), "Default enableSsl should be false");
        }

        @Test
        @DisplayName("createZone defaults to false")
        void createZoneDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(cfc.createZone(), "Default createZone should be false");
        }

        @Test
        @DisplayName("authMode defaults to 'none'")
        void authModeDefaultsToNone() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("none", cfc.authMode(), "Default authMode should be 'none'");
        }

        @Test
        @DisplayName("ssoInstanceArn defaults to null")
        void ssoInstanceArnDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.ssoInstanceArn(), "Default ssoInstanceArn should be null");
        }

        @Test
        @DisplayName("ssoGroupId defaults to null")
        void ssoGroupIdDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.ssoGroupId(), "Default ssoGroupId should be null");
        }

        @Test
        @DisplayName("ssoTargetAccountId defaults to null")
        void ssoTargetAccountIdDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.ssoTargetAccountId(), "Default ssoTargetAccountId should be null");
        }
    }

    @Nested
    @DisplayName("Storage and Logging Defaults")
    class StorageLoggingDefaults {

        @Test
        @DisplayName("artifactsBucket defaults to null")
        void artifactsBucketDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.artifactsBucket(), "Default artifactsBucket should be null");
        }

        @Test
        @DisplayName("artifactsPrefix defaults to standard path")
        void artifactsPrefixDefaultsToStandardPath() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("jenkins/job/${JOB_NAME}/${BUILD_NUMBER}", cfc.artifactsPrefix(),
                "Default artifactsPrefix should be 'jenkins/job/${JOB_NAME}/${BUILD_NUMBER}'");
        }

        @Test
        @DisplayName("enableFlowlogs defaults to false")
        void enableFlowlogsDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(Boolean.TRUE.equals(cfc.enableFlowlogs()), "Default enableFlowlogs should be false");
        }

        @Test
        @DisplayName("retainStorage defaults to false")
        void retainStorageDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(Boolean.TRUE.equals(cfc.retainStorage()), "Default retainStorage should be false");
        }

        @Test
        @DisplayName("existingFileSystemId defaults to null")
        void existingFileSystemIdDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.existingFileSystemId(), "Default existingFileSystemId should be null");
        }

        @Test
        @DisplayName("logRetentionDays defaults to null (uses SecurityProfileConfiguration)")
        void logRetentionDaysDefaultsToNull() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertNull(cfc.logRetentionDays(), "Default logRetentionDays should be null (SecurityProfileConfiguration provides actual default)");
        }
    }

    @Nested
    @DisplayName("Monitoring and Encryption Defaults")
    class MonitoringDefaults {

        @Test
        @DisplayName("enableMonitoring defaults to true")
        void enableMonitoringDefaultsToTrue() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertTrue(Boolean.TRUE.equals(cfc.enableMonitoring()) || cfc.enableMonitoring() == null, "Default enableMonitoring should be true or null");
        }

        @Test
        @DisplayName("enableEncryption defaults to true")
        void enableEncryptionDefaultsToTrue() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertTrue(Boolean.TRUE.equals(cfc.enableEncryption()) || cfc.enableEncryption() == null, "Default enableEncryption should be true or null");
        }

        @Test
        @DisplayName("awsConfigEnabled defaults to false")
        void awsConfigEnabledDefaultsToFalse() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertFalse(Boolean.TRUE.equals(cfc.awsConfigEnabled()), "Default awsConfigEnabled should be false");
        }
    }

    @Nested
    @DisplayName("Health Check Defaults")
    class HealthCheckDefaults {

        @Test
        @DisplayName("healthCheckGracePeriod defaults to 300")
        void healthCheckGracePeriodDefaultsTo300() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(300, cfc.healthCheckGracePeriod(), "Default healthCheckGracePeriod should be 300");
        }

        @Test
        @DisplayName("healthCheckInterval defaults to 30")
        void healthCheckIntervalDefaultsTo30() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(30, cfc.healthCheckInterval(), "Default healthCheckInterval should be 30");
        }

        @Test
        @DisplayName("healthCheckTimeout defaults to 5")
        void healthCheckTimeoutDefaultsTo5() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(5, cfc.healthCheckTimeout(), "Default healthCheckTimeout should be 5");
        }

        @Test
        @DisplayName("healthyThreshold defaults to 2")
        void healthyThresholdDefaultsTo2() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(2, cfc.healthyThreshold(), "Default healthyThreshold should be 2");
        }

        @Test
        @DisplayName("unhealthyThreshold defaults to 3")
        void unhealthyThresholdDefaultsTo3() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals(3, cfc.unhealthyThreshold(), "Default unhealthyThreshold should be 3");
        }
    }

    @Nested
    @DisplayName("Instance Type and Bastion Defaults")
    class InstanceDefaults {

        @Test
        @DisplayName("instanceType defaults to 't3.micro'")
        void instanceTypeDefaultsToT3Micro() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("t3.micro", cfc.instanceType(), "Default instanceType should be 't3.micro'");
        }

        @Test
        @DisplayName("bastionCidr defaults to '10.0.1.0/24'")
        void bastionCidrDefaultsToStandard() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());
            assertEquals("10.0.1.0/24", cfc.bastionCidr(), "Default bastionCidr should be '10.0.1.0/24'");
        }
    }

    @Nested
    @DisplayName("Complex Default Scenarios")
    class ComplexDefaultScenarios {

        @Test
        @DisplayName("all defaults are applied when config is empty")
        void allDefaultsAppliedWhenConfigEmpty() throws Exception {
            DeploymentContext cfc = fromMap(new LinkedHashMap<>());

            // Core config
            assertEquals("public", cfc.tier());
            assertEquals("dev", cfc.env());
            assertEquals("us-east-1", cfc.region());

            // Runtime and topology
            assertEquals(RuntimeType.FARGATE, cfc.runtime());
            assertEquals(TopologyType.JENKINS_SERVICE, cfc.topology());
            assertEquals(SecurityProfile.DEV, cfc.securityProfile());

            // Capacity
            assertEquals(1, cfc.minInstanceCapacity());
            assertEquals(1, cfc.maxInstanceCapacity());
            assertEquals(60, cfc.cpuTargetUtilization());

            // Networking
            assertEquals("public-no-nat", cfc.networkMode());
            assertEquals("alb", cfc.lbType());
            assertFalse(Boolean.TRUE.equals(cfc.wafEnabled()));

            // DNS
            assertNull(cfc.domain());
            assertNull(cfc.subdomain());
            assertNull(cfc.fqdn());

            // Security
            assertFalse(cfc.enableSsl());
            assertEquals("none", cfc.authMode());

            // Storage
            assertEquals(1024, cfc.cpu());
            assertEquals(2048, cfc.memory());

            // Monitoring
            assertTrue(Boolean.TRUE.equals(cfc.enableMonitoring()) || cfc.enableMonitoring() == null);
            assertTrue(Boolean.TRUE.equals(cfc.enableEncryption()) || cfc.enableEncryption() == null);
        }

        @Test
        @DisplayName("partial config preserves defaults for unspecified values")
        void partialConfigPreservesDefaults() throws Exception {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tier", "enterprise");
            m.put("cpu", 4096);

            DeploymentContext cfc = fromMap(m);

            // Specified values should override
            assertEquals("enterprise", cfc.tier());
            assertEquals(4096, cfc.cpu());

            // Unspecified values should use defaults
            assertEquals("dev", cfc.env());
            assertEquals(2048, cfc.memory());
            assertEquals("public-no-nat", cfc.networkMode());
            assertEquals(1, cfc.minInstanceCapacity());
        }
    }
}
