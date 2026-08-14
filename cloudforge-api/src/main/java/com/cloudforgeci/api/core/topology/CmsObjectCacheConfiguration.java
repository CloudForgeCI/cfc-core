package com.cloudforgeci.api.core.topology;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.CmsSpec;
import com.cloudforge.core.enums.SecurityProfile;

import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.elasticache.CfnReplicationGroup;
import software.amazon.awscdk.services.elasticache.CfnParameterGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ElastiCache Redis/Memcached configuration for CMS object caching.
 *
 * <p>Creates ElastiCache clusters with appropriate configuration for CMS
 * object caching, session storage, and page caching.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Redis or Memcached clusters</li>
 *   <li>Subnet group in VPC private subnets</li>
 *   <li>Security group integration</li>
 *   <li>AUTH password via Secrets Manager</li>
 *   <li>In-transit encryption (TLS)</li>
 *   <li>At-rest encryption for production</li>
 * </ul>
 *
 * @since 3.1.0
 */
public final class CmsObjectCacheConfiguration {

    private CmsObjectCacheConfiguration() {
        // Utility class
    }

    /**
     * Create ElastiCache Redis cluster for object caching (or, for non-CMS callers such as
     * {@code CloudForgeManagerApplicationSpec}, a Redis-backed session store).
     *
     * <p>Only {@link ApplicationSpec#applicationId()}/{@link ApplicationSpec#displayName()} are
     * used, so any {@link ApplicationSpec} works here — {@link CmsSpec} extends it and remains a
     * valid argument unchanged.</p>
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return Redis cache cluster
     */
    public static CfnCacheCluster createRedisCluster(SystemContext ctx, ApplicationSpec spec) {
        String clusterId = generateClusterId(ctx, spec);
        String nodeType = determineNodeType(ctx);

        // Create subnet group
        CfnSubnetGroup subnetGroup = createSubnetGroup(ctx, spec);

        // Create security group
        SecurityGroup securityGroup = createCacheSecurityGroup(ctx, spec);

        // Create parameter group for Redis 7
        CfnParameterGroup parameterGroup = createRedisParameterGroup(ctx, spec);

        return CfnCacheCluster.Builder.create(ctx, "CmsRedisCluster")
            .clusterName(clusterId)
            .engine("redis")
            .engineVersion("7.0")
            .cacheNodeType(nodeType)
            .numCacheNodes(1)
            .cacheSubnetGroupName(subnetGroup.getRef())
            .vpcSecurityGroupIds(List.of(securityGroup.getSecurityGroupId()))
            .cacheParameterGroupName(parameterGroup.getRef())
            .port(6379)
            .snapshotRetentionLimit(determineSnapshotRetention(ctx))
            .preferredMaintenanceWindow("sun:05:00-sun:06:00")
            .autoMinorVersionUpgrade(true)
            .build();
    }

    /**
     * Create ElastiCache Redis replication group for high availability.
     *
     * <p>Creates a Redis replication group with:</p>
     * <ul>
     *   <li>Primary node + read replicas</li>
     *   <li>Automatic failover</li>
     *   <li>Multi-AZ deployment</li>
     *   <li>In-transit and at-rest encryption</li>
     * </ul>
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @param numReplicas number of read replicas (1-5)
     * @return Redis replication group
     */
    public static CfnReplicationGroup createRedisReplicationGroup(
            SystemContext ctx,
            ApplicationSpec spec,
            int numReplicas) {

        String replicationGroupId = generateClusterId(ctx, spec, "-rg");
        String nodeType = determineNodeType(ctx);
        boolean isProduction = ctx.cfc.securityProfile() == SecurityProfile.PRODUCTION;

        // Create subnet group
        CfnSubnetGroup subnetGroup = createSubnetGroup(ctx, spec);

        // Create security group
        SecurityGroup securityGroup = createCacheSecurityGroup(ctx, spec);

        // Create parameter group
        CfnParameterGroup parameterGroup = createRedisParameterGroup(ctx, spec);

        return CfnReplicationGroup.Builder.create(ctx, "CmsRedisReplicationGroup")
            .replicationGroupDescription(String.format("CloudForge Redis for %s", spec.displayName()))
            .replicationGroupId(replicationGroupId)
            .engine("redis")
            .engineVersion("7.0")
            .cacheNodeType(nodeType)
            .numCacheClusters(1 + numReplicas)
            .automaticFailoverEnabled(numReplicas > 0)
            .multiAzEnabled(numReplicas > 0 && isProduction)
            .cacheSubnetGroupName(subnetGroup.getRef())
            .securityGroupIds(List.of(securityGroup.getSecurityGroupId()))
            .cacheParameterGroupName(parameterGroup.getRef())
            .port(6379)
            .transitEncryptionEnabled(isProduction)
            .atRestEncryptionEnabled(isProduction)
            .snapshotRetentionLimit(determineSnapshotRetention(ctx))
            .snapshotWindow("03:00-04:00")
            .preferredMaintenanceWindow("sun:05:00-sun:06:00")
            .autoMinorVersionUpgrade(true)
            .build();
    }

