package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.JsonNode;

/** One audited change applied while adapting a canonical template for local deployment. */
public record TemplateAdaptation(String path, String reason, JsonNode original) {
}
