package com.cloudforge.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@code application.properties} then {@code application-local.properties}
 * from the classpath, with later resources overriding earlier ones.
 *
 * <p>Resolution helpers also consult environment variables and JVM system
 * properties so CI/local shells can override without editing files.</p>
 *
 * @since 3.3.0
 */
public final class ApplicationPropertyLoader {

    private static final Logger LOG = Logger.getLogger(ApplicationPropertyLoader.class.getName());
    private static final String[] RESOURCE_NAMES = {
        "application.properties",
        "application-local.properties"
    };

    private static volatile Properties cached;

    /** Defaults to {@link System#getenv(String)}; tests may replace to isolate the shell. */
    private static volatile Function<String, String> environment = System::getenv;

    private ApplicationPropertyLoader() {
    }

    /** Test-only: override env lookup ({@code null} restores {@link System#getenv}). */
    static void overrideEnvironmentForTests(Function<String, String> lookup) {
        environment = lookup == null ? System::getenv : lookup;
    }

    /** Merged classpath properties (application then application-local). */
    public static Properties load() {
        Properties local = cached;
        if (local != null) {
            return local;
        }
        synchronized (ApplicationPropertyLoader.class) {
            if (cached == null) {
                cached = loadFresh();
            }
            return cached;
        }
    }

    /** Clears the cache (tests). */
    public static void clearCache() {
        synchronized (ApplicationPropertyLoader.class) {
            cached = null;
        }
    }

    /** Clears cache and restores default env lookup (tests). */
    static void resetForTests() {
        clearCache();
        overrideEnvironmentForTests(null);
    }

    /**
     * Resolve a dotted property key with env / system / file precedence.
     *
     * <p>Order: env ({@code CFC_MANAGER_URL} for {@code cfc.manager.url}) →
     * JVM {@code -Dkey} → classpath properties.</p>
     */
    public static String resolve(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) {
            return null;
        }
        String envName = toEnvName(propertyKey);
        String fromEnv = environment.apply(envName);
        if (notBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromSys = System.getProperty(propertyKey);
        if (notBlank(fromSys)) {
            return fromSys.trim();
        }
        // Also accept uppercase env-style system property
        String fromSysEnv = System.getProperty(envName);
        if (notBlank(fromSysEnv)) {
            return fromSysEnv.trim();
        }
        String fromFile = load().getProperty(propertyKey);
        if (notBlank(fromFile)) {
            return fromFile.trim();
        }
        return null;
    }

    /**
     * Fill blank {@link DeploymentConfig} fields that declare {@link com.cloudforge.core.annotation.ConfigField#propertyKey()}.
     */
    public static void applyPropertyDefaults(DeploymentConfig config) {
        if (config == null) {
            return;
        }
        for (java.lang.reflect.Field field : DeploymentConfig.class.getDeclaredFields()) {
            if (!field.isAnnotationPresent(com.cloudforge.core.annotation.ConfigField.class)) {
                continue;
            }
            ConfigFieldInfo info = ConfigFieldInfo.from(field);
            if (info.propertyKey() == null || info.propertyKey().isBlank()) {
                continue;
            }
            Object current = info.getValue(config);
            if (current != null && (!(current instanceof String s) || !s.isBlank())) {
                continue;
            }
            String resolved = resolve(info.propertyKey());
            if (resolved == null) {
                continue;
            }
            info.setValue(config, coerce(resolved, info.type()));
        }
    }

    static String toEnvName(String propertyKey) {
        return propertyKey.trim()
            .replace('.', '_')
            .replace('-', '_')
            .toUpperCase();
    }

    private static Properties loadFresh() {
        Properties merged = new Properties();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ApplicationPropertyLoader.class.getClassLoader();
        }
        for (String name : RESOURCE_NAMES) {
            List<Properties> loaded = loadAll(cl, name);
            for (Properties p : loaded) {
                merged.putAll(toMap(p));
            }
        }
        return merged;
    }

    private static List<Properties> loadAll(ClassLoader cl, String name) {
        List<Properties> list = new ArrayList<>();
        try {
            Enumeration<java.net.URL> urls = cl.getResources(name);
            while (urls.hasMoreElements()) {
                java.net.URL url = urls.nextElement();
                Properties p = new Properties();
                try (InputStream in = url.openStream();
                     InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(reader);
                    list.add(p);
                    LOG.fine("Loaded " + name + " from " + url);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed reading " + url, e);
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed enumerating " + name, e);
        }
        return list;
    }

    private static Map<String, String> toMap(Properties p) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : p.stringPropertyNames()) {
            map.put(name, p.getProperty(name));
        }
        return map;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Object coerce(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(value);
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(value);
        }
        return value;
    }
}
