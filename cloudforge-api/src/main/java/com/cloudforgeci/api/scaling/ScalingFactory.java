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

  public void scale(final FargateService service) {
    ScalableTaskCount scalable = service.autoScaleTaskCount(EnableScalingProps.builder().minCapacity(1).maxCapacity(5).build());
    scalable.scaleOnCpuUtilization("CpuScaleSvc", CpuUtilizationScalingProps.builder().targetUtilizationPercent(60).scaleInCooldown(Duration.minutes(2)).scaleOutCooldown(Duration.minutes(2)).build());
  }

  public void scale(final AutoScalingGroup asg) {
    asg.scaleOnCpuUtilization("CpuScaleAsg",
            software.amazon.awscdk.services.autoscaling.CpuUtilizationScalingProps.builder().targetUtilizationPercent(60).cooldown(Duration.minutes(2)).build());
  }


}
