package com.cloudforge.core.config;

import com.cloudforge.core.interfaces.ApplicationSpec;

import java.lang.reflect.Method;

/**
 * Evaluates visibility expressions for configuration fields.
 *
 * <p>This evaluator implements a recursive descent parser that supports:
 * <ul>
 *   <li><b>Logical operators:</b> && (AND), || (OR), ! (NOT)</li>
 *   <li><b>Comparison operators:</b> == (equals), != (not equals), &gt;, &lt;, &gt;=, &lt;=</li>
 *   <li><b>Parentheses:</b> for grouping expressions</li>
 *   <li><b>Capability checks:</b> supportsDatabase, supportsOidc, etc.</li>
 *   <li><b>Field comparisons:</b> provisionDatabase == true, runtime == FARGATE, maxCapacity &gt; 1</li>
 * </ul>
 *
 * <h2>Grammar (BNF):</h2>
 * <pre>
 * expression     ::= orExpression
 * orExpression   ::= andExpression ( "||" andExpression )*
 * andExpression  ::= notExpression ( "&&" notExpression )*
 * notExpression  ::= "!" primary | primary
 * primary        ::= "(" expression ")" | comparison | capability
 * comparison     ::= identifier ( "==" | "!=" ) value
 * capability     ::= identifier
 * identifier     ::= [a-zA-Z_][a-zA-Z0-9_]*
 * value          ::= string | number | boolean
 * </pre>
 *
 * <h2>Examples:</h2>
 * <pre>
 * supportsDatabase                           → Check if ApplicationSpec supports databases
 * provisionDatabase == true                  → Check if provisionDatabase field is true
 * runtimeType == "fargate"                   → Check if runtimeType equals "fargate"
 * supportsDatabase && provisionDatabase      → Logical AND
 * multiAz || databaseEngine == "aurora"      → Logical OR
 * !provisionDatabase                         → Logical NOT
 * (supportsDatabase && provisionDatabase) || useEmbeddedDb  → Parentheses for grouping
 * </pre>
 */
public class VisibilityExpressionEvaluator {

    private final ApplicationSpec appSpec;
    private final Object config;
    private final String expression;
    private int position;

    /**
     * Creates a new evaluator.
     *
     * @param appSpec the application spec (may be null)
     * @param config the deployment config object
     * @param expression the visibility expression to evaluate
     */
    public VisibilityExpressionEvaluator(ApplicationSpec appSpec, Object config, String expression) {
        this.appSpec = appSpec;
        this.config = config;
        this.expression = expression == null ? "" : expression.trim();
        this.position = 0;
    }

