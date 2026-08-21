package com.cloudforgeci.synthservice;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforgeci.api.deploy.CloudForgeSynthesizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code POST /v1/synthesize} — the sidecar's one real job. Mirrors {@code
 * ManagerSynthesisService.synthesize(DeploymentConfig)}'s exact contract (same request shape,
 * same {@link CloudForgeSynthesizer.Result} fields, same documented exception-to-status mapping)
 * so the main app's HTTP-client rewrite of that class is a pure transport swap, not a behavior
 * change.
 *
 * <p>Response is the full synthesized cloud-assembly directory as a zip (not just the template —
 * {@code DirectDeployService} reads both {@code templateFile()} and {@code assemblyDirectory()},
 * and MiniStack/LocalStack's adaptation pipelines may need asset manifests/{@code tree.json}
 * beyond the bare template), with an {@code X-Stack-Name} header carrying {@code
 * config.stackName} so the caller doesn't have to open the archive just to learn it.
 *
 * <p>Plain {@code java.util.zip} rather than a tar library: this only ever needs to round-trip
 * between two processes this codebase controls end-to-end, so there's no interop reason to prefer
 * tar, and it avoids adding a new dependency (commons-compress or similar) to a sidecar whose
 * whole reason to exist is keeping this boundary minimal.
 *
 * <p>Public (not package-private) specifically so {@code cloudforge-manager}'s own test suite can
 * stand up a real instance of this handler as a test fixture — proving {@code
 * ManagerSynthesisService}'s HTTP client actually speaks the same protocol this class implements,
 * not a protocol the two modules' tests each independently assumed. A test-scope-only dependency
 * on this module doesn't affect what cloudforge-manager's shipped native image needs to resolve.
 */
public final class SynthHttpHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(SynthHttpHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respondText(exchange, 405, "Method Not Allowed");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        DeploymentConfig config;
        try {
            config = DeploymentConfig.fromJson(requestBody);
        } catch (JsonProcessingException e) {
            respondText(exchange, 400, "Invalid DeploymentConfig JSON: " + e.getMessage());
            return;
        }

        Path outputDirectory;
        try {
            outputDirectory = Files.createTempDirectory("cfc-synth-service-");
        } catch (IOException e) {
            respondText(exchange, 502, "Could not allocate a synth output directory: " + e.getMessage());
            return;
        }

        CloudForgeSynthesizer.Result result;
        try {
            // Same call, same lock discipline (CloudForgeSynthesizer.SYNTH_LOCK) as the in-process
            // caller this replaces — nothing about the actual synth changes here, only who's on
            // the other end of the method call.
            result = CloudForgeSynthesizer.synthesize(config, outputDirectory);
        } catch (IllegalArgumentException e) {
            respondText(exchange, 400, e.getMessage());
            cleanupQuietly(outputDirectory);
            return;
        } catch (IOException e) {
            LOG.warning("Synthesis failed for stackName=" + config.stackName + ": " + e.getMessage());
            respondText(exchange, 502, "CDK synthesis failed: " + e.getMessage());
            cleanupQuietly(outputDirectory);
            return;
        } catch (RuntimeException e) {
            // jsii/CDK failures don't always surface as IOException (see the jsii
            // Node.addValidation() failure mode documented in project history) — catch broadly
            // here so a real synth crash comes back as a clear 502 to the caller instead of the
            // JDK HTTP server's own generic 500 with no useful body.
            LOG.warning("Synthesis crashed for stackName=" + config.stackName + ": " + e);
            respondText(exchange, 502, "CDK synthesis crashed: " + e);
            cleanupQuietly(outputDirectory);
            return;
        }

        try {
            exchange.getResponseHeaders().add("X-Stack-Name", result.stackName());
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream responseBody = exchange.getResponseBody();
                 ZipOutputStream zip = new ZipOutputStream(responseBody)) {
                zipDirectory(result.assemblyDirectory(), zip);
            }
        } finally {
            cleanupQuietly(outputDirectory);
        }
    }

    private static void zipDirectory(Path directory, ZipOutputStream zip) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                String entryName = directory.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private static void respondText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static void cleanupQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort — a leftover temp dir on the sidecar's own ephemeral container
                    // storage isn't worth failing the request over.
                }
            });
        } catch (IOException ignored) {
            // Directory may already be gone (e.g. createTempDirectory itself failed upstream).
        }
    }
}
