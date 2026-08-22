package com.cloudforgeci.api.deploy.catalog;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.servicecatalog.ServiceCatalogClient;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what can be verified without live AWS credentials: input validation ({@link
 * ProvisionProductInput}/{@link UpdateProvisionedProductInput}'s compact constructors) and error
 * paths against an intentionally-unreachable endpoint (same pattern {@code AwsDirectDeployerTest}/
 * {@code LocalStackDeployerLifecycleTest} already use) — proving {@code provision}/{@code
 * terminate}/{@code update} propagate connection failures rather than hanging or silently
 * succeeding.
 *
 * <p>Does NOT exercise a real successful provision/update — see {@link ServiceCatalogDeployer}'s
 * class javadoc; no AWS credentials or a real published product exist in this environment.</p>
 */
class ServiceCatalogDeployerTest {

    private static ServiceCatalogDeployer unreachableDeployer() {
        ServiceCatalogClient client = ServiceCatalogClient.builder()
            .endpointOverride(URI.create("http://127.0.0.1:1"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
        return new ServiceCatalogDeployer(client);
    }

    @Test
    void inputRequiresProductId() {
        assertThrows(NullPointerException.class, () -> new ProvisionProductInput(
            null, "pa-1", "my-product", Map.of()));
    }

    @Test
    void inputRequiresProvisioningArtifactId() {
        assertThrows(NullPointerException.class, () -> new ProvisionProductInput(
            "prod-1", null, "my-product", Map.of()));
    }

    @Test
    void inputRequiresProvisionedProductName() {
        assertThrows(NullPointerException.class, () -> new ProvisionProductInput(
            "prod-1", "pa-1", null, Map.of()));
    }

    @Test
    void inputDefaultsNullParametersToEmptyMap() {
        ProvisionProductInput input = new ProvisionProductInput(
            "prod-1", "pa-1", "my-product", null);
        assertTrue(input.parameters().isEmpty());
    }

    @Test
    void provisionPropagatesConnectionFailuresRatherThanHanging() {
        try (ServiceCatalogDeployer deployer = unreachableDeployer()) {
            ProvisionProductInput input = new ProvisionProductInput(
                "prod-1", "pa-1", "my-product", Map.of("Key", "Value"));
            assertThrows(RuntimeException.class, () -> deployer.provision(input, "token-1"));
        }
    }

    @Test
    void terminatePropagatesConnectionFailuresRatherThanHanging() {
        try (ServiceCatalogDeployer deployer = unreachableDeployer()) {
            assertThrows(RuntimeException.class, () -> deployer.terminate("pp-1", "token-2"));
        }
    }

    @Test
    void updatePropagatesConnectionFailuresRatherThanHanging() {
        try (ServiceCatalogDeployer deployer = unreachableDeployer()) {
            UpdateProvisionedProductInput input = new UpdateProvisionedProductInput(
                "pp-1", null, Map.of("Key", "NewValue"));
            assertThrows(RuntimeException.class, () -> deployer.update(input, "token-3"));
        }
    }

    @Test
    void updateInputRequiresProvisionedProductId() {
        assertThrows(NullPointerException.class, () -> new UpdateProvisionedProductInput(
            null, "pa-2", Map.of()));
    }

    @Test
    void updateInputAllowsNullProvisioningArtifactIdAndDefaultsParameters() {
        UpdateProvisionedProductInput input = new UpdateProvisionedProductInput("pp-1", null, null);
        assertEquals("pp-1", input.provisionedProductId());
        assertTrue(input.parameters().isEmpty());
    }

    @Test
    void resultRecordsAreImmutable() {
        ProvisionProductResult result = new ProvisionProductResult(
            "rec-1", "pp-1", Map.of("Url", "https://example.com"), null);
        assertEquals(1, result.outputs().size());
        assertTrue(result.statusMessages().isEmpty());
    }
}
