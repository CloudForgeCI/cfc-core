package com.cloudforgeci.api.scaling;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.interfaces.ApplicationSpec;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.constructs.Construct;

public class ScalingFactory extends BaseFactory {

  @DeploymentContext("minInstanceCapacity")
  private Integer minInstanceCapacity;

  @DeploymentContext("maxInstanceCapacity")
  private Integer maxInstanceCapacity;

  @DeploymentContext("cpuTargetUtilization")
  private Integer cpuTargetUtilization;

  @DeploymentContext("enableAutoScaling")
  private Boolean enableAutoScaling;

  @SystemContext("applicationSpec")
  private ApplicationSpec applicationSpec;

  public ScalingFactory(Construct scope, String id) {
    super(scope, id);
  }

  @Override
  public void create() {
    // ScalingFactory doesn't create infrastructure directly
    // It provides scaling methods for other factories to use
    // The create() method is required by BaseFactory but not used for this factory
  }

  /**
   * Fails synthesis when a deployment requests more than one instance
   * ({@code minInstanceCapacity}/{@code maxInstanceCapacity} above 1, or {@code enableAutoScaling})
   * for an application that can't safely run that way, in either of two distinct senses:
   *
   * <ul>
   *   <li>{@link com.cloudforge.core.interfaces.ApplicationSpec#supportsAutoScaling()} is {@code
   *   false} -- no clustering/HA support at all in this edition, such as the Jenkins controller or
   *   the Nexus/SonarQube community editions.</li>
   *   <li>{@link com.cloudforge.core.interfaces.ApplicationSpec#requiresSequentialDeploymentWithoutDatabase()}
   *   is {@code true} and no managed database connection is provisioned ({@code
   *   ctx.dbConnection} empty) -- a single-writer embedded-file database (e.g. CloudForge
   *   Manager's own H2) has no way to coordinate more than one writer; two replicas both racing
   *   to become that database's primary is exactly the deployment-time version of the same
   *   problem {@code FargateFactory}'s {@code minHealthyPercent(0)}/{@code maxHealthyPercent(100)}
   *   rollout override already defends against for a single *replacement*, but that override
   *   doesn't -- can't -- prevent two simultaneously-running peers when {@code desiredCount > 1}
   *   itself is the actual request. A managed database handles concurrent writers safely, so this
   *   only applies while none is configured.</li>
   * </ul>
   *
   * UIs may hide these fields, but this check is the enforcement point.
   *
   * <p>Called from both {@link #scale(FargateService)} and {@link #scale(AutoScalingGroup)} --
   * the EC2 runtime's own paths to more than one instance -- and, standalone (public, for exactly
   * this reason), from {@code FargateFactory#create()}: Fargate's OWN scaling wiring lives in each
   * topology configuration class's {@code wireBaseAutoscalingAndDns} (e.g. {@code
   * CmsServiceTopologyConfiguration}), already correctly guarded against double-registering
   * {@code autoScaleTaskCount} -- {@link #scale(FargateService)} itself is never invoked for a
   * real Fargate deploy, only this validation is.
   */
  public void rejectUnsupportedAutoScaling() {
    if (applicationSpec == null) {
      return;
    }
    boolean requestedMultiInstance = (minInstanceCapacity != null && minInstanceCapacity > 1)
        || (maxInstanceCapacity != null && maxInstanceCapacity > 1)
        || Boolean.TRUE.equals(enableAutoScaling);
    if (!requestedMultiInstance) {
      return;
    }
    if (!applicationSpec.supportsAutoScaling()) {
      throw new IllegalArgumentException(applicationSpec.displayName()
          + " does not support running more than one instance (no clustering/HA support in this "
          + "edition) -- set minInstanceCapacity/maxInstanceCapacity to 1 and enableAutoScaling to "
          + "false.");
    }
    if (applicationSpec.requiresSequentialDeploymentWithoutDatabase() && ctx.dbConnection.get().isEmpty()) {
      throw new IllegalArgumentException(applicationSpec.displayName()
          + " uses a single-writer embedded database with no managed database connection "
          + "provisioned -- running more than one instance would race multiple writers against "
          + "the same database file. Either set minInstanceCapacity/maxInstanceCapacity to 1 and "
          + "enableAutoScaling to false, or provision a managed database (see DatabaseSpec) so "
          + "concurrent writers are handled safely.");
    }
  }

  public void scale(final FargateService service) {
    rejectUnsupportedAutoScaling();

    // Check if auto-scaling is explicitly disabled
    if (Boolean.FALSE.equals(enableAutoScaling)) {
      return; // Auto-scaling explicitly disabled
    }

    // Only enable scaling if maxInstanceCapacity > 1
    // Use injected DeploymentContext values via annotations
    if (maxInstanceCapacity == null || maxInstanceCapacity <= 1) {
      return; // No scaling configuration
    }

    // Use injected values or defaults
    int minCapacity = minInstanceCapacity != null ? minInstanceCapacity : 1;
    int maxCapacity = maxInstanceCapacity;
    int targetUtilization = cpuTargetUtilization != null ? cpuTargetUtilization : 60;

    ScalableTaskCount scalable = service.autoScaleTaskCount(
        EnableScalingProps.builder()
            .minCapacity(minCapacity)
            .maxCapacity(maxCapacity)
            .build());

    scalable.scaleOnCpuUtilization("CpuScaleSvc",
        CpuUtilizationScalingProps.builder()
            .targetUtilizationPercent(targetUtilization)
            .scaleInCooldown(Duration.minutes(2))
            .scaleOutCooldown(Duration.minutes(2))
            .build());
  }

  public void scale(final AutoScalingGroup asg) {
    rejectUnsupportedAutoScaling();

    // Check if auto-scaling is explicitly disabled
    if (Boolean.FALSE.equals(enableAutoScaling)) {
      return; // Auto-scaling explicitly disabled
    }

    // Use injected cpuTargetUtilization or default to 60%
    int targetUtilization = cpuTargetUtilization != null ? cpuTargetUtilization : 60;

    asg.scaleOnCpuUtilization("CpuScaleAsg",
            software.amazon.awscdk.services.autoscaling.CpuUtilizationScalingProps.builder()
                    .targetUtilizationPercent(targetUtilization)
                    .cooldown(Duration.minutes(2))
                    .build());
  }


}
