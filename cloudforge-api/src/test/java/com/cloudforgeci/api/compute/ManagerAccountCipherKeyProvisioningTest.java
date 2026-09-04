package com.cloudforgeci.api.compute;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.manager.ManagerEnvKeys;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies {@code ApplicationFactory}/{@code ContainerFactory} provision and bind the AES cipher
 * key CloudForge Manager uses to encrypt cross-account connection secrets (see {@code
 * SecretCipher}/{@code AesGcmSecretCipher} in cloudforge-manager) — delivered as an ECS {@code
 * Secret} bound to a CDK-provisioned Secrets Manager entry, exactly like {@code
 * CFC_MANAGER_DATABASE_PASSWORD} is delivered, never a literal env var value.
 *
 * <p>Uses a minimal in-test {@link ApplicationSpec} rather than the real {@code
 * CloudForgeManagerApplicationSpec} — cloudforge-api deliberately cannot depend on
 * cloudforge-manager-deployment (that module depends on cloudforge-core only, to stay usable by
 * cloudforge-api's ServiceLoader without a cycle) — so this test only relies on the one thing
 * {@code ApplicationFactory}/{@code ContainerFactory} actually key off: {@code applicationId() ==
 * "cloudforge-manager"}.</p>
 */
class ManagerAccountCipherKeyProvisioningTest {

    private static final String SECRET_DESCRIPTION_MARKER = "cross-account connection secrets";

    private Stack createTestStack(App app, String stackName) {
        return new Stack(app, stackName, StackProps.builder()
            .env(Environment.builder().account("123456789012").region("us-east-1").build())
            .build());
    }

    private Template synthesize(String stackName, Map<String, Object> extraContext, ApplicationSpec spec) {
        App app = new App();
        Stack stack = createTestStack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("lbType", "alb");
        cfcContext.putAll(extraContext);
        stack.getNode().setContext("cfc", cfcContext);

        com.cloudforgeci.api.core.DeploymentContext cfc =
            com.cloudforgeci.api.core.DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.DEV);

        ApplicationFactory.createFargate(stack, "App", cfc, SecurityProfile.DEV, iamProfile, spec);
        return Template.fromStack(stack);
    }

    @Test
    void managerStackProvisionsAndBindsTheAccountCipherKeySecretByDefault() {
        Template template = synthesize("CipherKeyDefault", Map.of(), new FakeManagerSpec());

        template.hasResourceProperties("AWS::SecretsManager::Secret", Match.objectLike(Map.of(
            "Description", Match.stringLikeRegexp(".*" + SECRET_DESCRIPTION_MARKER + ".*")
        )));

        template.hasResourceProperties("AWS::ECS::TaskDefinition", Match.objectLike(Map.of(
            "ContainerDefinitions", Match.arrayWith(List.of(Match.objectLike(Map.of(
                "Secrets", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Name", "CFC_MANAGER_ACCOUNT_SECRET_KEY"
                ))))
            ))))
        )));
    }

    @Test
    void managerStackSkipsTheCipherKeySecretWhenExplicitlyDisabled() {
        Template template = synthesize("CipherKeyDisabled",
            Map.of("provisionManagerAccountCipherKey", false), new FakeManagerSpec());

        template.resourcePropertiesCountIs("AWS::SecretsManager::Secret", Match.objectLike(Map.of(
            "Description", Match.stringLikeRegexp(".*" + SECRET_DESCRIPTION_MARKER + ".*")
        )), 0);
    }

    @Test
    void nonManagerApplicationsNeverGetTheCipherKeySecret() {
        Template template = synthesize("CipherKeyOtherApp", Map.of(), new FakeOtherAppSpec());

        template.resourcePropertiesCountIs("AWS::SecretsManager::Secret", Match.objectLike(Map.of(
            "Description", Match.stringLikeRegexp(".*" + SECRET_DESCRIPTION_MARKER + ".*")
        )), 0);
    }

    /** Minimal stand-in for {@code CloudForgeManagerApplicationSpec} — see class javadoc. Real
     *  bug this test's own staleness caused: cipher-key provisioning used to key off {@code
     *  applicationId() == "cloudforge-manager"} directly; now that {@code
     *  ApplicationFactory}/{@code ContainerFactory} instead key off {@link
     *  ApplicationSpec#cipherKeySecretEnvVar()} returning a non-blank value (see that method's
     *  own javadoc), a fake spec that never overrides it — the interface default is blank — gets
     *  zero {@code AWS::SecretsManager::Secret} resources synthesized, silently reflecting the
     *  new contract-driven behavior rather than the old hardcoded one this test still names. */
    private static final class FakeManagerSpec implements ApplicationSpec {
        @Override public String applicationId() { return "cloudforge-manager"; }
        @Override public String cipherKeySecretEnvVar() { return ManagerEnvKeys.ACCOUNT_SECRET_KEY; }
        @Override public String defaultContainerImage() { return "cloudforgeci/manager:latest"; }
        @Override public int applicationPort() { return 1958; }
        @Override public String containerDataPath() { return "/data"; }
        @Override public String efsDataPath() { return "/manager"; }
        @Override public String volumeName() { return "managerData"; }
        @Override public String containerUser() { return "1000:1000"; }
        @Override public String efsPermissions() { return "750"; }
        @Override public String ebsDeviceName() { return "/dev/xvdh"; }
        @Override public String ec2DataPath() { return "/var/lib/manager"; }
        @Override public List<String> ec2LogPaths() { return List.of(); }
        @Override public void configureUserData(UserDataBuilder builder, Ec2Context context) { }
    }

    private static final class FakeOtherAppSpec implements ApplicationSpec {
        @Override public String applicationId() { return "jenkins-fake-for-cipher-key-test"; }
        @Override public String defaultContainerImage() { return "jenkins/jenkins:lts"; }
        @Override public int applicationPort() { return 8080; }
        @Override public String containerDataPath() { return "/var/jenkins_home"; }
        @Override public String efsDataPath() { return "/jenkins"; }
        @Override public String volumeName() { return "jenkinsHome"; }
        @Override public String containerUser() { return "1000:1000"; }
        @Override public String efsPermissions() { return "750"; }
        @Override public String ebsDeviceName() { return "/dev/xvdh"; }
        @Override public String ec2DataPath() { return "/var/lib/jenkins"; }
        @Override public List<String> ec2LogPaths() { return List.of(); }
        @Override public void configureUserData(UserDataBuilder builder, Ec2Context context) { }
    }
}
