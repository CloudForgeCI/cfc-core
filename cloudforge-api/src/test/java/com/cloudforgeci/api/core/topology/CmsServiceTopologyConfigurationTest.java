package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.application.cms.WordPressApplicationSpec;
import com.cloudforgeci.api.application.cms.DrupalApplicationSpec;
import com.cloudforgeci.api.application.cms.MagentoApplicationSpec;
import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        ctx.put("applicationId", "wordpress");
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

    /** {@code rules(ctx)} being non-empty (above) never confirms the CMS-specific validations
     *  actually fire — a rule that always returns {@code List.of()} would still pass that check.
     *  These four run every rule against a context deliberately built to violate one of them, and
     *  assert the real error surfaces. */
    private static List<String> checkAll(SystemContext ctx) {
        List<String> errors = new ArrayList<>();
        for (Rule rule : new CmsServiceTopologyConfiguration().rules(ctx)) {
            errors.addAll(rule.check(ctx));
        }
        return errors;
    }

    @Test
    void rulesFlagUnknownApplication() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("applicationId", "not-a-real-cms-platform");
        SystemContext ctx = makeContext(app, "CmsRulesUnknownApp", SecurityProfile.DEV,
            RuntimeType.FARGATE, extra);
        List<String> errors = checkAll(ctx);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown application")),
            "expected an 'unknown application' error, got: " + errors);
    }

    /** alb-oidc-without-SSL is actually caught one layer earlier than this topology's own rule —
     *  {@code DeploymentContext}'s own construction-time validation rejects it before {@code
     *  SystemContext.start} (and therefore {@code rules()}) is ever reached, so this topology's
     *  own "authMode = alb-oidc requires enableSsl" rule is unreachable for this exact case in
     *  practice. Asserting the real, earlier failure is the more accurate end-to-end test than
     *  assuming the topology rule itself fires. */
    @Test
    void albOidcWithoutSslFailsAtDeploymentContextConstruction() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("authMode", "alb-oidc");
        extra.put("enableSsl", false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> makeContext(app, "CmsRulesAlbOidcNoSsl", SecurityProfile.DEV, RuntimeType.FARGATE, extra));
        assertTrue(e.getMessage().contains("alb-oidc requires HTTPS"), e.getMessage());
    }

    /** {@code ctx.sslEnabled}/{@code ctx.fqdn} are {@code SystemContext} slots populated by later
     *  wiring steps (security-profile/domain factories), not by {@code SystemContext.start}
     *  itself — calling {@code rules(ctx)} directly, in isolation, the way this whole test class
     *  does never sees them populated from the raw context map at all. Set them directly to
     *  isolate testing the rule's own logic from that wiring, the same reasoning behind {@link
     *  #rulesFlagAsgOnFargate}'s raw-type {@code ctx.asg} set. */
    @Test
    void rulesFlagSslWithoutFqdn() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsRulesSslNoFqdn", SecurityProfile.DEV,
            RuntimeType.FARGATE, null);
        ctx.sslEnabled.set(true);
        // ctx.fqdn/subdomain/domain left unset -- neither an explicit fqdn nor a
        // subdomain+domain pair to compute one from.
        List<String> errors = checkAll(ctx);
        assertTrue(errors.stream().anyMatch(e -> e.contains("enableSsl = true requires fqdn")),
            "expected an SSL/FQDN error, got: " + errors);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rulesFlagAsgOnFargate() {
        App app = new App();
        SystemContext ctx = makeContext(app, "CmsRulesAsgOnFargate", SecurityProfile.DEV,
            RuntimeType.FARGATE, null);
        // forbid("AutoScalingGroup", x -> x.asg) only checks presence (see RuleKit#forbid), not
        // the real construct type -- a raw-type set() avoids standing up a full VPC/ASG just to
        // populate the slot.
        ((com.cloudforgeci.api.core.Slot) ctx.asg).set(new Object());
        List<String> errors = checkAll(ctx);
        assertTrue(errors.stream().anyMatch(e -> e.contains("forbidden: AutoScalingGroup")),
            "expected a forbidden-ASG-on-Fargate error, got: " + errors);
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
        extra.put("applicationId", "drupal");
        SystemContext ctx = makeContext(app, "CmsWireDrupal", SecurityProfile.DEV,
            RuntimeType.FARGATE, extra);
        ctx.applicationSpec.set(new DrupalApplicationSpec());
        assertDoesNotThrow(() -> new CmsServiceTopologyConfiguration().wire(ctx));
    }

    @Test
    void wireWithMagentoApplication() {
        App app = new App();
        Map<String, Object> extra = new HashMap<>();
        extra.put("applicationId", "magento");
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
