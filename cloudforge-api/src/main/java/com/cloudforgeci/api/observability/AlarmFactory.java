package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.enums.SecurityProfile;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.sns.Topic;
import software.constructs.Construct;

import java.util.logging.Logger;

/**
 * Creates CloudWatch alarms for application health monitoring.
 *
 * <p>This factory sets up alarms to notify you when your infrastructure isn't behaving
 * as expected. Alarms are automatically tuned based on your security profile - stricter
 * thresholds in production, more relaxed in development.</p>
 *
 * <h2>Alarms Created</h2>
 * <ul>
 *   <li><b>ALB 5xx Errors</b> - Backend server errors indicating infrastructure problems</li>
 *   <li><b>ALB 4xx Errors</b> - Client errors that might indicate misconfiguration</li>
 *   <li><b>High Response Time</b> - Performance degradation alerts</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * new AlarmFactory(this, "Alarms", new AlarmFactory.Props());
 * }</pre>
 *
 * <p>The factory automatically connects to your load balancer through the context
 * system. All alarms adapt to your environment - production gets strict monitoring,
 * development stays lenient to avoid alert fatigue.</p>
 *
 * <h2>Threshold Examples</h2>
 * <table border = "1">
 *   <tr><th>Alarm</th><th>Dev</th><th>Staging</th><th>Production</th></tr>
 *   <tr><td>5xx Errors</td><td>10/min</td><td>7/min</td><td>5/min</td></tr>
 *   <tr><td>4xx Errors</td><td>50/5min</td><td>30/5min</td><td>20/5min</td></tr>
 *   <tr><td>Response Time</td><td>5s</td><td>3s</td><td>2s</td></tr>
 * </table>
 *
 * @see SecurityMonitoringFactory for security-focused monitoring (CPU, memory, logins)
 * @see com.cloudforgeci.api.core.DeploymentContext for custom alarm thresholds
 */
public class AlarmFactory extends BaseFactory {

  private static final Logger LOG = Logger.getLogger(AlarmFactory.class.getName());

  private final Props p;

  @SystemContext("security")
  private SecurityProfile security;

  @SystemContext("alb")
  private IApplicationLoadBalancer alb;

  /**
   * Configuration properties for alarm creation.
   *
   * <p>Currently a placeholder for future extensibility. Future versions may include
   * custom thresholds, notification targets, or alarm-specific settings.</p>
   */
  public static class Props {
    //public final IApplicationLoadBalancer alb;
    public Props() {  }
  }

  /**
   * Creates a new alarm factory.
   *
   * @param scope the CDK construct scope
   * @param id the construct ID
   * @param p configuration properties (reserved for future use)
   */
  public AlarmFactory(Construct scope, String id, Props p) {
    super(scope, id);
    this.p = p;
    // Props not currently used but kept for future extensibility
  }

  /**
   * Creates CloudWatch alarms for the application load balancer.
   *
   * <p>Sets up multiple alarms covering error rates and response times.
   * All thresholds adapt automatically to your security profile for appropriate
   * sensitivity in each environment.</p>
   *
   * <p><b>Lifecycle:</b> Alarms are automatically deleted when the CloudFormation
   * stack is destroyed. No manual cleanup required.</p>
   */
  @Override
  public void create() {
    LOG.info("Creating ALB alarms for security profile: " + security);

    // Create SNS topic for alarm notifications (PCI-DSS Req 10.6 compliance)
    Topic alarmTopic = createAlarmTopic();

    create5xxAlarm(alb, alarmTopic);
    create4xxAlarm(alb, alarmTopic);
    createResponseTimeAlarm(alb, alarmTopic);

    LOG.info("ALB alarms created successfully for profile: " + security);
  }

  /**
   * Create SNS topic for alarm notifications.
   * Required for PCI-DSS Req 10.6 (review logs daily for suspicious activity).
   */
  private Topic createAlarmTopic() {
    String topicName = "alb-alarms-" + security.name().toLowerCase();

    Topic topic = Topic.Builder.create(this, "AlbAlarmsTopic")
        .topicName(topicName)
        .displayName("ALB Alarms for " + security + " Environment")
        .build();

    LOG.info("Created ALB alarms SNS topic: " + topicName);
    LOG.info("  Subscribe to this topic to receive alarm notifications");
    LOG.info("  Topic ARN: " + topic.getTopicArn());
    return topic;
  }

