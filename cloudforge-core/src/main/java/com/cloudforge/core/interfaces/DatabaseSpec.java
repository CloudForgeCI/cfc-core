package com.cloudforge.core.interfaces;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Database specification interface for applications requiring external databases.
 *
 * <p>Applications implementing this interface can request managed RDS databases
 * instead of using embedded file-based databases.</p>
 *
 * <p><b>Supported Engines:</b></p>
 * <ul>
 *   <li>PostgreSQL (postgres)</li>
 *   <li>MySQL (mysql)</li>
 *   <li>MariaDB (mariadb)</li>
 *   <li>Aurora PostgreSQL (aurora-postgresql)</li>
 *   <li>Aurora MySQL (aurora-mysql)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * public class MetabaseApplicationSpec implements ApplicationSpec, DatabaseSpec {
 *     @Override
 *     public DatabaseRequirement databaseRequirement() {
 *         return DatabaseRequirement.optional("postgres", "15");
 *     }
 *
 *     @Override
 *     public Map<String, String> containerEnvironmentVariables(
 *             String fqdn, boolean sslEnabled, String authMode, DatabaseConnection dbConn) {
 *         if (dbConn != null) {
 *             return Map.of(
 *                 "MB_DB_TYPE", "postgres",
 *                 "MB_DB_HOST", dbConn.endpoint(),
 *                 "MB_DB_PORT", String.valueOf(dbConn.port()),
 *                 "MB_DB_DBNAME", dbConn.databaseName(),
 *                 "MB_DB_USER", dbConn.username(),
 *                 "MB_DB_PASS", dbConn.passwordSecretArn()
 *             );
 *         } else {
 *             // Fallback to H2 embedded
 *             return Map.of("MB_DB_TYPE", "h2");
 *         }
 *     }
 * }
 * }</pre>
 *
 * @since 3.0.0
 */
public interface DatabaseSpec {

    /**
     * Database requirement for this application.
     *
     * @return database requirement (required, optional, or none)
     */
    DatabaseRequirement databaseRequirement();

    /**
     * Database initialization SQL scripts to run after creation.
     *
     * @return list of SQL scripts, or empty list
     */
    default List<String> databaseInitScripts() {
        return List.of();
    }

    /**
     * Database configuration overrides for specific engines.
     *
     * <p>Example PostgreSQL parameters:</p>
     * <ul>
     *   <li>max_connections - 200</li>
     *   <li>shared_buffers - {DBInstanceClassMemory/4096}</li>
     *   <li>work_mem - 16MB</li>
     *   <li>log_statement - all</li>
     * </ul>
     *
     * @return map of parameter group settings
     */
    default Map<String, String> databaseParameters() {
        return Map.of();
    }

    /**
     * Database backup retention requirements.
     *
     * @return backup retention days (1-35), default 7
     */
    default int backupRetentionDays() {
        return 7; // Minimum for production
    }

    /**
     * Whether this application requires read replicas for scaling.
     *
     * @return true if read replicas should be created
     */
    default boolean requiresReadReplicas() {
        return false;
    }

    /**
     * Number of read replicas for production.
     *
     * @return read replica count (0-5), default 0
     */
    default int readReplicaCount() {
        return 0;
    }

    /**
     * Every engine and version this application can be deployed with, as the choices the Manager and
     * the CLI offer. The default is derived from {@link #databaseRequirement()}: the requirement's
     * engine at every major version from its minimum upward, marked tested up to its tested maximum.
     * An application that supports more than one engine overrides this and lists each combination.
     *
     * <p>{@link #databaseRequirement()} stays the primary pairing: the engine and version used when a
     * deployment does not choose one.</p>
     *
     * @return the supported combinations, empty when the application has no database
     */
    default List<DatabaseOption> supportedDatabases() {
        DatabaseRequirement requirement = databaseRequirement();
        if (requirement == null || requirement.type() == DatabaseRequirement.RequirementType.NONE) {
            return List.of();
        }
        return DatabaseEngineCatalog.optionsFor(requirement);
    }

    /**
     * Finds the supported option matching an engine and version. A full version such as
     * {@code 8.0.35} or {@code 15.4} matches the option for its major version ({@code 8.0},
     * {@code 15}).
     *
     * @return the matching option, or empty when this application does not support the combination
     */
    default Optional<DatabaseOption> findSupportedDatabase(String engine, String version) {
        if (engine == null || version == null) {
            return Optional.empty();
        }
        String wanted = version.trim();
        return supportedDatabases().stream()
            .filter(option -> option.engine().equalsIgnoreCase(engine.trim()))
            .filter(option -> option.version().equals(wanted) || wanted.startsWith(option.version() + "."))
            .findFirst();
    }

