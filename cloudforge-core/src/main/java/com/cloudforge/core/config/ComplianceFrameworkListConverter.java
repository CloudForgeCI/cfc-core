package com.cloudforge.core.config;

import com.cloudforge.core.enums.ComplianceFrameworkType;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;

/**
 * Jackson serializer/deserializer for converting between comma-separated strings
 * and {@code List<ComplianceFrameworkType>}.
 *
 * <p>Enables backward-compatible JSON format:
 * <pre>{@code
 * "complianceFrameworks": "soc2,pci-dss,hipaa"
 * }</pre>
 *
 * <p>While providing type-safe Java handling:
 * <pre>{@code
 * List<ComplianceFrameworkType> frameworks = config.complianceFrameworks;
 * for (ComplianceFrameworkType f : frameworks) {
 *     System.out.println(f.getDisplayName());
 * }
 * }</pre>
 */
public class ComplianceFrameworkListConverter {

    private ComplianceFrameworkListConverter() {}

    /**
     * Deserializes a comma-separated string to {@code List<ComplianceFrameworkType>}.
     */
    public static class Deserializer extends JsonDeserializer<List<ComplianceFrameworkType>> {
        @Override
        public List<ComplianceFrameworkType> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            String value = p.getValueAsString();
            return ComplianceFrameworkType.parseCommaSeparated(value);
        }
    }

    /**
     * Serializes {@code List<ComplianceFrameworkType>} to a comma-separated string.
     */
    public static class Serializer extends JsonSerializer<List<ComplianceFrameworkType>> {
        @Override
        public void serialize(List<ComplianceFrameworkType> value, JsonGenerator gen,
                SerializerProvider serializers) throws IOException {
            gen.writeString(ComplianceFrameworkType.toCommaSeparated(value));
        }
    }
}
