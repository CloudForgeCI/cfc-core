import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks each SOC2 rule's claim against the synthesized CloudFormation template.
 *
 * <p>A rule that passes because a profile flag says a control is on proves nothing by itself; this
 * verifier asks whether the resource that implements the control exists, and is configured the way the
 * rule claims. Each result is {@code ruleId, OK|MISMATCH|N/A, detail}. MISMATCH means the rule passed
 * (synthesis succeeded) while the template does not back it up.</p>
 */
public class TemplateVerifier {

    public record Check(String ruleId, String status, String detail) { }

    private final JsonNode resources;
    private final Map<String, List<JsonNode>> byType = new TreeMap<>();
    private final Map<String, JsonNode> byId = new TreeMap<>();
    private final String runtime;
    private final String authMode;
    private final boolean production;
    private final boolean pinnedToOneInstance;
    private final boolean crossRegionVaultConfigured;

    public TemplateVerifier(JsonNode template, String runtime, String authMode, String profile,
                            boolean pinnedToOneInstance, boolean crossRegionVaultConfigured) {
        this.production = "PRODUCTION".equals(profile);
        this.pinnedToOneInstance = pinnedToOneInstance;
        this.crossRegionVaultConfigured = crossRegionVaultConfigured;
        this.resources = template.path("Resources");
        this.runtime = runtime;
        this.authMode = authMode;
        for (Iterator<Map.Entry<String, JsonNode>> it = resources.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            byType.computeIfAbsent(e.getValue().path("Type").asText(), k -> new ArrayList<>()).add(e.getValue());
            byId.put(e.getKey(), e.getValue());
        }
    }

    public List<Check> verify() {
        List<Check> out = new ArrayList<>();
        exists(out, "SOC2-CC6.1-IAM", "AWS::IAM::Role", "IAM roles");
        auth(out);
        efsEncryption(out);
        ebsEncryption(out);
        s3Encryption(out);
        exists(out, "SOC2-CC6.6-VPC", "AWS::EC2::VPC", "VPC");
        securityGroups(out);
        tls(out);
        efsTransit(out);
        waf(out);
        exists(out, "SOC2-CC7.2-CloudTrail", "AWS::CloudTrail::Trail", "CloudTrail trail");
        exists(out, "SOC2-CC8.1-CloudTrail", "AWS::CloudTrail::Trail", "CloudTrail trail");
        exists(out, "SOC2-CC7.2-FlowLogs", "AWS::EC2::FlowLog", "VPC flow log");
        exists(out, "SOC2-CC7.2-Config", "AWS::Config::ConfigRule", "AWS Config rules");
        exists(out, "SOC2-CC8.1-Config", "AWS::Config::ConfigRule", "AWS Config rules");
        logGroups(out);
        cloudTrailDetails(out);
        route53Logging(out);
        imdsv2(out);
        availability(out);
        database(out);
        backup(out);
        privateNetwork(out);
        return production ? out : scopeToProduction(out);
    }

    /** SOC2 rules that only apply to PRODUCTION: a STAGING template is not held to them. */
    private static final Set<String> PRODUCTION_ONLY = Set.of(
        "SOC2-CC6.1-LogEncryption", "SOC2-CC7.2-CloudTrailInsights", "SOC2-CC7.2-Route53QueryLogging",
        "SOC2-CC7.2-AuditLogImmutability", "SOC2-A1.2-MultiAZ", "SOC2-A1.2-AutoScaling", "SOC2-A1.3-Backup",
        "SOC2-A1.3-CrossRegion", "SOC2-A1.2-DatabaseMultiAZ", "SOC2-A1.3-DatabaseDeletionProtection");

