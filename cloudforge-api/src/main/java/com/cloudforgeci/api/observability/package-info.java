/**
 * AWS observability and security-monitoring constructs for CloudForge deployments.
 *
 * <p>The package includes factories for CloudWatch alarms and logs, VPC flow logs,
 * AWS WAF, GuardDuty, CloudTrail, AWS Config, and Audit Manager. Factory behavior and
 * defaults vary by the selected
 * {@link com.cloudforge.core.enums.SecurityProfile security profile}; deployments should
 * review generated resources and thresholds for their workload.</p>
 *
 * <p>{@link com.cloudforgeci.api.observability.ComplianceFactory} configures services
 * that can collect evidence for selected compliance frameworks. Enabling these services
 * does not by itself establish compliance or certification.</p>
 *
 * <p>Settings can be overridden in the deployment context:</p>
 *
 * <pre>{@code
 * cfc.put("wafEnabled", true);
 * cfc.put("enableMonitoring", true);
 * cfc.put("logRetentionDays", 90);
 * }</pre>
 *
 * @see com.cloudforge.core.enums.SecurityProfile
 * @see com.cloudforge.core.annotation.SecurityProfileConfiguration
 */
package com.cloudforgeci.api.observability;
