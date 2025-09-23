package com.cloudforgeci.api.core;

import com.cloudforgeci.api.core.utilities.DnsLabel;
import com.cloudforgeci.api.core.utilities.DnsName;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.TopologyType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed façade over CDK context (cdk.json / -c flags).
 *
 * Usable context keys (all optional unless noted):
 *   tier:            "public" | "enterprise"                 (default: public)
 *   runtime:         "ec2" | "fargate"                       (default: ec2)  // alias: variant, architecture
 *   topology:        "jenkins-single-node" | "jenkins-service" | "s3-website"
 *   env:             "dev" | "stage" | "prod"                (default: dev)
 *   securityProfile: "dev" | "staging" | "production"       (default: dev) -> SecurityProfile enum
 *   region:          e.g. "us-east-1"                        (default: us-east-1)
 *   domain:          e.g. "example.com"
 *   subdomain:       e.g. "jenkins" (used to compute fqdn if not provided)
 *   fqdn:            e.g. "jenkins.example.com" (wins over domain/subdomain)
 *   networkMode:     "public-no-nat" | "private-with-nat"    (default: public-no-nat)
 *   wafEnabled:      true/false                               (default: false)
 *   cloudfront:      true/false                               (default: false)
 *   authMode:        "none" | "alb-oidc" | "jenkins-oidc"    (default: none)
 *   ssoInstanceArn:  arn:aws:sso::...
 *   ssoGroupId:      UUID for a group
 *   ssoTargetAccountId: 12-digit account
 *   artifactsBucket: explicit bucket name (optional)
 *   artifactsPrefix: default "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}"
 *   lbType:          "alb" | "nlb"                            (default: alb)
 *   minInstanceCapacity: default 1
 *   maxInstanceCapacity: default 3
 *   cpuTargetUtilization: default 0
 *   cpu:             integer vCPU units (Fargate taskDef)     (default: 1024)
 *   memory:          integer MiB                              (default: 2048)
 *   deploymentId:    unique identifier for this deployment    (optional)
 *   deploymentVersion: version tag for this deployment        (optional)
 *   environment:     "dev" | "staging" | "prod"               (default: dev)
 *   region:          AWS region override                       (optional)
 *   tags:            JSON object of additional tags            (optional)
 *
 * Legacy one-field combos (still accepted, mapped to runtime+topology):
 *   runtime: "jenkins-fargate" -> topology=JENKINS_SERVICE, runtime=FARGATE
 *   runtime: "jenkins-ec2"     -> topology=JENKINS_SINGLE_NODE, runtime=EC2
 *   runtime: "cf-alb-s3"       -> topology=S3_WEBSITE, runtime=EC2 (ignored by topology)
 *   runtime: "cf-alb-proxy"    -> topology=JENKINS_SERVICE, runtime=EC2
 *
 * Read via:
 *   DeploymentContext cfc = DeploymentContext.from(app);
 * or DeploymentContext.from(scope) inside a Stack/Construct.
 */
public final class DeploymentContext {

    // Raw map snapshot (frozen)
    private final Map<String, Object> raw;

    // Required-ish high level knobs
    private final String tier;        // public | enterprise
    private final String env;         // dev | stage | prod
    private final SecurityProfile securityProfile; // DEV | STAGING | PRODUCTION
    private final String region;      // default: us-east-1

    // Naming / DNS
    @DnsName(message = "Domain must be a valid DNS name")
    private final String domain;

    @DnsLabel(message = "Subdomain must be a valid DNS label")
    private final String subdomain;
    private final String fqdn;        // computed if not provided

    // Networking
    private final String networkMode; // public-no-nat | private-with-nat
    private final boolean wafEnabled;
    private final boolean cloudfront;
    private final String lbType;      // alb | nlb

    // Auth / SSO
    private final String authMode;    // none | alb-oidc | jenkins-oidc
    private final String ssoInstanceArn;
    private final String ssoGroupId;
    private final String ssoTargetAccountId;

