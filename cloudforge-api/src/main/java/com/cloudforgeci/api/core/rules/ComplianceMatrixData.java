package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.rules.ComplianceMatrix.FrameworkRequirement;
import com.cloudforgeci.api.core.rules.ComplianceMatrix.RequirementLevel;
import com.cloudforgeci.api.core.rules.ComplianceMatrix.SecurityControl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the control descriptions and framework mappings from {@code compliance-matrix.tsv}.
 *
 * <p>A line with two tab-separated fields is a control and its description. A line with four is a
 * control, a framework, a requirement level and a citation. Lines starting with {@code #} are
 * comments.</p>
 */
final class ComplianceMatrixData {

    private static final String RESOURCE = "/compliance-matrix.tsv";

    private static final Map<SecurityControl, String> DESCRIPTIONS = new EnumMap<>(SecurityControl.class);
    private static final Map<SecurityControl, Map<String, FrameworkRequirement>> MAPPINGS =
        new EnumMap<>(SecurityControl.class);

    static {
        try (InputStream in = ComplianceMatrixData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                SecurityControl control = SecurityControl.valueOf(fields[0]);
                if (fields.length == 2) {
                    DESCRIPTIONS.put(control, fields[1]);
                } else if (fields.length == 4) {
                    MAPPINGS.computeIfAbsent(control, c -> new LinkedHashMap<>()).put(fields[1],
                        new FrameworkRequirement(fields[3], RequirementLevel.valueOf(fields[2])));
                } else {
                    throw new IllegalStateException("Malformed line in " + RESOURCE + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (SecurityControl control : SecurityControl.values()) {
            if (!DESCRIPTIONS.containsKey(control)) {
                throw new IllegalStateException("No description for " + control + " in " + RESOURCE);
            }
        }
    }

    private ComplianceMatrixData() {
    }

    static String description(SecurityControl control) {
        return DESCRIPTIONS.get(control);
    }

    static Map<String, FrameworkRequirement> mappings(SecurityControl control) {
        return MAPPINGS.getOrDefault(control, Map.of());
    }
}
