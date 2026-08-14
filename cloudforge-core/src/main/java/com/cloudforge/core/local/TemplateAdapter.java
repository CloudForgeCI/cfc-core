package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapts a canonical AWS CloudFormation template for a local emulator target.
 *
 * <p>Implementations live in target modules (for example {@code cloudforge-ministack}).</p>
 */
public interface TemplateAdapter {

    TemplateAdaptationResult adapt(ObjectNode canonicalTemplate, String stackName);

    /** Whether a local auth runtime should be started after deploy. */
    boolean requiresLocalAuthRuntime(TemplateAdaptationResult result);

    /** Application URL from adapted template outputs, if present. */
    String applicationUrl(TemplateAdaptationResult result);
}
