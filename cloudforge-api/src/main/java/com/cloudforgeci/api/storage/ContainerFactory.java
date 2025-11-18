package com.cloudforgeci.api.storage;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
import com.cloudforgeci.api.core.annotation.SystemContext;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_CONTAINER_PATH;
import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_HOME;
import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_PORT;

public class ContainerFactory extends BaseFactory {

    private final ContainerImage image;

    @DeploymentContext("fqdn")
    private String fqdn;

    @DeploymentContext("enableSsl")
    private Boolean enableSsl;

    @SystemContext("fargateTaskDef")
    private TaskDefinition fargateTaskDef;

    @SystemContext("logs")
    private LogGroup logs;

    public ContainerFactory(Construct scope, String id, ContainerImage image) {
        super(scope, id);
        this.image = image;
        // fqdn and enableSsl are automatically injected by BaseFactory
    }

    @Override
    public void create() {
        // Build environment variables for Jenkins configuration
        java.util.Map<String, String> environment = new java.util.HashMap<>();

        // Get configuration values from annotated fields
        boolean sslEnabled = Boolean.TRUE.equals(enableSsl);

        // Configure JAVA_OPTS for Jenkins
        StringBuilder javaOpts = new StringBuilder();

        // Configure Jenkins reverse proxy settings for ALB
        // This is CRITICAL to fix 403 CSRF errors when behind a load balancer

        // Allow Jenkins to trust X-Forwarded-* headers from ALB
        javaOpts.append("-Dorg.eclipse.jetty.server.Request.maxFormContentSize=1000000 ");

        // Disable CSP that can interfere with reverse proxy
        javaOpts.append("-Dhudson.model.DirectoryBrowserSupport.CSP=\"\" ");

        // Configure Jenkins to properly handle reverse proxy headers
        // This ensures Jenkins knows the correct external URL for CSRF token validation
        if (fqdn != null && !fqdn.isBlank()) {
            javaOpts.append("-Dhudson.TcpSlaveAgentListener.hostName=").append(fqdn).append(" ");

            // Set Jenkins root URL directly via system property
            // This fixes "reverse proxy setup is broken" errors
            String jenkinsRootUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            javaOpts.append("-Djenkins.model.Jenkins.rootUrl=").append(jenkinsRootUrl).append(" ");
        }

        // Note: NOT skipping setup wizard - user needs to go through initial Jenkins setup
        // to configure admin user, install plugins, etc.

        // Configure JENKINS_OPTS for reverse proxy support
        StringBuilder jenkinsOpts = new StringBuilder();
        jenkinsOpts.append("--httpListenAddress=0.0.0.0 ");
        jenkinsOpts.append("--httpsPort=-1 ");  // Disable direct HTTPS on Jenkins (ALB handles SSL)

        // Set Jenkins URL for proper CSRF token generation
        if (fqdn != null && !fqdn.isBlank()) {
            String jenkinsUrl = (sslEnabled ? "https://" : "http://") + fqdn;
            environment.put("JENKINS_URL", jenkinsUrl);
        }

        environment.put("JENKINS_OPTS", jenkinsOpts.toString().trim());

        if (javaOpts.length() > 0) {
            environment.put("JAVA_OPTS", javaOpts.toString().trim());
        }

        ContainerDefinition container = fargateTaskDef.addContainer(getNode().getId() + "Container",
                ContainerDefinitionOptions.builder()
                        .containerName(getNode().getId())
                        .image(image)
                        .user("1000:1000")
                        .environment(environment.isEmpty() ? null : environment)
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(logs)
                                .streamPrefix("jenkins").build()))
                        .build());

        container.addPortMappings(PortMapping
                .builder()
                .containerPort(JENKINS_PORT)
                .build());

        container.addMountPoints(MountPoint
                .builder()
                .containerPath(JENKINS_CONTAINER_PATH)
                .sourceVolume(JENKINS_HOME)
                .readOnly(false)
                .build());
        ctx.container.set(container);
    }

}
