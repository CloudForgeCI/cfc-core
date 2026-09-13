package com.cloudforgeci.api.deploy;

import com.cloudforgeci.api.core.rules.NagReportReader.ComplianceFinding;

import java.util.List;

/**
 * Thrown by {@link CloudForgeSynthesizer#synthesize} when {@code complianceMode=enforce} and
 * cdk-nag found at least one non-suppressed {@code ERROR}-level violation -- the real
 * implementation of {@code ComplianceMode.ENFORCE}'s "blocks CDK synthesis" behavior (see {@link
 * com.cloudforgeci.api.core.rules.NagReportReader}'s own javadoc for how findings are collected).
 *
 * <p>Deliberately its own exception type, not a generic {@code IllegalStateException}/{@code
 * IOException} — callers (Manager's own deploy pipeline in particular) need to distinguish "your
 * template has a real security problem, fix it" from "something about talking to AWS went wrong,"
 * the same way {@code ManagerSelfDeployRestrictedException}/{@code
 * CrossAccountDeployRestrictedException} are their own types rather than folded into a generic
 * failure. Carries the actual findings, not just a flattened message, so a caller can render them
 * as a real list rather than parsing one back out of prose.</p>
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
