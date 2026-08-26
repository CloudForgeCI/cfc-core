package com.cloudforgeci.api.deploy.catalog;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.manager.ManagerEndpointSupport;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.servicecatalog.ServiceCatalogClient;
import software.amazon.awssdk.services.servicecatalog.model.DescribeRecordRequest;
import software.amazon.awssdk.services.servicecatalog.model.DescribeRecordResponse;
import software.amazon.awssdk.services.servicecatalog.model.ProvisionProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.ProvisionProductResponse;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningParameter;
import software.amazon.awssdk.services.servicecatalog.model.RecordDetail;
import software.amazon.awssdk.services.servicecatalog.model.RecordError;
import software.amazon.awssdk.services.servicecatalog.model.RecordStatus;
import software.amazon.awssdk.services.servicecatalog.model.TerminateProvisionedProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.TerminateProvisionedProductResponse;
import software.amazon.awssdk.services.servicecatalog.model.UpdateProvisionedProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.UpdateProvisionedProductResponse;
import software.amazon.awssdk.services.servicecatalog.model.UpdateProvisioningParameter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provisions/terminates AWS Service Catalog products — the {@code deploy:catalog} path (backing
 * the {@code SC_PROVISION} capability). Deliberately simpler than {@code AwsDirectDeployer}: no
 * CDK synthesis, no template of any kind travels through this class — the product's publisher
 * already registered a provisioning artifact (a CloudFormation template) with Service Catalog,
 * so this only ever calls {@code ProvisionProduct}/{@code TerminateProvisionedProduct} with
 * parameters, then polls {@code DescribeRecord} until Service Catalog's own provisioning engine
 * (which itself just runs a CloudFormation stack update behind the scenes, on a launch-
 * constraint role that is <em>not</em> Manager's) reaches a terminal state — same "poll until
 * terminal" shape {@code AwsDirectDeployer}'s change-set wait already uses, just against a
 * different AWS API with no built-in SDK waiter to reuse.
 *
 * <p><b>Idempotency is native here, unlike the CFN path</b>: {@code ProvisionProductRequest
 * .provisionToken()}/{@code TerminateProvisionedProductRequest.terminateToken()} are real AWS API
 * idempotency tokens — passing the same token for a retried call returns the same record instead
 * of provisioning twice. Callers should still pass a real token (this class doesn't generate one
 * itself) so that guarantee actually applies.</p>
 *
 * <p><b>{@code update} only supports explicit new parameter values</b>, not Service Catalog's
 * {@code UpdateProvisioningParameter.usePreviousValue} — see {@link UpdateProvisionedProductInput}'s
 * javadoc for what that means for parameters an update call omits.</p>
 *
 * <p><b>Targets real AWS by default, but redirects to a local emulator when this Manager instance
 * is itself running inside one</b> — same {@code LOCALSTACK_ENDPOINT}/{@code AWS_ENDPOINT_URL}
 * detection {@code AwsDirectDeployer} uses, mirrored here for the same reason: this class
 * originally always used the default credential chain with no endpoint override, which would have
 * hit the exact same "calls real AWS from inside LocalStack" bug {@code AwsDirectDeployer} was
 * caught doing, the moment Service Catalog is ever exposed by a local emulator this deploys
 * against (it isn't yet on this repo's LocalStack setup — see the plan's 2026-08-11 note — so this
 * specific redirect is currently unexercised, but is not silently wrong the day that changes).</p>
 */
public final class ServiceCatalogDeployer implements AutoCloseable {

    private static final Duration OPERATION_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final ServiceCatalogClient client;

    /**
     * Defaults {@code target} to {@link DeploymentTarget#AWS} — this constructor predates a
     * target parameter existing at all, and every caller today is a real-AWS Service Catalog
     * operation; prefer the 2-arg overload for any new caller so the target is explicit rather
     * than assumed.
     */
    public ServiceCatalogDeployer(String region) {
        this(region, DeploymentTarget.AWS);
    }

    /**
     * Real AWS by default; redirects to a local emulator instead when {@code target} resolves to
     * one via {@link ManagerEndpointSupport#resolveLocalEmulatorEndpoint} — see class javadoc.
     * {@code target} must be the caller's own already-known, validated target (never re-derived
     * from env vars here — see that method's own javadoc for why).
     */
    public ServiceCatalogDeployer(String region, DeploymentTarget target) {
        this(client(region, target));
    }

    private static ServiceCatalogClient client(String region, DeploymentTarget target) {
        Region resolvedRegion = Region.of(region == null || region.isBlank() ? "us-east-1" : region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return ServiceCatalogClient.builder()
                .region(resolvedRegion)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return ServiceCatalogClient.builder()
            .region(resolvedRegion)
            .endpointOverride(URI.create(localEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    /** Visible for tests — inject a pre-built client (mock, or pointed at a test endpoint). */
    ServiceCatalogDeployer(ServiceCatalogClient client) {
        this.client = client;
    }

    /**
     * Calls {@code ProvisionProduct} and polls until the record reaches {@code SUCCEEDED} or
     * {@code FAILED}.
     *
     * @param idempotencyToken passed as {@code provisionToken} — see class javadoc
     * @throws IOException if the record reaches {@code FAILED}, or polling times out after 30
     *     minutes
     */
    public ProvisionProductResult provision(ProvisionProductInput input, String idempotencyToken)
            throws IOException {
        List<ProvisioningParameter> parameters = input.parameters().entrySet().stream()
            .map(entry -> ProvisioningParameter.builder().key(entry.getKey()).value(entry.getValue()).build())
            .toList();

        ProvisionProductResponse response = client.provisionProduct(ProvisionProductRequest.builder()
            .productId(input.productId())
            .provisioningArtifactId(input.provisioningArtifactId())
            .provisionedProductName(input.provisionedProductName())
            .provisioningParameters(parameters)
            .provisionToken(idempotencyToken)
            .build());

        return waitForTerminal(response.recordDetail().recordId());
    }

    /**
     * Calls {@code TerminateProvisionedProduct} and polls until the record reaches {@code
     * SUCCEEDED} or {@code FAILED}.
     *
     * @param idempotencyToken passed as {@code terminateToken} — see class javadoc
     */
    public ProvisionProductResult terminate(String provisionedProductId, String idempotencyToken)
            throws IOException {
        TerminateProvisionedProductResponse response = client.terminateProvisionedProduct(
            TerminateProvisionedProductRequest.builder()
                .provisionedProductId(provisionedProductId)
                .terminateToken(idempotencyToken)
                .build());

        return waitForTerminal(response.recordDetail().recordId());
    }

    /**
     * Calls {@code UpdateProvisionedProduct} and polls until the record reaches {@code
     * SUCCEEDED} or {@code FAILED}. Only explicit new parameter values are supported — see
     * {@link UpdateProvisionedProductInput}'s javadoc.
     *
     * @param idempotencyToken passed as {@code updateToken} — see class javadoc
     */
    public ProvisionProductResult update(UpdateProvisionedProductInput input, String idempotencyToken)
            throws IOException {
        List<UpdateProvisioningParameter> parameters = input.parameters().entrySet().stream()
            .map(entry -> UpdateProvisioningParameter.builder()
                .key(entry.getKey())
                .value(entry.getValue())
                .build())
            .toList();

        UpdateProvisionedProductRequest.Builder builder = UpdateProvisionedProductRequest.builder()
            .provisionedProductId(input.provisionedProductId())
            .provisioningParameters(parameters)
            .updateToken(idempotencyToken);
        if (input.provisioningArtifactId() != null && !input.provisioningArtifactId().isBlank()) {
            builder.provisioningArtifactId(input.provisioningArtifactId());
        }

        UpdateProvisionedProductResponse response = client.updateProvisionedProduct(builder.build());
        return waitForTerminal(response.recordDetail().recordId());
    }

    private ProvisionProductResult waitForTerminal(String recordId) throws IOException {
        Instant deadline = Instant.now().plus(OPERATION_TIMEOUT);
        DescribeRecordRequest request = DescribeRecordRequest.builder().id(recordId).build();
        while (Instant.now().isBefore(deadline)) {
            DescribeRecordResponse response = client.describeRecord(request);
            RecordStatus status = response.recordDetail().status();
            if (status == RecordStatus.SUCCEEDED) {
                return toResult(response);
            }
            if (status == RecordStatus.FAILED) {
                throw new IOException("Service Catalog record " + recordId + " failed: "
                    + describeErrors(response.recordDetail()));
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Service Catalog record " + recordId, e);
            }
        }
        throw new IOException("Timed out waiting for Service Catalog record " + recordId);
    }

    private static ProvisionProductResult toResult(DescribeRecordResponse response) {
        RecordDetail detail = response.recordDetail();
        Map<String, String> outputs = new LinkedHashMap<>();
        response.recordOutputs().forEach(output -> outputs.put(output.outputKey(), output.outputValue()));
        List<String> messages = List.of(); // no error messages on a SUCCEEDED record
        return new ProvisionProductResult(detail.recordId(), detail.provisionedProductId(), outputs, messages);
    }

    private static String describeErrors(RecordDetail detail) {
        List<RecordError> errors = detail.recordErrors();
        if (errors == null || errors.isEmpty()) {
            return "no error detail returned";
        }
        return errors.stream()
            .map(error -> error.code() + ": " + error.description())
            .reduce((a, b) -> a + "; " + b)
            .orElse("no error detail returned");
    }

    @Override
    public void close() {
        client.close();
    }
}
