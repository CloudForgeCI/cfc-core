package com.cloudforgeci.api.observability;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.Ec2Context;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforgeci.api.compute.ApplicationFactory;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Full-pipeline integration coverage for {@link ComplianceFactory#createManagerAuditTrailChannel}
 * -- unlike {@code ComplianceFactoryTest}'s construct-presence checks (which never populate a real
 * task role), this synthesizes a complete stack through {@link ApplicationFactory#createFargate}
 * so the {@code s3:PutObject} IAM grant actually has a role to attach to, and asserts on the real
 * synthesized template.
 */
class ManagerAuditTrailChannelIntegrationTest {

    private Stack createTestStack(App app, String stackName) {
        return new Stack(app, stackName, StackProps.builder()
            .env(Environment.builder().account("123456789012").region("us-east-1").build())
            .build());
    }

    private Template synthesize(String stackName, ApplicationSpec spec) {
        App app = new App();
        Stack stack = createTestStack(app, stackName);

        Map<String, Object> cfcContext = new HashMap<>();
        cfcContext.put("stackName", stackName);
        cfcContext.put("lbType", "alb");
        cfcContext.put("securityProfile", "PRODUCTION");
        cfcContext.put("domain", "example.com");
        stack.getNode().setContext("cfc", cfcContext);

        com.cloudforgeci.api.core.DeploymentContext cfc =
            com.cloudforgeci.api.core.DeploymentContext.from(stack);
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(SecurityProfile.PRODUCTION);

        assertDoesNotThrow(() ->
            ApplicationFactory.createFargate(stack, "App", cfc, SecurityProfile.PRODUCTION, iamProfile, spec));
        return Template.fromStack(stack);
    }

    @Test
    void managerStackGrantsItsTaskRolePutObjectOnTheAuditBucket() {
        Template template = synthesize("ManagerAuditTrailIntegration", new FakeManagerSpec());

        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
            "ObjectLockEnabled", true
        )));
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Action", "s3:PutObject"
                ))))
            ))
        )));
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Match.objectLike(Map.of(
            "ContainerDefinitions", Match.arrayWith(List.of(Match.objectLike(Map.of(
                "Environment", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Name", "CFC_MANAGER_AUDIT_TRAIL_BUCKET_ARN"
                ))))
            ))))
        )));
    }

    @Test
    void nonManagerStackGetsNoAuditBucketAndNoGrant() {
        Template template = synthesize("NonManagerAuditTrailIntegration", new FakeOtherAppSpec());

        template.resourcePropertiesCountIs("AWS::S3::Bucket", Match.objectLike(Map.of(
            "ObjectLockEnabled", true
        )), 0);
        template.resourcePropertiesCountIs("AWS::IAM::Policy", Match.objectLike(Map.of(
            "PolicyDocument", Match.objectLike(Map.of(
                "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Action", "s3:PutObject"
                ))))
            ))
        )), 0);
        template.resourcePropertiesCountIs("AWS::ECS::TaskDefinition", Match.objectLike(Map.of(
            "ContainerDefinitions", Match.arrayWith(List.of(Match.objectLike(Map.of(
                "Environment", Match.arrayWith(List.of(Match.objectLike(Map.of(
                    "Name", "CFC_MANAGER_AUDIT_TRAIL_BUCKET_ARN"
                ))))
            ))))
        )), 0);
    }

    /** Minimal stand-in for {@code CloudForgeManagerApplicationSpec} (cloudforge-api cannot depend
     *  on cloudforge-manager-deployment) -- only {@code applicationId()} matters for this gate. */
    private static final class FakeManagerSpec implements ApplicationSpec {
        @Override public String applicationId() { return "cloudforge-manager"; }
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
        @Override public String applicationId() { return "jenkins-fake-for-audit-trail-test"; }
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