    // Artifacts
    private final String artifactsBucket;
    private final String artifactsPrefix;

    // Auto Scaling
    private final Integer cpuTargetUtilization;
    private final Integer maxInstanceCapacity;
    private final Integer minInstanceCapacity;

    private final boolean enableFlowlogs;

    // Jenkins container size
    private final int cpu;
    private final int memory;

    // Derived conveniences
    private final boolean enableSsl;

    // New canonical types
    private final RuntimeType runtime;
    private final TopologyType topology;

    // Legacy raw values (kept for compatibility & logging)
    private final String runtimeRaw;      // may be "ec2"/"fargate" or a legacy combo like "jenkins-fargate"
    private final String topologyRaw;     // if user provided an explicit string topology

    // Additional deployment tracking fields
    private final String deploymentId;
    private final String deploymentVersion;
    private final String environment;
    private final String tags;

    protected DeploymentContext(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));

        this.tier   = str("tier", "public");
        this.env    = str("env", "dev");
        this.securityProfile = parseSecurityProfile(str("securityProfile", "dev"));
        this.region = str("region", "us-east-1");

        this.domain = str("domain", null);
        this.subdomain = str("subdomain", null);
        String fqdnCtx = str("fqdn", null);
        this.fqdn = (fqdnCtx != null) ? fqdnCtx : composeFqdn(subdomain, domain);

        this.networkMode = oneOf("networkMode", "public-no-nat",
                List.of("public-no-nat", "private-with-nat"));
        this.wafEnabled = bool("wafEnabled", false);
        this.cloudfront = bool("cloudfront", false);
        this.lbType = oneOf("lbType", "alb", List.of("alb", "nlb"));

        this.authMode = oneOf("authMode", "none",
                List.of("none", "alb-oidc", "jenkins-oidc"));

        this.ssoInstanceArn = str("ssoInstanceArn", null);
        this.ssoGroupId = str("ssoGroupId", null);
        this.ssoTargetAccountId = str("ssoTargetAccountId", null);

        this.artifactsBucket = str("artifactsBucket", null);
        this.artifactsPrefix = str("artifactsPrefix", "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}");

        this.cpu = intval("cpu", 1024);
        this.memory = intval("memory", 2048);

        this.minInstanceCapacity = intval("minInstanceCapacity", 1);
        this.maxInstanceCapacity = intval("maxInstanceCapacity", 3);
        this.cpuTargetUtilization = intval("cpuTargetUtilization", 60);

        this.enableFlowlogs = bool("enableFlowlogs", false);

        // Additional deployment tracking fields
        this.deploymentId = str("deploymentId", null);
        this.deploymentVersion = str("deploymentVersion", null);
        this.environment = str("environment", "dev");
        this.tags = str("tags", null);

        // Legacy/alias inputs
        String runtimeAlias = str("runtime", "fargate");
        this.runtimeRaw = runtimeAlias;
        this.topologyRaw = str("topology", "service");

        // Normalize to enums (supports legacy combos)
        DeploymentConfigurations configurations = process(runtimeAlias, topologyRaw);
        this.runtime = configurations.runtime;
        this.topology = configurations.topology;

        // SSL default remains explicit; do not silently infer on domain unless asked to
        this.enableSsl = bool("enableSsl", false);

        validateOrThrow();
    }

    /** Build from the 'cfc' context object on the App. */
    public static DeploymentContext from(App app) {
        return Util.extractDeploymentContext(app.getNode().tryGetContext("cfc"));
    }

    /** Build from the 'cfc' context object on any Construct scope. */
    public static DeploymentContext from(Construct scope) {
        return Util.extractDeploymentContext(scope.getNode().tryGetContext("cfc"));
    }

    // --------- Public getters ---------

    public String tier() { return tier; }
    public String env() { return env; }
    
    /**
     * Gets the security profile enum.
     * 
     * @return SecurityProfile enum value
     */
    public SecurityProfile securityProfile() {
        return securityProfile;
    }
    
    public String region() { return region; }

    public String domain() { return domain; }
    public String subdomain() { return subdomain; }
    public String fqdn() { return fqdn; }

    public String networkMode() { return networkMode; }
    public boolean wafEnabled() { return wafEnabled; }
    public boolean cloudfrontEnabled() { return cloudfront; }
    public String lbType() { return lbType; }

    public Integer cpuTargetUtilization() { return cpuTargetUtilization; }
    public Integer maxInstanceCapacity() { return maxInstanceCapacity; }
    public Integer minInstanceCapacity() { return minInstanceCapacity; }

    public boolean enableFlowlogs() { return enableFlowlogs; }

    public String authMode() { return authMode; }
    public String ssoInstanceArn() { return ssoInstanceArn; }
    public String ssoGroupId() { return ssoGroupId; }
    public String ssoTargetAccountId() { return ssoTargetAccountId; }

    // Additional deployment tracking fields
    public String deploymentId() { return deploymentId; }
    public String deploymentVersion() { return deploymentVersion; }
    public String environment() { return environment; }
    public String tags() { return tags; }

    public String artifactsBucket() { return artifactsBucket; }
    public String artifactsPrefix() { return artifactsPrefix; }

    public int cpu() { return cpu; }
    public int memory() { return memory; }

    public boolean enableSsl() { return enableSsl; }

    /** Raw immutable view of all context keys. */
    public Map<String, Object> raw() { return raw; }

    /** Canonical axes (preferred). */
    public RuntimeType runtime() { return runtime; }
    public TopologyType topology() { return topology; }

    /** Legacy raw accessors (compat only). */
    @Deprecated public String runtimeRaw() { return runtimeRaw; }
    @Deprecated public String topologyRaw() { return topologyRaw; }

    // --------- Helpers / derived behavior ---------

    /** True if the service should run in private subnets without public IPs. */
    public boolean isPrivateWithNat() { return "private-with-nat".equals(networkMode); }

    /** True if enterprise features should be enabled. */
    public boolean isEnterprise() { return "enterprise".equalsIgnoreCase(tier); }

    /** Get the runtime type. */
    public RuntimeType getRuntime() { return runtime; }

    /** Get the topology type. */
    public TopologyType getTopology() { return topology; }

    /** Get a context value by key with default. */
    public String getContextValue(String key, String defaultValue) {
        return str(key, defaultValue);
    }

    /** Tag a stack so you can see the config in the console. */
    public void tagStack(Stack stack) {
        stack.getTags().setTag("cfc:tier", tier);
        stack.getTags().setTag("cfc:runtime", runtime.name());
        stack.getTags().setTag("cfc:topology", topology.name());
        stack.getTags().setTag("cfc:env", env);
        if (fqdn != null) stack.getTags().setTag("cfc:fqdn", fqdn);
        stack.getTags().setTag("cfc:network", networkMode);
        stack.getTags().setTag("cfc:auth", authMode);
    }

    private void validateOrThrow() {
        List<String> errs = new ArrayList<>();

        if (enableSsl) {
            if (fqdn == null || fqdn.isBlank()) {
                if (domain == null || domain.isBlank()) {
                    errs.add("enableSsl=true but neither 'fqdn' nor 'domain' provided.");
                }
            }
        }

        if ("alb-oidc".equals(authMode) && !enableSsl) {
            errs.add("authMode=alb-oidc requires HTTPS listener; set enableSsl=true and provide fqdn/domain.");
        }

        // Cross-axis sanity (context level; rules will also validate)
        if (topology == TopologyType.JENKINS_SINGLE_NODE && runtime != RuntimeType.EC2) {
            errs.add("JENKINS_SINGLE_NODE requires runtime=EC2 (got " + runtime + ")");
        }

        if (!errs.isEmpty()) {
            throw new IllegalArgumentException("DeploymentContext validation failed:\n - "
                    + String.join("\n - ", errs));
        }
    }

    // ---- Normalization helpers ----

    private static final class DeploymentConfigurations {
        final RuntimeType runtime;
        final TopologyType topology;
        DeploymentConfigurations(RuntimeType r, TopologyType t) { this.runtime = r; this.topology = t; }
    }

    private static DeploymentConfigurations process(String runtimeAlias, String topologyAlias) {
        RuntimeType runtime = RuntimeType.FARGATE; // default

        TopologyType topology = TopologyType.JENKINS_SERVICE; // conservative default

        // explicit topology string wins if present
        if (topologyAlias != null) {
            topology = parseTopology(topologyAlias);
        }

        if (runtimeAlias != null) {
            String r = runtimeAlias.trim().toLowerCase(Locale.ROOT);
            switch (r) {
                case "ec2" -> { runtime = RuntimeType.EC2;}
                case "fargate" -> { runtime = RuntimeType.FARGATE; }
                case "jenkins-fargate" -> { runtime = RuntimeType.FARGATE; topology = TopologyType.JENKINS_SERVICE; }
                case "jenkins-ec2"     -> { runtime = RuntimeType.EC2;     topology = TopologyType.JENKINS_SINGLE_NODE; }
                case "cf-alb-s3"       -> { runtime = RuntimeType.EC2;     topology = TopologyType.S3_WEBSITE; }
                case "cf-alb-proxy"    -> { runtime = RuntimeType.EC2;     topology = TopologyType.JENKINS_SERVICE; }
                default -> { runtime = RuntimeType.FARGATE; topology = TopologyType.JENKINS_SERVICE; }
            }
        }

        return new DeploymentConfigurations(runtime, topology);
    }

    private static SecurityProfile parseSecurityProfile(String val) {
        String s = val.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "dev" -> SecurityProfile.DEV;
            case "staging" -> SecurityProfile.STAGING;
            case "production" -> SecurityProfile.PRODUCTION;
            default -> SecurityProfile.DEV; // Default to DEV
        };
    }

    private static TopologyType parseTopology(String val) {
        String t = val.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        return switch (t) {
            case "jenkins-single-node", "jenkins_single_node", "single-node", "single_node", "node" -> TopologyType.JENKINS_SINGLE_NODE;
            case "jenkins-service", "jenkins_service", "service" -> TopologyType.JENKINS_SERVICE;
            case "s3-website", "s3_website", "s3" -> TopologyType.S3_WEBSITE;
            default -> TopologyType.JENKINS_SINGLE_NODE;
        };
    }

    private static String composeFqdn(String sub, String dom) {
        if (dom == null || dom.isBlank()) return null;
        if (sub == null || sub.isBlank()) return dom;
        return sub + "." + dom;
    }

    private String str(String key, String def) {
        Object v = raw.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private String strOrNull(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null) return v;
        return null;
    }

    private boolean bool(String key, boolean def) {
        Object v = raw.get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private int intval(String key, int def) {
        Object v = raw.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return def; }
    }

    private String oneOf(String key, String def, List<String> allowed) {
        String val = str(key, def);
        if (!allowed.contains(val)) {
            String msg = String.format("Context '%s' must be one of %s (got '%s')",
                    key, allowed, val);
            throw new IllegalArgumentException(msg);
        }
        return val;
    }

    @Override public String toString() {
        return "DeploymentContext{" +
                "runtimeKind=" + runtime +
                ", topologyKind=" + topology +
                ", env='" + env + '\'' +
                ", fqdn='" + fqdn + '\'' +
                ", cpu=" + cpu +
                ", memory=" + memory +
                '}';
    }

    static DeploymentContext of(Map<String, Object> raw) {
        return new DeploymentContext(raw);
    }
}