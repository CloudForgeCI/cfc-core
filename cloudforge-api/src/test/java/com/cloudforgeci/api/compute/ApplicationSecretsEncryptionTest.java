package com.cloudforgeci.api.compute;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforgeci.api.application.database.PostgreSQLApplicationSpec;
import com.cloudforgeci.api.test.DeploymentSynth;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import software.amazon.awscdk.assertions.Match;

/**
 * The secrets the application factory generates (admin password, license key, cipher key) use a
 * customer-managed KMS key when the security profile asks for one: on in PRODUCTION and STAGING,
 * off in DEV, and a framework in ENFORCE mode requires it. The initial admin password is marked so
 * the key-management rotation rule skips it.
 */
class ApplicationSecretsEncryptionTest {

    private static final String SECRET = "AWS::SecretsManager::Secret";
    private static final String KEY = "AWS::KMS::Key";

    private static Map<String, Object> context(String profile) {
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        context.remove("complianceFrameworks");
        context.put("securityProfile", profile);
        context.put("applicationId", "postgresql");
        return context;
    }

    private static Template synthesize(Map<String, Object> context, RuntimeType runtime) {
        return DeploymentSynth.synthesize(context, new PostgreSQLApplicationSpec(), runtime);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> adminPasswordSecret(Template template) {
        List<Map<String, Object>> secrets = template.findResources(SECRET).entrySet().stream()
            .filter(entry -> entry.getKey().contains("AutoAdminPasswordSecret"))
            .map(entry -> (Map<String, Object>) entry.getValue())
            .collect(Collectors.toList());
        assertEquals(1, secrets.size(), "expected one admin password secret");
        return secrets.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> resource) {
        return (Map<String, Object>) resource.get("Properties");
    }

    private static List<String> secretsKeyIds(Template template) {
        return template.findResources(KEY).entrySet().stream()
            .filter(entry -> String.valueOf(entry.getValue()).contains("generated application secrets"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    @Test
    void productionEncryptsTheAdminPasswordSecretWithACustomerManagedKey() {
        Template template = synthesize(context("PRODUCTION"), RuntimeType.FARGATE);

        assertTrue(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
        assertEquals(1, secretsKeyIds(template).size());
    }

    @Test
    void theSecretsKeyRotatesAutomatically() {
        Template template = synthesize(context("PRODUCTION"), RuntimeType.FARGATE);

        template.hasResourceProperties(KEY, Map.of("EnableKeyRotation", true,
            "Description", Match.stringLikeRegexp(".*generated application secrets.*")));
    }

    @Test
    void stagingEncryptsTheSecretToo() {
        Template template = synthesize(context("STAGING"), RuntimeType.FARGATE);

        assertTrue(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
    }

    @Test
    void devKeepsTheAwsManagedKey() {
        Template template = synthesize(context("DEV"), RuntimeType.FARGATE);

        assertFalse(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
        assertEquals(0, secretsKeyIds(template).size());
    }

    @Test
    void anExplicitFalseKeepsTheAwsManagedKeyInProduction() {
        Map<String, Object> context = context("PRODUCTION");
        context.put("secretsKmsEncryptionEnabled", false);

        Template template = synthesize(context, RuntimeType.FARGATE);

        assertFalse(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
        assertEquals(0, secretsKeyIds(template).size());
    }

    @Test
    void aFrameworkInEnforceModeRequiresTheKeyWhateverTheSettingSays() {
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        context.put("applicationId", "postgresql");
        context.put("secretsKmsEncryptionEnabled", false);

        Template template = synthesize(context, RuntimeType.FARGATE);

        assertTrue(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
    }

    @Test
    void advisoryModeRespectsAnExplicitFalse() {
        Map<String, Object> context = DeploymentSynth.baseline("soc2");
        context.put("applicationId", "postgresql");
        context.put("complianceMode", "advisory");
        context.put("secretsKmsEncryptionEnabled", false);

        Template template = synthesize(context, RuntimeType.FARGATE);

        assertFalse(properties(adminPasswordSecret(template)).containsKey("KmsKeyId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAdminPasswordSecretIsMarkedExemptFromRotation() {
        Template template = synthesize(context("PRODUCTION"), RuntimeType.FARGATE);

        Map<String, Object> metadata = (Map<String, Object>) adminPasswordSecret(template).get("Metadata");
        assertEquals("exempt", metadata.get("CloudForgeRotation"));
    }

    @Test
    void theFargateExecutionRoleCanDecryptWithTheSecretsKey() {
        Template template = synthesize(context("PRODUCTION"), RuntimeType.FARGATE);

        String policies = template.findResources("AWS::IAM::Policy").toString();
        assertTrue(policies.contains("AllowDecryptApplicationSecrets"), "no kms:Decrypt statement for the secrets key");
        assertTrue(policies.contains("kms:Decrypt"));
    }

    @Test
    void theEc2InstanceRoleCanDecryptWithTheSecretsKey() {
        Template template = synthesize(context("PRODUCTION"), RuntimeType.EC2);

        String keyId = secretsKeyIds(template).get(0);
        boolean grantsDecrypt = template.findResources("AWS::IAM::Policy").values().stream()
            .map(String::valueOf)
            .anyMatch(policy -> policy.contains("kms:Decrypt") && policy.contains(keyId));
        assertTrue(grantsDecrypt, "no instance role policy allows kms:Decrypt on " + keyId);
    }

    /** The admin password application, also declaring a license key variable. */
    private static final class LicensedPostgresSpec extends PostgreSQLApplicationSpec {
        @Override
        public String licenseKeySecretEnvVar() {
            return "TEST_LICENSE_KEY";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theLicenseKeySecretIsEncryptedAndExemptFromRotation() {
        Map<String, Object> context = context("PRODUCTION");
        context.put("managerLicenseKey", "license-value");

        Template template = DeploymentSynth.synthesize(context, new LicensedPostgresSpec(), RuntimeType.FARGATE);

        List<Map<String, Object>> secrets = template.findResources(SECRET).entrySet().stream()
            .filter(entry -> entry.getKey().contains("LicenseKeySecret"))
            .map(entry -> (Map<String, Object>) entry.getValue())
            .collect(Collectors.toList());
        assertEquals(1, secrets.size(), "expected one license key secret");
        assertTrue(properties(secrets.get(0)).containsKey("KmsKeyId"));
        assertEquals("exempt", ((Map<String, Object>) secrets.get(0).get("Metadata")).get("CloudForgeRotation"));
    }
}
