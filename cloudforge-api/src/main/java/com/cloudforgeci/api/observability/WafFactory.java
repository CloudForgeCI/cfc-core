package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.services.wafv2.CfnWebACL;
import software.amazon.awscdk.services.wafv2.CfnWebACLAssociation;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for creating AWS WAF WebACL resources.
 * Creates Web Application Firewall protection for Application Load Balancers.
 */
public class WafFactory extends BaseFactory {

    private static final Logger LOG = Logger.getLogger(WafFactory.class.getName());

    @SystemContext("security")
    private SecurityProfile security;

    @SystemContext("stackName")
    private String stackName;

    @SystemContext("applicationSpec")
    private ApplicationSpec applicationSpec;

    public WafFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("Creating WAF WebACL for security profile: " + security);

        if (config.isWafEnabled()) {
            createWafWebAcl();
        } else {
            LOG.info("WAF disabled for security profile: " + security);
        }

        LOG.info("WAF resources created successfully for profile: " + security);
    }

    /**
     * Create AWS WAF WebACL with managed rule groups for Jenkins.
     */
    private void createWafWebAcl() {
        LOG.info("Creating WAF WebACL for web application protection");

        // Create list of managed rule groups
        List<Object> rules = new ArrayList<>();
        int priority = 0;

        List<String> knownBadInputsExclusions = new ArrayList<>();
        knownBadInputsExclusions.add("Host_localhost_HEADER");          // Jenkins may run on localhost in dev
        knownBadInputsExclusions.add("JavaDeserializationRCE_BODY");    // Jenkins serialization data
        knownBadInputsExclusions.add("JavaDeserializationRCE_QUERYSTRING"); // Jenkins serialization in URLs
        knownBadInputsExclusions.add("JavaDeserializationRCE_HEADER");  // Jenkins remoting headers
        knownBadInputsExclusions.add("JavaDeserializationRCE_URIPATH"); // Jenkins remoting endpoints

        // AWS Managed Rules - Known Bad Inputs (protects against known malicious inputs)
        // Excludes localhost header check for development/testing
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesKnownBadInputsRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesKnownBadInputsRuleSet",
            knownBadInputsExclusions
        ));

        // AWS Managed Rules - SQL Injection (protects against SQL injection attacks)
        // Exclude SQLi_BODY for Jenkins login - form data can trigger false positives
        List<String> sqliExclusions = new ArrayList<>();
        sqliExclusions.add("SQLi_BODY");              // Jenkins login forms can trigger this
        sqliExclusions.add("SQLi_QUERYARGUMENTS");    // Jenkins query parameters
        sqliExclusions.add("SQLi_COOKIE");            // Jenkins session cookies

        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesSQLiRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesSQLiRuleSet",
            sqliExclusions
        ));

        // AWS Managed Rules - Linux Operating System (protects against Linux-specific vulnerabilities)
        // No exclusions - Jenkins shouldn't trigger these rules
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesLinuxRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesLinuxRuleSet",
            null
        ));

        // Create WAF WebACL
        String appId = applicationSpec != null ? applicationSpec.applicationId() : "app";

        CfnWebACL webAcl = CfnWebACL.Builder.create(this, "WafWebACL")
                .scope("REGIONAL")  // REGIONAL for ALB (CLOUDFRONT for CloudFront distributions)
                .defaultAction(CfnWebACL.DefaultActionProperty.builder()
                        .allow(CfnWebACL.AllowActionProperty.builder().build())  // Allow by default, block specific threats
                        .build())
                .rules(rules)
                .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                        .cloudWatchMetricsEnabled(true)
                        .metricName(appId + "-waf-" + stackName.toLowerCase())
                        .sampledRequestsEnabled(true)
                        .build())
                .name(appId + "-waf-" + stackName.toLowerCase())
                .description("WAF WebACL for " + appId + " ALB - " + security.name())
                .build();

        ctx.wafWebAcl.set(webAcl);

        // Associate WAF WebACL with ALB
        // The ALB must be available before WafFactory.create() is called
        // This is ensured by SecurityRules calling this factory AFTER infrastructure creation
        if (!ctx.alb.get().isPresent()) {
            throw new IllegalStateException("ALB must be created before WafFactory - check factory orchestration order");
        }

        String albArn = ctx.alb.get().orElseThrow().getLoadBalancerArn();

        CfnWebACLAssociation.Builder.create(this, "WafAlbAssociation")
                .resourceArn(albArn)
                .webAclArn(webAcl.getAttrArn())
                .build();

        LOG.info("WAF WebACL created: " + webAcl.getAttrArn());
        LOG.info("WAF WebACL associated with ALB: " + albArn);
    }

    /**
     * Create a managed rule group statement for WAF with optional rule exclusions.
     *
     * @param name The name of the rule
     * @param priority The priority of the rule
     * @param vendorName The vendor name (e.g., "AWS")
     * @param ruleGroupName The name of the managed rule group
     * @param excludedRules List of specific rule names to exclude from this rule group
     */
    private Map<String, Object> createManagedRuleGroupStatement(
            String name,
            int priority,
            String vendorName,
            String ruleGroupName,
            List<String> excludedRules
    ) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", name);
        rule.put("priority", priority);

        Map<String, Object> statement = new HashMap<>();
        Map<String, Object> managedRuleGroup = new HashMap<>();
        managedRuleGroup.put("vendorName", vendorName);
        managedRuleGroup.put("name", ruleGroupName);

        // Add excluded rules if provided
        if (excludedRules != null && !excludedRules.isEmpty()) {
            List<Map<String, String>> excludedRulesList = new ArrayList<>();
            for (String ruleName : excludedRules) {
                Map<String, String> excludedRule = new HashMap<>();
                excludedRule.put("name", ruleName);
                excludedRulesList.add(excludedRule);
            }
            managedRuleGroup.put("excludedRules", excludedRulesList);
        }

        statement.put("managedRuleGroupStatement", managedRuleGroup);

        rule.put("statement", statement);

        // Use overrideAction: none to allow managed rules to BLOCK threats
        // The excludedRules parameter above ensures Jenkins-specific false positives are excluded
        // This provides actual security protection while preventing 403s on legitimate Jenkins traffic
        Map<String, Object> overrideAction = new HashMap<>();
        overrideAction.put("none", new HashMap<>()); // BLOCK mode: enforce all rules except exclusions
        rule.put("overrideAction", overrideAction);

        Map<String, Object> visibilityConfig = new HashMap<>();
        visibilityConfig.put("cloudWatchMetricsEnabled", true);
        visibilityConfig.put("metricName", name);
        visibilityConfig.put("sampledRequestsEnabled", true);
        rule.put("visibilityConfig", visibilityConfig);

        return rule;
    }
}