    private static List<Check> scopeToProduction(List<Check> checks) {
        List<Check> scoped = new ArrayList<>();
        for (Check c : checks) {
            scoped.add(PRODUCTION_ONLY.contains(c.ruleId())
                ? new Check(c.ruleId(), "N/A", "production-only rule; row is not PRODUCTION")
                : c);
        }
        return scoped;
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private List<JsonNode> of(String type) {
        return byType.getOrDefault(type, List.of());
    }

    private void ok(List<Check> out, String rule, String detail) {
        out.add(new Check(rule, "OK", detail));
    }

    private void mismatch(List<Check> out, String rule, String detail) {
        out.add(new Check(rule, "MISMATCH", detail));
    }

    private void na(List<Check> out, String rule, String detail) {
        out.add(new Check(rule, "N/A", detail));
    }

    private void exists(List<Check> out, String rule, String type, String label) {
        int n = of(type).size();
        if (n > 0) {
            ok(out, rule, n + " x " + label);
        } else {
            mismatch(out, rule, "no " + type + " in the template (" + label + ")");
        }
    }

    private static boolean truthy(JsonNode node) {
        return node != null && (node.asBoolean(false) || "true".equalsIgnoreCase(node.asText()));
    }

    // ---- individual claims -----------------------------------------------------------------------------

    private void auth(List<Check> out) {
        String rule = "SOC2-CC6.2-Auth";
        if ("application-oidc".equals(authMode)) {
            if (!of("AWS::Cognito::UserPoolClient").isEmpty()) {
                ok(out, rule, "Cognito user pool client present for application-oidc");
            } else if (resources.toString().contains("OIDC_ISSUER") || resources.toString().contains("oidcIssuer")) {
                ok(out, rule, "OIDC issuer configured for the application");
            } else {
                mismatch(out, rule, "application-oidc but no Cognito client or OIDC configuration in the template");
            }
        } else if ("alb-oidc".equals(authMode)) {
            String problem = albAuthenticationProblem();
            if (problem == null) {
                ok(out, rule, "ALB listener authenticates anonymous requests before forwarding");
            } else {
                mismatch(out, rule, problem);
            }
        } else {
            mismatch(out, rule, "authMode " + authMode + " provides no authentication");
        }
    }

    /**
     * Returns why the ALB does not require sign-in, or {@code null} when some listener authenticates before it
     * forwards. The authenticate action must run first and must not allow unauthenticated requests through.
     */
    private String albAuthenticationProblem() {
        String problem = "alb-oidc but no authenticate-oidc/cognito listener action in the template";
        List<JsonNode> actionLists = new ArrayList<>();
        for (JsonNode listener : of("AWS::ElasticLoadBalancingV2::Listener")) {
            actionLists.add(listener.path("Properties").path("DefaultActions"));
        }
        for (JsonNode rule : of("AWS::ElasticLoadBalancingV2::ListenerRule")) {
            actionLists.add(rule.path("Properties").path("Actions"));
        }
        for (JsonNode actions : actionLists) {
            int authOrder = Integer.MAX_VALUE;
            int forwardOrder = Integer.MAX_VALUE;
            boolean allowsAnonymous = false;
            boolean found = false;
            for (JsonNode action : actions) {
                String type = action.path("Type").asText();
                int order = action.path("Order").asInt(0);
                if (type.startsWith("authenticate-")) {
                    found = true;
                    authOrder = order;
                    JsonNode config = action.path(type.equals("authenticate-oidc")
                        ? "AuthenticateOidcConfig" : "AuthenticateCognitoConfig");
                    allowsAnonymous = "allow".equals(config.path("OnUnauthenticatedRequest").asText());
                } else if ("forward".equals(type)) {
                    forwardOrder = order;
                }
            }
            if (!found) {
                continue;
            }
            if (allowsAnonymous) {
                problem = "authenticate action has OnUnauthenticatedRequest=allow";
            } else if (authOrder >= forwardOrder) {
                problem = "authenticate action does not run before the forward action";
            } else {
                return null;
            }
        }
        return problem;
    }

    private void efsEncryption(List<Check> out) {
        List<JsonNode> systems = of("AWS::EFS::FileSystem");
        for (String rule : new String[] {"SOC2-CC6.1-Encryption", "SOC2-C1.1-EFS"}) {
            if (systems.isEmpty()) {
                na(out, rule, "no EFS file system");
                continue;
            }
            long bad = systems.stream().filter(s -> !truthy(s.path("Properties").path("Encrypted"))).count();
            if (bad == 0) {
                ok(out, rule, systems.size() + " EFS file system(s) all Encrypted=true");
            } else {
                mismatch(out, rule, bad + " of " + systems.size() + " EFS file system(s) not Encrypted");
            }
        }
    }

    private void ebsEncryption(List<Check> out) {
        String rule = "SOC2-C1.1-EBS";
        if (!"EC2".equals(runtime)) {
            na(out, rule, "Fargate has no EBS volumes");
            return;
        }
        List<JsonNode> templates = of("AWS::EC2::LaunchTemplate");
        if (templates.isEmpty()) {
            mismatch(out, rule, "EC2 runtime but no launch template");
            return;
        }
        for (JsonNode lt : templates) {
            JsonNode mappings = lt.path("Properties").path("LaunchTemplateData").path("BlockDeviceMappings");
            boolean encrypted = false;
            for (JsonNode m : mappings) {
                if (truthy(m.path("Ebs").path("Encrypted"))) {
                    encrypted = true;
                }
            }
            if (!encrypted) {
                mismatch(out, rule, "launch template has no explicitly encrypted EBS volume");
                return;
            }
        }
        ok(out, rule, "launch template EBS volume(s) Encrypted=true");
    }

    private void s3Encryption(List<Check> out) {
        String rule = "SOC2-C1.1-S3";
        List<JsonNode> buckets = of("AWS::S3::Bucket");
        if (buckets.isEmpty()) {
            na(out, rule, "no S3 buckets");
            return;
        }
        long bad = buckets.stream().filter(b -> b.path("Properties").path("BucketEncryption").isMissingNode()).count();
        if (bad == 0) {
            ok(out, rule, buckets.size() + " bucket(s) all have BucketEncryption");
        } else {
            mismatch(out, rule, bad + " of " + buckets.size() + " bucket(s) without BucketEncryption");
        }
    }

    private void securityGroups(List<Check> out) {
        String rule = "SOC2-CC6.6-SG";
        List<JsonNode> groups = of("AWS::EC2::SecurityGroup");
        if (groups.isEmpty()) {
            mismatch(out, rule, "no security groups");
            return;
        }
        Set<String> exposed = new HashSet<>();
        for (JsonNode g : groups) {
            for (JsonNode ingress : g.path("Properties").path("SecurityGroupIngress")) {
                if ("0.0.0.0/0".equals(ingress.path("CidrIp").asText())) {
                    int from = ingress.path("FromPort").asInt(-1);
                    if (from != 80 && from != 443) {
                        exposed.add(String.valueOf(from));
                    }
                }
            }
        }
        if (exposed.isEmpty()) {
            ok(out, rule, groups.size() + " security group(s); world-open ingress limited to 80/443");
        } else {
            mismatch(out, rule, "0.0.0.0/0 open on port(s) " + exposed);
        }
    }

    private void tls(List<Check> out) {
        boolean https = false;
        for (JsonNode listener : of("AWS::ElasticLoadBalancingV2::Listener")) {
            if ("HTTPS".equals(listener.path("Properties").path("Protocol").asText())
                    && listener.path("Properties").has("Certificates")) {
                https = true;
            }
        }
        for (String rule : new String[] {"SOC2-CC6.7-SSL", "SOC2-CC6.7-TLS"}) {
            if (https) {
                ok(out, rule, "HTTPS listener with a certificate");
            } else {
                mismatch(out, rule, "no HTTPS listener with a certificate");
            }
        }
    }

    private void efsTransit(List<Check> out) {
        String rule = "SOC2-CC6.7-EFS";
        if (of("AWS::EFS::FileSystem").isEmpty()) {
            na(out, rule, "no EFS file system");
            return;
        }
        if (!"FARGATE".equals(runtime)) {
            na(out, rule, "EC2 mounts EFS through the guest OS; not visible in the template");
            return;
        }
        for (JsonNode task : of("AWS::ECS::TaskDefinition")) {
            for (JsonNode volume : task.path("Properties").path("Volumes")) {
                JsonNode efs = volume.path("EFSVolumeConfiguration");
                if (!efs.isMissingNode() && !"ENABLED".equals(efs.path("TransitEncryption").asText())) {
                    mismatch(out, rule, "task volume mounts EFS without TransitEncryption=ENABLED");
                    return;
                }
            }
        }
        ok(out, rule, "EFS task volumes use TransitEncryption=ENABLED");
    }

    private void waf(List<Check> out) {
        String rule = "SOC2-CC6.6-WAF";
        if (of("AWS::WAFv2::WebACL").isEmpty()) {
            mismatch(out, rule, "no WAFv2 WebACL");
        } else if (of("AWS::WAFv2::WebACLAssociation").isEmpty()) {
            mismatch(out, rule, "WebACL exists but is not associated with the load balancer");
        } else {
            ok(out, rule, "WebACL associated with the load balancer");
        }
    }

    private void logGroups(List<Check> out) {
        List<JsonNode> groups = of("AWS::Logs::LogGroup");
        boolean retentionResource = !of("Custom::LogRetention").isEmpty();
        if (groups.isEmpty()) {
            na(out, "SOC2-CC7.2-LogRetention", "no log groups");
            na(out, "SOC2-CC6.1-LogEncryption", "no log groups");
            return;
        }
        long short_ = 0;
        long noRetention = 0;
        long noKms = 0;
        List<String> unencrypted = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : byId.entrySet()) {
            if ("AWS::Logs::LogGroup".equals(e.getValue().path("Type").asText())
                    && e.getValue().path("Properties").path("KmsKeyId").isMissingNode()) {
                unencrypted.add(e.getKey());
            }
        }
        for (JsonNode g : groups) {
            JsonNode days = g.path("Properties").path("RetentionInDays");
            if (days.isMissingNode()) {
                noRetention++;
            } else if (days.asInt(0) < 365 && days.asInt(0) != 0) {
                short_++;
            }
            if (g.path("Properties").path("KmsKeyId").isMissingNode()) {
                noKms++;
            }
        }
        if (short_ == 0 && (noRetention == 0 || retentionResource)) {
            ok(out, "SOC2-CC7.2-LogRetention", groups.size() + " log group(s), retention >= 365 days");
        } else {
            mismatch(out, "SOC2-CC7.2-LogRetention",
                short_ + " log group(s) under 365 days, " + noRetention + " with no retention set");
        }
        if (noKms == 0) {
            ok(out, "SOC2-CC6.1-LogEncryption", groups.size() + " log group(s) encrypted with a KMS key");
        } else {
            mismatch(out, "SOC2-CC6.1-LogEncryption", noKms + " of " + groups.size() + " log group(s) without a KMS key: " + unencrypted);
        }
    }

