package com.cloudforgeci.api.api;

import com.cloudforgeci.api.core.DeploymentContext;
import com.cloudforgeci.api.core.SystemContext;
import com.cloudforge.core.enums.IAMProfile;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.cloudforge.core.enums.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemContext lifecycle methods and behavior.
 * Covers start(), of(), once(), executeDeferredActions(), and related functionality.
 * Target: 15+ lifecycle tests.
 */
@DisplayName("SystemContext Lifecycle Tests")
class SystemContextLifecycleTest {

    private App app;
    private Stack stack;
    private DeploymentContext cfc;

    @BeforeEach
    void setUp() {
        app = new App();
        stack = new Stack(app, "LifecycleTestStack");
        cfc = DeploymentContext.from(stack);
    }

    @Nested
    @DisplayName("start() Method Tests")
    class StartMethodTests {

        @Test
        @DisplayName("start() should create SystemContext on first call")
        void startCreatesContextOnFirstCall() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertNotNull(ctx, "SystemContext should be created");
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
            assertEquals(RuntimeType.FARGATE, ctx.runtime);
            assertEquals(SecurityProfile.DEV, ctx.security);
            assertEquals(IAMProfile.MINIMAL, ctx.iamProfile);
        }

        @Test
        @DisplayName("start() should return same instance on subsequent calls with same parameters")
        void startReturnsSameInstance() {
            SystemContext ctx1 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertSame(ctx1, ctx2, "start() should return the same instance");
        }

