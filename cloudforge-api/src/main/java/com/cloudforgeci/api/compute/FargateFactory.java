package com.cloudforgeci.api.compute;


import com.cloudforgeci.api.core.DeploymentContext;

import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.storage.ContainerFactory;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.AccessPointOptions;
import software.amazon.awscdk.services.efs.Acl;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_PATH;

/**
 * Factory for creating Fargate-based Jenkins compute infrastructure.
 * 
 * <p>This factory creates and configures AWS Fargate services for Jenkins deployments,
 * providing a serverless container-based approach. It respects network mode configuration
 * to place tasks in appropriate subnets and handles EFS integration for persistent storage.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Fargate task definitions with Jenkins container</li>
 *   <li>ECS cluster and service configuration</li>
 *   <li>EFS access point integration for persistent storage</li>
 *   <li>IAM roles for task execution and EFS access</li>
 *   <li>Network mode awareness (public vs private subnets)</li>
 *   <li>Security group configuration</li>
 * </ul>
 * 
 * <p><strong>Network Configuration:</strong></p>
 * <ul>
 *   <li><strong>public-no-nat:</strong> Tasks get public IPs and use public subnets</li>
 *   <li><strong>private-with-nat:</strong> Tasks use private subnets with NAT gateway</li>
 * </ul>
 * 
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * FargateFactory factory = new FargateFactory(scope, "JenkinsFargate", 
 *     new FargateFactory.Props(cfc));
 * 
 * // Access created resources
 * FargateService service = ctx.fargateService.get().orElseThrow();
 * FargateTaskDefinition taskDef = ctx.fargateTaskDef.get().orElseThrow();
 * }</pre>
 * 
 * @author CloudForgeCI
 * @since 1.0.0
 * @see DeploymentContext
 * @see SystemContext
 * @see ContainerFactory
 * @see DeploymentContext#networkMode()
 */
public class FargateFactory extends Construct {


  private final Props p;

  /**
   * Configuration properties for FargateFactory.
   * 
   * @param cfc Deployment context containing configuration parameters
   */
  public record Props(DeploymentContext cfc) {}

  /**
   * Creates a new FargateFactory instance.
   * 
   * <p>This constructor initializes the factory and creates the complete Fargate
   * infrastructure including task definitions, services, and EFS integration.</p>
   * 
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Fargate factory
   * @param p Configuration properties containing deployment context
   */
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
    // Determine subnet type and public IP assignment based on network mode
    boolean assignPublicIp = "public-no-nat".equals(ctx.cfc.networkMode());
    SubnetType subnetType = assignPublicIp ? SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;
    
    FargateService service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1)
            .assignPublicIp(assignPublicIp)
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .build();
    
    // Set task definition in context first (needed by ContainerFactory)
    // Note: EFS permissions are handled by IAMRules based on security profile
    ctx.fargateTaskDef.set(taskDef);
    
    // Create container (now that task definition is available)
    new ContainerFactory(scope, id + "Container", ContainerImage.fromRegistry("jenkins/jenkins:lts"));
    
    // Now set the service in context after container is created
    ctx.fargateService.set(service);

    // Note: Auto-scaling is handled by JenkinsServiceTopologyConfiguration
    // to avoid conflicts with duplicate auto-scaling configuration

  }

}
