package com.cloudforge.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FargateCpuMemoryValidatorTest {

    private FargateCpuMemoryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FargateCpuMemoryValidator();
    }

    // Test config class with Fargate CPU/memory fields
    static class FargateConfig {
        public Integer fargateCpu;
        public Integer fargateMemory;
    }

    // Test config class without Fargate fields
    static class NonFargateConfig {
        public Integer otherField = 100;
    }

    private ConfigFieldInfo createFargateCpuFieldInfo() {
        return new ConfigFieldInfo(
            "fargateCpu",
            "Fargate CPU",
            "CPU units for Fargate task",
            "Compute",
            "always",
            "",
            true,
            "1024",
            new String[]{},
            256.0,
            16384.0,
            "",
            "",
            false,
            "",
            new com.cloudforge.core.annotation.FieldTag[]{},
            new String[]{"FargateCpuMemoryValidator"},
            10,
            Integer.class,
            null
        );
    }

    private ConfigFieldInfo createFargateMemoryFieldInfo() {
        return new ConfigFieldInfo(
            "fargateMemory",
            "Fargate Memory",
            "Memory in MB for Fargate task",
            "Compute",
            "always",
            "",
            true,
            "2048",
            new String[]{},
            512.0,
            122880.0,
            "",
            "",
            false,
            "",
            new com.cloudforge.core.annotation.FieldTag[]{},
            new String[]{"FargateCpuMemoryValidator"},
            11,
            Integer.class,
            null
        );
    }

    private ConfigFieldInfo createOtherFieldInfo() {
        return new ConfigFieldInfo(
            "someOtherField",
            "Other Field",
            "",
            "",
            "always",
            "",
            false,
            "",
            new String[]{},
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            "",
            "",
            false,
            "",
            new com.cloudforge.core.annotation.FieldTag[]{},
            new String[]{},
            0,
            Integer.class,
            null
        );
    }

    // Valid combinations for 256 CPU
    @ParameterizedTest
    @ValueSource(ints = {512, 1024, 2048})
    void testValidCombinationsFor256Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 256;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "256 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 512 CPU
    @ParameterizedTest
    @ValueSource(ints = {1024, 2048, 3072, 4096})
    void testValidCombinationsFor512Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 512;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "512 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 1024 CPU
    @ParameterizedTest
    @ValueSource(ints = {2048, 3072, 4096, 5120, 6144, 7168, 8192})
    void testValidCombinationsFor1024Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 1024;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "1024 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 2048 CPU (sample)
    @ParameterizedTest
    @ValueSource(ints = {4096, 8192, 12288, 16384})
    void testValidCombinationsFor2048Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 2048;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "2048 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 4096 CPU
    @ParameterizedTest
    @ValueSource(ints = {8192, 16384, 24576, 30720})
    void testValidCombinationsFor4096Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 4096;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "4096 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 8192 CPU
    @ParameterizedTest
    @ValueSource(ints = {16384, 32768, 61440})
    void testValidCombinationsFor8192Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 8192;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "8192 CPU with " + memory + " memory should be valid");
    }

    // Valid combinations for 16384 CPU
    @ParameterizedTest
    @ValueSource(ints = {32768, 65536, 122880})
    void testValidCombinationsFor16384Cpu(int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 16384;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isSuccess(), "16384 CPU with " + memory + " memory should be valid");
    }

    // Invalid combinations
    @ParameterizedTest
    @CsvSource({
        "256, 256",     // Too little memory for 256 CPU
        "256, 4096",    // Too much memory for 256 CPU
        "512, 512",     // Too little memory for 512 CPU
        "512, 8192",    // Too much memory for 512 CPU
        "1024, 1024",   // Too little memory for 1024 CPU
        "1024, 16384",  // Too much memory for 1024 CPU
    })
    void testInvalidCombinations(int cpu, int memory) {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = cpu;
        config.fargateMemory = memory;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), memory, config);
        assertTrue(result.isError(), "CPU " + cpu + " with memory " + memory + " should be invalid");
    }

    @Test
    void testInvalidCpuValue() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 128;  // Invalid CPU value
        config.fargateMemory = 1024;

        ValidationResult result = validator.validate(createFargateCpuFieldInfo(), 128, config);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Invalid Fargate CPU value"));
        assertTrue(result.getMessage().contains("256, 512, 1024, 2048, 4096, 8192, 16384"));
    }

    @Test
    void testSkipsValidationWhenConfigIsNull() {
        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), 2048, null);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenValueIsNull() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 1024;
        config.fargateMemory = 2048;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), null, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationForNonFargateField() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 1024;
        config.fargateMemory = 99999;  // Invalid value, but shouldn't matter

        ValidationResult result = validator.validate(createOtherFieldInfo(), 100, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenCpuFieldMissing() {
        NonFargateConfig config = new NonFargateConfig();

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), 2048, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenMemoryFieldMissing() {
        NonFargateConfig config = new NonFargateConfig();

        ValidationResult result = validator.validate(createFargateCpuFieldInfo(), 1024, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenCpuIsNull() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = null;
        config.fargateMemory = 2048;

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), 2048, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testSkipsValidationWhenMemoryIsNull() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 1024;
        config.fargateMemory = null;

        ValidationResult result = validator.validate(createFargateCpuFieldInfo(), 1024, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testValidationOnCpuField() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 1024;
        config.fargateMemory = 2048;

        ValidationResult result = validator.validate(createFargateCpuFieldInfo(), 1024, config);
        assertTrue(result.isSuccess());
    }

    @Test
    void testErrorMessageForInvalidMemory() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 256;
        config.fargateMemory = 8192;  // Invalid for 256 CPU

        ValidationResult result = validator.validate(createFargateMemoryFieldInfo(), 8192, config);
        assertTrue(result.isError());
        assertTrue(result.getMessage().contains("Invalid Fargate CPU/Memory combination"));
        assertTrue(result.getMessage().contains("256"));
    }

    @Test
    void testBoundaryMemoryFor4096Cpu() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 4096;

        // Test minimum valid
        config.fargateMemory = 8192;
        ValidationResult result1 = validator.validate(createFargateMemoryFieldInfo(), 8192, config);
        assertTrue(result1.isSuccess());

        // Test maximum valid
        config.fargateMemory = 30720;
        ValidationResult result2 = validator.validate(createFargateMemoryFieldInfo(), 30720, config);
        assertTrue(result2.isSuccess());

        // Test below minimum
        config.fargateMemory = 7168;
        ValidationResult result3 = validator.validate(createFargateMemoryFieldInfo(), 7168, config);
        assertTrue(result3.isError());

        // Test above maximum
        config.fargateMemory = 31744;
        ValidationResult result4 = validator.validate(createFargateMemoryFieldInfo(), 31744, config);
        assertTrue(result4.isError());
    }

    @Test
    void testMemoryIncrementValidation() {
        FargateConfig config = new FargateConfig();
        config.fargateCpu = 4096;

        // 4096 CPU requires 1024 MB increments
        // Valid: 8192, 9216, 10240...
        config.fargateMemory = 9216;  // Valid (8192 + 1024)
        ValidationResult result1 = validator.validate(createFargateMemoryFieldInfo(), 9216, config);
        assertTrue(result1.isSuccess());

        // Invalid: not on 1024 boundary
        config.fargateMemory = 8500;  // Invalid (not 1024 increment)
        ValidationResult result2 = validator.validate(createFargateMemoryFieldInfo(), 8500, config);
        assertTrue(result2.isError());
    }
}
