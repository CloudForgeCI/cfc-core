package com.cloudforgeci.api.core.rules;

import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.assertions.Template;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code ConfigurationValidationRules} runs unconditionally ({@code alwaysLoad = true}) whenever a
 * caller installs it directly, independent of the {@code auditManagerEnabled} gate that keeps
 * {@code SecurityRules} from installing it in a normal deployment.
 */
class ConfigurationValidationRulesTest {

    /** Builds a synthesizable test infrastructure fixture for the given context overrides. */
    private TestInfrastructureBuilder builderFor(String stackName, Map<String, Object> context) {
        TestInfrastructureBuilder builder =
            new TestInfrastructureBuilder(stackName, SecurityProfile.DEV, RuntimeType.FARGATE, context);
        builder.createMinimalInfrastructure();
        new ConfigurationValidationRules().install(builder.getSystemContext());
        return builder;
    }

    @Test
    void subdomainWithoutADomainFailsSynthesis() {
        Map<String, Object> context = new HashMap<>();
        context.put("subdomain", "app");

        TestInfrastructureBuilder builder = builderFor("ConfigSubdomainOnly", context);

        assertThrows(Exception.class, () -> Template.fromStack(builder.getStack()));
    }

    @Test
    void subdomainWithADomainSynthesizesCleanly() {
        Map<String, Object> context = new HashMap<>();
        context.put("subdomain", "app");
        context.put("domain", "example.com");

        TestInfrastructureBuilder builder = builderFor("ConfigSubdomainAndDomain", context);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void noSubdomainNeedsNoDomain() {
        TestInfrastructureBuilder builder = builderFor("ConfigNoSubdomain", new HashMap<>());

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void albOidcWithoutSslFailsSynthesis() {
        // DeploymentContext itself rejects this combination before a stack even exists (see
        // DeploymentContext#validateOrThrow), so the exception has to be expected around
        // construction, not just around Template.fromStack.
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "alb-oidc");
        context.put("enableSsl", "false");

        assertThrows(Exception.class, () -> builderFor("ConfigAlbOidcNoSsl", context));
    }

    @Test
    void albOidcWithSslSynthesizesCleanly() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "alb-oidc");
        context.put("enableSsl", "true");
        context.put("fqdn", "app.example.com");

        TestInfrastructureBuilder builder = builderFor("ConfigAlbOidcWithSsl", context);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }

    @Test
    void authModeNoneNeedsNoSsl() {
        Map<String, Object> context = new HashMap<>();
        context.put("authMode", "none");
        context.put("enableSsl", "false");

        TestInfrastructureBuilder builder = builderFor("ConfigNoneAuth", context);

        assertDoesNotThrow(() -> Template.fromStack(builder.getStack()));
    }
}
