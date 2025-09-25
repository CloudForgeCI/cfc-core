# CloudForgeCI Extended Testing Suite

This document describes the expanded testing infrastructure for CloudForgeCI, including extended synthesis tests and performance benchmarks.

## Overview

The extended testing suite provides extensive coverage of all possible configuration combinations, ensuring robust validation of the CloudForgeCI CDK infrastructure.

## Test Scripts

### 1. `test-synth-extended.sh`
**Purpose**: Tests ALL possible combinations of CloudForgeCI configuration options.

**Coverage**:
- **Runtime Types**: EC2, Fargate
- **Topology Types**: service, node, s3-website
- **Security Profiles**: dev, staging, production
- **Network Modes**: public-no-nat, private-with-nat
- **SSL Options**: enabled/disabled
- **Domain Options**: with/without domain
- **IAM Profiles**: MINIMAL, STANDARD, EXTENDED, auto
- **Load Balancer Types**: ALB, NLB
- **Authentication Modes**: none, alb-oidc, jenkins-oidc
- **Feature Flags**: WAF, CloudFront
- **Resource Limits**: CPU, memory, scaling
- **Edge Cases**: Invalid configurations, resource limits

**Expected Results**:
- Most combinations should succeed
- EC2 + Node topology expected to fail (known architectural issue)
- Invalid configurations expected to fail
- Resource limit violations expected to fail

### 2. `benchmark-synth-comprehensive.sh`
**Purpose**: Performance analysis across all configuration categories.

**Categories**:
1. **Basic Configurations**: Core runtime/topology combinations
2. **Runtime Variations**: EC2 vs Fargate performance
3. **Topology Variations**: Service vs Node vs S3-Website
4. **Security Profile Variations**: Dev vs Staging vs Production
5. **Network Mode Variations**: Public vs Private networking
6. **SSL Variations**: With/without SSL overhead
7. **Domain Variations**: Domain resolution impact
8. **IAM Profile Variations**: Permission complexity impact
9. **Load Balancer Variations**: ALB vs NLB performance
10. **Authentication Variations**: Auth mode complexity
11. **Feature Variations**: WAF/CloudFront overhead
12. **Scaling Variations**: Instance count impact
13. **Resource Variations**: CPU/memory allocation impact
14. **Edge Cases**: Minimal vs Maximal configurations
15. **Comprehensive**: All combinations matrix

**Output**: Detailed performance metrics including min/max/average synthesis times per category.

### 3. `test-comprehensive-quick.sh`
**Purpose**: Quick validation of the comprehensive test infrastructure.

**Coverage**: 6 representative test cases covering key scenarios.

### 4. `run-comprehensive-tests.sh`
**Purpose**: Unified test runner with menu-driven interface.

**Options**:
1. Quick Synthesis Tests (original)
2. Comprehensive Synthesis Tests
3. Quick Performance Benchmark (original)
4. Comprehensive Performance Benchmark
5. Run All Tests (comprehensive)
6. Run All Tests (quick)

## Configuration Matrix

The comprehensive tests cover the following configuration space:

| Dimension | Options | Count |
|-----------|---------|-------|
| Runtime | EC2, Fargate | 2 |
| Topology | service, node, s3-website | 3 |
| Security | dev, staging, production | 3 |
| Network | public-no-nat, private-with-nat | 2 |
| SSL | true, false | 2 |
| Domain | true, false | 2 |
| IAM | MINIMAL, STANDARD, EXTENDED, auto | 4 |
| LB Type | alb, nlb | 2 |
| Auth | none, alb-oidc, jenkins-oidc | 3 |
| WAF | true, false | 2 |
| CloudFront | true, false | 2 |

**Total Theoretical Combinations**: 2 × 3 × 3 × 2 × 2 × 2 × 4 × 2 × 3 × 2 × 2 = **13,824**

**Valid Combinations**: Significantly fewer due to:
- SSL requires domain
- Auth modes require SSL
- Fargate doesn't support single-node topology
- Invalid configuration combinations

## Usage

### Running Comprehensive Synthesis Tests
```bash
./test-synth-comprehensive.sh
```

### Running Comprehensive Performance Benchmarks
```bash
./benchmark-synth-comprehensive.sh
```

### Using the Test Runner
```bash
./run-comprehensive-tests.sh
```

### Quick Validation
```bash
./test-comprehensive-quick.sh
```

## Expected Performance Characteristics

### Synthesis Time Ranges
- **Minimal Config**: ~2-5 seconds
- **Standard Config**: ~5-15 seconds
- **Complex Config**: ~15-30 seconds
- **Maximal Config**: ~30-60 seconds

### Performance Factors
1. **Runtime Type**: Fargate typically faster than EC2
2. **Security Profile**: Production > Staging > Dev
3. **Network Mode**: Private-with-NAT slower than public-no-NAT
4. **SSL**: Adds ~2-5 seconds
5. **Domain**: Adds ~1-3 seconds
6. **WAF/CloudFront**: Adds ~3-8 seconds
7. **Authentication**: Adds ~2-5 seconds

## Output Files

### Synthesis Test Results
- Console output with pass/fail status
- Summary statistics
- Detailed failure analysis

### Performance Benchmark Results
- `benchmark_<category>_times.txt`: Per-category results
- `benchmark_comprehensive_times.txt`: Complete matrix results
- CSV format: `test_name,min_time,max_time,avg_time,category`

## Integration with CI/CD

These comprehensive tests are designed to:
1. **Validate**: All configuration combinations work
2. **Performance**: Establish baseline metrics
3. **Regression**: Detect performance degradation
4. **Coverage**: Ensure complete feature validation

## Maintenance

### Adding New Configuration Options
1. Update test scripts with new combinations
2. Add to configuration matrix
3. Update expected results
4. Re-run comprehensive tests

### Performance Monitoring
1. Track synthesis time trends
2. Identify performance regressions
3. Optimize slow configurations
4. Update performance baselines

## Known Issues

1. **EC2 + Node Topology**: Architectural incompatibility with HTTPS listener default action
2. **S3-Website + SSL**: May require additional configuration
3. **Fargate + Single Node**: Not supported (topology mismatch)

## Future Enhancements

1. **Parallel Testing**: Run multiple combinations simultaneously
2. **Cloud Integration**: Test against actual AWS resources
3. **Performance Profiling**: Detailed CDK synthesis profiling
4. **Automated Reporting**: Generate performance trend reports
5. **Configuration Validation**: Automated validation of new options
