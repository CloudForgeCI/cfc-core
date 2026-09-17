package com.cloudforgeci.api.core.iam;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CDK-synthesis coverage for {@link ManagerOperatorIamSupport#marketplaceEntitlementStatement} —
 * the opt-in {@code aws-marketplace:GetEntitlements} grant gated behind {@code
 * DeploymentConfig.marketplaceProductCode}.
 */
class ManagerMarketplaceEntitlementIamTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SID = "CloudForgeManagerMarketplaceEntitlement";

    @Test
    void managerStackWithProductCodeGetsScopedEntitlementGrant() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerMarketplaceIam", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .withMarketplaceProductCode("prod-abc123")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        JsonNode statement = findStatement(template, SID);

        assertTrue(statement != null, "Expected a policy statement with sid " + SID);
        assertTrue(statement.path("Action").asText().equals("aws-marketplace:GetEntitlements")
            || contains(statement.path("Action"), "aws-marketplace:GetEntitlements"));
        assertTrue(statement.path("Condition").path("StringEquals")
            .path("aws:marketplace:ProductCode").asText().equals("prod-abc123"));
    }

    @Test
    void managerStackWithoutProductCodeOmitsGrant() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "ManagerMarketplaceIamUnset", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId(ManagerOperatorIamSupport.APPLICATION_ID)
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        assertFalse(templateContainsSid(template, SID));
    }

    @Test
    void nonManagerStackOmitsGrantEvenWithProductCodeSet() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "JenkinsMarketplaceIam", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .withMarketplaceProductCode("prod-abc123")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        assertFalse(templateContainsSid(template, SID));
    }

    private static boolean templateContainsSid(Template template, String sid) throws Exception {
        return findStatement(template, sid) != null;
    }

    private static JsonNode findStatement(Template template, String sid) throws Exception {
        JsonNode root = MAPPER.valueToTree(template.toJSON());
        JsonNode resources = root.path("Resources");
        Iterator<String> names = resources.fieldNames();
        while (names.hasNext()) {
            JsonNode resource = resources.get(names.next());
            String type = resource.path("Type").asText();
            if ("AWS::IAM::Policy".equals(type) || "AWS::IAM::ManagedPolicy".equals(type)) {
                JsonNode found = findInStatements(
                    resource.path("Properties").path("PolicyDocument").path("Statement"), sid);
                if (found != null) {
                    return found;
                }
            } else if ("AWS::IAM::Role".equals(type)) {
                JsonNode policies = resource.path("Properties").path("Policies");
                if (policies.isArray()) {
                    for (JsonNode policy : policies) {
                        JsonNode found = findInStatements(
                            policy.path("PolicyDocument").path("Statement"), sid);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static JsonNode findInStatements(JsonNode statements, String sid) {
        if (!statements.isArray()) {
            return null;
        }
        for (JsonNode statement : statements) {
            if (sid.equals(statement.path("Sid").asText())) {
                return statement;
            }
        }
        return null;
    }

    private static boolean contains(JsonNode actionNode, String action) {
        if (actionNode.isArray()) {
            for (JsonNode node : actionNode) {
                if (action.equals(node.asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
