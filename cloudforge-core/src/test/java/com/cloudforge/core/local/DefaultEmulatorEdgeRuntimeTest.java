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

    /** Guards against a port-80 collision: 17 of the CMS/e-commerce catalog's applications all
     *  listen on container port 80 (see each's {@code applicationPort()}), so without per-app
     *  disambiguation, every one but drone/joomla/gitlab would fall through to the port-80
     *  default (gitlab) and route under the wrong hostname -- or, for a stack whose own name
     *  doesn't contain "gitlab" either, still resolve to {@code gitlab.cloudforge.localhost}
     *  rather than its own vhost, leaving its real hostname (e.g. {@code
     *  wordpress.cloudforge.localhost}) with no route at all. */
    @Test
    void parseDockerPortPublishesDisambiguatesEveryPort80CmsApplication() {
        Map<String, Integer> routes = DefaultEmulatorEdgeRuntime.parseDockerPortPublishes(List.of(
            "ls-ecs-WordPressStack-task\t0.0.0.0:23001->80/tcp",
            "ls-ecs-WooCommerceStack-task\t0.0.0.0:23002->80/tcp",
            "ls-ecs-DrupalStack-task\t0.0.0.0:23003->80/tcp",
            "ls-ecs-MagentoStack-task\t0.0.0.0:23004->80/tcp",
            "ls-ecs-PrestaShopStack-task\t0.0.0.0:23005->80/tcp",
            "ls-ecs-OpenCartStack-task\t0.0.0.0:23006->80/tcp",
            "ls-ecs-BagistoStack-task\t0.0.0.0:23007->80/tcp",
            "ls-ecs-SyliusStack-task\t0.0.0.0:23008->80/tcp",
            "ls-ecs-ConcreteCMSStack-task\t0.0.0.0:23009->80/tcp",
            "ls-ecs-OctoberCMSStack-task\t0.0.0.0:23010->80/tcp",
            "ls-ecs-TYPO3Stack-task\t0.0.0.0:23011->80/tcp",
            "ls-ecs-MediaWikiStack-task\t0.0.0.0:23012->80/tcp",
            "ls-ecs-MoodleStack-task\t0.0.0.0:23013->80/tcp",
            "ls-ecs-SuiteCRMStack-task\t0.0.0.0:23014->80/tcp",
            "ls-ecs-MyBBStack-task\t0.0.0.0:23015->80/tcp",
            "ls-ecs-FlarumStack-task\t0.0.0.0:23016->80/tcp",
            "ls-ecs-DolphinUNAStack-task\t0.0.0.0:23017->80/tcp"));

        assertEquals(23001, routes.get("wordpress.cloudforge.localhost").intValue());
        assertEquals(23002, routes.get("woocommerce.cloudforge.localhost").intValue());
        assertEquals(23003, routes.get("drupal.cloudforge.localhost").intValue());
        assertEquals(23004, routes.get("magento.cloudforge.localhost").intValue());
        assertEquals(23005, routes.get("prestashop.cloudforge.localhost").intValue());
        assertEquals(23006, routes.get("opencart.cloudforge.localhost").intValue());
        assertEquals(23007, routes.get("bagisto.cloudforge.localhost").intValue());
        assertEquals(23008, routes.get("sylius.cloudforge.localhost").intValue());
        assertEquals(23009, routes.get("concretecms.cloudforge.localhost").intValue());
        assertEquals(23010, routes.get("octobercms.cloudforge.localhost").intValue());
        assertEquals(23011, routes.get("typo3.cloudforge.localhost").intValue());
        assertEquals(23012, routes.get("mediawiki.cloudforge.localhost").intValue());
        assertEquals(23013, routes.get("moodle.cloudforge.localhost").intValue());
        assertEquals(23014, routes.get("suitecrm.cloudforge.localhost").intValue());
        assertEquals(23015, routes.get("mybb.cloudforge.localhost").intValue());
        assertEquals(23016, routes.get("flarum.cloudforge.localhost").intValue());
        assertEquals(23017, routes.get("dolphinuna.cloudforge.localhost").intValue());
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
    void defaultServerBlockSetsDefaultTypeOnItsOwnNotJustTheMatchedHostnameBlock() {
        // The whole-config `conf.contains(...)` check above passes even if only the
        // matched-hostname block sets default_type — isolate the default_server block specifically.
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(
            Map.of("jenkins.cloudforge.localhost", 18080));
        int defaultServerStart = conf.indexOf("listen 80 default_server;");
        int defaultServerEnd = conf.indexOf("\n}\n", defaultServerStart);
        assertTrue(defaultServerStart >= 0 && defaultServerEnd > defaultServerStart,
            "could not isolate the default_server block in the generated config");
        String defaultServerBlock = conf.substring(defaultServerStart, defaultServerEnd);
        assertTrue(defaultServerBlock.contains("default_type text/plain;"),
            "default_server block must set default_type itself -- otherwise its 404 response "
                + "serves as application/octet-stream and browsers download it instead of "
                + "rendering it");
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

    /** Guards against Jenkins-specific rewrites leaking onto every other path-prefixed app: the
     *  /login redirect and the "inject the ALB prefix into every request" rewrite are both
     *  workarounds for Jenkins's own --prefix flag, which makes it expect requests to already
     *  carry that prefix, so an anonymous bare "/" under it gets a 403 + meta-refresh instead of
     *  a clean redirect. Applying both unconditionally to every path-prefixed app regardless of
     *  hostname (see hostnameForContainer) would give a non-Jenkins app a forced "/" → "/login"
     *  redirect to a route that doesn't exist for it, and a forced-prefix request path its own
     *  webserver can't resolve to a real file (a genuine 404, never even reaching the app) —
     *  breaking its real root path outright. */
    @Test
    void nonJenkinsPathPrefixedAppIsProxiedAtItsOwnRootWithNoLoginRedirect() {
        String conf = DefaultEmulatorEdgeRuntime.renderNginxConf(
            Map.of("wordpress.cloudforge.localhost", 58967),
            Map.of("wordpress.cloudforge.localhost", "/_aws/elb/cfc-1pb6buq"));

        assertTrue(conf.contains("location ^~ /_aws/elb/cfc-1pb6buq/"));
        assertFalse(conf.contains("return 302 /login;"));
        assertFalse(conf.contains("rewrite ^/(.*)$ /_aws/elb/cfc-1pb6buq/$1 break;"));
        // The bare "location / { ... }" block still proxies straight through, response
        // rewrites and all — just without injecting the prefix into the request itself.
        assertTrue(conf.contains("proxy_redirect /_aws/elb/cfc-1pb6buq/ /;"));
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
