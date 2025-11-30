package com.cloudforge.core.interfaces;

import java.util.List;
import java.util.Map;

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
 * @since 3.1.0
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
     * Database requirement specification.
     */
    record DatabaseRequirement(
        RequirementType type,
        String engine,
        String version,
        String instanceClass,
        int allocatedStorageGB,
        String databaseName,
        boolean publiclyAccessible
    ) {
        public enum RequirementType {
            /** Application MUST have external database (GitLab, Mattermost) */
            REQUIRED,

            /** Application CAN use external database or embedded (Metabase, Grafana) */
            OPTIONAL,

            /** Application does not support external database (simple apps) */
            NONE
        }

        public static DatabaseRequirement required(String engine, String version) {
            return new DatabaseRequirement(
                RequirementType.REQUIRED,
                engine,
                version,
                "db.t3.micro",      // Default instance class
                20,                  // Default 20GB storage
                "applicationdb",     // Default database name
                false                // Never publicly accessible
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
                false
            );
        }

        public static DatabaseRequirement none() {
            return new DatabaseRequirement(
                RequirementType.NONE,
                null, null, null, 0, null, false
            );
        }

        public DatabaseRequirement withInstanceClass(String instanceClass) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible);
        }

        public DatabaseRequirement withStorage(int allocatedStorageGB) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible);
        }

        public DatabaseRequirement withDatabaseName(String databaseName) {
            return new DatabaseRequirement(type, engine, version, instanceClass,
                allocatedStorageGB, databaseName, publiclyAccessible);
        }
    }

    /**
     * Database connection information provided to applications.
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
