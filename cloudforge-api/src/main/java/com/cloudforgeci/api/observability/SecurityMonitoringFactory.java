package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.core.annotation.BaseFactory;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.sns.Topic;
import software.constructs.Construct;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory for creating security monitoring and alerting configurations.
 * Provides CloudWatch alarms and SNS notifications based on security profiles.
 * Uses annotation-based context injection for cleaner code.
 */
public class SecurityMonitoringFactory extends BaseFactory {
    
    private static final Logger LOG = Logger.getLogger(SecurityMonitoringFactory.class.getName());
    
    public SecurityMonitoringFactory(Construct scope, String id) {
        super(scope, id);
    }
    
    @Override
    public void create() {
        // SecurityProfileConfiguration is now injected directly via annotation
        LOG.info("Creating security monitoring for security profile: " + ctx.security);
        
        // Only configure monitoring if enabled for this security profile
        if (!config.isSecurityMonitoringEnabled()) {
            LOG.info("Security monitoring disabled for security profile: " + ctx.security);
            return;
        }
        
        // Create SNS topic for security alerts
        Topic securityAlertsTopic = createSecurityAlertsTopic();
        
        // Configure CloudWatch alarms
        configureSecurityAlarms(securityAlertsTopic);
        
        // Configure flow log monitoring if enabled
        if (config.isFlowLogsEnabled()) {
            configureFlowLogMonitoring(securityAlertsTopic);
        }
        
        LOG.info("Security monitoring configuration completed for profile: " + ctx.security);
    }
    
    /**
     * Create SNS topic for security alerts.
     */
    private Topic createSecurityAlertsTopic() {
        String topicName = "security-alerts-" + ctx.security.name().toLowerCase();
        
        Topic topic = Topic.Builder.create(this, "SecurityAlertsTopic")
                .topicName(topicName)
                .displayName("Security Alerts for " + ctx.security + " Environment")
                .build();
        
        LOG.info("Created security alerts SNS topic: " + topicName);
        return topic;
    }
    
    /**
     * Configure CloudWatch alarms for security monitoring.
     */
    private void configureSecurityAlarms(Topic alertsTopic) {
        LOG.info("Configuring CloudWatch security alarms for profile: " + ctx.security);
        
        // Configure different alarm thresholds based on security profile
        double highCpuThreshold = getHighCpuThreshold(ctx.security);
        double highMemoryThreshold = getHighMemoryThreshold(ctx.security);
        double highNetworkThreshold = getHighNetworkThreshold(ctx.security);
        
        // High CPU Utilization Alarm
        createCpuAlarm(alertsTopic, highCpuThreshold);
        
        // High Memory Utilization Alarm  
        createMemoryAlarm(alertsTopic, highMemoryThreshold);
        
        // High Network Traffic Alarm
        createNetworkAlarm(alertsTopic, highNetworkThreshold);
        
        // Failed Login Attempts Alarm (for production)
        if (ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION) {
            createFailedLoginAlarm(alertsTopic);
        }
        
        // Unusual API Activity Alarm (for staging and production)
        if (ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.STAGING || ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION) {
            createUnusualApiActivityAlarm(alertsTopic);
        }
    }
    
    /**
     * Configure VPC Flow Log monitoring for security analysis.
     */
    private void configureFlowLogMonitoring(Topic alertsTopic) {
        LOG.info("Configuring VPC Flow Log monitoring for profile: " + ctx.security);
        
        // Monitor for rejected connections (potential security threats)
        createRejectedConnectionsAlarm(alertsTopic);
        
        // Monitor for unusual traffic patterns (production only)
        if (ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION) {
            createUnusualTrafficPatternAlarm(alertsTopic);
        }
    }
    
    /**
     * Create CPU utilization alarm.
     */
    private void createCpuAlarm(Topic alertsTopic, double threshold) {
        Metric cpuMetric = Metric.Builder.create()
                .namespace("AWS/ECS")
                .metricName("CPUUtilization")
                .dimensionsMap(Map.of("ServiceName", "jenkins-" + ctx.security.name().toLowerCase()))
                .statistic("Average")
                .period(Duration.minutes(5))
                .build();
        
        Alarm cpuAlarm = Alarm.Builder.create(this, "HighCpuAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-high-cpu")
                .alarmDescription("High CPU utilization detected in " + ctx.security + " environment")
                .metric(cpuMetric)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(2)
                .build();
        
        cpuAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created CPU alarm with threshold: " + threshold + "%");
    }
    
    /**
     * Create memory utilization alarm.
     */
    private void createMemoryAlarm(Topic alertsTopic, double threshold) {
        Metric memoryMetric = Metric.Builder.create()
                .namespace("AWS/ECS")
                .metricName("MemoryUtilization")
                .dimensionsMap(Map.of("ServiceName", "jenkins-" + ctx.security.name().toLowerCase()))
                .statistic("Average")
                .period(Duration.minutes(5))
                .build();
        
        Alarm memoryAlarm = Alarm.Builder.create(this, "HighMemoryAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-high-memory")
                .alarmDescription("High memory utilization detected in " + ctx.security + " environment")
                .metric(memoryMetric)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(2)
                .build();
        
        memoryAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created memory alarm with threshold: " + threshold + "%");
    }
    
