package com.cloudforgeci.api.compute;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforgeci.api.core.annotation.DeploymentContext;
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

import static com.cloudforgeci.api.interfaces.Constants.Jenkins.JENKINS_HOME;
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
 * FargateFactory factory = new FargateFactory(scope, "JenkinsFargate");
 * factory.create();
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
public class FargateFactory extends BaseFactory {

  @DeploymentContext("bastionCidr")
  private String bastionCidr;

  @DeploymentContext("cpu")
  private Integer cpu;

  @DeploymentContext("memory")
  private Integer memory;

  @DeploymentContext("minInstanceCapacity")
  private Integer minInstanceCapacity;

  @DeploymentContext("networkMode")
  private String networkMode;

  /**
   * Creates a new FargateFactory instance.
   *
   * @param scope The CDK construct scope
   * @param id Unique identifier for the Fargate factory
   */
  public FargateFactory(Construct scope, String id) {
    super(scope, id);
    // bastionCidr, cpu, memory, minInstanceCapacity, and networkMode are automatically injected by BaseFactory
  }

  @Override
  public void create() {
    // Task execution role - for pulling images, writing logs, etc.
    Role executionRole = Role.Builder.create(this, "TaskExecutionRole")
            .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
            .managedPolicies(Arrays.asList(
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
            ))
            .build();

    // Task role - for application permissions within the container
    // Conditionally add ECS Exec permissions if bastionCidr is configured
    Role.Builder taskRoleBuilder = Role.Builder.create(this, "TaskRole")
            .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build());

    // Only add SSM permissions for ECS Exec if bastionCidr is configured
    // This indicates the user wants remote access capabilities
    boolean enableEcsExec = bastionCidr != null && !bastionCidr.isBlank();
    if (enableEcsExec) {
        taskRoleBuilder.managedPolicies(Arrays.asList(
                // Required for ECS Exec to work (SSM Session Manager access)
                ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")
        ));
    }

    Role taskRole = taskRoleBuilder.build();

    FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "Task")
            .cpu(cpu)
            .memoryLimitMiB(memory)
            .executionRole(executionRole)
            .taskRole(taskRole)
            .build();
    AccessPoint ap = ctx.efs.get().orElseThrow().addAccessPoint("JenkinsAp", AccessPointOptions.builder()
            .path(JENKINS_PATH)
            .posixUser(PosixUser.builder().uid("1000").gid("1000").build())
            .createAcl(Acl.builder().ownerUid("1000").ownerGid("1000").permissions("750").build())
            .build());
    ctx.ap.set(ap);
    Cluster cluster = Cluster.Builder.create(this, "Cluster").vpc(ctx.vpc.get().orElseThrow()).build();
    SecurityGroup serviceSg = SecurityGroup.Builder.create(this, getNode().getId() + "SvcSg")
            .vpc(ctx.vpc.get().orElseThrow())
            .allowAllOutbound(true).build();
    ctx.fargateServiceSg.set(serviceSg);
    // Determine subnet type and public IP assignment based on network mode
    boolean assignPublicIp = "public-no-nat".equals(networkMode);
    SubnetType subnetType = assignPublicIp ? SubnetType.PUBLIC : SubnetType.PRIVATE_WITH_EGRESS;

    // Enable ECS Exec only if bastionCidr is configured (indicates remote access needed)
    FargateService service = FargateService.Builder.create(this, "Service")
            .cluster(cluster)
            .securityGroups(List.of(serviceSg))
            .taskDefinition(taskDef)
            .desiredCount(minInstanceCapacity != null ? minInstanceCapacity : 1)
            .assignPublicIp(assignPublicIp)
            .vpcSubnets(SubnetSelection.builder().subnetType(subnetType).build())
            .enableExecuteCommand(enableEcsExec)  // Enable ECS Exec for shell access when bastionCidr is set
            .enableEcsManagedTags(true)  // Helps CloudFormation track and clean up ENIs on stack deletion
            .circuitBreaker(DeploymentCircuitBreaker.builder()
                    .enable(true)
                    .rollback(true)
                    .build())  // Prevents stuck deployments and enables automatic rollback
            .build();
    
    // Set task definition in context first (needed by ContainerFactory)
    // Note: EFS permissions are handled by IAMRules based on security profile
    ctx.fargateTaskDef.set(taskDef);
    
    // Add EFS volume to task definition (needed by ContainerFactory)
    ctx.fargateTaskDef.get().orElseThrow().addVolume(Volume.builder()
            .name(JENKINS_HOME)
            .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                    .fileSystemId(ctx.efs.get().orElseThrow().getFileSystemId())
                    .transitEncryption("ENABLED")
                    .authorizationConfig(AuthorizationConfig.builder()
                            .accessPointId(ctx.ap.get().orElseThrow().getAccessPointId())
                            .iam("ENABLED")
                            .build())
                    .build())
            .build());
    
    // Create container (now that task definition and volume are available)
    ContainerFactory containerFactory = new ContainerFactory(this, getNode().getId() + "Container", ContainerImage.fromRegistry("jenkins/jenkins:lts"));
    containerFactory.create();
    
    // Now set the service in context after container is created
    ctx.fargateService.set(service);

    // Note: Auto-scaling is handled by JenkinsServiceTopologyConfiguration
    // to avoid conflicts with duplicate auto-scaling configuration
  }

}
