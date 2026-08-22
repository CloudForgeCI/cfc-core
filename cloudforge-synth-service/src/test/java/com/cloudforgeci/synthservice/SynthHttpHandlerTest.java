package com.cloudforgeci.synthservice;

import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforge.core.enums.AuthMode;
import com.cloudforge.core.enums.RuntimeType;
import com.cloudforge.core.enums.SecurityProfile;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real HTTP round trip against {@link SynthHttpHandler} — same real-pipeline rigor as {@code
 * ManagerSynthesisServiceTest} in cloudforge-manager had for the in-process call this replaces
 * (see that test's own javadoc): a real {@link HttpServer} on a real socket, a real {@link
 * HttpClient} request, a real CDK synth underneath, asserting the response actually contains the
 * synthesized template rather than mocking any part of the pipeline.
 *
 * <p>This proves the HTTP contract works within a single JVM (server and client sharing a
 * process, still communicating over real TCP/HTTP). A true cross-process test (spawning the
 * packaged jar as its own process) belongs alongside the Docker packaging work, once there's a
 * real built artifact to spawn.
 */
class SynthHttpHandlerTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/synthesize", new SynthHttpHandler());
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void synthesizesARealTemplateOverHttp() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.stackName = "SynthServiceHttpTest";
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.securityProfile = SecurityProfile.DEV;
        config.authMode = AuthMode.NONE;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/synthesize"))
            .POST(HttpRequest.BodyPublishers.ofString(config.toJson(), StandardCharsets.UTF_8))
            .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode(), "expected a successful synth: " + new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("SynthServiceHttpTest", response.headers().firstValue("X-Stack-Name").orElse(null));

        boolean sawTemplateFile = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(response.body()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith(".template.json")) {
                    sawTemplateFile = true;
                    ByteArrayOutputStream contents = new ByteArrayOutputStream();
                    zip.transferTo(contents);
                    assertTrue(contents.size() > 0, "template entry should be non-empty");
                }
            }
        }
        assertTrue(sawTemplateFile, "zip response should contain a synthesized CloudFormation template");
    }

    @Test
    void missingStackNameReturns400WithAClearMessage() throws Exception {
        DeploymentConfig config = new DeploymentConfig();
        config.applicationId = "jenkins";
        config.runtime = RuntimeType.FARGATE;
        config.securityProfile = SecurityProfile.DEV;
        config.authMode = AuthMode.NONE;
        // stackName deliberately left null.

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/synthesize"))
            .POST(HttpRequest.BodyPublishers.ofString(config.toJson(), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("stackName"), "error body should explain what's missing: " + response.body());
    }

    @Test
    void malformedJsonReturns400InsteadOfCrashing() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/synthesize"))
            .POST(HttpRequest.BodyPublishers.ofString("{not valid json", StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }
}
