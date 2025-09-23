package com.cloudforgeci.api.scaling;


import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.constructs.Construct;

public class ScalingFactory extends Construct {


  public ScalingFactory(Construct scope, String id) {
    super(scope, id);
    SystemContext ctx = SystemContext.of(this);

  }

  public void scale(final FargateService service, SystemContext ctx) {
    // Use DeploymentContext values or defaults
    int minCapacity = ctx.cfc.minInstanceCapacity() != null ? ctx.cfc.minInstanceCapacity() : 1;
    int maxCapacity = ctx.cfc.maxInstanceCapacity() != null ? ctx.cfc.maxInstanceCapacity() : 5;
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
