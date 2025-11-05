package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
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

    public WafFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        LOG.info("Creating WAF WebACL for security profile: " + ctx.security);

        // Create WAF WebACL if enabled for this security profile
        if (config.isWafEnabled()) {
            createWafWebAcl();
        } else {
            LOG.info("WAF disabled for security profile: " + ctx.security);
        }

        LOG.info("WAF resources created successfully for profile: " + ctx.security);
    }

    /**
     * Create AWS WAF WebACL with managed rule groups.
     */
    private void createWafWebAcl() {
        LOG.info("Creating WAF WebACL for web application protection");

        // Create list of managed rule groups
        List<Object> rules = new ArrayList<>();
        int priority = 0;

        // AWS Managed Rules - Common Rule Set (protects against common threats)
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesCommonRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesCommonRuleSet"
        ));

        // AWS Managed Rules - Known Bad Inputs (protects against known malicious inputs)
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesKnownBadInputsRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesKnownBadInputsRuleSet"
        ));

        // AWS Managed Rules - SQL Injection (protects against SQL injection attacks)
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesSQLiRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesSQLiRuleSet"
        ));

        // AWS Managed Rules - Linux Operating System (protects against Linux-specific vulnerabilities)
        rules.add(createManagedRuleGroupStatement(
            "AWS-AWSManagedRulesLinuxRuleSet",
            priority++,
            "AWS",
            "AWSManagedRulesLinuxRuleSet"
        ));

        // Create WAF WebACL
        CfnWebACL webAcl = CfnWebACL.Builder.create(this, "WafWebACL")
                .scope("REGIONAL")  // REGIONAL for ALB (CLOUDFRONT for CloudFront distributions)
                .defaultAction(CfnWebACL.DefaultActionProperty.builder()
                        .allow(CfnWebACL.AllowActionProperty.builder().build())  // Allow by default, block specific threats
                        .build())
                .rules(rules)
                .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                        .cloudWatchMetricsEnabled(true)
                        .metricName("jenkins-waf-" + ctx.stackName.toLowerCase())
                        .sampledRequestsEnabled(true)
                        .build())
                .name("jenkins-waf-" + ctx.stackName.toLowerCase())
                .description("WAF WebACL for Jenkins ALB - " + ctx.security.name())
                .build();

        ctx.wafWebAcl.set(webAcl);

        // Associate WAF WebACL with ALB
        // This needs to wait for both WebACL and ALB to be available
        if (ctx.alb.get().isPresent()) {
            String albArn = ctx.alb.get().orElseThrow().getLoadBalancerArn();

            CfnWebACLAssociation.Builder.create(this, "WafAlbAssociation")
                    .resourceArn(albArn)
                    .webAclArn(webAcl.getAttrArn())
                    .build();

            LOG.info("WAF WebACL associated with ALB: " + albArn);
        } else {
            LOG.warning("ALB not available yet - WAF association will be created when ALB is ready");
        }

        LOG.info("WAF WebACL created: " + webAcl.getAttrArn());
    }

    /**
     * Create a managed rule group statement for WAF.
     */
    private Map<String, Object> createManagedRuleGroupStatement(
            String name,
            int priority,
            String vendorName,
            String ruleGroupName
    ) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("name", name);
        rule.put("priority", priority);

        Map<String, Object> statement = new HashMap<>();
        Map<String, Object> managedRuleGroup = new HashMap<>();
        managedRuleGroup.put("vendorName", vendorName);
        managedRuleGroup.put("name", ruleGroupName);
        statement.put("managedRuleGroupStatement", managedRuleGroup);

        rule.put("statement", statement);

        Map<String, Object> overrideAction = new HashMap<>();
        overrideAction.put("none", new HashMap<>());
        rule.put("overrideAction", overrideAction);

        Map<String, Object> visibilityConfig = new HashMap<>();
        visibilityConfig.put("cloudWatchMetricsEnabled", true);
        visibilityConfig.put("metricName", name);
        visibilityConfig.put("sampledRequestsEnabled", true);
        rule.put("visibilityConfig", visibilityConfig);

        return rule;
    }
}
