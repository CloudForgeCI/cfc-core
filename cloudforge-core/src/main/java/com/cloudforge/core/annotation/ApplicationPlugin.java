package com.cloudforge.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable application specification.
 *
 * <p>Application specifications annotated with this annotation are automatically discovered
 * and loaded by the CloudForge application deployment system via Java ServiceLoader. This enables
 * external contributors to add new applications without modifying core code.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ApplicationPlugin(
 *     value = "sonarqube",
 *     category = "code-quality",
 *     displayName = "SonarQube",
 *     description = "Continuous code quality and security inspection",
 *     defaultCpu = 2048,
 *     defaultMemory = 4096,
 *     supportsOidc = true
 * )
 * public class SonarQubeApplicationSpec implements ApplicationSpec {
 *     @Override
 *     public void deploy(SystemContext ctx, DeploymentContext dCtx) {
 *         // SonarQube deployment logic
 *     }
 * }
 * }</pre>
 *
 * <h2>Application Categories:</h2>
 * <ul>
 *   <li><strong>cicd:</strong> CI/CD platforms (Jenkins, GitLab, Drone)</li>
 *   <li><strong>vcs:</strong> Version control systems (Gitea)</li>
 *   <li><strong>monitoring:</strong> Monitoring and observability (Grafana, Prometheus)</li>
 *   <li><strong>analytics:</strong> Business intelligence (Metabase, Superset)</li>
 *   <li><strong>database:</strong> Databases and caching (PostgreSQL, Redis)</li>
 *   <li><strong>artifactregistry:</strong> Artifact repositories (Nexus, Harbor)</li>
 *   <li><strong>secrets:</strong> Secrets management (Vault)</li>
 *   <li><strong>collaboration:</strong> Team collaboration (Mattermost)</li>
 *   <li><strong>code-quality:</strong> Code analysis (SonarQube, etc.)</li>
 * </ul>
 *
 * <h2>Resource Requirements:</h2>
 * <p>Default resource values are used when not explicitly specified in deployment context:</p>
 * <ul>
 *   <li><strong>defaultCpu:</strong> Fargate CPU units (256, 512, 1024, 2048, 4096)</li>
 *   <li><strong>defaultMemory:</strong> Fargate memory in MB (512-30720, based on CPU)</li>
 *   <li><strong>defaultInstanceType:</strong> EC2 instance type (t3.small, t3.medium, etc.)</li>
 * </ul>
 *
 * @since 3.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ApplicationPlugin {
    /**
     * Application identifier used in deployment configuration.
     *
     * <p>This value is used in deployment-context.json to specify which application to deploy:</p>
     * <pre>{@code
     * {
     *   "application": "jenkins",
     *   "runtimeType": "FARGATE"
     * }
     * }</pre>
     *
     * <p>Examples: "jenkins", "gitlab", "grafana", "postgresql", "sonarqube"</p>
     *
     * @return the application identifier (lowercase, kebab-case)
     */
    String value();

    /**
     * Application category for grouping and discovery.
     *
     * <p>Categories help organize applications in deployment tools and documentation.</p>
     *
     * <p>Standard categories: cicd, vcs, monitoring, analytics, database, artifactregistry,
     * secrets, collaboration, code-quality</p>
     *
     * @return the application category
     */
    String category();

    /**
     * Human-readable display name for the application.
     *
     * <p>Used in CLI tools, logging, and documentation. If not specified, defaults to
     * the capitalized {@link #value()}.</p>
     *
     * <p>Examples: "Jenkins", "GitLab", "SonarQube", "PostgreSQL"</p>
     *
     * @return the display name
     */
    String displayName() default "";

    /**
     * Brief description of the application's purpose.
     *
     * <p>Used in interactive deployment tools and documentation.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Open-source automation server for CI/CD"</li>
     *   <li>"Complete DevOps platform with Git, CI/CD, and security"</li>
     *   <li>"Continuous code quality and security inspection"</li>
     * </ul>
     *
     * @return the application description
     */
    String description() default "";

    /**
     * Default Fargate CPU units when not specified in deployment context.
     *
     * <p>Valid values: 256 (.25 vCPU), 512 (.5 vCPU), 1024 (1 vCPU), 2048 (2 vCPU), 4096 (4 vCPU)</p>
     *
     * <p>Default: 1024 (1 vCPU)</p>
     *
     * @return the default CPU units
     */
    int defaultCpu() default 1024;

    /**
     * Default Fargate memory in MB when not specified in deployment context.
     *
     * <p>Memory must be compatible with the CPU configuration per AWS Fargate requirements:</p>
     * <ul>
     *   <li>256 CPU: 512-2048 MB</li>
     *   <li>512 CPU: 1024-4096 MB</li>
     *   <li>1024 CPU: 2048-8192 MB</li>
     *   <li>2048 CPU: 4096-16384 MB</li>
     *   <li>4096 CPU: 8192-30720 MB</li>
     * </ul>
     *
     * <p>Default: 2048 MB</p>
     *
     * @return the default memory in MB
     */
    int defaultMemory() default 2048;

    /**
     * Default EC2 instance type when not specified in deployment context.
     *
     * <p>Common types: t3.small, t3.medium, t3.large, m5.large, m5.xlarge</p>
     *
     * <p>Default: t3.small</p>
     *
     * @return the default EC2 instance type
     */
    String defaultInstanceType() default "t3.small";

    /**
     * Whether this application supports AWS Fargate deployment.
     *
     * <p>Most containerized applications support Fargate. Set to false for applications
     * that require specific host-level configurations or resources.</p>
     *
     * <p>Default: true</p>
     *
     * @return true if Fargate is supported
     */
    boolean supportsFargate() default true;

    /**
     * Whether this application supports AWS EC2 deployment.
     *
     * <p>All applications should support EC2 for maximum flexibility. Set to false only
     * if the application strictly requires serverless/Fargate architecture.</p>
     *
     * <p>Default: true</p>
     *
     * @return true if EC2 is supported
     */
    boolean supportsEc2() default true;

    /**
     * Whether this application supports OIDC authentication integration.
     *
     * <p>Applications with OIDC support can integrate with AWS Cognito or IAM Identity Center
     * for centralized authentication and single sign-on.</p>
     *
     * <p>Examples of OIDC-enabled applications: Jenkins, GitLab, Grafana</p>
     *
     * <p>Default: false</p>
     *
     * @return true if OIDC integration is supported
     */
    boolean supportsOidc() default false;

    /**
     * Whether this application requires an external database (RDS).
     *
     * <p>Applications that REQUIRE a database cannot function without one and must
     * always provision RDS instances. These applications do not support embedded databases
     * for production use.</p>
     *
     * <p>Examples of applications that require databases:</p>
     * <ul>
     *   <li>GitLab - PostgreSQL required</li>
     *   <li>Mattermost - PostgreSQL/MySQL required</li>
     *   <li>Superset - PostgreSQL/MySQL required for metadata</li>
     *   <li>Harbor - PostgreSQL required for registry metadata</li>
     * </ul>
     *
     * <p>Default: false</p>
     *
     * @return true if external database is required
     */
    boolean requiresDatabase() default false;

    /**
     * Whether this application supports optional external database (RDS).
     *
     * <p>Applications with optional database support can use either:</p>
     * <ul>
     *   <li><strong>Production:</strong> External RDS database for multi-instance deployments</li>
     *   <li><strong>Development:</strong> Embedded database for single-instance deployments</li>
     * </ul>
     *
     * <p>Examples of applications with optional database support:</p>
     * <ul>
     *   <li>Metabase - PostgreSQL/MySQL OR H2 embedded</li>
     *   <li>Grafana - PostgreSQL/MySQL OR SQLite embedded</li>
     * </ul>
     *
     * <p><strong>Important:</strong> Embedded databases (H2, SQLite) cannot support multiple
     * instances due to file locking. For high availability, external RDS is required.</p>
     *
     * <p>Default: false</p>
     *
     * @return true if external database is supported but not required
     */
    boolean supportsDatabase() default false;
}