    /**
     * Create ElastiCache Memcached cluster.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return Memcached cache cluster
     */
    public static CfnCacheCluster createMemcachedCluster(SystemContext ctx, ApplicationSpec spec) {
        String clusterId = generateClusterId(ctx, spec, "-mc");
        String nodeType = determineNodeType(ctx);

        // Create subnet group
        CfnSubnetGroup subnetGroup = createSubnetGroup(ctx, spec);

        // Create security group
        SecurityGroup securityGroup = createCacheSecurityGroup(ctx, spec);

        return CfnCacheCluster.Builder.create(ctx, "CmsMemcachedCluster")
            .clusterName(clusterId)
            .engine("memcached")
            .engineVersion("1.6.17")
            .cacheNodeType(nodeType)
            .numCacheNodes(1)
            .cacheSubnetGroupName(subnetGroup.getRef())
            .vpcSecurityGroupIds(List.of(securityGroup.getSecurityGroupId()))
            .port(11211)
            .preferredMaintenanceWindow("sun:05:00-sun:06:00")
            .autoMinorVersionUpgrade(true)
            .build();
    }

    /**
     * Create subnet group for ElastiCache.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return ElastiCache subnet group
     */
    private static CfnSubnetGroup createSubnetGroup(SystemContext ctx, ApplicationSpec spec) {
        IVpc vpc = ctx.vpc.get().orElseThrow(
            () -> new IllegalStateException("VPC must exist before creating cache subnet group"));

        List<String> subnetIds = vpc.selectSubnets(SubnetSelection.builder()
                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                .build())
            .getSubnetIds();

        return CfnSubnetGroup.Builder.create(ctx, "CmsCacheSubnetGroup")
            .description(String.format("Subnet group for %s Redis cache", spec.displayName()))
            .cacheSubnetGroupName(generateClusterId(ctx, spec) + "-subnet")
            .subnetIds(subnetIds)
            .build();
    }

    /**
     * Create security group for cache cluster.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return security group for cache access
     */
    private static SecurityGroup createCacheSecurityGroup(SystemContext ctx, ApplicationSpec spec) {
        IVpc vpc = ctx.vpc.get().orElseThrow(
            () -> new IllegalStateException("VPC must exist before creating cache security group"));

        SecurityGroup sg = SecurityGroup.Builder.create(ctx, "CmsCacheSecurityGroup")
            .vpc(vpc)
            .securityGroupName(generateClusterId(ctx, spec) + "-cache-sg")
            .description(String.format("Security group for %s cache cluster", spec.displayName()))
            .allowAllOutbound(false)
            .build();

        // Allow inbound from VPC CIDR on Redis port
        sg.addIngressRule(
            Peer.ipv4(vpc.getVpcCidrBlock()),
            Port.tcp(6379),
            "Allow Redis from VPC"
        );

        // Allow inbound from VPC CIDR on Memcached port
        sg.addIngressRule(
            Peer.ipv4(vpc.getVpcCidrBlock()),
            Port.tcp(11211),
            "Allow Memcached from VPC"
        );

        return sg;
    }

