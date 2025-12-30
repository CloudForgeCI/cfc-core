package com.cloudforge.core.annotation;

/**
 * Migration guide for converting existing factory classes to use annotation-based context injection.
 *
 * <p>BEFORE (Old Pattern):</p>
 * <pre>{@code
 * public class VpcFactory extends Construct {
 *   public VpcFactory(Construct scope, String id) {
 *     super(scope, id);
 *     SystemContext ctx = SystemContext.of(this);
 *   }
 *
 *   public void create(final SystemContext ctx) {
 *     // Create VPC
 *     Vpc vpc = createVpc(ctx);
 *     ctx.vpc.set(vpc);
 *   }
 *
 *   private Vpc createVpc(SystemContext ctx) {
 *     return Vpc.Builder.create(this, "Vpc")
 *             .maxAzs(2)
 *             .build();
 *   }
 * }
 * }</pre>
 *
 * <p>AFTER (New Pattern):</p>
 * <pre>{@code
 * public class VpcFactory extends BaseFactory {
 *   public VpcFactory(Construct scope, String id) {
 *     super(scope, id);
 *   }
 *
 *   @Override
 *   public void create() {
 *     // Create VPC - ctx is automatically injected!
 *     Vpc vpc = createVpc();
 *     ctx.vpc.set(vpc);
 *   }
 *
 *   private Vpc createVpc() {
 *     return Vpc.Builder.create(this, "Vpc")
 *             .maxAzs(2)
 *             .build();
 *   }
 * }
 * }</pre>
 *
 * BENEFITS:
 * 1. No need to pass SystemContext as parameters
 * 2. No need to pass DeploymentContext as parameters
 * 3. Cleaner method signatures
 * 4. Less boilerplate code
 * 5. Automatic context injection
 * 6. Type safety with annotations
 *
 * <p>USAGE PATTERNS:</p>
 *
 * <p>1. Basic Factory (extends BaseFactory):</p>
 * <pre>{@code
 * public class MyFactory extends BaseFactory {
 *   public MyFactory(Construct scope, String id) {
 *     super(scope, id);
 *   }
 *
 *   @Override
 *   public void create() {
 *     // Use ctx (SystemContext) and cfc (DeploymentContext) directly
 *     String domain = cfc.domain();
 *     Vpc vpc = ctx.vpc.get().orElseThrow();
 *   }
 * }
 * }</pre>
 *
 * <p>2. Custom Injection (using annotation-based injection):</p>
 * <pre>{@code
 * public class MyFactory extends Construct {
 *   @InjectSystemContext
 *   private SystemContext ctx;
 *
 *   @InjectDeploymentContext
 *   private DeploymentContext cfc;
 *
 *   public MyFactory(Construct scope, String id) {
 *     super(scope, id);
 *     ContextInjector.injectFromConstruct(this, this);
 *   }
 * }
 * }</pre>
 *
 * <p>3. Manual Injection:</p>
 * <pre>{@code
 * public class MyFactory extends Construct {
 *   @InjectSystemContext
 *   private SystemContext ctx;
 *
 *   public MyFactory(Construct scope, String id, SystemContext systemContext) {
 *     super(scope, id);
 *     ContextInjector.injectContexts(this, systemContext, DeploymentContext.from(scope));
 *   }
 * }
 * }</pre>
 */
public class MigrationGuide {
    // This class serves as documentation only
}