    private void cloudTrailDetails(List<Check> out) {
        List<JsonNode> trails = of("AWS::CloudTrail::Trail");
        if (trails.isEmpty()) {
            mismatch(out, "SOC2-CC7.2-CloudTrailInsights", "no CloudTrail trail");
            mismatch(out, "SOC2-CC7.2-AuditLogImmutability", "no CloudTrail trail");
            return;
        }
        JsonNode props = trails.get(0).path("Properties");
        if (props.path("InsightSelectors").isArray() && props.path("InsightSelectors").size() > 0) {
            ok(out, "SOC2-CC7.2-CloudTrailInsights", "trail has insight selectors");
        } else {
            mismatch(out, "SOC2-CC7.2-CloudTrailInsights", "trail has no InsightSelectors");
        }
        String bucketRef = props.path("S3BucketName").path("Ref").asText("");
        JsonNode bucket = byId.get(bucketRef);
        if (bucket != null && truthy(bucket.path("Properties").path("ObjectLockEnabled"))) {
            ok(out, "SOC2-CC7.2-AuditLogImmutability", "trail bucket has Object Lock enabled");
        } else {
            mismatch(out, "SOC2-CC7.2-AuditLogImmutability",
                bucket == null ? "trail bucket not resolvable in the template" : "trail bucket ObjectLockEnabled is not true");
        }
    }

