package com.cloudforgeci.core.api;


import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed façade over CDK context (cdk.json / -c flags).
 *
 * Usable context keys (all optional unless noted):
 *   tier:            "public" | "enterprise"          (default: public)
 *   variant:         "ec2" | "ec2-domain" | "fargate" | "fargate-domain"   (default: ec2)
 *   env:             "dev" | "stage" | "prod"         (default: dev)
 *   domain:          e.g. "example.com"
 *   subdomain:       e.g. "jenkins" (used to compute fqdn if not provided)
 *   fqdn:            e.g. "jenkins.example.com" (wins over domain/subdomain)
 *   networkMode:     "public-no-nat" | "private-with-nat"                  (default: public-no-nat)
 *   wafEnabled:      true/false                                           (default: false)
 *   cloudfront:      true/false                                           (default: false)
 *   authMode:        "none" | "alb-oidc" | "jenkins-oidc"                 (default: none)
 *   ssoInstanceArn:  arn:aws:sso::...
 *   ssoGroupId:      UUID for a group
 *   ssoTargetAccountId: 12-digit account
 *   artifactsBucket: explicit bucket name (optional)
 *   artifactsPrefix: default "jenkins/job/${JOB_NAME}/${BUILD_NUMBER}"
 *   lbType:          "alb" | "nlb"                                       (default: alb)
 *   cpu:             integer vCPU units (Fargate taskDef)                (default: 1024)
 *   memory:          integer MiB                                         (default: 2048)
 *
 * Read via:
 *   DeploymentContext ctx = DeploymentContext.from(app);
 * or DeploymentContext.from(scope) inside a Stack/Construct.
 */
public final class DeploymentContext {

    // Raw map snapshot (frozen)
    private final Map<String, Object> raw;

    // Required-ish high level knobs
    private final String tier;        // public | enterprise
    private final String variant;     // ec2 | ec2-domain | fargate | fargate-domain
    private final String env;         // dev | stage | prod (freeform allowed)

    // Naming / DNS
    private final String domain;
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

    // Jenkins container size hints
    private final int cpu;
    private final int memory;

    // Derived conveniences
    private final boolean enableDomainAndSsl;

    private DeploymentContext(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));

        this.tier = str("tier", "public");
        this.variant = str("variant", "ec2");
        this.env = str("env", "dev");

        this.domain = str("domain", null);
        this.subdomain = str("subdomain", "jenkins");
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

        this.enableDomainAndSsl = variant.contains("domain")
                || (fqdn != null && !fqdn.isBlank());
        validateOrThrow();
    }

    // --------- Static factories ---------
    private static Map<String, Object> convertToContext(Object obj) {
        Map<String, Object> map = new HashMap<>();
        try {
            Class<?> clazz = obj.getClass();
            for (Field field : clazz.getDeclaredFields()) {
                //field.setAccessible(true); // Allows access to private fields
                map.put(field.getName(), field.get(obj));
            }
        }catch(IllegalAccessException iae) {

        }
        return map;
    }

    public static DeploymentContext from(App app) {

        return new DeploymentContext(convertToContext(app.getNode().getAllContext()));
    }

    public static DeploymentContext from(Construct scope) {
        return new DeploymentContext(convertToContext(scope.getNode().getAllContext()));
    }

    // --------- Public getters ---------

    public String tier() { return tier; }
    public String variant() { return variant; }
    public String env() { return env; }

    public String domain() { return domain; }
    public String subdomain() { return subdomain; }
    public String fqdn() { return fqdn; }

    public String networkMode() { return networkMode; }
    public boolean wafEnabled() { return wafEnabled; }
    public boolean cloudfrontEnabled() { return cloudfront; }
    public String lbType() { return lbType; }

    public String authMode() { return authMode; }
    public String ssoInstanceArn() { return ssoInstanceArn; }
    public String ssoGroupId() { return ssoGroupId; }
    public String ssoTargetAccountId() { return ssoTargetAccountId; }

    public String artifactsBucket() { return artifactsBucket; }
    public String artifactsPrefix() { return artifactsPrefix; }

    public int cpu() { return cpu; }
    public int memory() { return memory; }

    public boolean enableDomainAndSsl() { return enableDomainAndSsl; }

    /** Raw immutable view of all context keys. */
    public Map<String, Object> raw() { return raw; }

    // --------- Helpers / derived behavior ---------

    /** True if the service should run in private subnets without public IPs. */
    public boolean isPrivateWithNat() { return "private-with-nat".equals(networkMode); }

    /** True if enterprise features should be enabled. */
    public boolean isEnterprise() { return "enterprise".equalsIgnoreCase(tier); }

    /** Tag a stack so you can see the config in the console. */
    public void tagStack(Stack stack) {
        stack.getTags().setTag("cfc:tier", tier);
        stack.getTags().setTag("cfc:variant", variant);
        stack.getTags().setTag("cfc:env", env);
        if (fqdn != null) stack.getTags().setTag("cfc:fqdn", fqdn);
        stack.getTags().setTag("cfc:network", networkMode);
        stack.getTags().setTag("cfc:auth", authMode);
    }

    /** Validate interdependent knobs and throw a single readable error list. */
    private void validateOrThrow() {
        List<String> errs = new ArrayList<>();

        if (enableDomainAndSsl) {
            if (fqdn == null || fqdn.isBlank()) {
                // If they didn't set fqdn, they must set domain at least
                if (domain == null || domain.isBlank()) {
                    errs.add("enableDomainAndSsl=true but neither 'fqdn' nor 'domain' provided.");
                }
            }
        }

        if ("alb-oidc".equals(authMode) && !enableDomainAndSsl) {
            errs.add("authMode=alb-oidc requires HTTPS listener; set variant=*-domain or provide fqdn/domain.");
        }

        if ("private-with-nat".equals(networkMode)) {
            // not strictly required, but warn users about image/plugin egress
            if (!"ecr".equalsIgnoreCase(imageOrigin(raw))) {
                // This is a soft recommendation in message form:
                // (no throw; could add a tag or log if you have a logger)
            }
        }

        if (!errs.isEmpty()) {
            throw new IllegalArgumentException("DeploymentContext validation failed:\n - "
                    + String.join("\n - ", errs));
        }
    }

    // crude discriminator for messaging; you can remove if not needed
    private static String imageOrigin(Map<String, Object> ctx) {
        Object img = ctx.get("image");
        if (img instanceof String) {
            String s = (String) img;
            if (s.startsWith("ecr://")) return "ecr";
            if (s.contains("amazonaws.com")) return "ecr";
        }
        return "other";
    }

    private static String composeFqdn(String sub, String dom) {
        if (dom == null || dom.isBlank()) return null;
        if (sub == null || sub.isBlank()) return dom;
        return sub + "." + dom;
    }

    // --------- typed readers with defaults ---------

    private String str(String key, String def) {
        Object v = raw.get(key);
        return v == null ? def : String.valueOf(v);
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
                "tier='" + tier + '\'' +
                ", variant='" + variant + '\'' +
                ", env='" + env + '\'' +
                ", fqdn='" + fqdn + '\'' +
                ", networkMode='" + networkMode + '\'' +
                ", wafEnabled=" + wafEnabled +
                ", cloudfront=" + cloudfront +
                ", authMode='" + authMode + '\'' +
                ", cpu=" + cpu +
                ", memory=" + memory +
                '}';
    }
}

