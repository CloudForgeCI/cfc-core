package com.cloudforge.core.config;

import com.cloudforge.core.enums.AuthMode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code List<String>}-typed {@link ConfigField} (e.g. {@code protectedPaths}) previously fell
 * through to {@code promptString}, which calls {@code field.setValue(config, aRawString)} --
 * {@link ConfigFieldInfo#setValue} does a plain {@code Field.set}, which throws
 * {@code IllegalArgumentException} for a {@code String} against a {@code List}-typed field.
 */
class InteractivePrompterTest {

    private static ConfigFieldInfo protectedPathsField(DeploymentConfig config) {
        return ConfigurationIntrospector.discoverVisibleFields(null, config, "security")
            .stream()
            .filter(f -> f.fieldName().equals("protectedPaths"))
            .findFirst()
            .orElseThrow();
    }

    private static DeploymentConfig authEnabledConfig() {
        DeploymentConfig config = new DeploymentConfig();
        config.authMode = AuthMode.ALB_OIDC;
        return config;
    }

    @Test
    void commaSeparatedInputBecomesAListInsteadOfThrowing() throws Exception {
        DeploymentConfig config = authEnabledConfig();
        InteractivePrompter prompter = new InteractivePrompter(
            new ByteArrayInputStream("/admin/*, /api/*\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(new ByteArrayOutputStream()));

        Method promptStringList = InteractivePrompter.class
            .getDeclaredMethod("promptStringList", ConfigFieldInfo.class, Object.class);
        promptStringList.setAccessible(true);

        assertDoesNotThrow(() -> promptStringList.invoke(prompter, protectedPathsField(config), config));
        assertEquals(List.of("/admin/*", "/api/*"), config.protectedPaths);
    }

    @Test
    void emptyInputLeavesTheListFieldNull() throws Exception {
        DeploymentConfig config = authEnabledConfig();
        InteractivePrompter prompter = new InteractivePrompter(
            new ByteArrayInputStream("\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(new ByteArrayOutputStream()));

        Method promptStringList = InteractivePrompter.class
            .getDeclaredMethod("promptStringList", ConfigFieldInfo.class, Object.class);
        promptStringList.setAccessible(true);

        assertDoesNotThrow(() -> promptStringList.invoke(prompter, protectedPathsField(config), config));
        assertEquals(null, config.protectedPaths);
    }
}
