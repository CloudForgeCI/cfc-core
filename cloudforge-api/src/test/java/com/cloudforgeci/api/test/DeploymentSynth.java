package com.cloudforgeci.api.test;

import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.iam.IAMProfileMapper;
import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import com.cloudforgeci.api.compute.ApplicationFactory;
import com.cloudforgeci.api.core.DeploymentContext;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.assertions.TemplateParsingOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes a PRODUCTION Fargate deployment through {@link ApplicationFactory}, the entry point
 * a deployment uses, so the template shows what the security profile creates and the compliance
 * rules evaluate.
 *
 * <p>A blocking compliance rule fails {@code Template.fromStack} with
 * {@code [stack/SystemContext] <rule>: ...} lines, which {@link #blockingRules} parses.</p>
 */
public final class DeploymentSynth {

    /** Frameworks a deployment can select. */
    public static final List<String> FRAMEWORKS =
        List.of("pci-dss", "hipaa", "soc2", "gdpr", "fedramp", "fedramp-high");

    private static final Pattern RULE_LINE = Pattern.compile("/SystemContext\\] ([^\\n]*?): ");

    private DeploymentSynth() {
    }

    /**
     * A deployment context that satisfies the blocking rules of one framework: a private network,
     * HTTPS, MFA-backed OIDC, and the monitoring controls PRODUCTION expects. It has no custom
     * domain, so the ALB certificate comes from a Private CA.
     */
    public static Map<String, Object> baseline(String framework) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("stackName", "rules-" + framework);
        context.put("region", "us-east-1");
        context.put("runtime", "FARGATE");
        context.put("topology", "APPLICATION_SERVICE");
        context.put("securityProfile", "PRODUCTION");
        context.put("applicationId", "jenkins");
        context.put("networkMode", "private-with-nat");
        context.put("enableSsl", true);
        context.put("authMode", "alb-oidc");
        context.put("cognitoAutoProvision", true);
        context.put("cognitoDomainPrefix", "rules-" + framework);
        context.put("cognitoMfaEnabled", true);
        context.put("wafEnabled", true);
        context.put("guardDutyEnabled", true);
        context.put("createGuardDutyDetector", true);
        context.put("awsConfigEnabled", true);
        context.put("macieEnabled", true);
        context.put("macieAutomatedDiscovery", true);
        context.put("createConfigInfrastructure", false);
        context.put("inspectorEnabled", true);
        context.put("enableFlowlogs", true);
        context.put("complianceFrameworks", framework);
        context.put("complianceMode", "enforce");
        context.put("auditManagerEnabled", true);
        if ("gdpr".equals(framework)) {
            context.put("gdprDataTransferApproved", true);
        }
        if ("fedramp-high".equals(framework)) {
            context.put("securityMonitoringEnabled", true);
        }
        return context;
    }

    /** The baseline with a custom domain and a Route 53 zone created for it. */
    public static Map<String, Object> baselineWithDomain(String framework) {
        Map<String, Object> context = baseline(framework);
        context.put("domain", "example.com");
        context.put("createZone", true);
        return context;
    }

    /** Builds the deployment and synthesizes it; throws when a blocking rule or validation fails. */
    public static Template synthesize(Map<String, Object> context) {
        return synthesize(context, false);
    }

    /**
     * Synthesizes without the assertions library's cyclic-dependency check. That check does work
     * proportional to 2 to the power of the deepest array nesting, so it cannot finish on a
     * template nested as deeply as the legacy lowercase ALB DNS expression.
     */
    public static Template synthesizeWithoutCycleCheck(Map<String, Object> context) {
        return synthesize(context, true);
    }

    /** Synthesizes with a given application and runtime instead of the default Jenkins on Fargate. */
    public static Template synthesize(Map<String, Object> context, ApplicationSpec spec, RuntimeType runtime) {
        return synthesize(context, false, spec, runtime);
    }

    private static Template synthesize(Map<String, Object> context, boolean skipCycleCheck) {
        return synthesize(context, skipCycleCheck, new JenkinsApplicationSpec(), RuntimeType.FARGATE);
    }

    private static Template synthesize(Map<String, Object> context, boolean skipCycleCheck,
                                       ApplicationSpec spec, RuntimeType runtime) {
        Stack stack = new Stack(new App(), String.valueOf(context.get("stackName")), StackProps.builder()
            .env(Environment.builder().account("123456789012").region("us-east-1").build())
            .build());
        stack.getNode().setContext("cfc", context);
        DeploymentContext cfc = DeploymentContext.from(stack);
        SecurityProfile profile = SecurityProfile.valueOf(String.valueOf(context.get("securityProfile")).toUpperCase());
        IAMProfile iamProfile = IAMProfileMapper.mapFromSecurity(profile);
        if (runtime == RuntimeType.EC2) {
            ApplicationFactory.createEc2(stack, "TestApp", cfc, profile, iamProfile, spec);
        } else {
            ApplicationFactory.createFargate(stack, "TestApp", cfc, profile, iamProfile, spec);
        }
        return skipCycleCheck
            ? Template.fromStack(stack, TemplateParsingOptions.builder().skipCyclicalDependenciesCheck(true).build())
            : Template.fromStack(stack);
    }

    /**
     * The identifiers of the rules that block this deployment, empty when it synthesizes. A
     * deployment rejected before any rule runs reports one {@code REJECTED:<reason>} entry. Any
     * other failure is rethrown.
     */
    public static Set<String> blockingRules(Map<String, Object> context) {
        try {
            synthesize(context);
            return new TreeSet<>();
        } catch (RuntimeException failure) {
            String message = String.valueOf(failure.getMessage());
            Set<String> rules = new TreeSet<>();
            Matcher line = RULE_LINE.matcher(message);
            while (line.find()) {
                rules.add(line.group(1));
            }
            if (!rules.isEmpty()) {
                return rules;
            }
            if (message.contains("DeploymentContext validation failed")) {
                String[] lines = message.split("\\n");
                return new TreeSet<>(Set.of("REJECTED:" + (lines.length > 2 ? lines[2].trim() : "")));
            }
            throw failure;
        }
    }

    /** The deepest nesting of arrays anywhere in the template. */
    public static int maxArrayDepth(Template template) {
        return depth(template.toJSON(), 0);
    }

    /** The deepest nesting of arrays in a resolved value, such as the result of {@code Stack.resolve}. */
    public static int maxArrayDepth(Object resolved) {
        return depth(resolved, 0);
    }

    private static int depth(Object node, int arrays) {
        int deepest = arrays;
        if (node instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                deepest = Math.max(deepest, depth(child, arrays));
            }
        } else if (node instanceof List<?> list) {
            deepest = arrays + 1;
            for (Object child : list) {
                deepest = Math.max(deepest, depth(child, arrays + 1));
            }
        }
        return deepest;
    }
}