    /**
     * Create network traffic alarm.
     */
    private void createNetworkAlarm(Topic alertsTopic, double threshold) {
        Metric networkMetric = Metric.Builder.create()
                .namespace("AWS/ECS")
                .metricName("NetworkIn")
                .dimensionsMap(Map.of("ServiceName", "jenkins-" + ctx.security.name().toLowerCase()))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        Alarm networkAlarm = Alarm.Builder.create(this, "HighNetworkAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-high-network")
                .alarmDescription("High network traffic detected in " + ctx.security + " environment")
                .metric(networkMetric)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(3)
                .build();
        
        networkAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created network alarm with threshold: " + threshold + " bytes");
    }
    
    /**
     * Create failed login attempts alarm (production only).
     */
    private void createFailedLoginAlarm(Topic alertsTopic) {
        if (!ctx.logs.get().isPresent()) {
            LOG.warning("Cannot create failed login alarm - logs not configured");
            return;
        }
        
        // Create metric filter for failed login attempts
        Metric failedLoginMetric = Metric.Builder.create()
                .namespace("CWLogs")
                .metricName("FailedLoginAttempts")
                .dimensionsMap(Map.of("Environment", ctx.security.name()))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        Alarm failedLoginAlarm = Alarm.Builder.create(this, "FailedLoginAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-failed-logins")
                .alarmDescription("Multiple failed login attempts detected in " + ctx.security + " environment")
                .metric(failedLoginMetric)
                .threshold(5.0) // 5 failed attempts in 5 minutes
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .evaluationPeriods(1)
                .build();
        
        failedLoginAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created failed login alarm for production environment");
    }
    
    /**
     * Create unusual API activity alarm.
     */
    private void createUnusualApiActivityAlarm(Topic alertsTopic) {
        Metric apiMetric = Metric.Builder.create()
                .namespace("AWS/ApplicationELB")
                .metricName("RequestCount")
                .dimensionsMap(Map.of("LoadBalancer", "jenkins-" + ctx.security.name().toLowerCase()))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        double threshold = ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION ? 1000.0 : 500.0;
        
        Alarm apiAlarm = Alarm.Builder.create(this, "UnusualApiActivityAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-unusual-api-activity")
                .alarmDescription("Unusual API activity detected in " + ctx.security + " environment")
                .metric(apiMetric)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(2)
                .build();
        
        apiAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created API activity alarm with threshold: " + threshold + " requests");
    }
    
    /**
     * Create rejected connections alarm from VPC Flow Logs.
     */
    private void createRejectedConnectionsAlarm(Topic alertsTopic) {
        Metric rejectedMetric = Metric.Builder.create()
                .namespace("CWLogs")
                .metricName("RejectedConnections")
                .dimensionsMap(Map.of("Environment", ctx.security.name()))
                .statistic("Sum")
                .period(Duration.minutes(5))
                .build();
        
        double threshold = ctx.security == com.cloudforgeci.api.interfaces.SecurityProfile.PRODUCTION ? 50.0 : 100.0;
        
        Alarm rejectedAlarm = Alarm.Builder.create(this, "RejectedConnectionsAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-rejected-connections")
                .alarmDescription("High number of rejected connections detected in " + ctx.security + " environment")
                .metric(rejectedMetric)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .evaluationPeriods(2)
                .build();
        
        rejectedAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created rejected connections alarm with threshold: " + threshold);
    }
    
    /**
     * Create unusual traffic pattern alarm (production only).
     */
    private void createUnusualTrafficPatternAlarm(Topic alertsTopic) {
        Metric trafficMetric = Metric.Builder.create()
                .namespace("CWLogs")
                .metricName("UnusualTrafficPatterns")
                .dimensionsMap(Map.of("Environment", ctx.security.name()))
                .statistic("Sum")
                .period(Duration.minutes(15))
                .build();
        
        Alarm trafficAlarm = Alarm.Builder.create(this, "UnusualTrafficAlarm")
                .alarmName("jenkins-" + ctx.security.name().toLowerCase() + "-unusual-traffic")
                .alarmDescription("Unusual traffic patterns detected in " + ctx.security + " environment")
                .metric(trafficMetric)
                .threshold(10.0) // 10 unusual patterns in 15 minutes
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .evaluationPeriods(1)
                .build();
        
        trafficAlarm.addAlarmAction(new SnsAction(alertsTopic));
        LOG.info("Created unusual traffic pattern alarm for production environment");
    }
    
    /**
     * Get CPU threshold based on security profile.
     */
    private double getHighCpuThreshold(com.cloudforgeci.api.interfaces.SecurityProfile profile) {
        return switch (profile) {
            case DEV -> 90.0;      // Relaxed for dev
            case STAGING -> 80.0;   // Moderate for staging
            case PRODUCTION -> 70.0; // Strict for production
        };
    }
    
    /**
     * Get memory threshold based on security profile.
     */
    private double getHighMemoryThreshold(com.cloudforgeci.api.interfaces.SecurityProfile profile) {
        return switch (profile) {
            case DEV -> 90.0;      // Relaxed for dev
            case STAGING -> 85.0;   // Moderate for staging
            case PRODUCTION -> 80.0; // Strict for production
        };
    }
    
    /**
     * Get network threshold based on security profile.
     */
    private double getHighNetworkThreshold(com.cloudforgeci.api.interfaces.SecurityProfile profile) {
        return switch (profile) {
            case DEV -> 1000000000.0;    // 1GB for dev
            case STAGING -> 500000000.0;  // 500MB for staging
            case PRODUCTION -> 100000000.0; // 100MB for production
        };
    }
}
