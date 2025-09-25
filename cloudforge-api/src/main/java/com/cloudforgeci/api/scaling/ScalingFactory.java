package com.cloudforgeci.api.scaling;


import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.constructs.Construct;

public class ScalingFactory extends BaseFactory {

  @com.cloudforgeci.api.core.annotation.SystemContext
  private SystemContext ctx;

  public ScalingFactory(Construct scope, String id) {
    super(scope, id);
  }

  @Override
  public void create() {
    // ScalingFactory doesn't create infrastructure directly
    // It provides scaling methods for other factories to use
    // The create() method is required by BaseFactory but not used for this factory
  }

  public void scale(final FargateService service, SystemContext ctx) {
    // Only enable scaling if maxInstanceCapacity > 1
    if (ctx.cfc.maxInstanceCapacity() == null || ctx.cfc.maxInstanceCapacity() <= 1) {
      return; // No scaling configuration
    }
    
    // Use DeploymentContext values or defaults
    int minCapacity = ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1;
    int maxCapacity = ctx.cfc.maxInstanceCapacity(); // Already checked for null above
    int targetUtilization = ctx.cfc.cpuTargetUtilization() != null ? ctx.cfc.cpuTargetUtilization() : 60;
    
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

  public void scale(final AutoScalingGroup asg, SystemContext ctx) {
    // Use DeploymentContext cpuTargetUtilization or default to 60%
    int targetUtilization = ctx.cfc.cpuTargetUtilization() != null ? ctx.cfc.cpuTargetUtilization() : 60;
    
    asg.scaleOnCpuUtilization("CpuScaleAsg",
            software.amazon.awscdk.services.autoscaling.CpuUtilizationScalingProps.builder()
                    .targetUtilizationPercent(targetUtilization)
                    .cooldown(Duration.minutes(2))
                    .build());
  }


}
