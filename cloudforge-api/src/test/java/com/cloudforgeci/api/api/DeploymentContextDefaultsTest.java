package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeploymentContextDefaultsTest {
    private DeploymentContext fromMap(Map<String,Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @Test
    void defaultsAreApplied() throws Exception {
        DeploymentContext cfc = fromMap(new LinkedHashMap<>());
        assertEquals("public", cfc.tier());
        assertEquals("FARGATE", cfc.runtime().name());
        assertEquals("dev", cfc.env());
        assertEquals(1, cfc.minInstanceCapacity());
        assertEquals(3, cfc.maxInstanceCapacity());
        assertEquals(60, cfc.cpuTargetUtilization());
        assertEquals(null, cfc.subdomain());
        assertNull(cfc.domain());
        assertNull(cfc.fqdn());
        assertEquals("public-no-nat", cfc.networkMode());
        assertFalse(cfc.wafEnabled());
        assertFalse(cfc.cloudfrontEnabled());
        assertEquals("alb", cfc.lbType());
        assertEquals("none", cfc.authMode());
        assertEquals(1024, cfc.cpu());
        assertEquals(2048, cfc.memory());
        assertFalse(cfc.enableSsl());
    }

    @Test
    void fqdnComposedFromSubdomainAndDomain() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("domain", "example.com");
        m.put("subdomain", "ci");
        DeploymentContext cfc = fromMap(m);
        assertEquals("ci.example.com", cfc.fqdn());
    }

    @Test
    void explicitFqdnBeatsPieces() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("domain", "example.com");
        m.put("subdomain", "ci");
        m.put("fqdn", "jenkins.example.org");
        DeploymentContext cfc = fromMap(m);
        assertEquals("jenkins.example.org", cfc.fqdn());
    }

    @Test
    void runtimeContainingDomainEnablesSsl() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("runtime", "fargate-domain");
        m.put("domain", "example.org");
        DeploymentContext cfc = fromMap(m);

        assertFalse(cfc.enableSsl());
    }
}
