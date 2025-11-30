/**
 * Observability and security monitoring components for CloudForge infrastructure.
 *
 * <p>This package provides comprehensive monitoring, logging, and security protection
 * for your Jenkins infrastructure. Everything automatically adapts to your security
 * profile (DEV, STAGING, or PRODUCTION) with sensible defaults.</p>
 *
 * <h2>What's Included</h2>
 *
 * <h3>Alarms and Notifications</h3>
 * <p>{@link com.cloudforgeci.api.observability.AlarmFactory} creates CloudWatch alarms
 * to alert you when things go wrong - like too many 5xx errors from your load balancer.</p>
 *
 * <h3>Security Monitoring</h3>
 * <p>{@link com.cloudforgeci.api.observability.SecurityMonitoringFactory} watches for
 * security issues like high CPU usage, failed logins, or unusual API activity. Thresholds
 * are stricter in production and more relaxed in dev.</p>
 *
 * <h3>Web Application Firewall (WAF)</h3>
 * <p>{@link com.cloudforgeci.api.observability.WafFactory} protects your Jenkins from
 * common web attacks like SQL injection and cross-site scripting. It uses AWS managed
 * rules tuned specifically for Jenkins to avoid false positives.</p>
 *
 * <h3>Logging</h3>
 * <ul>
 *   <li>{@link com.cloudforgeci.api.observability.FlowLogFactory} - Captures network
 *       traffic for security analysis and troubleshooting</li>
 *   <li>{@link com.cloudforgeci.api.observability.LoggingCwFactory} - Centralizes
 *       application logs in CloudWatch for easy searching and alerting</li>
 * </ul>
 *
 * <h3>Compliance and Auditing</h3>
 * <p>{@link com.cloudforgeci.api.observability.ComplianceFactory} sets up CloudTrail,
 * AWS Config, and Audit Manager for compliance frameworks like PCI-DSS, HIPAA, SOC2,
 * and GDPR. Perfect for regulated industries.</p>
 *
 * <h3>Threat Detection</h3>
 * <p>{@link com.cloudforgeci.api.observability.GuardDutyFactory} enables AWS GuardDuty
 * for intelligent threat detection using machine learning.</p>
 *
 * <h2>Getting Started</h2>
 *
 * <p>Most factories work automatically when you extend {@link com.cloudforgeci.api.core.annotation.BaseFactory}
 * and use the {@code @SystemContext} annotation to inject your security profile:</p>
 *
 * <pre>{@code
 * public class MyMonitoring extends BaseFactory {
 *     @SystemContext("security")
 *     private SecurityProfile security;
 *
 *     @Override
 *     public void create() {
 *         // Security profile automatically injected
 *         // Configuration automatically loaded
 *     }
 * }
 * }</pre>
 *
 * <p>Everything is configured through your security profile. For custom settings,
 * override values in your deployment context:</p>
 *
 * <pre>{@code
 * cfc.put("wafEnabled", true);
 * cfc.put("enableMonitoring", true);
 * cfc.put("logRetentionDays", 90);
 * }</pre>
 *
 * <h2>For Sales and Business Users</h2>
 *
 * <p>This package delivers enterprise-grade observability out of the box:</p>
 * <ul>
 *   <li><b>Security monitoring</b> that meets compliance requirements</li>
 *   <li><b>Automated alerting</b> that catches issues before they impact users</li>
 *   <li><b>Audit trails</b> for regulatory compliance (PCI-DSS, HIPAA, SOC2)</li>
 *   <li><b>Threat detection</b> using AWS machine learning</li>
 *   <li><b>Cost optimization</b> through environment-specific configurations</li>
 * </ul>
 *
 * <p>Development environments get basic monitoring to save costs, while production
 * gets comprehensive protection. All configurable without code changes.</p>
 *
 * @see SecurityProfile
 * @see SecurityProfileConfiguration
 */
package com.cloudforgeci.api.observability;
