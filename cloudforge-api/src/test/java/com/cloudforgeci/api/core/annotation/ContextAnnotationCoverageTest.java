package com.cloudforgeci.api.core.annotation;

import com.cloudforge.core.annotation.DeploymentContext;
import com.cloudforge.core.annotation.SecurityProfileConfiguration;
import com.cloudforge.core.annotation.SystemContext;
import com.cloudforge.core.config.DeploymentConfig;
import com.cloudforgeci.api.core.Slot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;

/**
 * Guards the context annotation system end to end. {@link ContextInjector} swallows every failure
 * (a missing getter, a type that cannot be assigned) and leaves the field unset, so a gap looks
 * like a feature that quietly does nothing. These tests fail instead:
 *
 * <ul>
 *   <li>every {@code @DeploymentContext}, {@code @SystemContext} and
 *       {@code @SecurityProfileConfiguration} field in main code resolves on the context it names,
 *       by the injector's own lookup rules, and the resolved type can be assigned to the field;</li>
 *   <li>every {@link DeploymentConfig} setting is reachable through {@code DeploymentContext} and
 *       is read by something, so a setting offered in a form cannot be inert without an error.</li>
 * </ul>
 *
 * The two {@code ...EXCEPTIONS} sets are for settings that predate this test; each test also fails
 * when an entry no longer applies, so the sets only shrink.
 */
class ContextAnnotationCoverageTest {

    /** DeploymentConfig settings with no accessor on DeploymentContext, with the reason. */
    private static final Map<String, String> NO_ACCESSOR_EXCEPTIONS = Map.of(
        "account", "read straight from DeploymentConfig by CloudForgeSynthesizer, before a context exists",
        "availabilityZones", "read straight from DeploymentConfig by CloudForgeSynthesizer",
        "complianceFrameworksRawOverride", "copied into the CDK app context by CloudForgeSynthesizer",
        "applicationName", "read straight from DeploymentConfig by DeploymentContextCatalog",
        "environment", "read straight from DeploymentConfig by DeploymentContextCatalog",
        "applicationSpec", "an object the deployer sets; factories read the SystemContext applicationSpec slot");

    /** DeploymentConfig settings nothing outside DeploymentConfig and its plain accessor reads. */
    private static final Map<String, String> UNREAD_EXCEPTIONS = Map.of(
        "artifactsBucket", "documented in DeploymentContext, no reader outside DeploymentConfig",
        "artifactsPrefix", "documented in DeploymentContext, no reader outside DeploymentConfig",
        "identityCenterGroupName", "documented in DeploymentContext, no reader; Identity Center/SAML is deferred",
        "ssoGroupId", "documented in DeploymentContext, no reader; Identity Center/SAML is deferred",
        "ssoTargetAccountId", "documented in DeploymentContext, no reader; Identity Center/SAML is deferred");

    /**
     * Annotated fields that resolve by name but not by type, with the reason. Each is a field
     * declared with a concrete type that the context only guarantees as an interface, so the
     * injector's assignment fails, with no error, whenever the value is the interface type.
     */
    private static final Map<String, String> SYSTEM_TYPE_EXCEPTIONS = Map.of(
        "ApplicationSamlFactory#keycloakDeployed",
        "declared as Slot<Boolean>, but the injector unwraps slots, so the field stays null (SAML is deferred)",
        "CognitoSamlFactory#cognitoUserPool",
        "concrete UserPool field; the slot is IUserPool (SAML is deferred)",
        "IdentityCenterSamlFactory#cognitoUserPool",
        "concrete UserPool field; the slot is IUserPool (SAML is deferred)");

    /** Annotated fields naming a property the context does not have, with the reason. */
    private static final Map<String, String> SYSTEM_UNRESOLVED_EXCEPTIONS = Map.of(
        "KeycloakFactory#cluster", "SAML/Keycloak is deferred work; SystemContext defines no such slot",
        "KeycloakFactory#database", "SAML/Keycloak is deferred work; SystemContext defines no databaseInstance slot",
        "KeycloakFactory#databaseSecret", "SAML/Keycloak is deferred work; SystemContext defines no such slot",
        "KeycloakFactory#applicationSecurityGroup", "SAML/Keycloak is deferred work; SystemContext defines no such slot");

