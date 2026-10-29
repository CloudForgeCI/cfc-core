package com.cloudforgeci.api.core.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runs key-management.guard through the cfn-guard binary against small templates. Secrets
 * Manager rotation is a separate RotationSchedule resource; a secret marked
 * {@code CloudForgeRotation: exempt} is skipped.
 */
class KeyManagementGuardTest {

    private static final String GUARD_FILE = "/cfn-guard/frameworks/key-management.guard";
    private static final int PASS = 0;
    private static final int FAIL = 19;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void requireCfnGuard() {
        boolean available;
        try {
            Process process = new ProcessBuilder("cfn-guard", "--version").redirectErrorStream(true).start();
            available = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            available = false;
        }
        Assumptions.assumeTrue(available, "cfn-guard is not installed");
    }

    private int validate(String template) throws IOException, InterruptedException {
        Path rules = tempDir.resolve("key-management.guard");
        try (InputStream in = getClass().getResourceAsStream(GUARD_FILE)) {
            assertNotNull(in, GUARD_FILE);
            Files.write(rules, in.readAllBytes());
        }
        Path data = tempDir.resolve("template.json");
        Files.writeString(data, template);
        Process process = new ProcessBuilder("cfn-guard", "validate", "-d", data.toString(), "-r", rules.toString(),
            "--show-summary", "none").redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals(true, process.waitFor(60, TimeUnit.SECONDS), "cfn-guard did not finish");
        return process.exitValue();
    }

    private static String secret(String id, boolean exempt) {
        String metadata = exempt ? "\"Metadata\":{\"CloudForgeRotation\":\"exempt\"}," : "";
        return "\"" + id + "\":{\"Type\":\"AWS::SecretsManager::Secret\"," + metadata + "\"Properties\":{\"KmsKeyId\":\"k\"}}";
    }

    private static String schedule(String secretId, int days) {
        return "\"Rotation\":{\"Type\":\"AWS::SecretsManager::RotationSchedule\",\"Properties\":{\"SecretId\":{\"Ref\":\""
            + secretId + "\"},\"RotationRules\":{\"AutomaticallyAfterDays\":" + days + "}}}";
    }

    private static String template(String... resources) {
        return "{\"Resources\":{" + String.join(",", resources) + "}}";
    }

    @Test
    void anExemptSecretNeedsNoRotationSchedule() throws Exception {
        assertEquals(PASS, validate(template(secret("AdminSecret", true))));
    }

    @Test
    void aSecretWithoutAnExemptionNeedsARotationSchedule() throws Exception {
        assertEquals(FAIL, validate(template(secret("OtherSecret", false))));
    }

    @Test
    void aRotationScheduleWithinNinetyDaysSatisfiesTheRule() throws Exception {
        assertEquals(PASS, validate(template(secret("OtherSecret", false), schedule("OtherSecret", 30))));
    }

    @Test
    void aRotationScheduleLongerThanNinetyDaysFailsTheRule() throws Exception {
        assertEquals(FAIL, validate(template(secret("OtherSecret", false), schedule("OtherSecret", 120))));
    }

    @Test
    void anExemptSecretDoesNotExcuseAnotherSecretInTheSameTemplate() throws Exception {
        assertEquals(FAIL, validate(template(secret("AdminSecret", true), secret("OtherSecret", false))));
    }

    @Test
    void aTemplateWithoutSecretsPasses() throws Exception {
        assertEquals(PASS, validate(template("\"Param\":{\"Type\":\"AWS::SSM::Parameter\",\"Properties\":{\"Type\":\"String\",\"Value\":\"v\"}}")));
    }
}
