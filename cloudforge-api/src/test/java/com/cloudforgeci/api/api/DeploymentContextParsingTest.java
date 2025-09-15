package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


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
        DeploymentContext cfc = fromMap(m);
        assertTrue(cfc.wafEnabled());
        assertTrue(cfc.cloudfrontEnabled());
    }

    @Test
    void cpuAndMemoryParseFromStrings() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("cpu", "512");
        m.put("memory", "3072");
        DeploymentContext cfc = fromMap(m);
        assertEquals(512, cfc.cpu());
        assertEquals(3072, cfc.memory());
    }

    @Test
    void invalidOneOfFallsBackToDefault() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("lbType", "alb");
        m.put("networkMode", "public-no-nat");
        DeploymentContext cfc = fromMap(m);
        assertEquals("alb", cfc.lbType());
        assertEquals("public-no-nat", cfc.networkMode());
    }
}