    /**
     * Evaluates the visibility expression.
     *
     * @return true if the field should be visible, false otherwise
     * @throws IllegalArgumentException if the expression is malformed
     */
    public boolean evaluate() {
        if (expression.isEmpty()) {
            return true; // Empty expression = always visible
        }

        try {
            boolean result = parseOrExpression();
            skipWhitespace();
            if (position < expression.length()) {
                throw new IllegalArgumentException("Unexpected characters at position " + position + ": " + expression.substring(position));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to evaluate visibility expression '" + expression + "': " + e.getMessage(), e);
        }
    }

    // ========== Recursive Descent Parser ==========

    /**
     * Parses OR expressions: andExpression ( "||" andExpression )*
     */
    private boolean parseOrExpression() {
        boolean result = parseAndExpression();
        while (matchOperator("||")) {
            boolean right = parseAndExpression();
            result = result || right;
        }
        return result;
    }

    /**
     * Parses AND expressions: notExpression ( "&&" notExpression )*
     */
    private boolean parseAndExpression() {
        boolean result = parseNotExpression();
        while (matchOperator("&&")) {
            boolean right = parseNotExpression();
            result = result && right;
        }
        return result;
    }

    /**
     * Parses NOT expressions: "!" primary | primary
     */
    private boolean parseNotExpression() {
        skipWhitespace();
        if (matchOperator("!")) {
            return !parsePrimary();
        }
        return parsePrimary();
    }

    /**
     * Parses primary expressions: "(" expression ")" | comparison | capability
     */
    private boolean parsePrimary() {
        skipWhitespace();

        // Handle parentheses
        if (matchOperator("(")) {
            boolean result = parseOrExpression();
            if (!matchOperator(")")) {
                throw new IllegalArgumentException("Expected ')' at position " + position);
            }
            return result;
        }

        // Check for boolean literals first
        if (matchKeyword("true")) {
            skipWhitespace();
            // Check for comparison operators after boolean literal
            if (matchOperator("==")) {
                Object value = parseValue();
                return valuesEqual(true, value);
            } else if (matchOperator("!=")) {
                Object value = parseValue();
                return !valuesEqual(true, value);
            }
            // Standalone "true"
            return true;
        }
        if (matchKeyword("false")) {
            skipWhitespace();
            // Check for comparison operators after boolean literal
            if (matchOperator("==")) {
                Object value = parseValue();
                return valuesEqual(false, value);
            } else if (matchOperator("!=")) {
                Object value = parseValue();
                return !valuesEqual(false, value);
            }
            // Standalone "false"
            return false;
        }

        // Parse identifier (could be comparison or capability check)
        String identifier = parseIdentifier();
        skipWhitespace();

        // Check for comparison operators (order matters: >= before >, <= before <)
        if (matchOperator("==")) {
            Object value = parseValue();
            return compareValues(identifier, value, "==");
        } else if (matchOperator("!=")) {
            Object value = parseValue();
            return compareValues(identifier, value, "!=");
        } else if (matchOperator(">=")) {
            Object value = parseValue();
            return compareValues(identifier, value, ">=");
        } else if (matchOperator("<=")) {
            Object value = parseValue();
            return compareValues(identifier, value, "<=");
        } else if (matchOperator(">")) {
            Object value = parseValue();
            return compareValues(identifier, value, ">");
        } else if (matchOperator("<")) {
            Object value = parseValue();
            return compareValues(identifier, value, "<");
        }

        // No comparison operator - treat as capability check
        return checkCapability(identifier);
    }

    // ========== Token Parsing ==========

    /**
     * Parses an identifier: [a-zA-Z_][a-zA-Z0-9_]*
     */
    private String parseIdentifier() {
        skipWhitespace();
        int start = position;
        if (position >= expression.length() || !isIdentifierStart(expression.charAt(position))) {
            throw new IllegalArgumentException("Expected identifier at position " + position);
        }

        while (position < expression.length() && isIdentifierPart(expression.charAt(position))) {
            position++;
        }

        return expression.substring(start, position);
    }

    /**
     * Parses a value: string | number | boolean
     */
    private Object parseValue() {
        skipWhitespace();

        // String literal
        if (position < expression.length() && expression.charAt(position) == '"') {
            return parseStringLiteral();
        }

        // Boolean literal
        if (matchKeyword("true")) {
            return true;
        }
        if (matchKeyword("false")) {
            return false;
        }

        // Number literal
        if (position < expression.length() && (Character.isDigit(expression.charAt(position)) || expression.charAt(position) == '-')) {
            return parseNumber();
        }

        // Unquoted identifier (for enum values like FARGATE, EC2, PRODUCTION)
        if (position < expression.length() && isIdentifierStart(expression.charAt(position))) {
            return parseIdentifier();
        }

        throw new IllegalArgumentException("Expected value at position " + position);
    }

    /**
     * Parses a string literal: "..."
     */
    private String parseStringLiteral() {
        position++; // Skip opening quote
        int start = position;
        while (position < expression.length() && expression.charAt(position) != '"') {
            position++;
        }
        if (position >= expression.length()) {
            throw new IllegalArgumentException("Unterminated string literal at position " + start);
        }
        String value = expression.substring(start, position);
        position++; // Skip closing quote
        return value;
    }

    /**
     * Parses a number literal: [0-9]+
     */
    private Integer parseNumber() {
        int start = position;
        if (expression.charAt(position) == '-') {
            position++;
        }
        while (position < expression.length() && Character.isDigit(expression.charAt(position))) {
            position++;
        }
        String numberStr = expression.substring(start, position);
        try {
            return Integer.parseInt(numberStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number at position " + start + ": " + numberStr);
        }
    }

    // ========== Evaluation Helpers ==========

    /**
     * Checks if an ApplicationSpec capability is present, or evaluates a config field as boolean.
     *
     * @param identifier the capability name (e.g., "supportsDatabase") or field name
     * @return true if the capability exists and returns true, or if the field evaluates to true
     */
    private boolean checkCapability(String identifier) {
        // First try to get field value from config
        Object fieldValue = getFieldValue(identifier);
        if (fieldValue != null) {
            // Field exists in config - evaluate as boolean
            if (fieldValue instanceof Boolean) {
                return (Boolean) fieldValue;
            }
            // Non-null, non-boolean value = true
            return true;
        }

        // No field found - try ApplicationSpec method
        if (appSpec == null) {
            return false;
        }

        try {
            // Try to call the method on ApplicationSpec
            Method method = appSpec.getClass().getMethod(identifier);
            Object result = method.invoke(appSpec);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            // Non-boolean method exists = true
            return true;
        } catch (NoSuchMethodException e) {
            // No such capability
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compares a field value with an expected value using the specified operator.
     *
     * @param identifier the field name
     * @param expectedValue the expected value
     * @param operator the comparison operator (==, !=, >, <, >=, <=)
     * @return comparison result
     */
    private boolean compareValues(String identifier, Object expectedValue, String operator) {
        Object actualValue = getFieldValue(identifier);

        switch (operator) {
            case "==":
                return valuesEqual(actualValue, expectedValue);
            case "!=":
                return !valuesEqual(actualValue, expectedValue);
            case ">":
                return compareNumeric(actualValue, expectedValue) > 0;
            case "<":
                return compareNumeric(actualValue, expectedValue) < 0;
            case ">=":
                return compareNumeric(actualValue, expectedValue) >= 0;
            case "<=":
                return compareNumeric(actualValue, expectedValue) <= 0;
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
    }

    /**
     * Compares two values numerically.
     *
     * @return negative if actual < expected, 0 if equal, positive if actual > expected
     */
    private int compareNumeric(Object actual, Object expected) {
        if (actual == null) {
            return expected == null ? 0 : -1;
        }
        if (expected == null) {
            return 1;
        }

        // Convert to numbers
        double actualNum = toNumber(actual);
        double expectedNum = toNumber(expected);
        return Double.compare(actualNum, expectedNum);
    }

    /**
     * Converts a value to a number.
     */
    private double toNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets a field value from the config object.
     *
     * @param fieldName the field name
     * @return the field value, or null if not found
     */
    private Object getFieldValue(String fieldName) {
        if (config == null) {
            return null;
        }

        try {
            java.lang.reflect.Field field = config.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(config);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compares two values for equality, handling type conversions.
     */
    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }

        // Handle string comparisons
        if (actual instanceof String && expected instanceof String) {
            return actual.equals(expected);
        }

        // Handle boolean comparisons
        if (actual instanceof Boolean && expected instanceof Boolean) {
            return actual.equals(expected);
        }

        // Handle number comparisons
        if (actual instanceof Number && expected instanceof Number) {
            return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
        }

        // Handle enum comparisons
        if (actual instanceof Enum) {
            return actual.toString().equals(expected.toString());
        }

        // Fallback to string comparison
        return actual.toString().equals(expected.toString());
    }

    // ========== Lexer Helpers ==========

    /**
     * Skips whitespace characters.
     */
    private void skipWhitespace() {
        while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
            position++;
        }
    }

    /**
     * Matches an operator and advances position if found.
     */
    private boolean matchOperator(String operator) {
        skipWhitespace();
        if (expression.startsWith(operator, position)) {
            position += operator.length();
            return true;
        }
        return false;
    }

    /**
     * Matches a keyword and advances position if found.
     * Ensures the keyword is not part of a larger identifier.
     */
    private boolean matchKeyword(String keyword) {
        skipWhitespace();
        if (expression.startsWith(keyword, position)) {
            int endPos = position + keyword.length();
            // Ensure keyword is not part of a larger identifier
            if (endPos >= expression.length() || !isIdentifierPart(expression.charAt(endPos))) {
                position = endPos;
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a character can start an identifier.
     */
    private boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * Checks if a character can be part of an identifier.
     */
    private boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
