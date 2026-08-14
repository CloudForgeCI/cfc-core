package com.cloudforgeci.api.deploy.catalog;

import java.util.Map;
import java.util.Objects;

/**
 * Inputs for {@link ServiceCatalogDeployer#update}.
 *
 * <p>{@code provisioningArtifactId} is nullable — omit it to update parameters on the currently-
 * provisioned version rather than moving to a different one. {@code parameters} only supports
 * explicit new values (mapped to {@code UpdateProvisioningParameter.value}); there is no way to
 * pass {@code usePreviousValue} through this type — see {@link ServiceCatalogDeployer}'s class
 * javadoc for why, and for what Service Catalog does with a parameter this update omits
 * entirely (not verified against a real account).</p>
 */
public record UpdateProvisionedProductInput(
        String provisionedProductId,
        String provisioningArtifactId,
        Map<String, String> parameters) {

    public UpdateProvisionedProductInput {
        Objects.requireNonNull(provisionedProductId, "provisionedProductId");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
