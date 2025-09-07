// Copyright (c) CloudForgeCI
// SPDX-License-Identifier: Apache-2.0

package com.cloudforgeci.core.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import software.amazon.awscdk.App;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
        DeploymentContext ctx = fromMap(new LinkedHashMap<>());
        assertEquals("public", ctx.tier());
        assertEquals("ec2", ctx.variant());
        assertEquals("dev", ctx.env());
        assertEquals("jenkins", ctx.subdomain());
        assertNull(ctx.domain());
        assertNull(ctx.fqdn());
        assertEquals("public-no-nat", ctx.networkMode());
        assertFalse(ctx.wafEnabled());
        assertFalse(ctx.cloudfrontEnabled());
        assertEquals("alb", ctx.lbType());
        assertEquals("none", ctx.authMode());
        assertEquals(1024, ctx.cpu());
        assertEquals(2048, ctx.memory());
        assertFalse(ctx.enableDomainAndSsl());
    }

    @Test
    void fqdnComposedFromSubdomainAndDomain() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("domain", "example.com");
        m.put("subdomain", "ci");
        DeploymentContext ctx = fromMap(m);
        assertEquals("ci.example.com", ctx.fqdn());
    }

    @Test
    void explicitFqdnBeatsPieces() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("domain", "example.com");
        m.put("subdomain", "ci");
        m.put("fqdn", "jenkins.example.org");
        DeploymentContext ctx = fromMap(m);
        assertEquals("jenkins.example.org", ctx.fqdn());
    }

    @Test
    void variantContainingDomainEnablesSsl() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("variant", "fargate-domain");
        m.put("domain", "example.org");
        DeploymentContext ctx = fromMap(m);

        assertTrue(ctx.enableDomainAndSsl());
    }
}
