/**
 * Cross-module contracts for post-synthesis local CloudFormation deployment and emulator lifecycle.
 *
 * <p>Implementations live in target modules such as {@code cloudforge-ministack}
 * and {@code cloudforge-localstack}, registered via {@link LocalEmulatorRuntimeProvider}
 * and {@link StackPortRuntimeProvider}. Resolve runtimes with {@link LocalEmulatorRuntimes}
 * and {@link StackPortRuntimes}. Shared nginx edge: {@link EmulatorEdgeLifecycle}.
 * CDK synthesis stays in {@code cloudforge-api}. See
 * {@code docs/architecture/module-boundaries-and-refactor.plan.md}.</p>
 */
package com.cloudforge.core.local;
