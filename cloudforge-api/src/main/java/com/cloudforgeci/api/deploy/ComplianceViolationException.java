package com.cloudforgeci.api.deploy;

import com.cloudforgeci.api.core.rules.NagReportReader.ComplianceFinding;

import java.util.List;

/**
 * Thrown by {@link CloudForgeSynthesizer#synthesize} when {@code complianceMode=enforce} and
 * cdk-nag found at least one non-suppressed {@code ERROR}-level violation. This implements
 * {@code ComplianceMode.ENFORCE}'s "blocks CDK synthesis" behavior (see {@link
 * com.cloudforgeci.api.core.rules.NagReportReader} for how findings are collected).
 *
 * <p>A dedicated type so callers such as a deploy pipeline can distinguish a template compliance
 * failure from an AWS communication error. Carries the structured findings so callers can render
 * them without parsing the message.</p>
 */
public final class ComplianceViolationException extends RuntimeException {

    private final transient List<ComplianceFinding> findings;

    public ComplianceViolationException(String stackName, List<ComplianceFinding> findings) {
        super(buildMessage(stackName, findings));
        this.findings = List.copyOf(findings);
    }

    public List<ComplianceFinding> findings() {
        return findings;
    }

    private static String buildMessage(String stackName, List<ComplianceFinding> findings) {
        StringBuilder message = new StringBuilder("Compliance check failed for ")
            .append(stackName)
            .append(" (complianceMode=enforce) -- ")
            .append(findings.size())
            .append(findings.size() == 1 ? " violation" : " violations")
            .append(":");
        for (ComplianceFinding finding : findings) {
            message.append("\n  - ").append(finding.describe());
        }
        return message.toString();
    }
}
