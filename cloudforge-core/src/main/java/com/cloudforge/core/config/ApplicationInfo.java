package com.cloudforge.core.config;

/**
 * Application metadata for display and configuration purposes.
 *
 * <p>This class captures application metadata from {@link com.cloudforge.core.interfaces.ApplicationSpec}
 * for use in interactive deployment tools and UIs.</p>
 *
 * @since CloudForge 3.0.0
 */
public class ApplicationInfo {

    /** Application identifier (e.g., "jenkins", "gitlab") */
    public String id;

    /** Human-readable name (e.g., "Jenkins", "GitLab") */
    public String name;

    /** Application description */
    public String description;

    /** Supports Fargate deployment */
    public boolean supportsFargate;

    /** Supports EC2 deployment */
    public boolean supportsEc2;

    /** Supports OIDC integration */
    public boolean supportsOidc;

    /** Minimum CPU units (Fargate) */
    public int minCpu;

    /** Minimum memory MB (Fargate) */
    public int minMemory;

    /** Minimum instance type (EC2) */
    public String minInstanceType;

    /**
     * Creates ApplicationInfo with default resource requirements.
     *
     * @param id the unique application identifier
     * @param name the application display name
     * @param description the application description
     * @param supportsFargate whether the application supports Fargate deployment
     * @param supportsEc2 whether the application supports EC2 deployment
     * @param supportsOidc whether the application supports OIDC integration
     */
    public ApplicationInfo(String id, String name, String description,
                          boolean supportsFargate, boolean supportsEc2, boolean supportsOidc) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.supportsFargate = supportsFargate;
        this.supportsEc2 = supportsEc2;
        this.supportsOidc = supportsOidc;
        this.minCpu = 1024;
        this.minMemory = 2048;
        this.minInstanceType = "t3.micro";
    }

    /**
     * Creates ApplicationInfo with explicit resource requirements.
     *
     * @param id the unique application identifier
     * @param name the application display name
     * @param description the application description
     * @param supportsFargate whether the application supports Fargate deployment
     * @param supportsEc2 whether the application supports EC2 deployment
     * @param supportsOidc whether the application supports OIDC integration
     * @param minCpu the minimum CPU units required (Fargate CPU units)
     * @param minMemory the minimum memory in MB required
     * @param minInstanceType the minimum EC2 instance type required
     */
    public ApplicationInfo(String id, String name, String description,
                          boolean supportsFargate, boolean supportsEc2, boolean supportsOidc,
                          int minCpu, int minMemory, String minInstanceType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.supportsFargate = supportsFargate;
        this.supportsEc2 = supportsEc2;
        this.supportsOidc = supportsOidc;
        this.minCpu = minCpu;
        this.minMemory = minMemory;
        this.minInstanceType = minInstanceType;
    }
}
