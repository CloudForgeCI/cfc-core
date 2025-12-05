package com.cloudforge.core.config;

import com.cloudforge.core.interfaces.ApplicationSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VisibilityExpressionEvaluator.
 */
public class VisibilityExpressionEvaluatorTest {

    // ========== Test Config Class ==========

    static class TestConfig {
        public boolean provisionDatabase = false;
        public boolean multiAz = false;
        public String runtimeType = "ec2";
        public String databaseEngine = "postgres";
        public int minCapacity = 1;
        public int maxCapacity = 10;
    }

    // ========== Test ApplicationSpec ==========

    static class TestApplicationSpec implements ApplicationSpec {
        private final boolean supportsDatabase;
        private final boolean supportsOidc;

        public TestApplicationSpec(boolean supportsDatabase, boolean supportsOidc) {
            this.supportsDatabase = supportsDatabase;
            this.supportsOidc = supportsOidc;
        }

        public boolean supportsDatabase() {
            return supportsDatabase;
        }

        public boolean supportsOidc() {
            return supportsOidc;
        }

        // Required ApplicationSpec methods (minimal implementation)
        @Override public String applicationId() { return "test"; }
        @Override public String defaultContainerImage() { return "test:latest"; }
        @Override public int applicationPort() { return 8080; }
        @Override public String containerDataPath() { return "/data"; }
        @Override public String efsDataPath() { return "/test"; }
        @Override public String volumeName() { return "testVolume"; }
        @Override public String containerUser() { return "1000:1000"; }
        @Override public String efsPermissions() { return "750"; }
        @Override public String ebsDeviceName() { return "/dev/xvdh"; }
        @Override public String ec2DataPath() { return "/var/lib/test"; }
        @Override public java.util.List<String> ec2LogPaths() { return java.util.List.of("/var/log/test.log"); }
        @Override public void configureUserData(com.cloudforge.core.interfaces.UserDataBuilder builder, com.cloudforge.core.interfaces.Ec2Context context) {}
    }

    // ========== Basic Expression Tests ==========

    @Test
    public void testEmptyExpression() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "");
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testNullExpression() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, null);
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testLiteralTrue() {
        // Note: "always" is handled at ConfigFieldInfo.isVisible() level before calling evaluator
        // This test verifies that literal boolean comparisons work
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "true == true");
        assertTrue(evaluator.evaluate());
    }

    // ========== Capability Check Tests ==========

    @Test
    public void testCapabilityCheck_Exists() {
        TestApplicationSpec appSpec = new TestApplicationSpec(true, false);
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(appSpec, config, "supportsDatabase");
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testCapabilityCheck_NotExists() {
        TestApplicationSpec appSpec = new TestApplicationSpec(false, false);
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(appSpec, config, "supportsDatabase");
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testCapabilityCheck_NullAppSpec() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "supportsDatabase");
        assertFalse(evaluator.evaluate());
    }

    // ========== Field Comparison Tests ==========

    @Test
    public void testBooleanComparison_True() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "provisionDatabase == true");
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testBooleanComparison_False() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "provisionDatabase == true");
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testStringComparison_Equals() {
        TestConfig config = new TestConfig();
        config.runtimeType = "fargate";
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "runtimeType == \"fargate\"");
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testStringComparison_NotEquals() {
        TestConfig config = new TestConfig();
        config.runtimeType = "ec2";
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "runtimeType == \"fargate\"");
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testStringComparison_NotEqualsOperator() {
        TestConfig config = new TestConfig();
        config.runtimeType = "ec2";
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "runtimeType != \"fargate\"");
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testNumberComparison() {
        TestConfig config = new TestConfig();
        config.minCapacity = 5;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(null, config, "minCapacity == 5");
        assertTrue(evaluator.evaluate());
    }

    // ========== Logical Operator Tests ==========

    @Test
    public void testAnd_BothTrue() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase && multiAz"
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testAnd_OneTrue() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase && multiAz"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testAnd_BothFalse() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = false;
        config.multiAz = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase && multiAz"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testOr_BothTrue() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase || multiAz"
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testOr_OneTrue() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase || multiAz"
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testOr_BothFalse() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = false;
        config.multiAz = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase || multiAz"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testNot_True() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "!provisionDatabase"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testNot_False() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "!provisionDatabase"
        );
        assertTrue(evaluator.evaluate());
    }

    // ========== Complex Expression Tests ==========

    @Test
    public void testParentheses_SimpleGrouping() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = false;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "(provisionDatabase && multiAz) || provisionDatabase"
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testParentheses_NestedGrouping() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "((provisionDatabase && multiAz) || !provisionDatabase)"
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testOperatorPrecedence_AndBeforeOr() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = false;
        config.multiAz = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase && multiAz || multiAz"
        );
        // Should parse as: (provisionDatabase && multiAz) || multiAz
        // = (false && true) || true
        // = false || true
        // = true
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testComplexExpression_RealWorld() {
        TestApplicationSpec appSpec = new TestApplicationSpec(true, false);
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.runtimeType = "fargate";
        config.databaseEngine = "aurora-postgresql";

        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            appSpec, config,
            "(supportsDatabase && provisionDatabase) || (runtimeType == \"fargate\" && databaseEngine == \"aurora-postgresql\")"
        );
        assertTrue(evaluator.evaluate());
    }

    // ========== Whitespace Handling Tests ==========

    @Test
    public void testWhitespace_ExtraSpaces() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "  provisionDatabase   ==   true  "
        );
        assertTrue(evaluator.evaluate());
    }

    @Test
    public void testWhitespace_NoSpaces() {
        TestConfig config = new TestConfig();
        config.provisionDatabase = true;
        config.multiAz = true;
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase&&multiAz"
        );
        assertTrue(evaluator.evaluate());
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testInvalidSyntax_UnmatchedParentheses() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "(provisionDatabase"
        );
        assertThrows(IllegalArgumentException.class, evaluator::evaluate);
    }

    @Test
    public void testInvalidSyntax_UnexpectedToken() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase =="
        );
        assertThrows(IllegalArgumentException.class, evaluator::evaluate);
    }

    @Test
    public void testInvalidSyntax_UnterminatedString() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "runtimeType == \"fargate"
        );
        assertThrows(IllegalArgumentException.class, evaluator::evaluate);
    }

    @Test
    public void testInvalidSyntax_ExtraCharactersAtEnd() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "provisionDatabase extra"
        );
        assertThrows(IllegalArgumentException.class, evaluator::evaluate);
    }

    // ========== Edge Cases ==========

    @Test
    public void testNullConfig() {
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, null, "provisionDatabase"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testNonExistentField() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "nonExistentField"
        );
        assertFalse(evaluator.evaluate());
    }

    @Test
    public void testNonExistentField_Comparison() {
        TestConfig config = new TestConfig();
        VisibilityExpressionEvaluator evaluator = new VisibilityExpressionEvaluator(
            null, config, "nonExistentField == true"
        );
        assertFalse(evaluator.evaluate());
    }
}
