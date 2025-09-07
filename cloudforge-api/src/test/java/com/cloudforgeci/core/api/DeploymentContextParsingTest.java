// Copyright (c) CloudForgeCI
// SPDX-License-Identifier: Apache-2.0

package com.cloudforgeci.core.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeploymentContextParsingTest {
    private DeploymentContext fromMap(Map<String,Object> m) throws Exception {
        Constructor<DeploymentContext> ctor = DeploymentContext.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(m);
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "1", "yes", "TRUE" })
    void booleanParsingTrueVariants(String v) throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("wafEnabled", v);
        m.put("cloudfront", v);
        DeploymentContext ctx = fromMap(m);
        assertTrue(ctx.wafEnabled());
        assertTrue(ctx.cloudfrontEnabled());
    }

    @Test
    void cpuAndMemoryParseFromStrings() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("cpu", "512");
        m.put("memory", "3072");
        DeploymentContext ctx = fromMap(m);
        assertEquals(512, ctx.cpu());
        assertEquals(3072, ctx.memory());
    }

    @Test
    void invalidOneOfFallsBackToDefault() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("lbType", "alb");
        m.put("networkMode", "public-no-nat");
        DeploymentContext ctx = fromMap(m);
        assertEquals("alb", ctx.lbType());
        assertEquals("public-no-nat", ctx.networkMode());
    }
}
