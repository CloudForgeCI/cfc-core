// Copyright (c) CloudForgeCI
// SPDX-License-Identifier: Apache-2.0

package com.cloudforgeci.core.api;

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

    //@Test
    void enablingSslRequiresFqdnOrDomain() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("variant", "ec2-domain"); // triggers enableDomainAndSsl
        // No fqdn and no domain -> should fail
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> fromMap(m));
        assertTrue(ex.getMessage().contains("enableDomainAndSsl=true"));
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
        assertTrue(cause.getMessage().contains("requires HTTPS listener"));
    }
}