    /**
     * Create Redis parameter group with optimized settings.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return Redis parameter group
     */
    private static CfnParameterGroup createRedisParameterGroup(SystemContext ctx, ApplicationSpec spec) {
        Map<String, String> parameters = new HashMap<>();

        // Memory management
        parameters.put("maxmemory-policy", "allkeys-lru");

        // Persistence (disable for pure cache use case)
        parameters.put("appendonly", "no");

        // Performance tuning
        parameters.put("tcp-keepalive", "300");
        parameters.put("timeout", "0");

        // Notify on key events (useful for cache invalidation)
        parameters.put("notify-keyspace-events", "Ex");

        return CfnParameterGroup.Builder.create(ctx, "CmsRedisParameterGroup")
            .cacheParameterGroupFamily("redis7")
            .description(String.format("Redis parameters for %s", spec.displayName()))
            .properties(parameters)
            .build();
    }

    /**
     * Generate cluster ID from context and spec.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @return cluster ID
     */
    private static String generateClusterId(SystemContext ctx, ApplicationSpec spec) {
        return generateClusterId(ctx, spec, "");
    }

    /**
     * Generate a cluster/replication-group ID, with {@code suffix} (e.g. {@code "-rg"},
     * {@code "-mc"}) folded in before truncation — callers that append a suffix AFTER this
     * method already truncated to 40 chars can overflow ElastiCache's 40-char ID limit.
     *
     * @param ctx the SystemContext
     * @param spec the application specification
     * @param suffix resource-type suffix to include inside the 40-char budget, or "" for none
     * @return cluster ID: lowercase, &lt;=40 chars, starts with a letter, no trailing/repeated hyphens
     */
    private static String generateClusterId(SystemContext ctx, ApplicationSpec spec, String suffix) {
        String env = ctx.cfc.env() != null ? ctx.cfc.env() : "dev";
        // ElastiCache cluster/replication-group IDs: lowercase, <=40 chars, alphanumeric +
        // hyphens, must start with a letter, no trailing or consecutive hyphens.
        String sanitized = String.format("%s-%s-cache%s",
            spec.applicationId().toLowerCase(),
            env.toLowerCase(),
            suffix
        ).replaceAll("[^a-z0-9-]", "").replaceAll("-{2,}", "-");
        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "a" + sanitized;
        }
        sanitized = sanitized.substring(0, Math.min(40, sanitized.length()));
        while (sanitized.endsWith("-")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized;
    }

    /**
     * Determine node type based on security profile.
     *
     * @param ctx the SystemContext
     * @return ElastiCache node type
     */
    private static String determineNodeType(SystemContext ctx) {
        SecurityProfile profile = ctx.cfc.securityProfile();
        return switch (profile) {
            case PRODUCTION -> "cache.r6g.large";
            case STAGING -> "cache.t4g.medium";
            default -> "cache.t4g.micro";
        };
    }

    /**
     * Determine snapshot retention based on security profile.
     *
     * @param ctx the SystemContext
     * @return snapshot retention in days
     */
    private static int determineSnapshotRetention(SystemContext ctx) {
        SecurityProfile profile = ctx.cfc.securityProfile();
        return switch (profile) {
            case PRODUCTION -> 7;
            case STAGING -> 3;
            default -> 0;  // No snapshots for dev
        };
    }

    /**
     * Create environment variables for Redis connection.
     *
     * @param endpoint Redis primary endpoint
     * @param port Redis port
     * @param spec the CMS specification
     * @return map of environment variables
     */
    public static Map<String, String> createRedisEnvironment(
            String endpoint,
            int port,
            CmsSpec spec) {
        // Delegate entirely to the spec — no hardcoded CMS IDs needed here.
        // Each CmsSpec implementation declares its own Redis env var names via redisEnvVars().
        return spec.redisEnvVars(endpoint, port);
    }

    /**
     * Create environment variables for Memcached connection.
     *
     * @param endpoint Memcached configuration endpoint
     * @param port Memcached port
     * @param spec the CMS specification
     * @return map of environment variables
     */
    public static Map<String, String> createMemcachedEnvironment(
            String endpoint,
            int port,
            CmsSpec spec) {
        // Base generic vars; CMS implementations can override redisEnvVars() if they also
        // support Memcached-specific keys (rare — most CMS plugins prefer Redis).
        Map<String, String> env = new HashMap<>();
        env.put("MEMCACHED_HOST", endpoint);
        env.put("MEMCACHED_PORT", String.valueOf(port));
        env.put("MEMCACHED_PREFIX", spec.applicationId().toLowerCase() + "_");
        return env;
    }
}
