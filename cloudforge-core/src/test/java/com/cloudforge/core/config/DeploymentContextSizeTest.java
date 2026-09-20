package com.cloudforge.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a fully-populated {@link DeploymentConfig} (every field set, not just the
 * handful a typical deploy uses) fits inside a single AWS Systems Manager Standard parameter
 * (4,096 bytes), so deployment-context can be persisted alongside a stack rather than only in
 * Manager's local file storage, which doesn't survive a container restart or a different Manager
 * instance taking over the same account.
 *
 * <p>Populates every declared field via reflection with a realistic, plausible value (a plausible
 * stack/domain/application name — not an artificially padded worst-case string) rather than
 * hand-listing all of {@link DeploymentConfig}'s fields, so this test doesn't stop
 * covering a field added later. Compact (non-pretty-printed) JSON, matching what would be
 * written to a parameter.
 */
class DeploymentContextSizeTest {

    private static final int SSM_STANDARD_PARAMETER_LIMIT_BYTES = 4096;

    @Test
    void fullyPopulatedDeploymentConfigFitsInAnSsmStandardParameter() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        populateEveryField(config);

        String json = new ObjectMapper().writeValueAsString(config.toContextMap());
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;

        System.out.println("Fully-populated DeploymentConfig JSON size: " + bytes + " bytes "
            + "(SSM Standard parameter limit: " + SSM_STANDARD_PARAMETER_LIMIT_BYTES + " bytes)");

        assertTrue(bytes <= SSM_STANDARD_PARAMETER_LIMIT_BYTES,
            "Fully-populated deployment context is " + bytes + " bytes, over the "
                + SSM_STANDARD_PARAMETER_LIMIT_BYTES + "-byte SSM Standard parameter limit");
    }

    /** Sets every declared instance field on {@code config} to a realistic value based on its
     *  declared type -- string fields get a plausible example (a realistic stack name,
     *  domain, ARN, etc., picked per field where a generic one wouldn't be representative), not
     *  padding. Static/final fields are skipped. */
    private static void populateEveryField(DeploymentConfig config) throws IllegalAccessException {
        for (Field field : DeploymentConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = valueFor(field);
            if (value != null) {
                field.set(config, value);
            }
        }
    }

    private static Object valueFor(Field field) {
        Class<?> type = field.getType();
        String name = field.getName();

        if (type == String.class) {
            return realisticStringFor(name);
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.TRUE;
        }
        if (type == Integer.class || type == int.class) {
            return 5;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        if (List.class.isAssignableFrom(type)) {
            ParameterizedType generic = field.getGenericType() instanceof ParameterizedType pt ? pt : null;
            Class<?> elementType = generic != null ? (Class<?>) generic.getActualTypeArguments()[0] : String.class;
            if (elementType.isEnum()) {
                return List.of(elementType.getEnumConstants());
            }
            // The one List<String> field in DeploymentConfig today -- complianceFrameworks is
            // realistically all four real framework slugs on a fully-compliant deploy.
            return List.of("soc2", "hipaa", "pci-dss", "gdpr");
        }
        // ApplicationSpec and anything else not naturally JSON-serializable as config data --
        // left null, matching what a real deployment-context.json would have (this field isn't
        // hand-authored, it's resolved at runtime from applicationId).
        return null;
    }

    /** One example value per field name where a generic one wouldn't be representative
     *  of what deployments set, falling back to a short generic string otherwise. */
    private static String realisticStringFor(String fieldName) {
        return switch (fieldName) {
            case "stackName" -> "CloudForgeManager-Production";
            case "applicationId" -> "cloudforge-manager";
            case "applicationName" -> "CloudForge Manager";
            case "environment" -> "production";
            case "domain" -> "cloudforgeci.com";
            case "subdomain" -> "manager";
            case "fqdn" -> "manager.cloudforgeci.com";
            case "region" -> "us-east-1";
            case "authMode" -> "application-oidc";
            case "cognitoAdminGroupName" -> "ManagerAdmins";
            case "cognitoUserGroupName" -> "ManagerUsers";
            case "cognitoDomainPrefix" -> "cfc-manager";
            case "cognitoInitialAdminEmail" -> "admin@cloudforgeci.com";
            case "complianceMode" -> "ENFORCE";
            case "marketplaceProductCode" -> "abcdefghijklmnopqrstuvwxyz0";
            case "accountId" -> "123456789012";
            case "propertyKey", "displayName", "description" -> "example";
            default -> "example-value";
        };
    }
}
