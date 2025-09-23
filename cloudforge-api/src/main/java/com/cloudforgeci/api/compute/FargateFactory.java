package com.cloudforgeci.api.compute;


import com.cloudforgeci.api.core.DeploymentContext;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.storage.ContainerFactory;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.AccessPointOptions;
import software.amazon.awscdk.services.efs.Acl;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_PATH;

public class FargateFactory extends Construct {


  private final Props p;

  public record Props(DeploymentContext cfc) {}

  public FargateFactory(Construct scope, String id, Props p) {
    super(scope, id);
    this.p = p;
    SystemContext ctx = SystemContext.of(this);

    Role executionRole = Role.Builder.create(this, "TaskExecutionRole")
            .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
            .managedPolicies(Arrays.asList(
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
            ))
            .build();
    FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "Task").cpu(ctx.cfc.cpu()).memoryLimitMiB(ctx.cfc.memory()).taskRole(executionRole).build();
    AccessPoint ap = ctx.efs.get().orElseThrow().addAccessPoint("JenkinsAp", AccessPointOptions.builder()
            .path(JENKINS_PATH)
            .posixUser(PosixUser.builder().uid("1000").gid("1000").build())
            .createAcl(Acl.builder().ownerUid("1000").ownerGid("1000").permissions("750").build())
            .build());
    ctx.ap.set(ap);
    Cluster cluster = Cluster.Builder.create(this, "Cluster").vpc(ctx.vpc.get().orElseThrow()).build();
    SecurityGroup serviceSg = SecurityGroup.Builder.create(this, id + "SvcSg")
            .vpc(ctx.vpc.get().orElseThrow())
            .allowAllOutbound(true).build();
    ctx.fargateServiceSg.set(serviceSg);
    FargateService service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1)
            .assignPublicIp(true).build();
    
    // Set task definition in context first (needed by ContainerFactory)
    taskDef.getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
            .actions(List.of("elasticfilesystem:ClientMount","elasticfilesystem:ClientWrite","elasticfilesystem:ClientRootAccess"))
            .resources(List.of(ctx.efs.get().orElseThrow().getFileSystemArn(), ctx.ap.get().orElseThrow().getAccessPointArn()))
            .build());
    ctx.fargateTaskDef.set(taskDef);
    
    // Create container (now that task definition is available)
    new ContainerFactory(scope, id + "Container", ContainerImage.fromRegistry("jenkins/jenkins:lts"));
    
    // Now set the service in context after container is created
    ctx.fargateService.set(service);

    // Note: Auto-scaling is handled by JenkinsServiceTopologyConfiguration
    // to avoid conflicts with duplicate auto-scaling configuration

  }

}
