package com.cloudforgeci.api.deploy.aws;

/**
 * Receives each new CloudFormation stack event as {@link AwsDirectDeployer} observes it, in the
 * order CloudFormation reported it. Mirrors the fields cloudforge-manager's own deployment window
 * shows ({@code StackEventView}), so a caller can surface the same live, event-by-event view a
 * one-shot CLI invocation without standing up cloudforge-manager's EventBridge/SQS pipeline.
 */
@FunctionalInterface
public interface StackProgressListener {

    void onEvent(StackProgressEvent event);

    /** One captured CloudFormation stack event. */
    record StackProgressEvent(
        String timestamp,
        String resourceType,
        String logicalResourceId,
        String physicalResourceId,
        String resourceStatus,
        String resourceStatusReason) {
    }
}