    /**
     * Resolves the engine and version a deployment will use against {@link #supportedDatabases()}.
     * An unset engine means the requirement's engine; an unset version means the requirement's
     * default for that engine, or the first listed option for any other engine.
     *
     * @throws IllegalArgumentException when the application does not support the combination
     */
    default DatabaseChoice resolveDatabase(String applicationId, String engine, String version) {
        DatabaseRequirement requirement = databaseRequirement();
        List<DatabaseOption> supported = supportedDatabases();
        String chosenEngine = engine != null ? engine.trim() : requirement.engine();
        List<DatabaseOption> forEngine = supported.stream()
            .filter(option -> option.engine().equalsIgnoreCase(chosenEngine))
            .toList();
        if (forEngine.isEmpty()) {
            throw new IllegalArgumentException(applicationId + " does not support databaseEngine=" + chosenEngine
                + ". Choose one of: " + describe(supported));
        }
        String chosenVersion = version != null ? version.trim()
            : chosenEngine.equalsIgnoreCase(requirement.engine())
                ? requirement.effectiveDefaultVersion()
                : forEngine.get(0).version();
        Optional<DatabaseOption> match = findSupportedDatabase(chosenEngine, chosenVersion);
        if (match.isPresent()) {
            return new DatabaseChoice(match.get().engine(), chosenVersion, match.get());
        }
        if (chosenEngine.equalsIgnoreCase(requirement.engine())
                && DatabaseRequirement.compareVersions(chosenVersion, forEngine.get(0).version()) < 0) {
            throw new IllegalArgumentException(applicationId + " requires " + requirement.engine() + " "
                + forEngine.get(0).version() + " or higher, but databaseVersion=" + chosenVersion
                + ". Choose " + forEngine.get(0).version() + " or a later version.");
        }
        throw new IllegalArgumentException(applicationId + " does not support " + chosenEngine + " "
            + chosenVersion + ". Choose one of: " + describe(forEngine));
    }

    private static String describe(List<DatabaseOption> options) {
        return options.stream()
            .map(option -> option.engine() + " " + option.version())
            .collect(Collectors.joining(", "));
    }

    /** The engine and version a deployment resolved to, and the supported option it matches. */
    record DatabaseChoice(String engine, String version, DatabaseOption option) {
    }

    /**
     * One engine and version an application can be deployed with.
     *
     * @param engine the engine, as {@code DeploymentConfig.databaseEngine} spells it
     * @param version the major version, e.g. {@code 15} or {@code 8.0}
     * @param tested whether this combination has been verified; an untested one is offered but
     *     deploys with a warning
     */
    record DatabaseOption(String engine, String version, boolean tested) {
    }

    /**
     * Database requirement specification.
     *
     * @param type the requirement type (REQUIRED, OPTIONAL, or NONE)
     * @param engine the database engine (e.g., "postgres", "mysql")
     * @param version the minimum database engine version the application supports. It is also the
     *     version provisioned when the deployment does not choose one; a deployment may select a
     *     higher version but never a lower one (see {@link #meetsMinimum(String)})
     * @param instanceClass the RDS instance class (e.g., "db.t3.micro")
     * @param allocatedStorageGB the allocated storage in GB
     * @param databaseName the name of the database to create
     * @param publiclyAccessible whether the database should be publicly accessible
     * @param defaultVersion the version provisioned when the deployment does not choose one, or
     *     null to use {@code version}. It must not be lower than {@code version}; use it when the
     *     minimum is an engine release the database service no longer supports
     * @param testedMaxVersion the newest version this application has been verified against, or
     *     null when no upper bound has been established. A higher version is still accepted;
     *     it is flagged as untested
     */
    record DatabaseRequirement(
        RequirementType type,
        String engine,
        String version,
        String instanceClass,
        int allocatedStorageGB,
        String databaseName,
        boolean publiclyAccessible,
        String defaultVersion,
        String testedMaxVersion
    ) {
        public enum RequirementType {
            /** Application MUST have external database (GitLab, Mattermost) */
            REQUIRED,

            /** Application CAN use external database or embedded (Metabase, Grafana) */
            OPTIONAL,

            /** Application does not support external database (simple apps) */
            NONE
        }

        /**
         * Whether {@code candidate} is at least this requirement's minimum {@link #version()}.
         * Versions are compared segment by segment as numbers ("17" &lt; "17.2" &lt; "18"; "8.0" equals
         * "8.0.0"). A version that cannot be read as dotted numbers is not rejected, because
         * engine-specific suffixes should not block a deployment on a guess.
         */
        public boolean meetsMinimum(String candidate) {
            return compareVersions(candidate, version) >= 0;
        }

        /**
         * Compares dotted numeric versions segment by segment; a missing segment counts as 0.
         * Returns 0 when either side has a segment that does not start with a digit.
         */
        public static int compareVersions(String a, String b) {
            if (a == null || b == null) {
                return 0;
            }
            String[] left = a.trim().split("\\.");
            String[] right = b.trim().split("\\.");
            for (int i = 0; i < Math.max(left.length, right.length); i++) {
                Integer l = i < left.length ? leadingNumber(left[i]) : Integer.valueOf(0);
                Integer r = i < right.length ? leadingNumber(right[i]) : Integer.valueOf(0);
                if (l == null || r == null) {
                    return 0;
                }
                if (!l.equals(r)) {
                    return Integer.compare(l, r);
                }
            }
            return 0;
        }

        private static Integer leadingNumber(String segment) {
            int end = 0;
            while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
                end++;
            }
            return end == 0 ? null : Integer.valueOf(segment.substring(0, end));
        }

