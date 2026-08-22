package com.cloudforge.core.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Semantic resource-level diff for canonical CloudFormation templates. */
public final class CloudFormationTemplateDiff {
    private CloudFormationTemplateDiff() {
    }

    public enum Action {
        ADD,
        MODIFY,
        REMOVE
    }

    public record ResourceChange(Action action, String logicalId, String resourceType) {
    }

    public static List<ResourceChange> diff(ObjectNode before, ObjectNode after) {
        ObjectNode beforeResources = resources(before);
        ObjectNode afterResources = resources(after);
        Set<String> logicalIds = new LinkedHashSet<>();
        beforeResources.fieldNames().forEachRemaining(logicalIds::add);
        afterResources.fieldNames().forEachRemaining(logicalIds::add);

        List<ResourceChange> changes = new ArrayList<>();
        for (String logicalId : logicalIds) {
            // logicalId is drawn from the union of both resource maps' field names, so exactly
            // one of oldResource/newResource can be null here, never the one being read below —
            // guarded defensively anyway since ObjectNode#get(String) returning null on a
            // missing key isn't obvious from this method's own signature alone.
            JsonNode oldResource = beforeResources.get(logicalId);
            JsonNode newResource = afterResources.get(logicalId);
            if (oldResource == null) {
                changes.add(new ResourceChange(
                    Action.ADD, logicalId, orMissing(newResource).path("Type").asText()));
            } else if (newResource == null) {
                changes.add(new ResourceChange(
                    Action.REMOVE, logicalId, orMissing(oldResource).path("Type").asText()));
            } else if (!oldResource.equals(newResource)) {
                changes.add(new ResourceChange(
                    Action.MODIFY, logicalId, newResource.path("Type").asText()));
            }
        }
        return List.copyOf(changes);
    }

    private static ObjectNode resources(ObjectNode template) {
        JsonNode node = template.get("Resources");
        return node instanceof ObjectNode objectNode
            ? objectNode
            : template.objectNode();
    }

    private static JsonNode orMissing(JsonNode node) {
        return node == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : node;
    }
}
