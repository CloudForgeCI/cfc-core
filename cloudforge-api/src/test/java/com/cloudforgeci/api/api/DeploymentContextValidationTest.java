package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeploymentContextValidationTest {
    private DeploymentContext fromMap(Map<String,Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @Test
    void enablingSslWithoutDomainSucceeds() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("enableSsl", true);        // SSL without domain is valid (uses Private CA)
        // no fqdn, no domain -> should succeed (Private CA will be used for ALB DNS)
        assertDoesNotThrow(() -> fromMap(m), "SSL without domain should succeed using Private CA");
    }

    @Test
    void albOidcRequiresSsl() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("authMode", "alb-oidc");

        InvocationTargetException ex =
                assertThrows(InvocationTargetException.class, () -> fromMap(m));

        Throwable cause = ex.getTargetException();
        assertNotNull(cause, "Expected a cause exception");
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertNotNull(cause.getMessage());
        assertTrue(cause.getMessage().contains("requires HTTPS"));
    }
}