        public static DatabaseRequirement required(String engine, String version) {
            return new DatabaseRequirement(
                RequirementType.REQUIRED,
                engine,
                version,
                "db.t3.micro",      // Default instance class
                20,                  // Default 20GB storage
                "applicationdb",     // Default database name
                false,               // Never publicly accessible
                null,
                null
            );
        }

        public static DatabaseRequirement optional(String engine, String version) {
            return new DatabaseRequirement(
                RequirementType.OPTIONAL,
                engine,
                version,
                "db.t3.micro",
                20,
                "applicationdb",
                false,
                null,
                null
            );
        }

        public static DatabaseRequirement none() {
            return new DatabaseRequirement(
                RequirementType.NONE,
                null, null, null, 0, null, false, null, null
            );
        }

        /** The version provisioned when the deployment does not choose one. */
        public String effectiveDefaultVersion() {
            return defaultVersion != null ? defaultVersion : version;
        }

        /**
         * Whether {@code candidate} is newer than the newest version this application has been
         * verified against. Always false when no tested maximum has been recorded.
         */
        public boolean exceedsTestedMax(String candidate) {
            return testedMaxVersion != null && compareVersions(candidate, testedMaxVersion) > 0;
        }

        /** Sets the version provisioned by default; it may not be lower than the minimum. */
        public DatabaseRequirement withDefaultVersion(String defaultVersion) {
            if (!meetsMinimum(defaultVersion)) {
                throw new IllegalArgumentException("default " + engine + " version " + defaultVersion
                    + " is lower than the minimum " + version);
            }
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible, defaultVersion, testedMaxVersion);
        }

        /** Records the newest version this application has been verified against. */
        public DatabaseRequirement withTestedMaxVersion(String testedMaxVersion) {
            if (compareVersions(testedMaxVersion, effectiveDefaultVersion()) < 0) {
                throw new IllegalArgumentException("tested maximum " + testedMaxVersion
                    + " is lower than the default " + effectiveDefaultVersion());
            }
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible, defaultVersion, testedMaxVersion);
        }

        public DatabaseRequirement withInstanceClass(String instanceClass) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible, defaultVersion, testedMaxVersion);
        }

        public DatabaseRequirement withStorage(int allocatedStorageGB) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible, defaultVersion, testedMaxVersion);
        }

        public DatabaseRequirement withDatabaseName(String databaseName) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible, defaultVersion, testedMaxVersion);
        }
    }

    /**
     * Database connection information provided to applications.
     *
     * @param endpoint the database endpoint hostname
     * @param port the database port number
     * @param databaseName the name of the database
     * @param username the database username
     * @param passwordSecretArn the ARN of the secret containing the database password
     * @param engine the database engine type
     * @param version the database engine version
     * @param readReplicaEndpoints list of read replica endpoints (if any)
     */
    record DatabaseConnection(
        String endpoint,
        int port,
        String databaseName,
        String username,
        String passwordSecretArn,
        String engine,
        String version,
        List<String> readReplicaEndpoints
    ) {
        public boolean hasReadReplicas() {
            return readReplicaEndpoints != null && !readReplicaEndpoints.isEmpty();
        }
    }
}
