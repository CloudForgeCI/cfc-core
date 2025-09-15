package com.cloudforgeci.api.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;


class Util {

    private static ObjectMapper getMapper() {
        return new ObjectMapper();
    }

    public static DeploymentContext extractDeploymentContext(Object cfc) {
        Map<String, Object> map = convertToContext(cfc);
        DeploymentContext result = new DeploymentContext(map);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertToContext(Object obj) {
        if (obj == null) return java.util.Collections.emptyMap();

        if (obj instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.HashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }

        if (obj instanceof String s) {
            String json = s.trim();
            if (json.isEmpty()) return java.util.Collections.emptyMap();
            try {
                return getMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse context JSON: " + json, e);
            }
        }

        return getMapper().convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
}