        @Test
        @DisplayName("start() should throw exception when called with different topology")
        void startThrowsOnDifferentTopology() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);
            }, "Should throw IllegalStateException for different topology");
        }

        @Test
        @DisplayName("start() should throw exception when called with different security profile")
        void startThrowsOnDifferentSecurity() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc);
            }, "Should throw IllegalStateException for different security profile");
        }

        @Test
        @DisplayName("start() should throw exception when called with different IAM profile")
        void startThrowsOnDifferentIamProfile() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertThrows(IllegalStateException.class, () -> {
                SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                        SecurityProfile.DEV, IAMProfile.EXTENDED, cfc);
            }, "Should throw IllegalStateException for different IAM profile");
        }

        @Test
        @DisplayName("start() allows different runtime types in same stack")
        void startAllowsDifferentRuntimes() {
            // First start with FARGATE
            SystemContext ctx1 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Second start with EC2 should return same instance (runtime is not checked for equality)
            SystemContext ctx2 = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.EC2,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertSame(ctx1, ctx2, "Should return same instance even with different runtime");
        }
    }

    @Nested
    @DisplayName("of() Method Tests")
    class OfMethodTests {

        @Test
        @DisplayName("of() should return SystemContext when already started")
        void ofReturnsContextWhenStarted() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            SystemContext ctx = SystemContext.of(stack);

            assertNotNull(ctx, "of() should return SystemContext");
            assertEquals(TopologyType.JENKINS_SERVICE, ctx.topology);
        }

        @Test
        @DisplayName("of() should throw exception when not started")
        void ofThrowsWhenNotStarted() {
            assertThrows(IllegalStateException.class, () -> {
                SystemContext.of(stack);
            }, "of() should throw IllegalStateException when context not started");
        }

        @Test
        @DisplayName("of() should find context from child constructs")
        void ofFindsContextFromChildren() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Create a child construct
            software.constructs.Construct child = new software.constructs.Construct(stack, "ChildConstruct");

            SystemContext foundCtx = SystemContext.of(child);

            assertSame(ctx, foundCtx, "of() should find context from child construct");
        }

        @Test
        @DisplayName("of() should return same instance as start()")
        void ofReturnsSameAsStart() {
            SystemContext startedCtx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            SystemContext foundCtx = SystemContext.of(stack);

            assertSame(startedCtx, foundCtx, "of() and start() should return same instance");
        }
    }

    @Nested
    @DisplayName("once() Method Tests")
    class OnceMethodTests {

        @Test
        @DisplayName("once() should execute action on first call")
        void onceExecutesOnFirstCall() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            boolean executed = ctx.once("test-action", () -> counter.incrementAndGet());

            assertTrue(executed, "once() should return true on first execution");
            assertEquals(0, counter.get(), "Action should be deferred, not executed immediately");
        }

        @Test
        @DisplayName("once() should not execute action on second call with same key")
        void onceDoesNotExecuteTwice() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            ctx.once("test-action", () -> counter.incrementAndGet());
            boolean secondExecution = ctx.once("test-action", () -> counter.incrementAndGet());

            assertFalse(secondExecution, "once() should return false on second execution");
        }

        @Test
        @DisplayName("once() should allow different keys to execute")
        void onceAllowsDifferentKeys() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            boolean executed1 = ctx.once("action-1", () -> {});
            boolean executed2 = ctx.once("action-2", () -> {});

            assertTrue(executed1, "First action should be registered");
            assertTrue(executed2, "Second action with different key should be registered");
        }

        @Test
        @DisplayName("once() registered actions should execute when executeDeferredActions is called")
        void onceActionsExecuteWhenDeferred() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            ctx.once("test-action", () -> counter.incrementAndGet());

            ctx.executeDeferredActions();

            assertEquals(1, counter.get(), "Deferred action should execute when executeDeferredActions is called");
        }
    }

    @Nested
    @DisplayName("executeDeferredActions() Tests")
    class ExecuteDeferredActionsTests {

        @Test
        @DisplayName("executeDeferredActions() should execute all registered actions")
        void executesAllRegisteredActions() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            ctx.once("action-1", counter::incrementAndGet);
            ctx.once("action-2", counter::incrementAndGet);
            ctx.once("action-3", counter::incrementAndGet);

            ctx.executeDeferredActions();

            assertEquals(3, counter.get(), "All three deferred actions should execute");
        }

        @Test
        @DisplayName("executeDeferredActions() should execute actions in registration order")
        void executesActionsInOrder() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            StringBuilder order = new StringBuilder();
            ctx.once("first", () -> order.append("1"));
            ctx.once("second", () -> order.append("2"));
            ctx.once("third", () -> order.append("3"));

            ctx.executeDeferredActions();

            assertEquals("123", order.toString(), "Actions should execute in registration order");
        }

        @Test
        @DisplayName("executeDeferredActions() should clear action list after execution")
        void clearsActionsAfterExecution() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            ctx.once("action", counter::incrementAndGet);

            ctx.executeDeferredActions();
            assertEquals(1, counter.get());

            // Execute again - counter should not increase since actions were cleared
            ctx.executeDeferredActions();
            assertEquals(1, counter.get(), "Actions should not re-execute after being cleared");
        }

        @Test
        @DisplayName("executeDeferredActions() should handle empty action list")
        void handlesEmptyActionList() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Should not throw exception
            assertDoesNotThrow(() -> ctx.executeDeferredActions(),
                    "executeDeferredActions() should handle empty action list");
        }

        @Test
        @DisplayName("executeDeferredActions() should allow new actions to be registered after execution")
        void allowsNewActionsAfterExecution() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            AtomicInteger counter = new AtomicInteger(0);
            ctx.once("action-1", counter::incrementAndGet);

            ctx.executeDeferredActions();
            assertEquals(1, counter.get());

            // Register new action after execution
            ctx.once("action-2", counter::incrementAndGet);
            ctx.executeDeferredActions();

            assertEquals(2, counter.get(), "New actions should be executable after first execution");
        }
    }

    @Nested
    @DisplayName("Stack Name and Identity Tests")
    class StackIdentityTests {

        @Test
        @DisplayName("stackName should match stack name from CDK")
        void stackNameMatchesCdkStack() {
            SystemContext ctx = SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            assertEquals("LifecycleTestStack", ctx.stackName, "Stack name should match");
        }

        @Test
        @DisplayName("SystemContext should be attached as child of stack")
        void contextAttachedToStack() {
            SystemContext.start(stack, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc);

            // Context should be findable as child
            Object child = stack.getNode().tryFindChild("SystemContext");

            assertNotNull(child, "SystemContext should be attached as child of stack");
            assertInstanceOf(SystemContext.class, child, "Child should be SystemContext instance");
        }
    }

    @Nested
    @DisplayName("Multiple Stack Scenarios")
    class MultipleStackTests {

        @Test
        @DisplayName("Different stacks should have independent SystemContexts")
        void differentStacksHaveIndependentContexts() {
            Stack stack1 = new Stack(app, "Stack1");
            Stack stack2 = new Stack(app, "Stack2");
            DeploymentContext cfc1 = DeploymentContext.from(stack1);
            DeploymentContext cfc2 = DeploymentContext.from(stack2);

            SystemContext ctx1 = SystemContext.start(stack1, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc1);

            SystemContext ctx2 = SystemContext.start(stack2, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.EXTENDED, cfc2);

            assertNotSame(ctx1, ctx2, "Different stacks should have different contexts");
            assertEquals(SecurityProfile.DEV, ctx1.security);
            assertEquals(SecurityProfile.PRODUCTION, ctx2.security);
        }

        @Test
        @DisplayName("of() should find correct context for each stack")
        void ofFindsCorrectContextPerStack() {
            Stack stack1 = new Stack(app, "Stack1");
            Stack stack2 = new Stack(app, "Stack2");
            DeploymentContext cfc1 = DeploymentContext.from(stack1);
            DeploymentContext cfc2 = DeploymentContext.from(stack2);

            SystemContext ctx1 = SystemContext.start(stack1, TopologyType.JENKINS_SERVICE, RuntimeType.FARGATE,
                    SecurityProfile.DEV, IAMProfile.MINIMAL, cfc1);

            SystemContext ctx2 = SystemContext.start(stack2, TopologyType.S3_WEBSITE, RuntimeType.FARGATE,
                    SecurityProfile.PRODUCTION, IAMProfile.MINIMAL, cfc2);

            SystemContext found1 = SystemContext.of(stack1);
            SystemContext found2 = SystemContext.of(stack2);

            assertSame(ctx1, found1, "of() should find correct context for stack1");
            assertSame(ctx2, found2, "of() should find correct context for stack2");
            assertEquals(TopologyType.JENKINS_SERVICE, found1.topology);
            assertEquals(TopologyType.S3_WEBSITE, found2.topology);
        }
    }
}
