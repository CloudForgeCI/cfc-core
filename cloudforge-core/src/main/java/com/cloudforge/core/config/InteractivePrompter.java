package com.cloudforge.core.config;

import com.cloudforge.core.interfaces.ApplicationSpec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

/**
 * Interactive prompting utility that generates questions from @ConfigField annotations.
 *
 * <p>Uses {@link ConfigurationIntrospector} to discover fields and automatically
 * generates appropriate prompts based on field metadata.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DeploymentConfig config = new DeploymentConfig();
 * InteractivePrompter prompter = new InteractivePrompter(System.in, System.out);
 * prompter.promptForConfiguration(config, applicationSpec);
 * }</pre>
 *
 * @since 3.0.0
 */
public class InteractivePrompter {

    private final BufferedReader reader;
    private final PrintStream output;

    /**
     * Creates a new InteractivePrompter with the specified input and output streams.
     *
     * @param input  input stream for reading user responses
     * @param output output stream for displaying prompts
     */
    public InteractivePrompter(InputStream input, PrintStream output) {
        this.reader = new BufferedReader(new InputStreamReader(input));
        this.output = output;
    }

    /**
     * Prompts for all visible configuration fields.
     *
     * <p>Iterates through visible fields (based on application capabilities and
     * current configuration state) and prompts for values.</p>
     *
     * @param config  the configuration object to populate
     * @param appSpec the application spec for determining field visibility
     */
    public void promptForConfiguration(DeploymentConfig config, ApplicationSpec appSpec) {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverVisibleFields(appSpec, config);

        for (ConfigFieldInfo field : fields) {
            promptForField(field, config, appSpec);

            // Re-evaluate visible fields after each field change
            // (some fields may become visible/hidden based on values)
            fields = ConfigurationIntrospector.discoverVisibleFields(appSpec, config);
        }
    }

    /**
     * Prompts for fields in a specific category.
     *
     * @param config   the configuration object to populate
     * @param appSpec  the application spec
     * @param category the category to prompt for
     */
    public void promptForCategory(DeploymentConfig config, ApplicationSpec appSpec, String category) {
        List<ConfigFieldInfo> fields = ConfigurationIntrospector.discoverVisibleFields(appSpec, config, category);

        for (ConfigFieldInfo field : fields) {
            promptForField(field, config, appSpec);
        }
    }

    /**
     * Prompts for a single field.
     */
    private void promptForField(ConfigFieldInfo field, Object config, ApplicationSpec appSpec) {
        // Skip if field is not visible
        if (!field.isVisible(appSpec, config)) {
            return;
        }

        Class<?> type = field.type();

        if (field.allowedValues().length > 0) {
            promptChoice(field, config);
        } else if (type == boolean.class || type == Boolean.class) {
            promptYesNo(field, config);
        } else if (isNumericType(type)) {
            promptNumeric(field, config);
        } else {
            promptString(field, config);
        }
    }