  /**
   * Creates alarm for 5xx server errors.
   * These indicate backend problems like crashes, timeouts, or misconfigurations.
   */
  private void create5xxAlarm(IApplicationLoadBalancer alb, Topic alarmTopic) {
    double threshold = switch (security) {
      case DEV -> 10.0;       // Allow more errors in dev
      case STAGING -> 7.0;    // Moderate threshold
      case PRODUCTION -> 5.0; // Strict monitoring in prod
    };

    Metric metric = alb.getMetrics().httpCodeElb(HttpCodeElb.ELB_5XX_COUNT,
        MetricOptions.builder()
            .statistic("Sum")
            .period(Duration.minutes(1))
            .build());

    Alarm alarm = Alarm.Builder.create(this, "Alb5xxErrors")
        .alarmName("alb-5xx-errors-" + security.name().toLowerCase())
        .alarmDescription("High rate of 5xx errors from ALB in " + security + " environment")
        .metric(metric)
        .threshold(threshold)
        .evaluationPeriods(1)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();

    // Add SNS action for PCI-DSS compliance (Req 10.6: review logs daily)
    alarm.addAlarmAction(new SnsAction(alarmTopic));

    LOG.info("Created 5xx alarm with threshold: " + threshold);
  }

  /**
   * Creates alarm for 4xx client errors.
   * High 4xx rates might indicate misconfiguration, broken links, or authentication issues.
   */
  private void create4xxAlarm(IApplicationLoadBalancer alb, Topic alarmTopic) {
    double threshold = switch (security) {
      case DEV -> 50.0;       // Very lenient in dev
      case STAGING -> 30.0;   // Moderate threshold
      case PRODUCTION -> 20.0; // Catch issues early in prod
    };

    Metric metric = alb.getMetrics().httpCodeElb(HttpCodeElb.ELB_4XX_COUNT,
        MetricOptions.builder()
            .statistic("Sum")
            .period(Duration.minutes(5))
            .build());

    Alarm alarm = Alarm.Builder.create(this, "Alb4xxErrors")
        .alarmName("alb-4xx-errors-" + security.name().toLowerCase())
        .alarmDescription("High rate of 4xx errors from ALB in " + security + " environment")
        .metric(metric)
        .threshold(threshold)
        .evaluationPeriods(2)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();

    // Add SNS action for PCI-DSS compliance (Req 10.6: review logs daily)
    alarm.addAlarmAction(new SnsAction(alarmTopic));

    LOG.info("Created 4xx alarm with threshold: " + threshold);
  }

  /**
   * Creates alarm for high response times.
   * Slow responses indicate performance issues, database problems, or insufficient capacity.
   */
  private void createResponseTimeAlarm(IApplicationLoadBalancer alb, Topic alarmTopic) {
    double thresholdSeconds = switch (security) {
      case DEV -> 5.0;        // Lenient in dev
      case STAGING -> 3.0;    // Moderate threshold
      case PRODUCTION -> 2.0; // Fast response required in prod
    };

    Metric metric = alb.getMetrics().targetResponseTime(
        MetricOptions.builder()
            .statistic("Average")
            .period(Duration.minutes(5))
            .build());

    Alarm alarm = Alarm.Builder.create(this, "HighResponseTime")
        .alarmName("alb-high-response-time-" + security.name().toLowerCase())
        .alarmDescription("ALB response time exceeds " + thresholdSeconds + "s in " + security + " environment")
        .metric(metric)
        .threshold(thresholdSeconds)
        .evaluationPeriods(2)
        .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
        .treatMissingData(TreatMissingData.NOT_BREACHING)
        .build();

    // Add SNS action for PCI-DSS compliance (Req 10.6: review logs daily)
    alarm.addAlarmAction(new SnsAction(alarmTopic));

    LOG.info("Created response time alarm with threshold: " + thresholdSeconds + "s");
  }
}
