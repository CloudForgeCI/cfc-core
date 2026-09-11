package com.cloudforgeci.api.deploy.catalog;

import com.cloudforge.core.local.DeploymentTarget;
import com.cloudforge.core.manager.ManagerEndpointSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.servicecatalog.ServiceCatalogClient;
import software.amazon.awssdk.services.servicecatalog.model.AssociatePrincipalWithPortfolioRequest;
import software.amazon.awssdk.services.servicecatalog.model.AssociateProductWithPortfolioRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateConstraintRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreatePortfolioRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateProductRequest;
import software.amazon.awssdk.services.servicecatalog.model.CreateProvisioningArtifactRequest;
import software.amazon.awssdk.services.servicecatalog.model.DescribeProvisioningArtifactRequest;
import software.amazon.awssdk.services.servicecatalog.model.ListPortfoliosRequest;
import software.amazon.awssdk.services.servicecatalog.model.PortfolioDetail;
import software.amazon.awssdk.services.servicecatalog.model.ProductType;
import software.amazon.awssdk.services.servicecatalog.model.ProductViewDetail;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactProperties;
import software.amazon.awssdk.services.servicecatalog.model.PrincipalType;
import software.amazon.awssdk.services.servicecatalog.model.ProvisioningArtifactType;
import software.amazon.awssdk.services.servicecatalog.model.SearchProductsAsAdminRequest;
import software.amazon.awssdk.services.servicecatalog.model.Status;
import software.amazon.awssdk.services.servicecatalog.model.Tag;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 * <p><b>Not exercised against real AWS or a real Service Catalog backend.</b> LocalStack's own
 * {@code _localstack/health} service list has no {@code servicecatalog} entry, unlike
 * {@code AwsDirectDeployer}/{@code ServiceCatalogDeployer}, which run against a live LocalStack
 * CloudFormation endpoint — so this class has never called any of these APIs against a running
 * backend, only been written to match the documented Service Catalog API shape. Treat a real
 * publish as the first true validation.</p>
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
 *
 * <p><b>The template bucket stays private; the URL is presigned, not a bucket-policy grant.</b>
 * A bucket policy granting {@code cloudformation.amazonaws.com}/{@code servicecatalog.amazonaws.com}
 * read access looks like the standard approach and works for a direct, non-Service-Catalog {@code
 * CreateStack} call — but for {@code ProvisionProduct} specifically, the underlying template fetch
 * reliably fails with a generic "S3 error: Access Denied" whenever the effective caller is an
 * {@code sts:AssumeRole} session (any IAM role, launch-constraint roles included), regardless of
 * how permissive the bucket policy or the role's own IAM policy are — confirmed by testing a
 * fresh, purpose-built launch-constraint role directly. A plain IAM user, or the same user's
 * {@code sts:GetSessionToken} credentials, both work fine. A presigned URL sidesteps the question
 * entirely: nothing re-authenticates as any live identity to follow it, since the signature is
 * validated as a self-contained bearer credential computed once at sign time — which is why this
 * class presigns instead of granting bucket access to any principal.</p>
 *
 * <p>The tradeoff a presigned URL brings back: SigV4 caps any single signature at 7 days, so a
 * URL baked into a provisioning artifact once, at publish time, can't stay valid indefinitely.
 * Deliberately not addressed here with a scheduled refresh — a background job only fires while
 * something keeps it running, silently stops covering anything the moment this process itself
 * isn't. Freshness is instead the provisioning caller's own responsibility, at the moment
 * provisioning actually happens: see {@code CatalogPublishService#republishForProvision}, called
 * synchronously by {@code CatalogDeployService#runProvision} immediately before every {@code
 * ProvisionProduct} call, so the artifact actually used is never more than moments old.</p>
 */
public final class ServiceCatalogProductPublisher implements AutoCloseable {

    /** Comfortably under SigV4's own 7-day (604800s) cap on any presigned URL, permanent or
     *  temporary credentials alike -- see this class's own javadoc for why a presigned URL is
     *  used at all instead of a bucket-policy grant. */
    private static final Duration TEMPLATE_URL_DURATION = Duration.ofDays(6);

    /** How long {@link #awaitArtifactAvailable} waits for Service Catalog's own template
     *  fetch-and-validate step before giving up -- generous relative to how fast this has been
     *  observed to actually resolve, since the caller is a synchronous, journal-tracked request
     *  (see {@code CatalogDeployService#runProvision}), not a background job with its own retry
     *  loop above this one. */
    private static final Duration ARTIFACT_AVAILABLE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ARTIFACT_AVAILABLE_POLL_INTERVAL = Duration.ofSeconds(2);

    private final ServiceCatalogClient serviceCatalog;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final StsClient sts;
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
        this(serviceCatalogClient(region, target), s3Client(region, target),
            s3Presigner(region, target), stsClient(region, target), templateBucketName(region));
    }

    /** Visible for tests — inject pre-built clients. */
    ServiceCatalogProductPublisher(
            ServiceCatalogClient serviceCatalog, S3Client s3, S3Presigner presigner, StsClient sts,
            String templateBucket) {
        this.serviceCatalog = serviceCatalog;
        this.s3 = s3;
        this.presigner = presigner;
        this.sts = sts;
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

    /** Mirrors {@link #s3Client}'s own real-AWS-vs-local-emulator split. */
    private static S3Presigner s3Presigner(String region, DeploymentTarget target) {
        Region resolvedRegion = Region.of(region == null || region.isBlank() ? "us-east-1" : region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return S3Presigner.builder()
                .region(resolvedRegion)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return S3Presigner.builder()
            .region(resolvedRegion)
            .endpointOverride(URI.create(localEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    /** Mirrors {@link #s3Client}'s own real-AWS-vs-local-emulator split -- {@link
     *  #ensurePrincipalLaunchPath} needs the calling principal's own ARN. */
    private static StsClient stsClient(String region, DeploymentTarget target) {
        Region resolvedRegion = Region.of(region == null || region.isBlank() ? "us-east-1" : region);
        String localEndpoint = ManagerEndpointSupport.resolveLocalEmulatorEndpoint(target);
        if (localEndpoint == null) {
            return StsClient.builder()
                .region(resolvedRegion)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        }
        return StsClient.builder()
            .region(resolvedRegion)
            .endpointOverride(URI.create(localEndpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    /** Public so {@code ManagerOperatorIamSupport} can scope Manager's own task-role IAM grant to
     *  this exact bucket prefix, same convention {@code AwsDirectDeployer.TEMPLATE_BUCKET_PREFIX}
     *  already uses for {@code deploy:create}'s own template bucket -- a genuinely different
     *  bucket, not to be conflated with that one. */
    public static final String CATALOG_TEMPLATE_BUCKET_PREFIX = "cfc-catalog-templates-";

    private static String templateBucketName(String region) {
        return CATALOG_TEMPLATE_BUCKET_PREFIX + (region == null || region.isBlank() ? "us-east-1" : region);
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
        ensurePrincipalLaunchPath(portfolioId);
        String templateUrl = uploadTemplate(productName, stripCdkBootstrapArtifacts(templateBody));

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

        awaitArtifactAvailable(productId, provisioningArtifactId);

        return new PublishResult(
            portfolioId, productId, provisioningArtifactId, templateUrl, launchConstraintCreated);
    }

    /** A freshly created provisioning artifact starts out {@code CREATING} -- Service Catalog
     *  fetches and validates the template (the same S3 URL {@link #uploadTemplate} just produced)
     *  asynchronously before it becomes {@code AVAILABLE}. {@link CatalogDeployService#runProvision}
     *  in cloudforge-manager calls straight into {@code ProvisionProduct} moments after {@link
     *  #publishProduct} returns -- confirmed live that without waiting here, that immediate
     *  provision fails outright with "Package is in state CREATING, but must be in state
     *  AVAILABLE" rather than transiently retrying. Polling here, once, in the one place every
     *  caller already goes through, means neither {@code republishForProvision} nor any other
     *  caller needs its own copy of this wait. */
    private void awaitArtifactAvailable(String productId, String provisioningArtifactId) throws IOException {
        Instant deadline = Instant.now().plus(ARTIFACT_AVAILABLE_TIMEOUT);
        while (true) {
            var response = serviceCatalog.describeProvisioningArtifact(
                DescribeProvisioningArtifactRequest.builder()
                    .productId(productId)
                    .provisioningArtifactId(provisioningArtifactId)
                    .build());
            Status status = response.status();
            if (status == Status.AVAILABLE) {
                return;
            }
            if (status == Status.FAILED) {
                throw new IOException("Provisioning artifact " + provisioningArtifactId
                    + " failed validation (Service Catalog status FAILED) -- check the synthesized "
                    + "template for the actual cause.");
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IOException("Provisioning artifact " + provisioningArtifactId
                    + " still " + status + " after " + ARTIFACT_AVAILABLE_TIMEOUT.getSeconds()
                    + "s -- Service Catalog never finished validating it.");
            }
            try {
                Thread.sleep(ARTIFACT_AVAILABLE_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for provisioning artifact "
                    + provisioningArtifactId + " to become AVAILABLE", e);
            }
        }
    }

    /**
     * Grants the calling principal (Manager's own task role, publishing and later provisioning
     * from the same identity for self-test purposes) a launch path onto {@code portfolioId}.
     * Publishing a product only requires {@code AssociateProductWithPortfolio} (already called
     * below); provisioning it separately requires a principal to be associated with the
     * portfolio, or {@code ProvisionProduct} fails with Service Catalog's generic "No launch
     * paths found for resource". Idempotent on AWS's own side (re-associating the same
     * principal+type is a no-op, not an error), so this runs on every publish rather than
     * needing its own existence check first.
     */
    private void ensurePrincipalLaunchPath(String portfolioId) {
        String callerArn = sts.getCallerIdentity(GetCallerIdentityRequest.builder().build()).arn();
        serviceCatalog.associatePrincipalWithPortfolio(AssociatePrincipalWithPortfolioRequest.builder()
            .portfolioId(portfolioId)
            .principalARN(roleArnFromCallerIdentity(callerArn))
            .principalType(PrincipalType.IAM)
            .build());
    }

    /**
     * A Fargate task's own {@code GetCallerIdentity} returns an STS assumed-role session ARN
     * ({@code arn:aws:sts::ACCOUNT:assumed-role/ROLE_NAME/SESSION_NAME}), not the underlying IAM
     * role -- Service Catalog's own launch-path authorization checks against the ROLE, not one
     * particular session of it, so the session ARN needs converting to
     * {@code arn:aws:iam::ACCOUNT:role/ROLE_NAME} first. Left unchanged (not an assumed-role ARN
     * at all) for a plain IAM user/role caller, e.g. local development under a real IAM user.
     */
    static String roleArnFromCallerIdentity(String callerArn) {
        // arn:aws:sts::ACCOUNT_ID:assumed-role/ROLE_NAME/SESSION_NAME
        //   0    1   2  3   4          5
        String[] parts = callerArn.split(":", 6);
        if (parts.length != 6 || !"sts".equals(parts[2]) || !parts[5].startsWith("assumed-role/")) {
            return callerArn;
        }
        String partition = parts[1];
        String accountId = parts[4];
        String afterAssumedRole = parts[5].substring("assumed-role/".length());
        String roleName = afterAssumedRole.substring(0, afterAssumedRole.indexOf('/'));
        return "arn:" + partition + ":iam::" + accountId + ":role/" + roleName;
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
        return java.util.Optional.ofNullable(listAllProducts().get(productName));
    }

    /** Every product currently registered under Service Catalog's admin view, keyed by product
     *  name — paginated (the single-name lookup this replaced wasn't, a latent gap for any
     *  account with more products than fit one page). Exists so a caller checking many
     *  applications' status in one pass (see {@link #lookupProducts}) fetches this once instead
     *  of once per application: a per-application {@code SearchProductsAsAdmin} call is enough
     *  traffic to trip AWS's own throttling. Still a live call, not a cache: AWS stays the only
     *  source of truth, this just avoids asking it the same question dozens of times in a row
     *  for an answer that can't have changed in between.
     */
    public Map<String, ProductViewDetail> listAllProducts() {
        Map<String, ProductViewDetail> byName = new LinkedHashMap<>();
        String pageToken = null;
        do {
            var response = serviceCatalog.searchProductsAsAdmin(SearchProductsAsAdminRequest.builder()
                .pageToken(pageToken)
                .build());
            for (ProductViewDetail detail : response.productViewDetails()) {
                byName.put(detail.productViewSummary().name(), detail);
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        return byName;
    }

    /**
     * Bulk variant of {@link #lookupProduct} — backs {@link
     * com.cloudforgeci.manager.deploy.CatalogPublishService#listAll} (checking every registered
     * application's publish status in one page load). Resolves the portfolio and the full
     * product list exactly once, then matches every requested name against them in memory,
     * instead of {@code productNames.size()} separate full round trips through {@link
     * #lookupProduct} for data that's identical across every one of them.
     *
     * @return a name → result map containing only the names that are actually published —
     *     absence from the map is "not published or lookup failed", the same fallback shape
     *     {@link #lookupProduct}'s own javadoc already documents
     */
    public Map<String, LookupResult> lookupProducts(String portfolioName, java.util.Collection<String> productNames) {
        String portfolioId = ensurePortfolio(portfolioName);
        Map<String, ProductViewDetail> allProducts = listAllProducts();
        Map<String, LookupResult> results = new LinkedHashMap<>();
        for (String productName : productNames) {
            ProductViewDetail product = allProducts.get(productName);
            if (product == null) {
                continue;
            }
            String productId = product.productViewSummary().productId();
            String artifactId = latestProvisioningArtifactId(productId);
            if (artifactId == null) {
                continue;
            }
            results.put(productName, new LookupResult(portfolioId, productId, artifactId));
        }
        return results;
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

    /**
     * Strips the {@code BootstrapVersion} parameter and its {@code CheckBootstrapVersion} rule
     * that every CDK-synthesized template carries by default. Service Catalog's {@code
     * CreateProduct}/{@code CreateProvisioningArtifact} reject any template containing an
     * {@code AWS::SSM::Parameter::Value<...>}-typed parameter outright, surfacing it as a
     * generic "Invalid templateBody" with no indication of the real cause. The parameter only
     * exists so {@code cdk deploy}'s own CLI can assert the target account's CDK bootstrap stack
     * is a compatible version before deploying -- meaningless for a Service Catalog product,
     * which end users provision through Service Catalog's own launch mechanism, never
     * {@code cdk deploy}.
     */
    private static String stripCdkBootstrapArtifacts(String templateBody) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(templateBody);
        var parameters = root.get("Parameters");
        if (parameters instanceof com.fasterxml.jackson.databind.node.ObjectNode paramsNode) {
            paramsNode.remove("BootstrapVersion");
            if (paramsNode.isEmpty()) {
                root.remove("Parameters");
            }
        }
        var rules = root.get("Rules");
        if (rules instanceof com.fasterxml.jackson.databind.node.ObjectNode rulesNode) {
            rulesNode.remove("CheckBootstrapVersion");
            if (rulesNode.isEmpty()) {
                root.remove("Rules");
            }
        }
        return mapper.writeValueAsString(root);
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
        // Presigned, deliberately -- see this class's own javadoc for why a bucket-policy grant
        // to the CloudFormation/Service Catalog service principals doesn't reliably work for
        // ProvisionProduct's own template fetch, and why freshness is the provisioning caller's
        // responsibility (republish immediately before provisioning) rather than a long-lived
        // grant here.
        var presigned = presigner.presignGetObject(builder -> builder
            .signatureDuration(TEMPLATE_URL_DURATION)
            .getObjectRequest(GetObjectRequest.builder().bucket(templateBucket).key(key).build()));
        return presigned.url().toString();
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
        presigner.close();
        sts.close();
    }
}
