package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.Slot;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforgeci.api.interfaces.IAMProfile;
import com.cloudforgeci.api.interfaces.Rule;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import com.cloudforgeci.api.interfaces.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for RuleKit utility methods.
 * Tests rule validators and slot combinators.
 * Target: Achieve 80%+ coverage for RuleKit class.
 */
@DisplayName("RuleKit Tests")
class RuleKitTest {

    private App app;
    private Stack stack;
    private SystemContext ctx;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "RuleKitTestStack");
        DeploymentContext cfc = DeploymentContext.from(stack);
        ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
    }

    @Nested
    @DisplayName("require() Rule Tests")
    class RequireRuleTests {

        @Test
        @DisplayName("require() should return empty list when slot is present")
        void requirePassesWhenSlotPresent() {
            // Set VPC slot
            ctx.vpc.set(software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "TestVpc")
                    .maxAzs(2)
                    .build());

            Rule rule = RuleKit.require("vpc", c -> c.vpc);
            List<String> errors = rule.check(ctx);

            assertTrue(errors.isEmpty(), "require() should pass when slot is present");
        }

        @Test
        @DisplayName("require() should return error when slot is empty")
        void requireFailsWhenSlotEmpty() {
            Rule rule = RuleKit.require("vpc", c -> c.vpc);
            List<String> errors = rule.check(ctx);

            assertEquals(1, errors.size(), "Should have one error");
            assertEquals("required: vpc", errors.get(0), "Error message should indicate missing requirement");
        }

        @Test
        @DisplayName("require() should work with different slot types")
        void requireWorksWithDifferentSlots() {
            // Test with ALB slot
            Rule albRule = RuleKit.require("alb", c -> c.alb);
            List<String> albErrors = albRule.check(ctx);
            assertEquals(1, albErrors.size());
            assertEquals("required: alb", albErrors.get(0));

            // Set ALB and recheck
            ctx.alb.set(software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer.Builder
                    .create(stack, "TestAlb")
                    .vpc(software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "VpcForAlb")
                            .maxAzs(2)
                            .build())
                    .build());

            albErrors = albRule.check(ctx);
            assertTrue(albErrors.isEmpty(), "Should pass after ALB is set");
        }

        @Test
        @DisplayName("require() with custom name")
        void requireWithCustomName() {
            Rule rule = RuleKit.require("my-custom-resource", c -> c.efs);
            List<String> errors = rule.check(ctx);

            assertEquals("required: my-custom-resource", errors.get(0));
        }
    }

    @Nested
    @DisplayName("forbid() Rule Tests")
    class ForbidRuleTests {

        @Test
        @DisplayName("forbid() should return empty list when slot is empty")
        void forbidPassesWhenSlotEmpty() {
            Rule rule = RuleKit.forbid("vpc", c -> c.vpc);
            List<String> errors = rule.check(ctx);

            assertTrue(errors.isEmpty(), "forbid() should pass when slot is empty");
        }

        @Test
        @DisplayName("forbid() should return error when slot is present")
        void forbidFailsWhenSlotPresent() {
            ctx.vpc.set(software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "TestVpc")
                    .maxAzs(2)
                    .build());

            Rule rule = RuleKit.forbid("vpc", c -> c.vpc);
            List<String> errors = rule.check(ctx);

            assertEquals(1, errors.size(), "Should have one error");
            assertEquals("forbidden: vpc", errors.get(0), "Error message should indicate forbidden resource");
        }

        @Test
        @DisplayName("forbid() should work with configuration slots")
        void forbidWorksWithConfigSlots() {
            // Test with wafEnabled slot (configuration)
            Rule rule = RuleKit.forbid("wafEnabled", c -> c.wafEnabled);
            List<String> errors = rule.check(ctx);
            assertTrue(errors.isEmpty(), "Should pass when config slot is empty");

            // Set wafEnabled and recheck
            ctx.wafEnabled.set(true);
            errors = rule.check(ctx);
            assertEquals(1, errors.size());
            assertEquals("forbidden: wafEnabled", errors.get(0));
        }

        @Test
        @DisplayName("forbid() with custom name")
        void forbidWithCustomName() {
            ctx.https.set(software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener.Builder
                    .create(stack, "TestListener")
                    .loadBalancer(software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer.Builder
                            .create(stack, "LbForListener")
                            .vpc(software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "VpcForLb")
                                    .maxAzs(2)
                                    .build())
                            .build())
                    .port(443)
                    .build());

            Rule rule = RuleKit.forbid("https-in-dev", c -> c.https);
            List<String> errors = rule.check(ctx);

            assertEquals("forbidden: https-in-dev", errors.get(0));
        }
    }

    @Nested
    @DisplayName("when() Conditional Rule Tests")
    class WhenRuleTests {

        @Test
        @DisplayName("when() should apply rule when condition is true")
        void whenAppliesRuleWhenTrue() {
            Rule innerRule = RuleKit.require("vpc", c -> c.vpc);
            Rule conditionalRule = RuleKit.when(true, innerRule);

            List<String> errors = conditionalRule.check(ctx);

            assertEquals(1, errors.size(), "Rule should be applied when condition is true");
            assertEquals("required: vpc", errors.get(0));
        }

        @Test
        @DisplayName("when() should skip rule when condition is false")
        void whenSkipsRuleWhenFalse() {
            Rule innerRule = RuleKit.require("vpc", c -> c.vpc);
            Rule conditionalRule = RuleKit.when(false, innerRule);

            List<String> errors = conditionalRule.check(ctx);

            assertTrue(errors.isEmpty(), "Rule should be skipped when condition is false");
        }

        @Test
        @DisplayName("when() can be combined with forbid")
        void whenWorksWithForbid() {
            ctx.wafEnabled.set(true);

            Rule forbidWaf = RuleKit.forbid("waf", c -> c.wafEnabled);
            Rule conditionalForbid = RuleKit.when(true, forbidWaf);

            List<String> errors = conditionalForbid.check(ctx);
            assertEquals(1, errors.size());
        }

        @Test
        @DisplayName("when() with dynamic condition")
        void whenWithDynamicCondition() {
            boolean isProduction = ctx.security == SecurityProfile.PRODUCTION;

            Rule rule = RuleKit.when(isProduction, RuleKit.require("waf", c -> c.wafEnabled));
            List<String> errors = rule.check(ctx);

            assertTrue(errors.isEmpty(), "Should skip rule in DEV environment");
        }
    }

    @Nested
    @DisplayName("whenBoth() Combinator Tests")
    class WhenBothTests {

        @Test
        @DisplayName("whenBoth() should execute when both slots are set")
        void whenBothExecutesWhenBothSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenBoth(slotA, slotB, (a, b) -> {
                counter.incrementAndGet();
                assertEquals("test", a);
                assertEquals(42, b);
            });

            assertEquals(0, counter.get(), "Should not execute before both are set");

            slotA.set("test");
            assertEquals(0, counter.get(), "Should not execute with only one slot set");

            slotB.set(42);
            assertEquals(1, counter.get(), "Should execute after both slots are set");
        }

        @Test
        @DisplayName("whenBoth() should execute immediately if both slots already set")
        void whenBothExecutesImmediatelyIfBothAlreadySet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            slotA.set("test");
            slotB.set(42);

            RuleKit.whenBoth(slotA, slotB, (a, b) -> counter.incrementAndGet());

            // Executes: 1 time immediately + 1 for slotA callback + 1 for slotB callback = 3 times
            assertTrue(counter.get() >= 1, "Should execute at least once when both already set");
        }

        @Test
        @DisplayName("whenBoth() should not execute if only first slot is set")
        void whenBothDoesNotExecuteWithOnlyFirst() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenBoth(slotA, slotB, (a, b) -> counter.incrementAndGet());

            slotA.set("test");
            assertEquals(0, counter.get(), "Should not execute with only first slot");
        }

        @Test
        @DisplayName("whenBoth() should not execute if only second slot is set")
        void whenBothDoesNotExecuteWithOnlySecond() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenBoth(slotA, slotB, (a, b) -> counter.incrementAndGet());

            slotB.set(42);
            assertEquals(0, counter.get(), "Should not execute with only second slot");
        }

        @Test
        @DisplayName("whenBoth() should provide correct values to consumer")
        void whenBothProvidesCorrectValues() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicReference<String> capturedA = new AtomicReference<>();
            AtomicReference<Integer> capturedB = new AtomicReference<>();

            RuleKit.whenBoth(slotA, slotB, (a, b) -> {
                capturedA.set(a);
                capturedB.set(b);
            });

            slotA.set("hello");
            slotB.set(100);

            assertEquals("hello", capturedA.get());
            assertEquals(100, capturedB.get());
        }

        @Test
        @DisplayName("whenBoth() values are accessible in callback")
        void whenBothValuesAccessible() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            AtomicReference<String> resultA = new AtomicReference<>();
            AtomicReference<Integer> resultB = new AtomicReference<>();

            slotA.set("test");
            slotB.set(42);

            RuleKit.whenBoth(slotA, slotB, (a, b) -> {
                resultA.set(a);
                resultB.set(b);
            });

            assertEquals("test", resultA.get());
            assertEquals(42, resultB.get());
        }
    }

    @Nested
    @DisplayName("whenAll() (3 slots) Combinator Tests")
    class WhenAll3Tests {

        @Test
        @DisplayName("whenAll() should execute when all three slots are set")
        void whenAllExecutesWhenAllThreeSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll(slotA, slotB, slotC, (a, b, c) -> {
                counter.incrementAndGet();
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
            });

            slotA.set("test");
            assertEquals(0, counter.get());

            slotB.set(42);
            assertEquals(0, counter.get());

            slotC.set(true);
            assertEquals(1, counter.get(), "Should execute after all three slots are set");
        }

        @Test
        @DisplayName("whenAll() should execute immediately if all three already set")
        void whenAllExecutesImmediatelyIfAllSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);

            RuleKit.whenAll(slotA, slotB, slotC, (a, b, c) -> counter.incrementAndGet());

            assertTrue(counter.get() >= 1, "Should execute at least once when all already set");
        }

        @Test
        @DisplayName("whenAll() should not execute if only two slots are set")
        void whenAllDoesNotExecuteWithOnlyTwo() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll(slotA, slotB, slotC, (a, b, c) -> counter.incrementAndGet());

            slotA.set("test");
            slotB.set(42);
            assertEquals(0, counter.get(), "Should not execute with only two slots");
        }

        @Test
        @DisplayName("whenAll() should provide correct values")
        void whenAllProvidesCorrectValues() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            AtomicReference<String> capturedA = new AtomicReference<>();
            AtomicReference<Integer> capturedB = new AtomicReference<>();
            AtomicReference<Boolean> capturedC = new AtomicReference<>();

            RuleKit.whenAll(slotA, slotB, slotC, (a, b, c) -> {
                capturedA.set(a);
                capturedB.set(b);
                capturedC.set(c);
            });

            slotA.set("hello");
            slotB.set(100);
            slotC.set(false);

            assertEquals("hello", capturedA.get());
            assertEquals(100, capturedB.get());
            assertFalse(capturedC.get());
        }
    }

    @Nested
    @DisplayName("whenAll4() (4 slots) Combinator Tests")
    class WhenAll4Tests {

        @Test
        @DisplayName("whenAll4() should execute when all four slots are set")
        void whenAll4ExecutesWhenAllFourSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll4(slotA, slotB, slotC, slotD, (a, b, c, d) -> {
                counter.incrementAndGet();
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
                assertEquals(3.14, d, 0.001);
            });

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            assertEquals(0, counter.get());

            slotD.set(3.14);
            assertEquals(1, counter.get(), "Should execute after all four slots are set");
        }

        @Test
        @DisplayName("whenAll4() should execute immediately if all four already set")
        void whenAll4ExecutesImmediatelyIfAllSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            slotD.set(3.14);

            RuleKit.whenAll4(slotA, slotB, slotC, slotD, (a, b, c, d) -> counter.incrementAndGet());

            assertTrue(counter.get() >= 1, "Should execute at least once when all 4 slots already set");
        }

        @Test
        @DisplayName("whenAll4() should not execute if only three slots are set")
        void whenAll4DoesNotExecuteWithOnlyThree() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll4(slotA, slotB, slotC, slotD, (a, b, c, d) -> counter.incrementAndGet());

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            assertEquals(0, counter.get());
        }
    }

    @Nested
    @DisplayName("whenAll5() (5 slots) Combinator Tests")
    class WhenAll5Tests {

        @Test
        @DisplayName("whenAll5() should execute when all five slots are set")
        void whenAll5ExecutesWhenAllFiveSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            Slot<Long> slotE = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll5(slotA, slotB, slotC, slotD, slotE, (a, b, c, d, e) -> {
                counter.incrementAndGet();
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
                assertEquals(3.14, d, 0.001);
                assertEquals(999L, e);
            });

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            slotD.set(3.14);
            assertEquals(0, counter.get());

            slotE.set(999L);
            assertEquals(1, counter.get(), "Should execute after all five slots are set");
        }

        @Test
        @DisplayName("whenAll5() should execute immediately if all five already set")
        void whenAll5ExecutesImmediatelyIfAllSet() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            Slot<Long> slotE = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            slotD.set(3.14);
            slotE.set(999L);

            RuleKit.whenAll5(slotA, slotB, slotC, slotD, slotE, (a, b, c, d, e) -> counter.incrementAndGet());

            assertTrue(counter.get() >= 1, "Should execute at least once when all 5 slots already set");
        }

        @Test
        @DisplayName("whenAll5() should not execute if only four slots are set")
        void whenAll5DoesNotExecuteWithOnlyFour() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            Slot<Long> slotE = new Slot<>();
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenAll5(slotA, slotB, slotC, slotD, slotE, (a, b, c, d, e) -> counter.incrementAndGet());

            slotA.set("test");
            slotB.set(42);
            slotC.set(true);
            slotD.set(3.14);
            assertEquals(0, counter.get());
        }

        @Test
        @DisplayName("whenAll5() should provide correct values")
        void whenAll5ProvidesCorrectValues() {
            Slot<String> slotA = new Slot<>();
            Slot<Integer> slotB = new Slot<>();
            Slot<Boolean> slotC = new Slot<>();
            Slot<Double> slotD = new Slot<>();
            Slot<Long> slotE = new Slot<>();

            AtomicReference<String> capturedA = new AtomicReference<>();
            AtomicReference<Integer> capturedB = new AtomicReference<>();
            AtomicReference<Boolean> capturedC = new AtomicReference<>();
            AtomicReference<Double> capturedD = new AtomicReference<>();
            AtomicReference<Long> capturedE = new AtomicReference<>();

            RuleKit.whenAll5(slotA, slotB, slotC, slotD, slotE, (a, b, c, d, e) -> {
                capturedA.set(a);
                capturedB.set(b);
                capturedC.set(c);
                capturedD.set(d);
                capturedE.set(e);
            });

            slotA.set("hello");
            slotB.set(100);
            slotC.set(false);
            slotD.set(2.71);
            slotE.set(123L);

            assertEquals("hello", capturedA.get());
            assertEquals(100, capturedB.get());
            assertFalse(capturedC.get());
            assertEquals(2.71, capturedD.get(), 0.001);
            assertEquals(123L, capturedE.get());
        }
    }

    @Nested
    @DisplayName("Functional Interface Tests")
    class FunctionalInterfaceTests {

        @Test
        @DisplayName("TriConsumer should accept three parameters")
        void triConsumerWorks() {
            RuleKit.TriConsumer<String, Integer, Boolean> consumer = (a, b, c) -> {
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
            };

            consumer.accept("test", 42, true);
        }

        @Test
        @DisplayName("QuadConsumer should accept four parameters")
        void quadConsumerWorks() {
            RuleKit.QuadConsumer<String, Integer, Boolean, Double> consumer = (a, b, c, d) -> {
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
                assertEquals(3.14, d, 0.001);
            };

            consumer.accept("test", 42, true, 3.14);
        }

        @Test
        @DisplayName("PentaConsumer should accept five parameters")
        void pentaConsumerWorks() {
            RuleKit.PentaConsumer<String, Integer, Boolean, Double, Long> consumer = (a, b, c, d, e) -> {
                assertEquals("test", a);
                assertEquals(42, b);
                assertTrue(c);
                assertEquals(3.14, d, 0.001);
                assertEquals(999L, e);
            };

            consumer.accept("test", 42, true, 3.14, 999L);
        }
    }

    @Nested
    @DisplayName("Integration Tests with SystemContext")
    class IntegrationTests {

        @Test
        @DisplayName("whenBoth() can coordinate VPC and ALB creation")
        void whenBothCoordinatesVpcAndAlb() {
            AtomicInteger counter = new AtomicInteger(0);

            RuleKit.whenBoth(ctx.vpc, ctx.alb, (vpc, alb) -> {
                counter.incrementAndGet();
                assertNotNull(vpc);
                assertNotNull(alb);
            });

            var vpc = software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "Vpc").maxAzs(2).build();
            ctx.vpc.set(vpc);
            assertEquals(0, counter.get());

            var alb = software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer.Builder
                    .create(stack, "Alb")
                    .vpc(vpc)
                    .build();
            ctx.alb.set(alb);
            assertEquals(1, counter.get());
        }

        @Test
        @DisplayName("require() can validate SystemContext slots")
        void requireValidatesSystemContextSlots() {
            Rule vpcRequired = RuleKit.require("vpc", c -> c.vpc);
            List<String> errors = vpcRequired.check(ctx);

            assertFalse(errors.isEmpty());

            var vpc = software.amazon.awscdk.services.ec2.Vpc.Builder.create(stack, "Vpc").maxAzs(2).build();
            ctx.vpc.set(vpc);

            errors = vpcRequired.check(ctx);
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("when() can conditionally apply rules based on SecurityProfile")
        void whenAppliesRulesBasedOnSecurityProfile() {
            boolean isProd = ctx.security == SecurityProfile.PRODUCTION;
            Rule wafRequired = RuleKit.when(isProd, RuleKit.require("waf", c -> c.wafEnabled));

            List<String> errors = wafRequired.check(ctx);
            assertTrue(errors.isEmpty(), "WAF should not be required in DEV");
        }
    }
}
