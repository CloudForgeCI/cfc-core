package com.cloudforgeci.synthservice;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Entry point for the synth sidecar — a thin HTTP wrapper around {@link
 * com.cloudforgeci.api.deploy.CloudForgeSynthesizer}, the one door into jsii/AWS-CDK synthesis in
 * the whole cloudforge-manager deploy pipeline (see {@link SynthHttpHandler}'s own javadoc for
 * why this exists). No Spring Boot here deliberately — one real endpoint plus a health check
 * doesn't need it, and this keeps the sidecar's own footprint (and attack surface, since it holds
 * no AWS credentials of its own) minimal alongside the Node.js/jsii runtime it already has to
 * carry.
 */
public final class SynthServiceMain {

    private static final Logger LOG = Logger.getLogger(SynthServiceMain.class.getName());

    private SynthServiceMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("SYNTH_SERVICE_PORT", "8090"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/v1/synthesize", new SynthHttpHandler());
        server.createContext("/v1/healthz", exchange -> {
            byte[] body = "OK".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        // Fixed-size, not cached/unbounded: CloudForgeSynthesizer.synthesize already serializes
        // every real synth call process-wide via its own static SYNTH_LOCK, so more than a
        // handful of concurrent HTTP handler threads here would just queue up waiting on that
        // lock anyway — this only needs enough threads to keep /v1/healthz responsive while a
        // synth call is in flight.
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        LOG.info("cloudforge-synth-service listening on :" + port);
    }
}
