package com.cloudforge.core.local;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEmulatorEdgeRuntimeTest {

    @Test
    void parseDockerPortPublishesMapsContainerPortsToHostnames() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "cfc-localstack\t0.0.0.0:4566->4566/tcp",
            "ecs-jenkins\t0.0.0.0:18080->8080/tcp",
            "manager\t127.0.0.1:1958->1958/tcp"
        ));

        assertEquals(18080, routes.get("jenkins.cloudforge.localhost").intValue());
        assertEquals(4566, routes.get(LocalEmulatorDefaults.HOST_LOCALSTACK).intValue());
        assertEquals(4566, routes.get(LocalEmulatorDefaults.HOST_EMULATOR).intValue());
        assertEquals(1958, routes.get(LocalEmulatorDefaults.HOST_MANAGER).intValue());
        assertFalse(routes.containsKey(LocalEmulatorDefaults.HOST_MINISTACK));
    }

    @Test
    void parseDockerPortPublishesMapsMiniStackGateway() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "cfc-ministack\t0.0.0.0:4566->4566/tcp"
        ));
        assertEquals(4566, routes.get(LocalEmulatorDefaults.HOST_MINISTACK).intValue());
        assertEquals(4566, routes.get(LocalEmulatorDefaults.HOST_EMULATOR).intValue());
        assertFalse(routes.containsKey(LocalEmulatorDefaults.HOST_LOCALSTACK));
    }

    @Test
    void parseDockerPortPublishesDoesNotMapStackPortInternal8080ToJenkins() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "cfc-ministack-stackport\t0.0.0.0:8888->8080/tcp"
        ));
        assertEquals(Map.of(LocalEmulatorDefaults.HOST_STACKPORT, 8888), routes);
        assertFalse(routes.containsKey("jenkins.cloudforge.localhost"));
    }

    @Test
    void managerRoutePrefersStandardPortRegardlessOfDockerListingOrder() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "manager-replacement\t0.0.0.0:7514->1958/tcp",
            "manager-current\t0.0.0.0:1958->1958/tcp"));

        assertEquals(1958, routes.get(LocalEmulatorDefaults.HOST_MANAGER).intValue());
    }

    @Test
    void parseDockerPortPublishesUsesApplicationNameForSharedPorts() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "ls-ecs-MetabaseStack-task\t0.0.0.0:3000->3000/tcp",
            "ls-ecs-MattermostStack-task\t0.0.0.0:8065->8065/tcp",
            "ls-ecs-DroneStack-task\t0.0.0.0:80->80/tcp"));

        assertEquals(3000, routes.get("metabase.cloudforge.localhost").intValue());
        assertEquals(8065, routes.get("mattermost.cloudforge.localhost").intValue());
        assertEquals(80, routes.get("drone.cloudforge.localhost").intValue());
        assertFalse(routes.containsKey("grafana.cloudforge.localhost"));
        assertFalse(routes.containsKey("gitlab.cloudforge.localhost"));
    }

    @Test
    void renderNginxConfIncludesProxyPassAndNginxStatus() {
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(
            Map.of("jenkins.cloudforge.localhost", 18080,
                LocalEmulatorDefaults.HOST_LOCALSTACK, 4566,
                LocalEmulatorDefaults.HOST_STACKPORT, 8888));
        assertTrue(conf.contains("server_name jenkins.cloudforge.localhost;"));
        assertTrue(conf.contains("proxy_pass http://host.docker.internal:18080;"));
        assertTrue(conf.contains("server_name " + LocalEmulatorDefaults.HOST_LOCALSTACK + ";"));
        assertTrue(conf.contains("listen 80 default_server;"));
        assertTrue(conf.contains("server_name " + LocalEmulatorDefaults.HOST_NGINX + ";"));
        assertTrue(conf.contains("server_name _;"));
        assertTrue(conf.contains("return 404 \"CloudForge application route not found"));
        assertTrue(conf.contains("return 200"));
        assertFalse(conf.contains("proxy_pass http://host.docker.internal:80;"));
        assertTrue(conf.contains("proxy_set_header Host localhost;"));
        assertTrue(conf.contains(LocalEmulatorDefaults.HOST_STACKPORT));
        assertTrue(conf.contains("default_type text/plain;"));
        assertFalse(conf.contains("return 302 " + LocalEmulatorDefaults.LOCALSTACK_HEALTH_PATH));
        assertTrue(conf.contains("location = /favicon.ico"));
    }

    @Test
    void renderNginxConfEmptyStillHasNginxStatus() {
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(Map.of());
        assertTrue(conf.contains("listen 80 default_server;"));
        assertTrue(conf.contains("server_name " + LocalEmulatorDefaults.HOST_NGINX));
        assertTrue(conf.contains("server_name _;"));
        assertTrue(conf.contains("No app vhosts yet") || conf.contains("No matching host ports"));
    }

    @Test
    void extractAlbPathPrefixFromEnv() {
        assertEquals("/_aws/elb/cfc-xnp5kj",
            DefaultEmulatorEdgeRuntime.extractAlbPathPrefix(List.of(
                "CFC_LOCALSTACK_ALB_PREFIX=/_aws/elb/cfc-xnp5kj/",
                "JENKINS_OPTS=--prefix=/other")));
        assertEquals("/_aws/elb/cfc-xnp5kj",
            DefaultEmulatorEdgeRuntime.extractAlbPathPrefix(List.of(
                "JENKINS_OPTS=--httpListenAddress=0.0.0.0 --prefix=/_aws/elb/cfc-xnp5kj")));
    }

    @Test
    void renderNginxConfRewritesLocalStackAlbPrefix() {
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(
            Map.of("jenkins.cloudforge.localhost", 48843),
            Map.of("jenkins.cloudforge.localhost", "/_aws/elb/cfc-xnp5kj"));
        assertTrue(conf.contains("rewrite ^/(.*)$ /_aws/elb/cfc-xnp5kj/$1 break;"));
        assertTrue(conf.contains("location ^~ /_aws/elb/cfc-xnp5kj/"));
        assertTrue(conf.contains("proxy_redirect /_aws/elb/cfc-xnp5kj/ /;"));
        assertTrue(conf.contains("location = / {\n        return 302 /login;"));
        assertTrue(conf.contains("sub_filter \"/_aws/elb/cfc-xnp5kj\" \"\";"));
        assertTrue(conf.contains("proxy_pass http://host.docker.internal:48843;"));
    }

    @Test
    void cloudForgeManagerFriendlyRouteDoesNotUseLocalStackAlbPrefix() {
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(
            Map.of(LocalEmulatorDefaults.HOST_MANAGER, 1958),
            Map.of(LocalEmulatorDefaults.HOST_MANAGER, "/_aws/elb/cfc-manager"));

        assertTrue(conf.contains("proxy_pass http://host.docker.internal:1958;"));
        assertFalse(conf.contains("return 302 /login;"));
        assertFalse(conf.contains("rewrite ^/(.*)$ /_aws/elb/cfc-manager/$1 break;"));
    }

    @Test
    void edgeHostnameOverrideGivesTwoInstancesOfTheSameAppDistinctVhosts() {
        // Two separately-deployed Jenkins stacks (subdomain=jenkins1 / jenkins2) both publish
        // container port 8080 — without the override they'd collide on one shared
        // jenkins.cloudforge.localhost route (see managerRoutePrefersStandardPortRegardlessOf...
        // for the analogous pre-existing collision-merge behavior this deliberately bypasses).
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(
            List.of(
                "ecs-jenkins1-task\t0.0.0.0:18080->8080/tcp",
                "ecs-jenkins2-task\t0.0.0.0:18081->8080/tcp"),
            Map.of(
                "ecs-jenkins1-task", "jenkins1.cloudforge.localhost",
                "ecs-jenkins2-task", "jenkins2.cloudforge.localhost"));

        assertEquals(18080, routes.get("jenkins1.cloudforge.localhost").intValue());
        assertEquals(18081, routes.get("jenkins2.cloudforge.localhost").intValue());
        assertFalse(routes.containsKey("jenkins.cloudforge.localhost"));
    }

    @Test
    void edgeHostnameOverrideMissingFallsBackToTheStaticPerApplicationHostname() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(
            List.of("ecs-jenkins-task\t0.0.0.0:18080->8080/tcp"),
            Map.of("some-other-container", "unused.cloudforge.localhost"));

        assertEquals(18080, routes.get("jenkins.cloudforge.localhost").intValue());
    }

    @Test
    void extractEdgeHostnameFromEnv() {
        assertEquals("jenkins1.cloudforge.localhost",
            DefaultEmulatorEdgeRuntime.extractEdgeHostname(List.of(
                "CFC_LOCALSTACK_ALB_PREFIX=/_aws/elb/cfc-xnp5kj/",
                "CFC_LOCALSTACK_EDGE_HOSTNAME=jenkins1.cloudforge.localhost")));
        assertEquals(null, DefaultEmulatorEdgeRuntime.extractEdgeHostname(
            List.of("CFC_LOCALSTACK_ALB_PREFIX=/_aws/elb/cfc-xnp5kj/")));
        assertEquals(null, DefaultEmulatorEdgeRuntime.extractEdgeHostname(null));
    }

    @Test
    void pathPrefixesForPublishedPortsMapsHostname() {
        Map<String, String> prefixes = DefaultEmulatorEdgeRuntime.pathPrefixesForPublishedPorts(
            Map.of("jenkins.cloudforge.localhost", 48843, "grafana.cloudforge.localhost", 3000),
            Map.of(48843, "/_aws/elb/cfc-xnp5kj"));
        assertEquals(Map.of("jenkins.cloudforge.localhost", "/_aws/elb/cfc-xnp5kj"), prefixes);
    }
}
