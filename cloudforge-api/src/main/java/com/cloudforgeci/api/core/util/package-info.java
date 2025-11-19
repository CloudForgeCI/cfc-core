/**
 * Utility classes for CloudForge API core functionality.
 *
 * This package contains reusable utility classes that support compliance,
 * configuration, and infrastructure management across the CloudForge platform.
 *
 * Key utilities:
 * - {@link com.cloudforgeci.api.core.util.RetentionDaysConverter}: Converts integer day
 *   values to AWS CDK RetentionDays enum, supporting compliance frameworks like
 *   PCI-DSS (1 year), HIPAA (6 years), SOC2, and GDPR.
 */
package com.cloudforgeci.api.core.util;
