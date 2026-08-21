package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforge.core.enums.LoadBalancerType;
import com.cloudforge.core.enums.NetworkMode;
import com.cloudforge.core.enums.TopologyType;
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
    // Note: "1" and "0" NOT supported to avoid ambiguity with integer values
    @ValueSource(strings = { "true", "yes", "on", "TRUE" })
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
    void validEnumValuesParsed() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("lbType", "alb");
        m.put("networkMode", "public-no-nat");  // legacy alias for PUBLIC
        DeploymentContext cfc = fromMap(m);
        assertEquals(LoadBalancerType.ALB, cfc.lbType());
        assertEquals(NetworkMode.PUBLIC, cfc.networkMode());
    }

    /** Regression: {@code topology: "cms-service"} is an advertised, documented value (see
     *  DeploymentContext's own class javadoc and parseTopology's error message) — real deployment
     *  contexts from JSON/CLI must actually resolve it, not just the {@code TopologyType} enum in
     *  isolation. */
    @Test
    void cmsServiceTopologyParsedFromRawContextString() throws Exception {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("topology", "cms-service");
        DeploymentContext cfc = fromMap(m);
        assertEquals(TopologyType.CMS_SERVICE, cfc.topology());
    }
}
