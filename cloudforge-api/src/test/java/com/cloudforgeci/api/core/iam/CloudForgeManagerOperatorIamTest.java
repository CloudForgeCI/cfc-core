package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.manager.ManagerAwsCapabilityCatalog;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDK task-role IAM generated from {@link ManagerAwsCapabilityCatalog}'s baseline operator
 * capabilities (CFN inventory/delete, ECS lifecycle, RDS snapshot/restore).
 */
class CloudForgeManagerOperatorIamTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void managerFargateTaskRoleIncludesOperatorBaselineSid() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerOperatorIam", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());

        Set<String> actions = iamActions(template);
        for (String required : ManagerAwsCapabilityCatalog.operatorBaselineIamActions()) {
            assertTrue(actions.contains(required), "Missing IAM action on manager stack: " + required);
        }
        assertTrue(templateJson.contains(ManagerOperatorIamSupport.OPERATOR_POLICY_SID));
    }

    @Test
    void nonManagerStackOmitsOperatorBaselineSid() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsOperatorIam", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        String templateJson = MAPPER.writeValueAsString(template.toJSON());
        assertFalse(templateJson.contains(ManagerOperatorIamSupport.OPERATOR_POLICY_SID));
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
