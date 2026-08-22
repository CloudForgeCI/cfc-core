package com.cloudforge.core.local;

/** One CloudFormation resource change from a local deploy change set. */
public record LocalResourceChange(
        String action,
        String logicalResourceId,
        String resourceType,
        String replacement) {
}
