package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.interfaces.SecurityProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive threshold validation tests for SecurityMonitoringFactory.
 * Tests threshold calculation methods and boundary conditions.
 */
@DisplayName("SecurityMonitoringFactory Threshold Tests")
class SecurityMonitoringFactoryThresholdTest {

    private SecurityMonitoringFactory factory;
    private Method getCpuThresholdMethod;
    private Method getMemoryThresholdMethod;
    private Method getNetworkThresholdMethod;
    private App app;
    private Stack stack;

    @BeforeEach
    void setUp() throws Exception {
        // Create proper CDK context for testing
        app = new App();
        stack = new Stack(app, "TestStack");
        
        // Create a factory instance for testing
        factory = new SecurityMonitoringFactory(stack, "TestFactory");
        
        // Get private threshold methods using reflection
        getCpuThresholdMethod = SecurityMonitoringFactory.class.getDeclaredMethod("getHighCpuThreshold", SecurityProfile.class);
        getCpuThresholdMethod.setAccessible(true);
        
        getMemoryThresholdMethod = SecurityMonitoringFactory.class.getDeclaredMethod("getHighMemoryThreshold", SecurityProfile.class);
        getMemoryThresholdMethod.setAccessible(true);
        
        getNetworkThresholdMethod = SecurityMonitoringFactory.class.getDeclaredMethod("getHighNetworkThreshold", SecurityProfile.class);
        getNetworkThresholdMethod.setAccessible(true);
    }

    @Nested
    @DisplayName("CPU Threshold Tests")
    class CpuThresholdTests {

        @Test
        @DisplayName("Should return correct CPU threshold for DEV profile")
        void shouldReturnCorrectCpuThresholdForDevProfile() throws Exception {
            double threshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.DEV);
            assertEquals(90.0, threshold, "DEV CPU threshold should be 90%");
        }

        @Test
        @DisplayName("Should return correct CPU threshold for STAGING profile")
        void shouldReturnCorrectCpuThresholdForStagingProfile() throws Exception {
            double threshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            assertEquals(80.0, threshold, "STAGING CPU threshold should be 80%");
        }

        @Test
        @DisplayName("Should return correct CPU threshold for PRODUCTION profile")
        void shouldReturnCorrectCpuThresholdForProductionProfile() throws Exception {
            double threshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            assertEquals(70.0, threshold, "PRODUCTION CPU threshold should be 70%");
        }

        @ParameterizedTest
        @EnumSource(SecurityProfile.class)
        @DisplayName("Should return valid CPU threshold for all security profiles")
        void shouldReturnValidCpuThresholdForAllSecurityProfiles(SecurityProfile profile) throws Exception {
            double threshold = (double) getCpuThresholdMethod.invoke(factory, profile);
            assertTrue(threshold > 0, "CPU threshold should be positive");
            assertTrue(threshold <= 100, "CPU threshold should not exceed 100%");
        }

