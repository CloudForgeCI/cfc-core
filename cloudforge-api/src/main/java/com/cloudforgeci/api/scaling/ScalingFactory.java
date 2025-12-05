package com.cloudforgeci.api.scaling;


import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.DeploymentContext;
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

  public ScalingFactory(Construct scope, String id) {
    super(scope, id);
  }

  @Override
  public void create() {
    // ScalingFactory doesn't create infrastructure directly
    // It provides scaling methods for other factories to use
    // The create() method is required by BaseFactory but not used for this factory
  }

  public void scale(final FargateService service) {
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