    // The annotations imported above share simple names with the contexts they read, so the
    // context classes are written out once here.
    private static final Class<?> DEPLOYMENT_CONTEXT = com.cloudforgeci.api.core.DeploymentContext.class;
    private static final Class<?> SYSTEM_CONTEXT = com.cloudforgeci.api.core.SystemContext.class;
    private static final Class<?> SECURITY_PROFILE_CONFIGURATION =
        com.cloudforgeci.api.interfaces.SecurityProfileConfiguration.class;

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
        boolean.class, Boolean.class, int.class, Integer.class, long.class, Long.class,
        double.class, Double.class, float.class, Float.class, short.class, Short.class,
        byte.class, Byte.class, char.class, Character.class);

    private record Carrier(Class<?> owner, Field field, String key) {
        String describe() {
            return owner.getName() + "#" + field.getName() + " (\"" + key + "\")";
        }
    }

    // ---------------------------------------------------------------- annotated fields

    @Test
    void everyDeploymentContextFieldResolvesOnDeploymentContext() {
        assertResolves(DeploymentContext.class, DeploymentContext::value,
            DEPLOYMENT_CONTEXT, false, 10, Map.of(), Map.of());
    }

    @Test
    void everySystemContextFieldResolvesOnSystemContext() {
        assertResolves(SystemContext.class, SystemContext::value,
            SYSTEM_CONTEXT, true, 10,
            SYSTEM_UNRESOLVED_EXCEPTIONS, SYSTEM_TYPE_EXCEPTIONS);
    }

    @Test
    void everySecurityProfileConfigurationFieldResolvesOnTheProfileInterface() {
        assertResolves(SecurityProfileConfiguration.class, SecurityProfileConfiguration::value,
            SECURITY_PROFILE_CONFIGURATION, false, 0,
            Map.of(), Map.of());
    }

    private <A extends Annotation> void assertResolves(
            Class<A> annotation, Function<A, String> key, Class<?> context, boolean unwrapSlots,
            int minimumCarriers, Map<String, String> unresolvedAllowed, Map<String, String> typeAllowed) {
        List<Carrier> carriers = carriers(annotation, key);
        assertTrue(carriers.size() >= minimumCarriers,
            "expected annotated fields to be found for @" + annotation.getSimpleName()
                + ", found " + carriers.size());

        Set<String> unresolved = new TreeSet<>();
        Set<String> mistyped = new TreeSet<>();
        Map<String, String> detail = new HashMap<>();
        for (Carrier carrier : carriers) {
            String id = carrier.owner().getSimpleName() + "#" + carrier.field().getName();
            Member accessor = ContextInjector.accessorFor(context, carrier.key());
            if (accessor == null) {
                unresolved.add(id);
                detail.put(id, context.getSimpleName() + " has no field, " + carrier.key() + "(), get"
                    + capitalize(carrier.key()) + "() or is" + capitalize(carrier.key()) + "()");
                continue;
            }
            Class<?> provided = providedType(accessor, unwrapSlots);
            if (provided != null && !assignable(carrier.field().getType(), provided)) {
                mistyped.add(id);
                detail.put(id, provided.getSimpleName() + " cannot be assigned to "
                    + carrier.field().getType().getSimpleName());
            }
        }
        assertShrinkOnly(unresolved, unresolvedAllowed.keySet(), "SYSTEM_UNRESOLVED_EXCEPTIONS",
            "@" + annotation.getSimpleName() + " fields the injector leaves unset without an error", detail);
        assertShrinkOnly(mistyped, typeAllowed.keySet(), "SYSTEM_TYPE_EXCEPTIONS",
            "@" + annotation.getSimpleName() + " fields the injector cannot assign", detail);
    }

    // ---------------------------------------------------------------- DeploymentConfig

    @Test
    void everyDeploymentConfigSettingIsReachableThroughDeploymentContext() {
        Map<String, Set<String>> aliases = accessorAliases();
        Set<String> missing = new TreeSet<>();
        for (String setting : configSettings()) {
            boolean exact = ContextInjector.accessorFor(
                DEPLOYMENT_CONTEXT, setting) != null;
            if (!exact && aliases.getOrDefault(setting, Set.of()).isEmpty()) {
                missing.add(setting);
            }
        }
        assertShrinkOnly(missing, NO_ACCESSOR_EXCEPTIONS.keySet(), "NO_ACCESSOR_EXCEPTIONS",
            "DeploymentConfig settings with no DeploymentContext accessor, so no factory can read them",
            Map.of());
    }

    @Test
    void everyDeploymentConfigSettingIsReadBySomething() throws IOException {
        String sources = mainSources();
        Map<String, Set<String>> aliases = accessorAliases();
        Set<String> unread = new TreeSet<>();
        for (String setting : configSettings()) {
            // A setting is read under its own name or under any DeploymentContext accessor that
            // forwards it (macieAutomatedDiscoveryEnabled() reads macieAutomatedDiscovery), as an
            // annotation key (plain or fully qualified), or as a raw context key.
            Set<String> names = new TreeSet<>(aliases.getOrDefault(setting, Set.of()));
            names.add(setting);
            boolean read = names.stream().anyMatch(name -> Pattern.compile(
                "DeploymentContext\\(\"" + name + "\"\\)|\\." + name + "\\b|\\bget"
                    + capitalize(name) + "\\(|\"" + name + "\"").matcher(sources).find());
            if (!read) {
                unread.add(setting);
            }
        }
        assertShrinkOnly(unread, UNREAD_EXCEPTIONS.keySet(), "UNREAD_EXCEPTIONS",
            "DeploymentConfig settings that no code reads, so setting them does nothing", Map.of());
    }

    /** Accessors on DeploymentContext that forward a setting, keyed by the setting's name. */
    private static Map<String, Set<String>> accessorAliases() {
        Map<String, Set<String>> aliases = new HashMap<>();
        try {
            String source = Files.readString(Path.of(
                "src/main/java/com/cloudforgeci/api/core/DeploymentContext.java"));
            Matcher forwarded = Pattern.compile(
                "public [\\w<>\\[\\], ?.]+ (\\w+)\\(\\)\\s*\\{\\s*return config\\.(\\w+);").matcher(source);
            while (forwarded.find()) {
                aliases.computeIfAbsent(forwarded.group(2), k -> new TreeSet<>()).add(forwarded.group(1));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return aliases;
    }

    private static void assertShrinkOnly(Set<String> found, Set<String> allowed, String name, String what,
                                         Map<String, String> detail) {
        Set<String> unexpected = new TreeSet<>(found);
        unexpected.removeAll(allowed);
        String listed = unexpected.stream()
            .map(item -> detail.containsKey(item) ? item + ": " + detail.get(item) : item)
            .collect(Collectors.joining("\n  ", "\n  ", ""));
        assertTrue(unexpected.isEmpty(), what + ":" + listed
            + "\nWire each one up and cover it with a test, or add it to " + name + " with the reason.");
        Set<String> stale = new TreeSet<>(allowed);
        stale.removeAll(found);
        assertTrue(stale.isEmpty(), name + " lists settings that no longer apply: " + stale
            + ". Remove them.");
    }

    @Test
    void theSharedLookupFollowsTheInjectorsOrder() {
        class Ctx {
            public String exact;
            public String exact() { return "method"; }
            public String getOnlyGetter() { return ""; }
            public boolean isFlag() { return true; }
        }
        assertEquals("exact", ContextInjector.accessorFor(Ctx.class, "exact").getName());
        assertTrue(ContextInjector.accessorFor(Ctx.class, "exact") instanceof Field);
        assertEquals("getOnlyGetter", ContextInjector.accessorFor(Ctx.class, "onlyGetter").getName());
        assertEquals("isFlag", ContextInjector.accessorFor(Ctx.class, "flag").getName());
        assertEquals(null, ContextInjector.accessorFor(Ctx.class, "absent"));
    }

    // ---------------------------------------------------------------- helpers

    private static Set<String> configSettings() {
        return Arrays.stream(DeploymentConfig.class.getFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static <A extends Annotation> List<Carrier> carriers(
            Class<A> annotation, Function<A, String> key) {
        List<Carrier> result = new ArrayList<>();
        for (Class<?> type : mainClasses()) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable unloadable) {
                continue;
            }
            for (Field field : fields) {
                A found = field.getAnnotation(annotation);
                if (found != null && !key.apply(found).isEmpty()) {
                    result.add(new Carrier(type, field, key.apply(found)));
                }
            }
        }
        return result;
    }

    /** The type the injector hands to the field: a Slot's contents when it unwraps slots. */
    private static Class<?> providedType(Member accessor, boolean unwrapSlots) {
        Class<?> raw;
        Type generic;
        if (accessor instanceof Field field) {
            raw = field.getType();
            generic = field.getGenericType();
        } else {
            Method method = (Method) accessor;
            raw = method.getReturnType();
            generic = method.getGenericReturnType();
        }
        if (Slot.class.isAssignableFrom(raw)) {
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> content) {
                return content;
            }
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof ParameterizedType nested
                    && nested.getRawType() instanceof Class<?> nestedRaw) {
                return nestedRaw;
            }
            return null; // unresolvable type variable: nothing to compare
        }
        return raw;
    }

    private static boolean assignable(Class<?> target, Class<?> source) {
        Class<?> wrappedTarget = WRAPPERS.getOrDefault(target, target);
        Class<?> wrappedSource = WRAPPERS.getOrDefault(source, source);
        return wrappedTarget.isAssignableFrom(wrappedSource);
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String mainSources() throws IOException {
        StringBuilder all = new StringBuilder();
        for (String root : new String[] {"src/main/java", "../cloudforge-core/src/main/java"}) {
            Path dir = Path.of(root);
            assertTrue(Files.isDirectory(dir), "run from the cloudforge-api module: missing " + dir);
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    if (file.endsWith("config/DeploymentConfig.java")) {
                        continue;
                    }
                    String text = Files.readString(file);
                    if (file.endsWith("core/DeploymentContext.java")) {
                        // The plain accessors are the gap being measured, not a reader.
                        text = text.replaceAll("(?m)^\\s*public [^\\n]*\\{ return config\\.\\w+; \\}\\s*$", "");
                    }
                    all.append(text).append('\n');
                }
            }
        }
        return all.toString();
    }

    private static List<Class<?>> mainClasses() {
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> anchor : List.of(ContextInjector.class, DeploymentContext.class)) {
            URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
            for (String name : classNames(location)) {
                try {
                    classes.add(Class.forName(name, false, anchor.getClassLoader()));
                } catch (Throwable optionalDependencyMissing) {
                    // not loadable here, so nothing can be injected into it either
                }
            }
        }
        return classes;
    }

    private static List<String> classNames(URL location) {
        List<String> names = new ArrayList<>();
        try {
            Path path = Path.of(location.toURI());
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    files.filter(p -> p.toString().endsWith(".class"))
                        .map(p -> path.relativize(p).toString())
                        .map(ContextAnnotationCoverageTest::toClassName)
                        .forEach(names::add);
                }
            } else {
                try (JarFile jar = new JarFile(path.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement().getName();
                        if (entry.endsWith(".class")) {
                            names.add(toClassName(entry));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("cannot scan " + location, e);
        }
        return names;
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.').replace('\\', '.');
    }
}