    /**
     * Prompts for a choice from allowed values.
     */
    private void promptChoice(ConfigFieldInfo field, Object config) {
        String[] choices = field.allowedValues();
        Object currentValue = field.getValue(config);
        String defaultValue = currentValue != null ? currentValue.toString() : "";

        output.println();
        output.println("📋 " + field.displayName());
        if (!field.description().isEmpty()) {
            output.println("   " + field.description());
        }

        // Find default index
        int defaultIndex = 0;
        for (int i = 0; i < choices.length; i++) {
            String marker = choices[i].equals(defaultValue) ? " (default)" : "";
            output.printf("   [%d] %s%s%n", i + 1, choices[i], marker);
            if (choices[i].equals(defaultValue)) {
                defaultIndex = i + 1;
            }
        }

        String prompt = String.format("Select [1-%d] (default: %d): ", choices.length, defaultIndex);
        String response = readLine(prompt);

        String value;
        if (response.isEmpty()) {
            value = defaultValue.isEmpty() ? choices[0] : defaultValue;
        } else {
            try {
                int index = Integer.parseInt(response) - 1;
                if (index >= 0 && index < choices.length) {
                    value = choices[index];
                } else {
                    output.println("❌ Invalid selection, using default");
                    value = defaultValue.isEmpty() ? choices[0] : defaultValue;
                }
            } catch (NumberFormatException e) {
                // Check if they typed the value directly
                boolean found = false;
                for (String choice : choices) {
                    if (choice.equalsIgnoreCase(response)) {
                        value = choice;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    output.println("❌ Invalid selection, using default");
                    value = defaultValue.isEmpty() ? choices[0] : defaultValue;
                } else {
                    // value already set in loop
                    value = response;
                }
            }
        }

        setFieldValue(field, config, value);
        output.println("✅ " + field.displayName() + ": " + value);
    }

    /**
     * Prompts for a yes/no boolean value.
     */
    private void promptYesNo(ConfigFieldInfo field, Object config) {
        Object currentValue = field.getValue(config);
        boolean defaultValue = currentValue != null ? (Boolean) currentValue : false;

        output.println();
        output.println("📋 " + field.displayName());
        if (!field.description().isEmpty()) {
            output.println("   " + field.description());
        }

        String defaultStr = defaultValue ? "Y" : "N";
        String prompt = String.format("%s [y/n] (default: %s): ", field.displayName(), defaultStr);
        String response = readLine(prompt).toLowerCase();

        boolean value;
        if (response.isEmpty()) {
            value = defaultValue;
        } else if (response.startsWith("y")) {
            value = true;
        } else if (response.startsWith("n")) {
            value = false;
        } else {
            output.println("❌ Invalid response, using default: " + defaultValue);
            value = defaultValue;
        }

        field.setValue(config, value);
        output.println("✅ " + field.displayName() + ": " + (value ? "Yes" : "No"));
    }

    /**
     * Prompts for a numeric value with validation.
     */
    private void promptNumeric(ConfigFieldInfo field, Object config) {
        Object currentValue = field.getValue(config);
        String defaultStr = currentValue != null ? currentValue.toString() : "";

        output.println();
        output.println("📋 " + field.displayName());
        if (!field.description().isEmpty()) {
            output.println("   " + field.description());
        }

        // Show range if specified
        if (field.min() > Double.NEGATIVE_INFINITY || field.max() < Double.POSITIVE_INFINITY) {
            String range = String.format("   Range: %.0f - %.0f", field.min(), field.max());
            output.println(range);
        }

        String prompt = String.format("%s (default: %s): ", field.displayName(), defaultStr);
        String response = readLine(prompt);

        if (response.isEmpty()) {
            // Keep current value
            output.println("✅ " + field.displayName() + ": " + defaultStr);
            return;
        }

        try {
            Number value = parseNumber(response, field.type());

            // Validate range
            ValidationResult result = field.validate(value, config);
            if (result.isError()) {
                output.println("❌ " + result.getMessage() + ", using default");
                return;
            }

            setFieldValue(field, config, value);
            output.println("✅ " + field.displayName() + ": " + value);
        } catch (NumberFormatException e) {
            output.println("❌ Invalid number, using default");
        }
    }

    /**
     * Prompts for a string value.
     */
    private void promptString(ConfigFieldInfo field, Object config) {
        Object currentValue = field.getValue(config);
        String defaultValue = currentValue != null ? currentValue.toString() : "";

        output.println();
        output.println("📋 " + field.displayName());
        if (!field.description().isEmpty()) {
            output.println("   " + field.description());
        }
        if (!field.example().isEmpty()) {
            output.println("   Example: " + field.example());
        }

        String defaultDisplay = defaultValue.isEmpty() ? "(none)" : defaultValue;
        String prompt = String.format("%s (default: %s): ", field.displayName(), defaultDisplay);

        String response;
        if (field.sensitive()) {
            response = readLineSecure(prompt);
        } else {
            response = readLine(prompt);
        }

        String value = response.isEmpty() ? defaultValue : response;

        // Validate pattern if specified
        if (!field.pattern().isEmpty() && !value.isEmpty()) {
            if (!value.matches(field.pattern())) {
                output.println("❌ Value does not match required pattern: " + field.pattern());
                output.println("   Using default: " + defaultValue);
                value = defaultValue;
            }
        }

        // Validate required
        if (field.required() && value.isEmpty()) {
            output.println("❌ This field is required");
            promptString(field, config); // Retry
            return;
        }

        field.setValue(config, value.isEmpty() ? null : value);
        String displayValue = field.sensitive() ? "********" : value;
        output.println("✅ " + field.displayName() + ": " + (displayValue.isEmpty() ? "(not set)" : displayValue));
    }

    /**
     * Reads a line of input from the user.
     */
    private String readLine(String prompt) {
        output.print(prompt);
        output.flush();
        try {
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Reads a line of input securely (for sensitive fields).
     * Falls back to regular input if console not available.
     */
    private String readLineSecure(String prompt) {
        if (System.console() != null) {
            char[] chars = System.console().readPassword(prompt);
            return chars != null ? new String(chars) : "";
        }
        return readLine(prompt);
    }

    /**
     * Sets a field value with appropriate type conversion.
     */
    private void setFieldValue(ConfigFieldInfo field, Object config, Object value) {
        Class<?> type = field.type();

        if (type.isEnum() && value instanceof String) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<Enum>) type, ((String) value).toUpperCase());
            field.setValue(config, enumValue);
        } else if (isNumericType(type) && value instanceof String) {
            field.setValue(config, parseNumber((String) value, type));
        } else {
            field.setValue(config, value);
        }
    }

    /**
     * Checks if a type is numeric.
     */
    private boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == long.class || type == Long.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class;
    }

    /**
     * Parses a string to a number of the specified type.
     */
    private Number parseNumber(String value, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        return Integer.parseInt(value);
    }
}
