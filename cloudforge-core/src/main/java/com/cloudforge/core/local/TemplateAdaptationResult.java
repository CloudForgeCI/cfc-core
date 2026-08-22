package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/** Adapted CloudFormation template plus an explicit adaptation audit trail. */
public record TemplateAdaptationResult(
        ObjectNode template,
        List<TemplateAdaptation> adaptations) {

    public boolean hasAdaptations() {
        return !adaptations.isEmpty();
    }

    public Optional<String> outputValue(String outputKey) {
        String value = template.path("Outputs").path(outputKey).path("Value").asText(null);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
