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
 * output key {@code "ApplicationUrl"} — before this fix, {@link FargateFactory} only emitted a
 * per-app key like {@code "JenkinsUrl"}/{@code "Cloudforge-managerUrl"}, so AWS deployments never
 * resolved an "Open" link or health-check URL through CloudFormation outputs; only LocalStack/
 * MiniStack (which emit their own fixed-name outputs from a different code path) worked.</p>
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
        // CDK-generated disambiguation hash (that's the whole point of the fix).
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
        // EC2 previously emitted NO application-URL output at all — Ec2RuntimeConfiguration.wire()
        // now creates the same stable + per-app pair Fargate does, once its ALB slot is set.
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
