package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all SystemContext slot operations.
 * Tests all 57 slots to ensure proper get/set/isPresent functionality.
 */
@DisplayName("SystemContext Slot Tests")
class SystemContextSlotTest {

    private App app;
    private Stack stack;
    private SystemContext ctx;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "TestStack");
        ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                                   SecurityProfile.DEV, IAMProfile.MINIMAL, DeploymentContext.from(stack));
    }

    @Nested
    @DisplayName("Infrastructure Slots - Initially Empty")
    class InfrastructureSlots {

        @Test
        @DisplayName("vpc slot should be empty initially")
        void vpcSlot() {
            assertFalse(ctx.vpc.get().isPresent(), "VPC slot should be empty initially");
        }

        @Test
        @DisplayName("alb slot should be empty initially")
        void albSlot() {
            assertFalse(ctx.alb.get().isPresent(), "ALB slot should be empty initially");
        }

        @Test
        @DisplayName("asg slot should be empty initially")
        void asgSlot() {
            assertFalse(ctx.asg.get().isPresent(), "ASG slot should be empty initially");
        }

        @Test
        @DisplayName("ec2Instance slot should be empty initially")
        void ec2InstanceSlot() {
            assertFalse(ctx.ec2Instance.get().isPresent(), "EC2 instance slot should be empty initially");
        }

        @Test
        @DisplayName("efs slot should be empty initially")
        void efsSlot() {
            assertFalse(ctx.efs.get().isPresent(), "EFS slot should be empty initially");
        }

        @Test
        @DisplayName("logs slot should be empty initially")
        void logsSlot() {
            assertFalse(ctx.logs.get().isPresent(), "Logs slot should be empty initially");
        }

        @Test
        @DisplayName("zone slot should be empty initially")
        void zoneSlot() {
            assertFalse(ctx.zone.get().isPresent(), "Zone slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Security Group Slots - Initially Empty")
    class SecurityGroupSlots {

        @Test
        @DisplayName("instanceSg slot should be empty initially")
        void instanceSgSlot() {
            assertFalse(ctx.instanceSg.get().isPresent(), "Instance SG slot should be empty initially");
        }

        @Test
        @DisplayName("albSg slot should be empty initially")
        void albSgSlot() {
            assertFalse(ctx.albSg.get().isPresent(), "ALB SG slot should be empty initially");
        }

        @Test
        @DisplayName("efsSg slot should be empty initially")
        void efsSgSlot() {
            assertFalse(ctx.efsSg.get().isPresent(), "EFS SG slot should be empty initially");
        }

        @Test
        @DisplayName("fargateServiceSg slot should be empty initially")
        void fargateServiceSgSlot() {
            assertFalse(ctx.fargateServiceSg.get().isPresent(), "Fargate service SG slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("ALB-related Slots - Initially Empty")
    class AlbRelatedSlots {

        @Test
        @DisplayName("albTargetGroup slot should be empty initially")
        void albTargetGroupSlot() {
            assertFalse(ctx.albTargetGroup.get().isPresent(), "ALB target group slot should be empty initially");
        }

        @Test
        @DisplayName("http slot should be empty initially")
        void httpSlot() {
            assertFalse(ctx.http.get().isPresent(), "HTTP listener slot should be empty initially");
        }

        @Test
        @DisplayName("https slot should be empty initially")
        void httpsSlot() {
            assertFalse(ctx.https.get().isPresent(), "HTTPS listener slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Boolean Flag Slots")
    class BooleanFlagSlots {

        @Test
        @DisplayName("httpsTargetsAdded slot should be empty initially and settable")
        void httpsTargetsAddedSlot() {
            assertFalse(ctx.httpsTargetsAdded.get().isPresent());
            ctx.httpsTargetsAdded.set(true);
            assertTrue(ctx.httpsTargetsAdded.get().isPresent());
            assertTrue(ctx.httpsTargetsAdded.get().get());
        }

        @Test
        @DisplayName("wired slot should be empty initially and settable")
        void wiredSlot() {
            assertFalse(ctx.wired.get().isPresent());
            ctx.wired.set(true);
            assertTrue(ctx.wired.get().isPresent());
            assertTrue(ctx.wired.get().get());
        }

        @Test
        @DisplayName("dnsRecordsCreated slot should be empty initially and settable")
        void dnsRecordsCreatedSlot() {
            assertFalse(ctx.dnsRecordsCreated.get().isPresent());
            ctx.dnsRecordsCreated.set(true);
            assertTrue(ctx.dnsRecordsCreated.get().isPresent());
            assertTrue(ctx.dnsRecordsCreated.get().get());
        }

        @Test
        @DisplayName("dnsRecordsCallbackRegistered slot should be empty initially and settable")
        void dnsRecordsCallbackRegisteredSlot() {
            assertFalse(ctx.dnsRecordsCallbackRegistered.get().isPresent());
            ctx.dnsRecordsCallbackRegistered.set(true);
            assertTrue(ctx.dnsRecordsCallbackRegistered.get().isPresent());
            assertTrue(ctx.dnsRecordsCallbackRegistered.get().get());
        }

        @Test
        @DisplayName("asgAddedToTargetGroup slot should be empty initially and settable")
        void asgAddedToTargetGroupSlot() {
            assertFalse(ctx.asgAddedToTargetGroup.get().isPresent());
            ctx.asgAddedToTargetGroup.set(true);
            assertTrue(ctx.asgAddedToTargetGroup.get().isPresent());
            assertTrue(ctx.asgAddedToTargetGroup.get().get());
        }

        @Test
        @DisplayName("scalingPoliciesApplied slot should be empty initially and settable")
        void scalingPoliciesAppliedSlot() {
            assertFalse(ctx.scalingPoliciesApplied.get().isPresent());
            ctx.scalingPoliciesApplied.set(true);
            assertTrue(ctx.scalingPoliciesApplied.get().isPresent());
            assertTrue(ctx.scalingPoliciesApplied.get().get());
        }

        @Test
        @DisplayName("fargateAutoscalingConfigured slot should be empty initially and settable")
        void fargateAutoscalingConfiguredSlot() {
            assertFalse(ctx.fargateAutoscalingConfigured.get().isPresent());
            ctx.fargateAutoscalingConfigured.set(true);
            assertTrue(ctx.fargateAutoscalingConfigured.get().isPresent());
            assertTrue(ctx.fargateAutoscalingConfigured.get().get());
        }

        @Test
        @DisplayName("fargateAutoscalingCallbackRegistered slot should be empty initially and settable")
        void fargateAutoscalingCallbackRegisteredSlot() {
            assertFalse(ctx.fargateAutoscalingCallbackRegistered.get().isPresent());
            ctx.fargateAutoscalingCallbackRegistered.set(true);
            assertTrue(ctx.fargateAutoscalingCallbackRegistered.get().isPresent());
            assertTrue(ctx.fargateAutoscalingCallbackRegistered.get().get());
        }

        @Test
        @DisplayName("ec2AutoscalingCallbackRegistered slot should be empty initially and settable")
        void ec2AutoscalingCallbackRegisteredSlot() {
            assertFalse(ctx.ec2AutoscalingCallbackRegistered.get().isPresent());
            ctx.ec2AutoscalingCallbackRegistered.set(true);
            assertTrue(ctx.ec2AutoscalingCallbackRegistered.get().isPresent());
            assertTrue(ctx.ec2AutoscalingCallbackRegistered.get().get());
        }

        @Test
        @DisplayName("sslEnabled slot should be empty initially and settable")
        void sslEnabledSlot() {
            assertFalse(ctx.sslEnabled.get().isPresent());
            ctx.sslEnabled.set(true);
            assertTrue(ctx.sslEnabled.get().isPresent());
            assertTrue(ctx.sslEnabled.get().get());
        }

        @Test
        @DisplayName("httpRedirectEnabled slot should be empty initially and settable")
        void httpRedirectEnabledSlot() {
            assertFalse(ctx.httpRedirectEnabled.get().isPresent());
            ctx.httpRedirectEnabled.set(true);
            assertTrue(ctx.httpRedirectEnabled.get().isPresent());
            assertTrue(ctx.httpRedirectEnabled.get().get());
        }

        @Test
        @DisplayName("wafEnabled slot should be empty initially and settable")
        void wafEnabledSlot() {
            assertFalse(ctx.wafEnabled.get().isPresent());
            ctx.wafEnabled.set(true);
            assertTrue(ctx.wafEnabled.get().isPresent());
            assertTrue(ctx.wafEnabled.get().get());
        }

        @Test
        @DisplayName("cloudfront slot should be empty initially and settable")
        void cloudfrontSlot() {
            assertFalse(ctx.cloudfront.get().isPresent());
            ctx.cloudfront.set(true);
            assertTrue(ctx.cloudfront.get().isPresent());
            assertTrue(ctx.cloudfront.get().get());
        }

        @Test
        @DisplayName("enableFlowlogs slot should be empty initially and settable")
        void enableFlowlogsSlot() {
            assertFalse(ctx.enableFlowlogs.get().isPresent());
            ctx.enableFlowlogs.set(true);
            assertTrue(ctx.enableFlowlogs.get().isPresent());
            assertTrue(ctx.enableFlowlogs.get().get());
        }
    }

    @Nested
    @DisplayName("EFS-related Slots - Initially Empty")
    class EfsRelatedSlots {

        @Test
        @DisplayName("ap (AccessPoint) slot should be empty initially")
        void apSlot() {
            assertFalse(ctx.ap.get().isPresent(), "Access point slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Fargate-related Slots - Initially Empty")
    class FargateRelatedSlots {

        @Test
        @DisplayName("fargateService slot should be empty initially")
        void fargateServiceSlot() {
            assertFalse(ctx.fargateService.get().isPresent(), "Fargate service slot should be empty initially");
        }

        @Test
        @DisplayName("fargateTaskDef slot should be empty initially")
        void fargateTaskDefSlot() {
            assertFalse(ctx.fargateTaskDef.get().isPresent(), "Fargate task definition slot should be empty initially");
        }

        @Test
        @DisplayName("container slot should be empty initially")
        void containerSlot() {
            assertFalse(ctx.container.get().isPresent(), "Container slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Certificate and SSL Slots - Initially Empty")
    class CertificateSlots {

        @Test
        @DisplayName("cert slot should be empty initially")
        void certSlot() {
            assertFalse(ctx.cert.get().isPresent(), "Certificate slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Configuration String Slots")
    class ConfigurationStringSlots {

        @Test
        @DisplayName("networkMode slot should be empty initially and settable")
        void networkModeSlot() {
            assertFalse(ctx.networkMode.get().isPresent());
            ctx.networkMode.set("public-no-nat");
            assertTrue(ctx.networkMode.get().isPresent());
            assertEquals("public-no-nat", ctx.networkMode.get().get());
        }

        @Test
        @DisplayName("lbType slot should be empty initially and settable")
        void lbTypeSlot() {
            assertFalse(ctx.lbType.get().isPresent());
            ctx.lbType.set("alb");
            assertTrue(ctx.lbType.get().isPresent());
            assertEquals("alb", ctx.lbType.get().get());
        }

        @Test
        @DisplayName("authMode slot should be empty initially and settable")
        void authModeSlot() {
            assertFalse(ctx.authMode.get().isPresent());
            ctx.authMode.set("alb-oidc");
            assertTrue(ctx.authMode.get().isPresent());
            assertEquals("alb-oidc", ctx.authMode.get().get());
        }

        @Test
        @DisplayName("ssoInstanceArn slot should be empty initially and settable")
        void ssoInstanceArnSlot() {
            assertFalse(ctx.ssoInstanceArn.get().isPresent());
            ctx.ssoInstanceArn.set("arn:aws:sso:::instance/test");
            assertTrue(ctx.ssoInstanceArn.get().isPresent());
            assertEquals("arn:aws:sso:::instance/test", ctx.ssoInstanceArn.get().get());
        }

        @Test
        @DisplayName("ssoGroupId slot should be empty initially and settable")
        void ssoGroupIdSlot() {
            assertFalse(ctx.ssoGroupId.get().isPresent());
            ctx.ssoGroupId.set("group-123");
            assertTrue(ctx.ssoGroupId.get().isPresent());
            assertEquals("group-123", ctx.ssoGroupId.get().get());
        }

        @Test
        @DisplayName("ssoTargetAccountId slot should be empty initially and settable")
        void ssoTargetAccountIdSlot() {
            assertFalse(ctx.ssoTargetAccountId.get().isPresent());
            ctx.ssoTargetAccountId.set("123456789012");
            assertTrue(ctx.ssoTargetAccountId.get().isPresent());
            assertEquals("123456789012", ctx.ssoTargetAccountId.get().get());
        }

        @Test
        @DisplayName("artifactsBucket slot should be empty initially and settable")
        void artifactsBucketSlot() {
            assertFalse(ctx.artifactsBucket.get().isPresent());
            ctx.artifactsBucket.set("my-artifacts-bucket");
            assertTrue(ctx.artifactsBucket.get().isPresent());
            assertEquals("my-artifacts-bucket", ctx.artifactsBucket.get().get());
        }

        @Test
        @DisplayName("artifactsPrefix slot should be empty initially and settable")
        void artifactsPrefixSlot() {
            assertFalse(ctx.artifactsPrefix.get().isPresent());
            ctx.artifactsPrefix.set("jenkins/artifacts/");
            assertTrue(ctx.artifactsPrefix.get().isPresent());
            assertEquals("jenkins/artifacts/", ctx.artifactsPrefix.get().get());
        }

        @Test
        @DisplayName("domain slot should be empty initially and settable")
        void domainSlot() {
            assertFalse(ctx.domain.get().isPresent());
            ctx.domain.set("example.com");
            assertTrue(ctx.domain.get().isPresent());
            assertEquals("example.com", ctx.domain.get().get());
        }

        @Test
        @DisplayName("subdomain slot should be empty initially and settable")
        void subdomainSlot() {
            assertFalse(ctx.subdomain.get().isPresent());
            ctx.subdomain.set("jenkins");
            assertTrue(ctx.subdomain.get().isPresent());
            assertEquals("jenkins", ctx.subdomain.get().get());
        }

        @Test
        @DisplayName("fqdn slot should be empty initially and settable")
        void fqdnSlot() {
            assertFalse(ctx.fqdn.get().isPresent());
            ctx.fqdn.set("jenkins.example.com");
            assertTrue(ctx.fqdn.get().isPresent());
            assertEquals("jenkins.example.com", ctx.fqdn.get().get());
        }
    }

    @Nested
    @DisplayName("Configuration Integer Slots")
    class ConfigurationIntegerSlots {

        @Test
        @DisplayName("minInstanceCapacity slot should be empty initially and settable")
        void minInstanceCapacitySlot() {
            assertFalse(ctx.minInstanceCapacity.get().isPresent());
            ctx.minInstanceCapacity.set(1);
            assertTrue(ctx.minInstanceCapacity.get().isPresent());
            assertEquals(1, ctx.minInstanceCapacity.get().get());
        }

        @Test
        @DisplayName("maxInstanceCapacity slot should be empty initially and settable")
        void maxInstanceCapacitySlot() {
            assertFalse(ctx.maxInstanceCapacity.get().isPresent());
            ctx.maxInstanceCapacity.set(10);
            assertTrue(ctx.maxInstanceCapacity.get().isPresent());
            assertEquals(10, ctx.maxInstanceCapacity.get().get());
        }

        @Test
        @DisplayName("cpuTargetUtilization slot should be empty initially and settable")
        void cpuTargetUtilizationSlot() {
            assertFalse(ctx.cpuTargetUtilization.get().isPresent());
            ctx.cpuTargetUtilization.set(70);
            assertTrue(ctx.cpuTargetUtilization.get().isPresent());
            assertEquals(70, ctx.cpuTargetUtilization.get().get());
        }

        @Test
        @DisplayName("cpu slot should be empty initially and settable")
        void cpuSlot() {
            assertFalse(ctx.cpu.get().isPresent());
            ctx.cpu.set(2048);
            assertTrue(ctx.cpu.get().isPresent());
            assertEquals(2048, ctx.cpu.get().get());
        }

        @Test
        @DisplayName("memory slot should be empty initially and settable")
        void memorySlot() {
            assertFalse(ctx.memory.get().isPresent());
            ctx.memory.set(4096);
            assertTrue(ctx.memory.get().isPresent());
            assertEquals(4096, ctx.memory.get().get());
        }
    }

    @Nested
    @DisplayName("S3 and CloudFront Slots - Initially Empty")
    class S3CloudFrontSlots {

        @Test
        @DisplayName("websiteBucket slot should be empty initially")
        void websiteBucketSlot() {
            assertFalse(ctx.websiteBucket.get().isPresent(), "Website bucket slot should be empty initially");
        }

        @Test
        @DisplayName("distribution slot should be empty initially")
        void distributionSlot() {
            assertFalse(ctx.distribution.get().isPresent(), "Distribution slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("Logging and Security Slots - Initially Empty")
    class LoggingSecuritySlots {

        @Test
        @DisplayName("flowlogs slot should be empty initially")
        void flowlogsSlot() {
            assertFalse(ctx.flowlogs.get().isPresent(), "Flow logs slot should be empty initially");
        }

        @Test
        @DisplayName("wafWebAcl slot should be empty initially")
        void wafWebAclSlot() {
            assertFalse(ctx.wafWebAcl.get().isPresent(), "WAF web ACL slot should be empty initially");
        }
    }

    @Nested
    @DisplayName("IAM Role Slots - Initially Empty")
    class IamRoleSlots {

        @Test
        @DisplayName("ec2InstanceRole slot should be empty initially")
        void ec2InstanceRoleSlot() {
            assertFalse(ctx.ec2InstanceRole.get().isPresent(), "EC2 instance role slot should be empty initially");
        }

        @Test
        @DisplayName("fargateExecutionRole slot may be populated by IAM rules")
        void fargateExecutionRoleSlot() {
            // Note: This slot may be populated by IAM configuration rules
            // Just verify the slot exists and is accessible
            assertNotNull(ctx.fargateExecutionRole, "Fargate execution role slot should exist");
        }

        @Test
        @DisplayName("fargateTaskRole slot may be populated by IAM rules")
        void fargateTaskRoleSlot() {
            // Note: This slot may be populated by IAM configuration rules
            // Just verify the slot exists and is accessible
            assertNotNull(ctx.fargateTaskRole, "Fargate task role slot should exist");
        }
    }

    @Nested
    @DisplayName("Security Profile Configuration Slot")
    class SecurityProfileConfigSlot {

        @Test
        @DisplayName("securityProfileConfig slot should be populated by security rules")
        void securityProfileConfigSlot() {
            // Security profile config is populated during SystemContext initialization by security rules
            assertTrue(ctx.securityProfileConfig.get().isPresent(), "Security profile config should be populated");
            assertNotNull(ctx.securityProfileConfig.get().get(), "Security profile config should not be null");
        }
    }
}
