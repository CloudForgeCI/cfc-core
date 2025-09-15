package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.SystemContext;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;

public class AlarmFactory extends Construct {
  private final Props p;
  public static class Props {
    //public final IApplicationLoadBalancer alb;
    public Props() {  }
  }
  public AlarmFactory(Construct scope, String id, Props p) {
    super(scope, id);
    this.p = p;
    SystemContext ctx = SystemContext.of(this);

    Metric m = ctx.alb.get().orElseThrow().getMetrics().httpCodeElb(HttpCodeElb.ELB_5XX_COUNT);
    Alarm.Builder.create(this, "Alb5xx").metric(m).threshold(5).evaluationPeriods(1).build();
  }
}
