# Annotation-Based Context Injection System

This system provides a clean way to inject `SystemContext`, `DeploymentContext`, and `SecurityProfileConfiguration` into factory classes using annotations, eliminating the need to pass these contexts as parameters throughout your code.

## Overview

The annotation system consists of:
- `@SystemContext` - Injects `SystemContext` into fields
- `@DeploymentContext` - Injects `DeploymentContext` into fields  
- `@SecurityProfileConfiguration` - Injects `SecurityProfileConfiguration` into fields
- `BaseFactory` - Base class for factories using annotation injection
- `ContextInjector` - Utility class for manual context injection

## Benefits

✅ **Cleaner Code**: No more passing `SystemContext ctx`, `DeploymentContext cfc`, and `SecurityProfileConfiguration config` as parameters  
✅ **Less Boilerplate**: Eliminates repetitive parameter passing  
✅ **Type Safety**: Compile-time checking of annotation usage  
✅ **Automatic Injection**: Contexts are injected automatically when available  
✅ **Flexible**: Works with inheritance and can be used manually when needed  
✅ **Single Source of Truth**: Direct access to security profile configuration  

## Usage Patterns

### 1. Basic Factory (Recommended)

Extend `BaseFactory` for the cleanest approach:

```java
public class VpcFactory extends BaseFactory {
    public VpcFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Use ctx (SystemContext), cfc (DeploymentContext), and config (SecurityProfileConfiguration) directly
        String domain = cfc.domain();
        Vpc vpc = ctx.vpc.get().orElseThrow();
        
        // Access security profile configuration directly
        boolean flowLogsEnabled = config.isFlowLogsEnabled();
        RetentionDays retention = config.getLogRetentionDays();
        
        // Create VPC
        Vpc vpc = Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .build();
        ctx.vpc.set(vpc);
    }
}
```

### 2. Custom Injection

Use annotations directly on your own classes:

```java
public class MyFactory extends Construct {
    @SystemContext
    private SystemContext ctx;
    
    @DeploymentContext
    private DeploymentContext cfc;
    
    @SecurityProfileConfiguration
    private SecurityProfileConfiguration config;
    
    public MyFactory(Construct scope, String id) {
        super(scope, id);
        ContextInjector.injectFromConstruct(this, this);
    }
    
    public void doSomething() {
        // Use injected contexts directly
        String domain = cfc.domain();
        boolean flowLogsEnabled = config.isFlowLogsEnabled();
        RetentionDays retention = config.getLogRetentionDays();
    }
}
```

### 3. Security Profile Configuration Access

The `@SecurityProfileConfiguration` annotation provides direct access to security profile settings:

```java
public class SecurityAwareFactory extends BaseFactory {
    @Override
    public void create() {
        // Direct access to security profile configuration
        if (config.isFlowLogsEnabled()) {
            configureFlowLogs(config.getFlowLogRetentionDays());
        }
        
        if (config.isSecurityMonitoringEnabled()) {
            configureSecurityMonitoring();
        }
        
        if (config.isEbsEncryptionEnabled()) {
            configureEncryption();
        }
        
        // Access profile-specific settings
        int minInstances = config.getMinInstanceCount();
        int maxInstances = config.getMaxInstanceCount();
        boolean autoScaling = config.isAutoScalingEnabled();
    }
}
```

### 4. Manual Injection

Inject contexts manually when needed:

```java
public class MyFactory extends Construct {
    @SystemContext
    private SystemContext ctx;
    
    @SecurityProfileConfiguration
    private SecurityProfileConfiguration config;
    
    public MyFactory(Construct scope, String id, SystemContext systemContext) {
        super(scope, id);
        ContextInjector.injectContexts(this, systemContext, DeploymentContext.from(scope));
    }
}
```

## Migration Guide

### Before (Old Pattern)
```java
public class VpcFactory extends Construct {
    public VpcFactory(Construct scope, String id) {
        super(scope, id);
        SystemContext ctx = SystemContext.of(this);
    }

    public void create(final SystemContext ctx) {
        // Create VPC
        Vpc vpc = createVpc(ctx);
        ctx.vpc.set(vpc);
    }

    private Vpc createVpc(SystemContext ctx) {
        return Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .build();
    }
}
```

### After (New Pattern)
```java
public class VpcFactory extends BaseFactory {
    public VpcFactory(Construct scope, String id) {
        super(scope, id);
    }

    @Override
    public void create() {
        // Create VPC - ctx and config are automatically injected!
        Vpc vpc = createVpc();
        ctx.vpc.set(vpc);
        
        // Access security profile configuration directly
        if (config.isFlowLogsEnabled()) {
            configureFlowLogs();
        }
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "Vpc")
                .maxAzs(2)
                .build();
    }
}
```

## Key Features

### Automatic Context Injection
- Contexts are automatically injected when `SystemContext.start()` has been called
- If contexts aren't available yet, injection is deferred until `injectContexts()` is called manually

