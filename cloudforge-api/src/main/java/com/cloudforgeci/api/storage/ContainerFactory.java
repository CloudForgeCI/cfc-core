package com.cloudforgeci.api.storage;


import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.constructs.Construct;

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_CONTAINER_PATH;
import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_HOME;
import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_PORT;

public class ContainerFactory extends BaseFactory {

    @com.cloudforgeci.api.core.annotation.SystemContext
    private SystemContext ctx;

    private final ContainerImage image;

    public ContainerFactory(Construct scope, String id, ContainerImage image) {
        super(scope, id);
        this.image = image;
    }

    @Override
    public void create() {
        ContainerDefinition container = ctx.fargateTaskDef.get().orElseThrow().addContainer(getNode().getId() + "Container",
                ContainerDefinitionOptions.builder()
                        .containerName(getNode().getId())
                        .image(image)
                        .user("1000:1000")
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(ctx.logs.get().isPresent() ? ctx.logs.get().orElseThrow() : null)
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
