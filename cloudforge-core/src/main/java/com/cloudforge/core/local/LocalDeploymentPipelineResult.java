package com.cloudforge.core.local;

/** Combined outcome of template adaptation and local CloudFormation deploy. */
public record LocalDeploymentPipelineResult(
        TemplateAdaptationResult adaptation,
        LocalDeployResult deployment) {
}
