package com.cloudforge.core.config;

import com.cloudforge.core.local.DeploymentTarget;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Jackson serializer/deserializer for {@link DeploymentTarget}, overriding {@link DeploymentConfig
 * #createMapper()}'s {@code WRITE_ENUMS_USING_TO_STRING}/{@code READ_ENUMS_USING_TO_STRING}
 * defaults for this one type. Those features fall back to the plain enum's {@code toString()} —
 * {@code name()}, upper-case — but every other place this codebase writes or reads a target
 * ({@code CFC_MANAGER_TARGET}, deployment history, {@code DeploymentTargetId}) uses {@link
 * DeploymentTarget#configKey()}'s lower-case wire format. Without this, {@code toContextMap()}/
 * {@code toHistoryContextMap()} would emit {@code "AWS"} instead of {@code "aws"} — a real
 * mismatch, not a style nit.
 */
public class DeploymentTargetConverter {

    private DeploymentTargetConverter() {}

    public static class Serializer extends JsonSerializer<DeploymentTarget> {
        @Override
        public void serialize(DeploymentTarget value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.configKey());
        }
    }

    public static class Deserializer extends JsonDeserializer<DeploymentTarget> {
        @Override
        public DeploymentTarget deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return DeploymentTarget.fromConfigKey(p.getValueAsString());
        }
    }
}
