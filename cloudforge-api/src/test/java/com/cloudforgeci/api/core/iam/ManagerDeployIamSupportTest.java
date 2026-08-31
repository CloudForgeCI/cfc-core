package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.assertions.Template;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDK-synthesis coverage for {@link ManagerOperatorIamSupport}'s deploy-capability methods
 * ({@code deployStatements}/{@code catalogProvisionStatement}/{@code attachDeployCapabilities}).
 *
 * <p>{@code deployStatements}/{@code catalogProvisionStatement} are pure catalog queries gated
 * only by {@link ManagerOperatorIamSupport#isCloudForgeManager} — what the capabilities would
 * look like, independent of whether this deployment actually grants them. {@code
 * attachDeployCapabilities} is the one CDK side-effect method, gated additionally behind {@code
 * DeploymentConfig.managerDirectDeployEnabled} (default false) — these tests invoke it directly
 * against a manually-created role rather than relying on {@code createFargate()}, since IAM
 * configuration classes call it unconditionally and let the flag do the gating.</p>
 */
class ManagerDeployIamSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deployStatementsReturnsFourStatementsForManager() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployStatements", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        assertEquals(4, statements.size());
    }

    /** Confirmed live: deploy:create's own template upload had no S3 grant at all before this --
     *  AwsDirectDeployer puts the CloudFormation template into a bucket before CreateStack/
     *  CreateChangeSet ever runs, and the three tag-conditioned statements above only ever
     *  covered CloudFormation/IAM actions. */
    @Test
    void deployStatementsGrantsS3OnTheTemplateBucketPrefix() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerDeployTemplateBucket", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        List<PolicyStatement> statements = ManagerOperatorIamSupport.deployStatements(builder.getSystemContext());
        PolicyStatement bucketStatement = statements.stream()
            .filter(s -> "CloudForgeManagerDeployTemplateBucket".equals(s.getSid()))
            .findFirst()
            .orElseThrow();

        assertTrue(bucketStatement.getActions().contains("s3:*"));
        assertTrue(bucketStatement.getResources().stream()
            .anyMatch(r -> r.contains(com.cloudforgeci.api.deploy.aws.AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX)));
    }

    @Test
    void deployStatementsIsEmptyForNonManagerApplication() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsDeployStatements", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        assertTrue(ManagerOperatorIamSupport.deployStatements(builder.getSystemContext()).isEmpty());
        assertTrue(ManagerOperatorIamSupport.catalogProvisionStatement(builder.getSystemContext()).isEmpty());
    }

    @Test
    void catalogProvisionStatementIsPresentAndConditionFreeForManager() {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerCatalogProvision", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Optional<PolicyStatement> statement =
            ManagerOperatorIamSupport.catalogProvisionStatement(builder.getSystemContext());
        assertTrue(statement.isPresent());
        assertTrue(statement.get().toJSON().toString().contains("servicecatalog:ProvisionProduct"));
        assertFalse(statement.get().toJSON().toString().contains("cloudformation:"));
    }

    @Test
    void attachDeployCapabilitiesAddsAllFourSidsWithExpectedActionsAndConditionsWhenFlagEnabled()
            throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAttachDeploy", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .withManagerDirectDeployEnabled(true)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());

        assertTrue(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployUpdate"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployPassRole"));
        assertTrue(templateJson.contains("CloudForgeManagerDeployCatalog"));

        Set<String> actions = iamActions(template);
        assertTrue(actions.contains("cloudformation:CreateStack"));
        assertTrue(actions.contains("cloudformation:UpdateStack"));
        assertTrue(actions.contains("iam:PassRole"));
        assertTrue(actions.contains("servicecatalog:ProvisionProduct"));

        assertTrue(templateJson.contains("aws:RequestTag/cloudforge:managed"));
        assertTrue(templateJson.contains("aws:ResourceTag/cloudforge:managed"));
        assertTrue(templateJson.contains("iam:ResourceTag/cloudforge:managed"));
    }

    @Test
    void attachDeployCapabilitiesIsNoOpForManagerWhenFlagIsUnsetOrDefault() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerAttachDeployFlagOff", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertFalse(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertFalse(templateJson.contains("CloudForgeManagerDeployCatalog"));
    }

    @Test
    void attachDeployCapabilitiesIsNoOpForNonManagerApplicationEvenWithFlagEnabled() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsAttachDeploy", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .withManagerDirectDeployEnabled(true)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Role role = Role.Builder.create(builder.getStack(), "TestDeployRole")
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();
        ManagerOperatorIamSupport.attachDeployCapabilities(builder.getSystemContext(), role);

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertFalse(templateJson.contains("CloudForgeManagerDeployCreate"));
        assertFalse(templateJson.contains("CloudForgeManagerDeployCatalog"));
    }

    private static Set<String> iamActions(Template template) throws Exception {
        JsonNode root = MAPPER.valueToTree(template.toJSON());
        JsonNode resources = root.path("Resources");
        Set<String> actions = new HashSet<>();
        Iterator<String> names = resources.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode resource = resources.get(name);
            if (!"AWS::IAM::Policy".equals(resource.path("Type").asText())
                && !"AWS::IAM::ManagedPolicy".equals(resource.path("Type").asText())) {
                continue;
            }
            collectActions(resource.path("Properties").path("PolicyDocument").path("Statement"), actions);
        }
        Iterator<String> roleNames = resources.fieldNames();
        while (roleNames.hasNext()) {
            JsonNode resource = resources.get(roleNames.next());
            if (!"AWS::IAM::Role".equals(resource.path("Type").asText())) {
                continue;
            }
            JsonNode policies = resource.path("Properties").path("Policies");
            if (policies.isArray()) {
                for (JsonNode policy : policies) {
                    collectActions(policy.path("PolicyDocument").path("Statement"), actions);
                }
            }
        }
        return actions;
    }

    private static void collectActions(JsonNode statements, Set<String> actions) {
        if (statements.isArray()) {
            for (JsonNode statement : statements) {
                addActionNode(statement.path("Action"), actions);
            }
        } else if (statements.isObject()) {
            addActionNode(statements.path("Action"), actions);
        }
    }

    private static void addActionNode(JsonNode actionNode, Set<String> actions) {
        if (actionNode.isArray()) {
            actionNode.forEach(node -> actions.add(node.asText()));
        } else if (actionNode.isTextual()) {
            actions.add(actionNode.asText());
        }
    }
}