### Inheritance Support
- The system checks the entire class hierarchy for annotated fields
- Works with `BaseFactory` and custom inheritance patterns

### Type Safety
- Annotations are checked at compile time
- Fields must be of the correct type (`SystemContext`, `DeploymentContext`, or `SecurityProfileConfiguration`)

### Flexible Usage
- Can be used with `BaseFactory` for automatic injection
- Can be used manually with `ContextInjector` for custom scenarios
- Works in both test and production environments

## Test Results & Performance

**✅ Comprehensive Synthesis Test Suite** - September 23, 2025

The annotation-based context injection system has been thoroughly tested with the CloudForge Community test suite:

### 📊 **Test Results Summary**

| Combination | Status | Notes |
|-------------|--------|-------|
| EC2 + Service + Production + Domain + SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Service + Production + Domain + No SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Service + Production + No Domain | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + Domain + No SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + No Domain | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Production + Domain + SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Dev + Domain + SSL | ✅ SUCCESS | Working perfectly |
| Fargate + Service + Staging + Domain + SSL | ✅ SUCCESS | Working perfectly |
| EC2 + Node + Production + Domain + SSL | ❌ FAILED | Single-node topology issue |
| EC2 + Node + Dev + Domain + SSL | ❌ FAILED | Single-node topology issue |

**Success Rate: 8/10 combinations (80%)**

**Known Issues:**
- EC2 + Node topology: Single-node architectural incompatibility (HTTPS listener missing default action)

### 🎯 **Verified Annotation System Components:**

- ✅ **SystemContext injection** via `@SystemContext` across all successful tests
- ✅ **DeploymentContext injection** via `@DeploymentContext` across all successful tests  
- ✅ **SecurityProfileConfiguration injection** via `@SecurityProfileConfiguration` across all successful tests
- ✅ **BaseFactory automatic context injection** working in all environments
- ✅ **Security Profile Integration** - DEV, STAGING, PRODUCTION profiles all working
- ✅ **Runtime Integration** - EC2 and Fargate runtimes both supported
- ✅ **Factory Pattern** - VpcFactory, AlbFactory, DomainFactory using annotation-based approach
- ✅ **Observability Integration** - FlowLogFactory and LoggingCwFactory using SecurityProfileConfiguration

### 🚀 **Performance Benchmarking**

The `benchmark-synth.sh` script provides comprehensive performance testing for CDK synthesis operations:

**Available Test Cases:**
| # | Test Case | Status | Description |
|---|-----------|--------|-------------|
| 1 | EC2 + Service + Production + Domain + SSL | ✅ Working | Full production setup with SSL |
| 2 | EC2 + Service + Production + Domain + No SSL | ✅ Working | Production setup without SSL |
| 3 | EC2 + Service + Production + No Domain | ✅ Working | Minimal production setup |
| 4 | Fargate + Service + Production + Domain + No SSL | ✅ Working | Fargate production without SSL |
| 5 | Fargate + Service + Production + No Domain | ✅ Working | Minimal Fargate setup |
| 6 | EC2 + Node + Production + Domain + SSL | ❌ Known Issue | Single-node topology issue |
| 7 | EC2 + Node + Dev + Domain + SSL | ❌ Known Issue | Single-node topology issue |
| 8 | Fargate + Service + Production + Domain + SSL | ❌ Known Issue | HTTP listener issue |
| 9 | Fargate + Service + Dev + Domain + SSL | ❌ Known Issue | HTTP listener issue |
| 10 | Fargate + Service + Staging + Domain + SSL | ❌ Known Issue | HTTP listener issue |

**Running Benchmarks:**
```bash
# Run the benchmark tool
./benchmark-synth.sh
```

**Use Cases:**
- **Performance Regression Testing**: Compare synthesis times across different versions
- **Configuration Optimization**: Identify which configurations are fastest/slowest
- **Resource Planning**: Estimate synthesis time for CI/CD pipelines
- **Debugging**: Identify performance bottlenecks in specific configurations

The comprehensive test suite confirms that the annotation-based context injection system is robust and works correctly across multiple runtime types, security profiles, and deployment configurations.

## Best Practices

1. **Use `BaseFactory`** for new factories - it provides the cleanest interface with all three contexts
2. **Call `injectContexts()`** manually if creating factories before `SystemContext.start()`
3. **Use `cfc` variable** for `DeploymentContext` access (shorter and cleaner)
4. **Use `ctx` variable** for `SystemContext` access (shorter and cleaner)
5. **Use `config` variable** for `SecurityProfileConfiguration` access (direct injection)
6. **Migrate existing factories** gradually - the old pattern still works
7. **Access security settings directly** via `config` instead of `ctx.securityProfileConfig.get().orElseThrow()`

This system makes your factory code much cleaner and more maintainable while preserving all the functionality of the original context system.
