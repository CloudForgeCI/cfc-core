package com.cloudforgeci.api.deploy.catalog;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.manager.ManagerEndpointSupport;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.servicecatalog.ServiceCatalogClient;
import software.amazon.awssdk.services.servicecatalog.model.AssociateProductWithPortfolioRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateConstraintRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreatePortfolioRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateProvisioningArtifactRequest;
import software.amazon.awssdk.services.servicecatalog.model.ListPortfoliosRequest;
import software.amazon.awssdk.services.servicecatalog.model.PortfolioDetail;
import software.amazon.awssdk.services.servicecatalog.model.ProductType;
import software.amazon.awssdk.services.servicecatalog.model.ProductViewDetail;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactProperties;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactType;
import software.amazon.awssdk.services.servicecatalog.model.SearchProductsAsAdminRequest;
import software.amazon.awssdk.services.servicecatalog.model.Tag;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a CloudForge application's synthesized CloudFormation template as an AWS Service
 * Catalog product — the missing counterpart to {@code ServiceCatalogDeployer}, which only ever
 * <em>provisions</em> a product someone else already published. Nothing in this codebase had a
 * "someone else" until this class: CloudForge's 37 {@code ApplicationSpec}s were deployable via
 * {@code deploy:create} but never packageable for {@code deploy:catalog}'s more constrained lane.
 *
 * <p><b>Not exercised against real AWS or a real Service Catalog backend — there is no way to in
 * this environment.</b> Unlike {@code AwsDirectDeployer}/{@code ServiceCatalogDeployer}, which
 * eventually got proven against a live LocalStack instance this same session, LocalStack's own
 * {@code _localstack/health} service list has no {@code servicecatalog} entry at all here (see
 * the plan's notes on this) — so this class has never actually called any of these APIs against a
 * running backend, only been written to match the documented Service Catalog API shape. Treat a
 * real publish as the first true validation, the same caveat {@code AwsDirectDeployer}'s own
 * class javadoc carried before its real bugs surfaced.</p>
 *
 * <p><b>Launch constraints are the caller's responsibility, not auto-created here</b>: Service
 * Catalog provisions under its own IAM role (a "launch constraint"), separate from whichever
 * principal calls {@code ProvisionProduct}. Silently minting a new, broadly-scoped IAM role as a
 * side effect of a "publish" click felt like exactly the kind of infrastructure decision that
 * deserves an explicit human choice, not an auto-generated default — so {@link #publishProduct}
 * accepts an optional {@code launchConstraintRoleArn} and only wires the constraint when one is
 * given. A product published without one is real and browsable but not launchable by anyone
 * lacking their own sufficient permissions.</p>
 *
 * <p>Every provisioned product this eventually leads to is tagged with the same {@code
 * cloudforge:managed}/{@code cloudforge:application}/{@code cloudforge:runtime} convention {@code
 * AwsDirectDeployer} already applies (passed as {@code Tags} on {@code CreateProduct}, which
 * Service Catalog propagates onto the CloudFormation stack it creates at provision time) — so a
 * catalog-provisioned stack is visible in Manager's Instances list the same way a deploy:create
 * one now is, not a second invisible-by-default class of stack.</p>
 */
public final class ServiceCatalogProductPublisher implements AutoCloseable {

    private final ServiceCatalogClient serviceCatalog;
    private final S3Client s3;
    private final String templateBucket;

    /**
     * Defaults {@code target} to {@link DeploymentTarget#AWS} — no current caller exists (see
     * class javadoc's own history), kept as a safe default for whenever one is wired up; prefer
     * the 2-arg overload for any new caller so the target is explicit rather than assumed.
     */
    public ServiceCatalogProductPublisher(String region) {
        this(region, DeploymentTarget.AWS);
    }

    /**
     * Real AWS by default; redirects to a local emulator instead when {@code target} resolves to
     * one via {@link ManagerEndpointSupport#resolveLocalEmulatorEndpoint} — see class javadoc.
     * {@code target} must be the caller's own already-known, validated target (never re-derived
     * from env vars here — see that method's own javadoc for why).
     */
    public ServiceCatalogProductPublisher(String region, DeploymentTarget target) {
        this(serviceCatalogClient(region, target), s3Client(region, target), templateBucketName(region));
    }

    /** Visible for tests — inject pre-built clients. */
    ServiceCatalogProductPublisher(ServiceCatalogClient serviceCatalog, S3Client s3, String templateBucket) {
        this.serviceCatalog = serviceCatalog;
        this.s3 = s3;
        this.templateBucket = templateBucket;
    }

    private static ServiceCatalogClient serviceCatalogClient(String region, DeploymentTarget target) {
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

    private static S3Client s3Client(String region, DeploymentTarget target) {
        Region resolvedRegion = Region.of(region == null || region.isBlank() ? "us-east-1" : region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return S3Client.builder()
                .region(resolvedRegion)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return S3Client.builder()
            .region(resolvedRegion)
            .endpointOverride(URI.create(localEndpoint))
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    private static String templateBucketName(String region) {
        return "cfc-catalog-templates-" + (region == null || region.isBlank() ? "us-east-1" : region);
    }

    public record PublishResult(
            String portfolioId,
            String productId,
            String provisioningArtifactId,
            String templateUrl,
            boolean launchConstraintCreated) {
    }

    /**
     * Ensures a portfolio named {@code portfolioName} exists (searches first — {@code
     * CreatePortfolio} is not idempotent on its own), and publishes {@code templateBody} as a new
     * product (or a new provisioning artifact on an existing product of the same name — Service
     * Catalog products are versioned, not replaced).
     *
     * @param applicationId used only for the {@code cloudforge:application} tag on the resulting
     *     product/stack — has no other Service Catalog meaning, same role it plays elsewhere
     * @param runtimeTag the {@code cloudforge:runtime} tag value (e.g. {@code "fargate"})
     */
    public PublishResult publishProduct(
            String portfolioName,
            String productName,
            String applicationId,
            String runtimeTag,
            String templateBody,
            String launchConstraintRoleArn) throws IOException {
        String portfolioId = ensurePortfolio(portfolioName);
        String templateUrl = uploadTemplate(productName, templateBody);

        List<Tag> tags = List.of(
            Tag.builder().key("cloudforge:managed").value("true").build(),
            Tag.builder().key("cloudforge:application").value(
                applicationId == null || applicationId.isBlank() ? "unknown" : applicationId).build(),
            Tag.builder().key("cloudforge:runtime").value(
                runtimeTag == null || runtimeTag.isBlank() ? "unknown" : runtimeTag).build());

        var existingProduct = findProductByName(productName);
        String productId;
        String provisioningArtifactId;
        if (existingProduct.isPresent()) {
            productId = existingProduct.get().productViewSummary().productId();
            var artifact = serviceCatalog.createProvisioningArtifact(
                CreateProvisioningArtifactRequest.builder()
                    .productId(productId)
                    .parameters(ProvisioningArtifactProperties.builder()
                        .name(productName + "-" + System.currentTimeMillis())
                        .type(ProvisioningArtifactType.CLOUD_FORMATION_TEMPLATE)
                        .info(Map.of("LoadTemplateFromURL", templateUrl))
                        .build())
                    .build());
            provisioningArtifactId = artifact.provisioningArtifactDetail().id();
        } else {
            var created = serviceCatalog.createProduct(CreateProductRequest.builder()
                .name(productName)
                .owner("CloudForge")
                .productType(ProductType.CLOUD_FORMATION_TEMPLATE)
                .provisioningArtifactParameters(ProvisioningArtifactProperties.builder()
                    .name(productName + "-1")
                    .type(ProvisioningArtifactType.CLOUD_FORMATION_TEMPLATE)
                    .info(Map.of("LoadTemplateFromURL", templateUrl))
                    .build())
                .tags(tags)
                .build());
            productId = created.productViewDetail().productViewSummary().productId();
            provisioningArtifactId = created.provisioningArtifactDetail().id();
            serviceCatalog.associateProductWithPortfolio(AssociateProductWithPortfolioRequest.builder()
                .productId(productId)
                .portfolioId(portfolioId)
                .build());
        }

        boolean launchConstraintCreated = false;
        if (launchConstraintRoleArn != null && !launchConstraintRoleArn.isBlank()) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("RoleArn", launchConstraintRoleArn);
            serviceCatalog.createConstraint(CreateConstraintRequest.builder()
                .portfolioId(portfolioId)
                .productId(productId)
                .type("LAUNCH")
                .parameters(toJson(params))
                .build());
            launchConstraintCreated = true;
        }

        return new PublishResult(
            portfolioId, productId, provisioningArtifactId, templateUrl, launchConstraintCreated);
    }

    /** Idempotent: reuses an existing portfolio with the exact same display name if one exists. */
    String ensurePortfolio(String portfolioName) {
        String nextToken = null;
        do {
            var response = serviceCatalog.listPortfolios(ListPortfoliosRequest.builder()
                .pageToken(nextToken)
                .build());
            for (PortfolioDetail detail : response.portfolioDetails()) {
                if (portfolioName.equals(detail.displayName())) {
                    return detail.id();
                }
            }
            nextToken = response.nextPageToken();
        } while (nextToken != null);

        return serviceCatalog.createPortfolio(CreatePortfolioRequest.builder()
                .displayName(portfolioName)
                .providerName("CloudForge")
                .description("CloudForge-published application products")
                .build())
            .portfolioDetail()
            .id();
    }

    private java.util.Optional<ProductViewDetail> findProductByName(String productName) {
        var response = serviceCatalog.searchProductsAsAdmin(SearchProductsAsAdminRequest.builder()
            .build());
        return response.productViewDetails().stream()
            .filter(detail -> productName.equals(detail.productViewSummary().name()))
            .findFirst();
    }

    public record LookupResult(String portfolioId, String productId, String provisioningArtifactId) {
    }

    /**
     * Looks up an already-published product by the exact deterministic name {@link
     * com.cloudforgeci.api.compute.ApplicationLoader}'s callers construct from an application's
     * own {@code displayName}/{@code applicationId} ({@code "<displayName> (<applicationId>)"} —
     * see {@code CatalogPublishService.publishOne}) — reconstructing that same name is how the
     * caller asks "has this application been packaged yet?" without needing a separate index.
     * Returns empty when no product with that name exists — "not published yet," not an error.
     */
    public java.util.Optional<LookupResult> lookupProduct(String portfolioName, String productName) {
        var product = findProductByName(productName);
        if (product.isEmpty()) {
            return java.util.Optional.empty();
        }
        String productId = product.get().productViewSummary().productId();
        String portfolioId = ensurePortfolio(portfolioName);
        String artifactId = latestProvisioningArtifactId(productId);
        if (artifactId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new LookupResult(portfolioId, productId, artifactId));
    }

    /** Most recently created provisioning artifact — the version a fresh provision should use. */
    private String latestProvisioningArtifactId(String productId) {
        var response = serviceCatalog.describeProductAsAdmin(
            software.amazon.awssdk.services.servicecatalog.model.DescribeProductAsAdminRequest.builder()
                .id(productId)
                .build());
        return response.provisioningArtifactSummaries().stream()
            .max(java.util.Comparator.comparing(
                software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactSummary::createdTime))
            .map(software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactSummary::id)
            .orElse(null);
    }

    private String uploadTemplate(String productName, String templateBody) throws IOException {
        ensureTemplateBucket();
        String key = productName + "/" + System.currentTimeMillis() + ".template.json";
        // Service Catalog's LoadTemplateFromURL only accepts a template it can fetch over HTTP(S)
        // — no inline-body option the way CreateChangeSet has, so every publish uploads to S3
        // regardless of template size (unlike AwsDirectDeployer, which only uploads when the
        // inline limit is exceeded).
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(templateBucket)
                .key(key)
                .contentType("application/json")
                .build(),
            RequestBody.fromString(templateBody, StandardCharsets.UTF_8));
        return s3.utilities().getUrl(builder -> builder.bucket(templateBucket).key(key)).toString();
    }

    private void ensureTemplateBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(templateBucket).build());
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(templateBucket).build());
            } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
                // concurrent create
            }
        }
    }

    private static String toJson(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
        }
        return sb.append('}').toString();
    }

    @Override
    public void close() {
        serviceCatalog.close();
        s3.close();
    }
}
