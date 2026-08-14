package com.cloudforge.core.config;

import com.cloudforge.core.annotation.ApplicationPlugin;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentContextPreparerTest {

    @ApplicationPlugin(
        value = "sample-jenkins",
        category = "cicd",
        displayName = "Sample Jenkins",
        defaultCpu = 1024,
        defaultMemory = 2048,
        defaultInstanceType = "t3.small",
        supportsOidc = true
    )
    static class SampleJenkinsSpec implements ApplicationSpec {
        @Override
        public String applicationId() {
            return "sample-jenkins";
        }

        @Override
        public String defaultContainerImage() {
            return "jenkins/jenkins:lts";
        }

        @Override
        public int applicationPort() {
            return 8080;
        }

        @Override
        public String containerDataPath() {
            return "/var/jenkins_home";
        }

        @Override
        public String efsDataPath() {
            return "/jenkins";
        }

        @Override
        public String volumeName() {
            return "jenkinsHome";
        }

        @Override
        public String containerUser() {
            return "1000:1000";
        }

        @Override
        public String efsPermissions() {
            return "750";
        }

        @Override
        public String ebsDeviceName() {
            return "/dev/xvdh";
        }

        @Override
        public String ec2DataPath() {
            return "/var/lib/jenkins";
        }

        @Override
        public List<String> ec2LogPaths() {
            return List.of("/var/log/jenkins/jenkins.log");
        }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        }

        @Override
        public boolean supportsOidcIntegration() {
            return true;
        }

        @Override
        public List<String> getSupportedAuthModes(String deploymentTarget) {
            return List.of("application-oidc", "alb-oidc", "none");
        }
    }

    @ApplicationPlugin(
        value = "sample-manager",
        category = "operations",
        displayName = "Sample Manager",
        defaultCpu = 512,
        defaultMemory = 1024
    )
    static class SampleManagerSpec implements ApplicationSpec {
        @Override
        public String applicationId() {
            return "sample-manager";
        }

        @Override
        public String defaultContainerImage() {
            return "cloudforge/manager:latest";
        }

        @Override
        public int applicationPort() {
            return 8080;
        }

        @Override
        public String containerDataPath() {
            return "/data";
        }

        @Override
        public String efsDataPath() {
            return "/data";
        }

        @Override
        public String volumeName() {
            return "data";
        }

        @Override
        public String containerUser() {
            return "1000:1000";
        }

        @Override
        public String efsPermissions() {
            return "750";
        }

        @Override
        public String ebsDeviceName() {
            return "/dev/xvdh";
        }

        @Override
        public String ec2DataPath() {
            return "/var/lib/manager";
        }

        @Override
        public List<String> ec2LogPaths() {
            return List.of("/var/log/manager.log");
        }

        @Override
        public void configureUserData(UserDataBuilder builder, Ec2Context context) {
        }

        @Override
        public List<String> getSupportedAuthModes(String deploymentTarget) {
            return switch (deploymentTarget == null ? "" : deploymentTarget.toLowerCase()) {
                case "localstack" -> List.of("none", "application-oidc");
                default -> List.of("alb-oidc", "none");
            };
        }

        @Override
        public String getRecommendedAuthMode(String deploymentTarget) {
            if ("localstack".equalsIgnoreCase(deploymentTarget)) {
                return "none";
            }
            return "alb-oidc";
        }
    }

    @Test
    void fillsCpuMemoryAndCognitoFromAlbOidcContext() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Stack";
        config.applicationId = "jenkins";
        config.authMode = AuthMode.ALB_OIDC;

        DeploymentContextPreparer.PrepareResult result =
            DeploymentContextPreparer.prepare(config, new SampleJenkinsSpec(), null);

        assertEquals(1024, config.cpu);
        assertEquals(2048, config.memory);
        assertEquals(RuntimeType.FARGATE, config.runtime);
        assertTrue(Boolean.TRUE.equals(config.enableSsl));
        assertEquals("cognito", config.oidcProvider);
        assertTrue(config.cognitoAutoProvision);
        assertEquals("jenkins-stack-auth", config.cognitoDomainPrefix);
        assertEquals("jenkins-Admins", config.cognitoAdminGroupName);
        assertEquals("jenkins-Users", config.cognitoUserGroupName);
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("cognito")));
    }

    @Test
    void coercesUnsupportedAuthForLocalStackTarget() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Manager-Dev";
        config.applicationId = "sample-manager";
        config.authMode = AuthMode.ALB_OIDC;

        DeploymentContextPreparer.prepare(config, new SampleManagerSpec(), "localstack");

        assertEquals(AuthMode.NONE, config.authMode);
    }

    @Test
    void preservesExplicitDomainAndSslForJenkinsLocalStack() {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "Jenkins-Stack";
        config.applicationId = "jenkins";
        config.domain = "local.test";
        config.subdomain = "jenkins";
        config.createZone = true;
        config.enableSsl = true;
        config.authMode = AuthMode.ALB_OIDC;
        config.oidcProvider = "cognito";
        config.cognitoAutoProvision = true;
        config.cognitoDomainPrefix = "jenkins-localstack-auth";

        DeploymentContextPreparer.prepare(config, new SampleJenkinsSpec(), "localstack");

        assertEquals("local.test", config.domain);
        assertEquals("jenkins", config.subdomain);
        assertEquals(AuthMode.ALB_OIDC, config.authMode);
        assertEquals("jenkins-localstack-auth", config.cognitoDomainPrefix);
    }
}