        @Test
        @DisplayName("CPU thresholds should follow security strictness order")
        void cpuThresholdsShouldFollowSecurityStrictnessOrder() throws Exception {
            double devThreshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingThreshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double productionThreshold = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devThreshold >= stagingThreshold, "DEV should have higher or equal threshold than STAGING");
            assertTrue(stagingThreshold >= productionThreshold, "STAGING should have higher or equal threshold than PRODUCTION");
        }
    }

    @Nested
    @DisplayName("Memory Threshold Tests")
    class MemoryThresholdTests {

        @Test
        @DisplayName("Should return correct memory threshold for DEV profile")
        void shouldReturnCorrectMemoryThresholdForDevProfile() throws Exception {
            double threshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.DEV);
            assertEquals(90.0, threshold, "DEV memory threshold should be 90%");
        }

        @Test
        @DisplayName("Should return correct memory threshold for STAGING profile")
        void shouldReturnCorrectMemoryThresholdForStagingProfile() throws Exception {
            double threshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            assertEquals(85.0, threshold, "STAGING memory threshold should be 85%");
        }

        @Test
        @DisplayName("Should return correct memory threshold for PRODUCTION profile")
        void shouldReturnCorrectMemoryThresholdForProductionProfile() throws Exception {
            double threshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            assertEquals(80.0, threshold, "PRODUCTION memory threshold should be 80%");
        }

        @ParameterizedTest
        @EnumSource(SecurityProfile.class)
        @DisplayName("Should return valid memory threshold for all security profiles")
        void shouldReturnValidMemoryThresholdForAllSecurityProfiles(SecurityProfile profile) throws Exception {
            double threshold = (double) getMemoryThresholdMethod.invoke(factory, profile);
            assertTrue(threshold > 0, "Memory threshold should be positive");
            assertTrue(threshold <= 100, "Memory threshold should not exceed 100%");
        }

        @Test
        @DisplayName("Memory thresholds should follow security strictness order")
        void memoryThresholdsShouldFollowSecurityStrictnessOrder() throws Exception {
            double devThreshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingThreshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double productionThreshold = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devThreshold >= stagingThreshold, "DEV should have higher or equal threshold than STAGING");
            assertTrue(stagingThreshold >= productionThreshold, "STAGING should have higher or equal threshold than PRODUCTION");
        }
    }

    @Nested
    @DisplayName("Network Threshold Tests")
    class NetworkThresholdTests {

        @Test
        @DisplayName("Should return correct network threshold for DEV profile")
        void shouldReturnCorrectNetworkThresholdForDevProfile() throws Exception {
            double threshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.DEV);
            assertEquals(1000000000.0, threshold, "DEV network threshold should be 1GB");
        }

        @Test
        @DisplayName("Should return correct network threshold for STAGING profile")
        void shouldReturnCorrectNetworkThresholdForStagingProfile() throws Exception {
            double threshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            assertEquals(500000000.0, threshold, "STAGING network threshold should be 500MB");
        }

        @Test
        @DisplayName("Should return correct network threshold for PRODUCTION profile")
        void shouldReturnCorrectNetworkThresholdForProductionProfile() throws Exception {
            double threshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            assertEquals(100000000.0, threshold, "PRODUCTION network threshold should be 100MB");
        }

        @ParameterizedTest
        @EnumSource(SecurityProfile.class)
        @DisplayName("Should return valid network threshold for all security profiles")
        void shouldReturnValidNetworkThresholdForAllSecurityProfiles(SecurityProfile profile) throws Exception {
            double threshold = (double) getNetworkThresholdMethod.invoke(factory, profile);
            assertTrue(threshold > 0, "Network threshold should be positive");
            assertTrue(threshold >= 100000000.0, "Network threshold should be at least 100MB");
        }

        @Test
        @DisplayName("Network thresholds should follow security strictness order")
        void networkThresholdsShouldFollowSecurityStrictnessOrder() throws Exception {
            double devThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double productionThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devThreshold >= stagingThreshold, "DEV should have higher or equal threshold than STAGING");
            assertTrue(stagingThreshold >= productionThreshold, "STAGING should have higher or equal threshold than PRODUCTION");
        }

        @Test
        @DisplayName("Network thresholds should represent reasonable byte values")
        void networkThresholdsShouldRepresentReasonableByteValues() throws Exception {
            double devThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double productionThreshold = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            // DEV: 1GB = 1,000,000,000 bytes
            assertEquals(1000000000.0, devThreshold, "DEV threshold should be exactly 1GB");
            
            // STAGING: 500MB = 500,000,000 bytes
            assertEquals(500000000.0, stagingThreshold, "STAGING threshold should be exactly 500MB");
            
            // PRODUCTION: 100MB = 100,000,000 bytes
            assertEquals(100000000.0, productionThreshold, "PRODUCTION threshold should be exactly 100MB");
        }
    }

    @Nested
    @DisplayName("Threshold Boundary Tests")
    class ThresholdBoundaryTests {

        @Test
        @DisplayName("CPU thresholds should be reasonable for monitoring")
        void cpuThresholdsShouldBeReasonableForMonitoring() throws Exception {
            for (SecurityProfile profile : SecurityProfile.values()) {
                double threshold = (double) getCpuThresholdMethod.invoke(factory, profile);
                assertTrue(threshold >= 60.0, "CPU threshold should be at least 60% for " + profile);
                assertTrue(threshold <= 95.0, "CPU threshold should be at most 95% for " + profile);
            }
        }

        @Test
        @DisplayName("Memory thresholds should be reasonable for monitoring")
        void memoryThresholdsShouldBeReasonableForMonitoring() throws Exception {
            for (SecurityProfile profile : SecurityProfile.values()) {
                double threshold = (double) getMemoryThresholdMethod.invoke(factory, profile);
                assertTrue(threshold >= 70.0, "Memory threshold should be at least 70% for " + profile);
                assertTrue(threshold <= 95.0, "Memory threshold should be at most 95% for " + profile);
            }
        }

        @Test
        @DisplayName("Network thresholds should be reasonable for monitoring")
        void networkThresholdsShouldBeReasonableForMonitoring() throws Exception {
            for (SecurityProfile profile : SecurityProfile.values()) {
                double threshold = (double) getNetworkThresholdMethod.invoke(factory, profile);
                assertTrue(threshold >= 50000000.0, "Network threshold should be at least 50MB for " + profile);
                assertTrue(threshold <= 2000000000.0, "Network threshold should be at most 2GB for " + profile);
            }
        }
    }

    @Nested
    @DisplayName("Threshold Consistency Tests")
    class ThresholdConsistencyTests {

        @Test
        @DisplayName("Production should have the strictest thresholds")
        void productionShouldHaveTheStrictestThresholds() throws Exception {
            double prodCpu = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            double prodMemory = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            double prodNetwork = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            for (SecurityProfile profile : SecurityProfile.values()) {
                if (profile != SecurityProfile.PRODUCTION) {
                    double cpu = (double) getCpuThresholdMethod.invoke(factory, profile);
                    double memory = (double) getMemoryThresholdMethod.invoke(factory, profile);
                    double network = (double) getNetworkThresholdMethod.invoke(factory, profile);
                    
                    assertTrue(cpu >= prodCpu, "Production should have lowest CPU threshold");
                    assertTrue(memory >= prodMemory, "Production should have lowest memory threshold");
                    assertTrue(network >= prodNetwork, "Production should have lowest network threshold");
                }
            }
        }

        @Test
        @DisplayName("DEV should have the most relaxed thresholds")
        void devShouldHaveTheMostRelaxedThresholds() throws Exception {
            double devCpu = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double devMemory = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double devNetwork = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.DEV);
            
            for (SecurityProfile profile : SecurityProfile.values()) {
                if (profile != SecurityProfile.DEV) {
                    double cpu = (double) getCpuThresholdMethod.invoke(factory, profile);
                    double memory = (double) getMemoryThresholdMethod.invoke(factory, profile);
                    double network = (double) getNetworkThresholdMethod.invoke(factory, profile);
                    
                    assertTrue(devCpu >= cpu, "DEV should have highest CPU threshold");
                    assertTrue(devMemory >= memory, "DEV should have highest memory threshold");
                    assertTrue(devNetwork >= network, "DEV should have highest network threshold");
                }
            }
        }

        @Test
        @DisplayName("Threshold differences should be meaningful")
        void thresholdDifferencesShouldBeMeaningful() throws Exception {
            // CPU thresholds should have meaningful differences
            double devCpu = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingCpu = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double prodCpu = (double) getCpuThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devCpu - stagingCpu >= 5.0, "DEV and STAGING CPU thresholds should differ by at least 5%");
            assertTrue(stagingCpu - prodCpu >= 5.0, "STAGING and PRODUCTION CPU thresholds should differ by at least 5%");
            
            // Memory thresholds should have meaningful differences
            double devMemory = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingMemory = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double prodMemory = (double) getMemoryThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devMemory - stagingMemory >= 3.0, "DEV and STAGING memory thresholds should differ by at least 3%");
            assertTrue(stagingMemory - prodMemory >= 3.0, "STAGING and PRODUCTION memory thresholds should differ by at least 3%");
            
            // Network thresholds should have meaningful differences
            double devNetwork = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.DEV);
            double stagingNetwork = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.STAGING);
            double prodNetwork = (double) getNetworkThresholdMethod.invoke(factory, SecurityProfile.PRODUCTION);
            
            assertTrue(devNetwork / stagingNetwork >= 1.5, "DEV network threshold should be at least 1.5x STAGING");
            assertTrue(stagingNetwork / prodNetwork >= 2.0, "STAGING network threshold should be at least 2x PRODUCTION");
        }
    }

    @Nested
    @DisplayName("Method Behavior Tests")
    class MethodBehaviorTests {

        @Test
        @DisplayName("Threshold methods should be instance methods")
        void thresholdMethodsShouldBeInstanceMethods() {
            assertFalse(java.lang.reflect.Modifier.isStatic(getCpuThresholdMethod.getModifiers()), 
                "getCpuThreshold should be an instance method");
            assertFalse(java.lang.reflect.Modifier.isStatic(getMemoryThresholdMethod.getModifiers()), 
                "getMemoryThreshold should be an instance method");
            assertFalse(java.lang.reflect.Modifier.isStatic(getNetworkThresholdMethod.getModifiers()), 
                "getNetworkThreshold should be an instance method");
        }

        @Test
        @DisplayName("Threshold methods should be private")
        void thresholdMethodsShouldBePrivate() {
            assertTrue(java.lang.reflect.Modifier.isPrivate(getCpuThresholdMethod.getModifiers()), 
                "getCpuThreshold should be private");
            assertTrue(java.lang.reflect.Modifier.isPrivate(getMemoryThresholdMethod.getModifiers()), 
                "getMemoryThreshold should be private");
            assertTrue(java.lang.reflect.Modifier.isPrivate(getNetworkThresholdMethod.getModifiers()), 
                "getNetworkThreshold should be private");
        }

        @Test
        @DisplayName("Threshold methods should return double")
        void thresholdMethodsShouldReturnDouble() {
            assertEquals(double.class, getCpuThresholdMethod.getReturnType(), 
                "getCpuThreshold should return double");
            assertEquals(double.class, getMemoryThresholdMethod.getReturnType(), 
                "getMemoryThreshold should return double");
            assertEquals(double.class, getNetworkThresholdMethod.getReturnType(), 
                "getNetworkThreshold should return double");
        }

        @Test
        @DisplayName("Threshold methods should handle null gracefully")
        void thresholdMethodsShouldHandleNullGracefully() {
            assertThrows(Exception.class, () -> getCpuThresholdMethod.invoke(factory, (SecurityProfile) null), 
                "getCpuThreshold should throw exception with null input");
            assertThrows(Exception.class, () -> getMemoryThresholdMethod.invoke(factory, (SecurityProfile) null), 
                "getMemoryThreshold should throw exception with null input");
            assertThrows(Exception.class, () -> getNetworkThresholdMethod.invoke(factory, (SecurityProfile) null), 
                "getNetworkThreshold should throw exception with null input");
        }
    }
}
