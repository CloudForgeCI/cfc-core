package com.cloudforgeci.api.deploy.catalog;

import java.util.List;
import java.util.Map;

/** Outcome of a Service Catalog record that reached {@code SUCCEEDED} — see {@link ServiceCatalogDeployer}. */
public record ProvisionProductResult(
        String recordId,
        String provisionedProductId,
        Map<String, String> outputs,
        List<String> statusMessages) {

    public ProvisionProductResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        statusMessages = statusMessages == null ? List.of() : List.copyOf(statusMessages);
    }
}
