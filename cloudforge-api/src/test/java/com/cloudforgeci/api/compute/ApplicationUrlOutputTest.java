package com.cloudforgeci.api.compute;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the stable {@code ApplicationUrl} CFN output.
 *
 * <p>{@code CloudFormationInventory.preferredUrl} (cloudforge-manager) looks for the literal
 * output key {@code "ApplicationUrl"} to resolve an "Open" link or health-check URL. Stacks emit
 * it alongside the per-app key (e.g. {@code "JenkinsUrl"}).</p>
 */
class ApplicationUrlOutputTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fargateStackEmitsAStableApplicationUrlOutputAlongsideThePerAppOne() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "AppUrlOutputJenkins", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        JsonNode outputs = MAPPER.valueToTree(template.toJSON()).path("Outputs");

        // Created directly on the Stack, so its OutputKey is exactly "ApplicationUrl" — no
        // CDK-generated disambiguation hash.
        assertTrue(outputs.has("ApplicationUrl"), "Missing stable ApplicationUrl output: " + outputs);

        // The pre-existing per-app output is nested under the FargateFactory construct, so its
        // OutputKey is CDK-mangled (e.g. "FargateJenkinsUrl<hash>") — just confirm it's still
        // there and carries the same value as the new stable alias.
        String perAppKey = findKeyContaining(outputs, "JenkinsUrl");
        assertTrue(perAppKey != null, "Per-app *JenkinsUrl output should still be present: " + outputs);
        assertEquals(outputs.path(perAppKey).path("Value"), outputs.path("ApplicationUrl").path("Value"));
    }

    private static String findKeyContaining(JsonNode outputs, String substring) {
        var names = outputs.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.contains(substring)) {
                return name;
            }
        }
        return null;
    }

    @Test
    void managerStackEmitsAStableApplicationUrlOutputToo() throws Exception {
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "AppUrlOutputManager", SecurityProfile.DEV, RuntimeType.FARGATE)
            .withApplicationId("cloudforge-manager")
            .createVpc()
            .createAlb()
            .createEfs()
            .createFargate();

        Template template = Template.fromStack(builder.getStack());
        JsonNode outputs = MAPPER.valueToTree(template.toJSON()).path("Outputs");

        assertTrue(outputs.has("ApplicationUrl"), "Missing stable ApplicationUrl output: " + outputs);
    }

    @Test
    void ec2StackEmitsAStableApplicationUrlOutputToo() throws Exception {
        // Ec2RuntimeConfiguration.wire() creates the same stable + per-app pair as Fargate, once
        // its ALB slot is set.
        //
        // Unlike FargateFactory (which calls createApplicationUrlOutput() directly inside
        // create()), Ec2RuntimeConfiguration.wire() only runs as a *deferred* action registered
        // via ctx.once(...) — in production that's flushed by ApplicationFactory.create() via
        // ctx.executeDeferredActions(); this piecemeal builder bypasses ApplicationFactory, so
        // the test has to flush it explicitly, same as the real orchestration path does.
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
                "AppUrlOutputEc2", SecurityProfile.DEV, RuntimeType.EC2)
            .withApplicationId("jenkins")
            .createVpc()
            .createAlb()
            .createEfs()
            .createEc2();
        builder.getSystemContext().executeDeferredActions();

        Template template = Template.fromStack(builder.getStack());
        JsonNode outputs = MAPPER.valueToTree(template.toJSON()).path("Outputs");

        assertTrue(outputs.has("ApplicationUrl"), "Missing stable ApplicationUrl output on EC2: " + outputs);
        assertTrue(findKeyContaining(outputs, "JenkinsUrl") != null,
            "Per-app *JenkinsUrl output should be present on EC2 too: " + outputs);
    }
}
