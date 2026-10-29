package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSettingsTest {

    /**
     * Keys read with {@link RuleSettings#accessorValue} that have no {@code Boolean} accessor on
     * DeploymentContext. The lookup fails for these, so each rule uses its default whatever the
     * user sets. Give a key an accessor, or stop reading it, and remove it from this set.
     */
    private static final Set<String> KEYS_WITHOUT_ACCESSOR = Set.of(
        "cloudfrontMinTls12", "cloudfrontLogging", "apiGatewayXrayTracing",
        "terminationProtection", "eksSecretsEncryption", "eksPrivateEndpoint", "eksControlPlaneLogging",
        "albTls12Policy", "albDeletionProtection",
        "leastPrivilegeReviewed", "rootMfaEnabled", "rootAccessKeysDisabled", "credentialRotationEnabled",
        "unusedCredentialsCheckEnabled",
        "lambdaEnabled", "lambdaInVpc", "lambdaEnvEncryption", "lambdaXrayTracing", "lambdaDeadLetterQueue",
        "kinesisEncryption", "secretsRotationEnabled", "secretsKmsEncryption", "deadLetterQueueEnabled");

    private static SystemContext context(Map<String, Object> extra) {
        Map<String, Object> cfc = new java.util.HashMap<>(extra);
        cfc.put("stackName", "RuleSettings");
        cfc.put("region", "us-east-1");
        TestInfrastructureBuilder builder = new TestInfrastructureBuilder(
            "RuleSettings", SecurityProfile.PRODUCTION, RuntimeType.FARGATE, cfc);
        builder.createMinimalInfrastructure();
        return builder.getSystemContext();
    }

    @Test
    void contextValueReadsTheRawMapAndFallsBackToTheDefault() {
        SystemContext ctx = context(Map.of("someRuleFlag", "true", "anotherRuleFlag", "false"));

        assertTrue(RuleSettings.contextValue(ctx, "someRuleFlag", false));
        assertFalse(RuleSettings.contextValue(ctx, "anotherRuleFlag", true));
        assertTrue(RuleSettings.contextValue(ctx, "missingRuleFlag", true));
        assertFalse(RuleSettings.contextValue(ctx, "missingRuleFlag", false));
    }

    @Test
    void accessorValueReadsTheTypedAccessor() {
        SystemContext ctx = context(Map.of("inspectorEc2Scanning", false));

        assertFalse(RuleSettings.accessorValue(ctx, "inspectorEc2Scanning", true));
    }

    @Test
    void accessorValueUsesTheDefaultWhenTheSettingIsUnset() {
        SystemContext ctx = context(Map.of());

        assertTrue(RuleSettings.accessorValue(ctx, "imdsv2Required", true));
        assertFalse(RuleSettings.accessorValue(ctx, "imdsv2Required", false));
    }

    @Test
    void accessorValueUsesTheDefaultForAKeyWithNoAccessor() {
        SystemContext ctx = context(Map.of("rootMfaEnabled", true));

        assertFalse(RuleSettings.accessorValue(ctx, "rootMfaEnabled", false),
            "a key with no accessor is not read, so the user's value is ignored");
        assertTrue(RuleSettings.accessorValue(ctx, "rootMfaEnabled", true));
    }

    @Test
    void everyKeyWithoutAnAccessorIsListed() throws IOException {
        String contextSource = Files.readString(
            Path.of("src/main/java/com/cloudforgeci/api/core/DeploymentContext.java"));
        Set<String> booleanAccessors = new TreeSet<>();
        Matcher accessor = Pattern.compile("public\\s+Boolean\\s+(\\w+)\\(\\)").matcher(contextSource);
        while (accessor.find()) {
            booleanAccessors.add(accessor.group(1));
        }

        Set<String> withoutAccessor = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/cloudforgeci/api/core/rules"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher read = Pattern.compile("RuleSettings\\.accessorValue\\(ctx,\\s*\"(\\w+)\"")
                    .matcher(Files.readString(file));
                while (read.find()) {
                    if (!booleanAccessors.contains(read.group(1))) {
                        withoutAccessor.add(read.group(1));
                    }
                }
            }
        }

        assertEquals(new TreeSet<>(KEYS_WITHOUT_ACCESSOR), withoutAccessor,
            "keys read through accessorValue that have no Boolean accessor on DeploymentContext");
    }
}