    private void route53Logging(List<Check> out) {
        String rule = "SOC2-CC7.2-Route53QueryLogging";
        List<JsonNode> zones = of("AWS::Route53::HostedZone");
        if (zones.isEmpty()) {
            na(out, rule, "no hosted zone created by this stack");
            return;
        }
        long bad = zones.stream().filter(z -> z.path("Properties").path("QueryLoggingConfig").isMissingNode()).count();
        if (bad == 0) {
            ok(out, rule, "hosted zone(s) have query logging");
        } else {
            mismatch(out, rule, bad + " hosted zone(s) without QueryLoggingConfig");
        }
    }

    private void imdsv2(List<Check> out) {
        String rule = "SOC2-CC6.6-IMDSv2";
        if (!"EC2".equals(runtime)) {
            na(out, rule, "Fargate has no instance metadata service");
            return;
        }
        for (JsonNode lt : of("AWS::EC2::LaunchTemplate")) {
            String tokens = lt.path("Properties").path("LaunchTemplateData").path("MetadataOptions")
                .path("HttpTokens").asText();
            if (!"required".equals(tokens)) {
                mismatch(out, rule, "launch template HttpTokens=" + (tokens.isEmpty() ? "unset" : tokens));
                return;
            }
        }
        if (of("AWS::EC2::LaunchTemplate").isEmpty()) {
            mismatch(out, rule, "EC2 runtime but no launch template");
        } else {
            ok(out, rule, "launch template requires IMDSv2 tokens");
        }
    }

