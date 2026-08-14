package com.cloudforgeci.api.deploy.catalog;

import java.util.Map;
import java.util.Objects;

/**
 * Inputs for {@link ServiceCatalogDeployer#provision} — deliberately narrower than {@code
 * DeploymentConfig} (the {@code deploy:create} shape): a Service Catalog product's parameters
 * are whatever the product's own template declares, not CloudForge's full topology/security/
 * compliance combinatorial space. That narrowness is the point of {@code deploy:catalog} — the
 * product's publisher already constrained what's configurable.
 *
 * <p>No {@code region} field, deliberately — region selects which {@code ServiceCatalogClient}
 * (and therefore which {@link ServiceCatalogDeployer} instance) a call goes through, it isn't
 * part of the {@code ProvisionProduct} API request body itself, so it belongs on the caller's own
 * request DTO (e.g. Manager's {@code CatalogProvisionRequest}), not duplicated here. An earlier
 * version of this record carried a {@code region} field anyway; {@link ServiceCatalogDeployer
 * #provision} never read it, exactly the same dead-field shape as the {@code applicationId} bug
 * fixed on {@code CatalogUpdateRequest} — removed for the same reason.</p>
 */
public record ProvisionProductInput(
        String productId,
        String provisioningArtifactId,
        String provisionedProductName,
        Map<String, String> parameters) {

    public ProvisionProductInput {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(provisioningArtifactId, "provisioningArtifactId");
        Objects.requireNonNull(provisionedProductName, "provisionedProductName");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
