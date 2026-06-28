package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.application.cms.WordPressApplicationSpec;
import com.cloudforgeci.api.application.cms.DrupalApplicationSpec;
import com.cloudforgeci.api.application.cms.MagentoApplicationSpec;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CmsServiceTopologyConfigurationTest {

    private Stack createTestStack(App app, String stackName, SecurityProfile profile,
                                   Map<String, Object> extra) {
        Stack stack = new Stack(app, stackName);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("stackName", stackName);
        ctx.put("securityProfile", profile.name());
        ctx.put("domain", "example.com");
        ctx.put("application", "wordpress");
        if (extra != null) ctx.putAll(extra);
        stack.getNode().setContext("cfc", ctx);
        return stack;
    }

    private SystemContext makeContext(App app, String name, SecurityProfile profile,
                                      RuntimeType runtime, Map<String, Object> extra) {
        Stack stack = createTestStack(app, name, profile, extra);
        DeploymentContext cfc = DeploymentContext.from(stack);
        IAMProfile iam = IAMProfileMapper.mapFromSecurity(profile);
        return SystemContext.start(stack, TopologyType.CMS_SERVICE, runtime, profile, iam, cfc);
    }

    @Test
    void kindIsCmsService() {
        assertEquals(TopologyType.CMS_SERVICE, new CmsServiceTopologyConfiguration().kind());
    }

    @Test
    void idIsExpected() {
        assertEquals("topology:CMS_SERVICE", new CmsServiceTopologyConfiguration().id());
    }

    @Test
    void rulesWithFargateDevAreNonEmpty() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsTopoFargateDev", SecurityProfile.DEV,
            RuntimeType.FARGATE, null);
        var rules = new CmsServiceTopologyConfiguration().rules(ctx);
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
    }

    @Test
    void rulesWithEc2DevAreNonEmpty() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsTopoEc2Dev", SecurityProfile.DEV,
            RuntimeType.EC2, null);
        var rules = new CmsServiceTopologyConfiguration().rules(ctx);
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
    }

    @Test
    void wireWithFargateDev() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsWireFargateDev", SecurityProfile.DEV,
            RuntimeType.FARGATE, null);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithEc2Dev() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsWireEc2Dev", SecurityProfile.DEV,
            RuntimeType.EC2, null);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithFargateStaging() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsWireFargateStaging", SecurityProfile.STAGING,
            RuntimeType.FARGATE, null);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithFargateProduction() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsWireFargateProd", SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, null);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithSslAndSubdomain() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("enableSsl", true);
        extra.put("subdomain", "blog");
        SystemContext ctx = makeContext(app, "CmsWireSslSubdomain", SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, extra);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithAutoscaling() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("minInstanceCapacity", 1);
        extra.put("maxInstanceCapacity", 4);
        SystemContext ctx = makeContext(app, "CmsWireAutoscale", SecurityProfile.STAGING,
            RuntimeType.FARGATE, extra);
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithDrupalApplication() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("application", "drupal");
        SystemContext ctx = makeContext(app, "CmsWireDrupal", SecurityProfile.DEV,
            RuntimeType.FARGATE, extra);
        ctx.applicationSpec.set(new DrupalApplicationSpec());
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithMagentoApplication() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("application", "magento");
        SystemContext ctx = makeContext(app, "CmsWireMagento", SecurityProfile.PRODUCTION,
            RuntimeType.FARGATE, extra);
        ctx.applicationSpec.set(new MagentoApplicationSpec());
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithWordPressSpecPreset() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsWireWordPress", SecurityProfile.DEV,
            RuntimeType.FARGATE, null);
        ctx.applicationSpec.set(new WordPressApplicationSpec());
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }
}