    private void availability(List<Check> out) {
        Set<String> zones = new HashSet<>();
        for (JsonNode subnet : of("AWS::EC2::Subnet")) {
            zones.add(subnet.path("Properties").path("AvailabilityZone").toString());
        }
        if (zones.size() >= 2) {
            ok(out, "SOC2-A1.2-MultiAZ", "subnets span " + zones.size() + " availability zones");
        } else {
            mismatch(out, "SOC2-A1.2-MultiAZ", "subnets span " + zones.size() + " availability zone(s)");
        }
        if (pinnedToOneInstance) {
            na(out, "SOC2-A1.2-AutoScaling", "deployment is pinned to one instance");
            return;
        }
        boolean scaling = !of("AWS::ApplicationAutoScaling::ScalableTarget").isEmpty()
            || of("AWS::AutoScaling::AutoScalingGroup").stream().anyMatch(a -> {
                JsonNode p = a.path("Properties");
                return p.path("MaxSize").asInt(1) > p.path("MinSize").asInt(1);
            });
        if (scaling) {
            ok(out, "SOC2-A1.2-AutoScaling", "scaling configured");
        } else {
            mismatch(out, "SOC2-A1.2-AutoScaling", "no scalable target and no ASG with max > min");
        }
    }

    private void database(List<Check> out) {
        List<JsonNode> dbs = of("AWS::RDS::DBInstance");
        if (dbs.isEmpty()) {
            na(out, "SOC2-A1.2-DatabaseMultiAZ", "no RDS instance");
            na(out, "SOC2-A1.3-DatabaseDeletionProtection", "no RDS instance");
            return;
        }
        long single = dbs.stream().filter(d -> !truthy(d.path("Properties").path("MultiAZ"))).count();
        if (single == 0) {
            ok(out, "SOC2-A1.2-DatabaseMultiAZ", dbs.size() + " RDS instance(s) MultiAZ=true");
        } else {
            mismatch(out, "SOC2-A1.2-DatabaseMultiAZ", single + " RDS instance(s) not Multi-AZ");
        }
        long open = dbs.stream().filter(d -> !truthy(d.path("Properties").path("DeletionProtection"))).count();
        if (open == 0) {
            ok(out, "SOC2-A1.3-DatabaseDeletionProtection", "RDS DeletionProtection=true");
        } else {
            mismatch(out, "SOC2-A1.3-DatabaseDeletionProtection", open + " RDS instance(s) without deletion protection");
        }
    }

    private void backup(List<Check> out) {
        List<JsonNode> plans = of("AWS::Backup::BackupPlan");
        if (plans.isEmpty()) {
            mismatch(out, "SOC2-A1.3-Backup", "no AWS Backup plan");
            mismatch(out, "SOC2-A1.3-CrossRegion", "no AWS Backup plan");
            return;
        }
        ok(out, "SOC2-A1.3-Backup", plans.size() + " backup plan(s)");
        boolean copy = plans.stream().anyMatch(p -> p.toString().contains("CopyActions"));
        if (!crossRegionVaultConfigured) {
            na(out, "SOC2-A1.3-CrossRegion", "advisory; no destination vault configured");
        } else if (copy) {
            ok(out, "SOC2-A1.3-CrossRegion", "backup plan copies to another region");
        } else {
            mismatch(out, "SOC2-A1.3-CrossRegion", "backup plan has no CopyActions");
        }
    }

    private void privateNetwork(List<Check> out) {
        String rule = "SOC2-C1.2-Network";
        if ("FARGATE".equals(runtime)) {
            for (JsonNode service : of("AWS::ECS::Service")) {
                String ip = service.path("Properties").path("NetworkConfiguration")
                    .path("AwsvpcConfiguration").path("AssignPublicIp").asText();
                if (!"DISABLED".equals(ip)) {
                    mismatch(out, rule, "ECS service AssignPublicIp=" + (ip.isEmpty() ? "unset" : ip));
                    return;
                }
            }
            ok(out, rule, "ECS service tasks have no public IP");
        } else {
            for (JsonNode asg : of("AWS::AutoScaling::AutoScalingGroup")) {
                for (JsonNode subnet : asg.path("Properties").path("VPCZoneIdentifier")) {
                    String ref = subnet.path("Ref").asText("");
                    if (ref.toLowerCase().contains("public")) {
                        mismatch(out, rule, "ASG places instances in a public subnet: " + ref);
                        return;
                    }
                }
            }
            ok(out, rule, "ASG instances are in private subnets");
        }
    }
}
