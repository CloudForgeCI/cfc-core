package com.cloudforgeci.api.application;

import com.cloudforge.core.interfaces.ApplicationSpec;
import com.cloudforge.core.interfaces.DatabaseEngineCatalog;
import com.cloudforge.core.interfaces.DatabaseSpec;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseConnection;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement;
import com.cloudforge.core.interfaces.DatabaseSpec.DatabaseRequirement.RequirementType;
import com.cloudforgeci.api.application.analytics.MetabaseApplicationSpec;
import com.cloudforgeci.api.application.analytics.SupersetApplicationSpec;
import com.cloudforgeci.api.application.artifactregistry.HarborApplicationSpec;
import com.cloudforgeci.api.application.cicd.GitLabApplicationSpec;
import com.cloudforgeci.api.application.collaboration.MattermostApplicationSpec;
import com.cloudforgeci.api.application.monitoring.GrafanaApplicationSpec;
import com.cloudforgeci.api.application.JenkinsApplicationSpec;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Map;
import java.util.stream.Stream;
import software.amazon.awscdk.services.rds.MariaDbEngineVersion;
import software.amazon.awscdk.services.rds.MysqlEngineVersion;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DatabaseSpec interface implementation across all applications.
 *
 * <p>Tests validate:</p>
 * <ul>
 *   <li>Database requirement configuration (REQUIRED vs OPTIONAL)</li>
 *   <li>Environment variable generation with/without database connection</li>
 *   <li>Database parameters optimization</li>
 *   <li>Backup retention requirements</li>
 *   <li>Plugin annotation database flags</li>
 * </ul>
 */
@DisplayName("DatabaseSpec Implementation Tests")
public class DatabaseSpecTest {

    // ========== Test Data Providers ==========

    /**
     * Provides all applications implementing DatabaseSpec with their expected configurations.
     */
    // Trailing supportsDb/requiresDb columns were removed: supportsDb was always true across
    // every case here (nothing to regress-test), and requiresDb was exactly
    // (expectedType == RequirementType.REQUIRED), already covered by testDatabaseRequirementType's
    // own expectedType assertion -- neither was ever actually consumed by a test.
    static Stream<Arguments> databaseApplicationProvider() {
        return Stream.of(
            // OPTIONAL databases (can use embedded fallback)
            Arguments.of(new MetabaseApplicationSpec(), RequirementType.OPTIONAL, "postgres", "15", "metabase", 7),
            Arguments.of(new GrafanaApplicationSpec(), RequirementType.OPTIONAL, "postgres", "14", "grafana", 7),

            // REQUIRED databases (must have external RDS)
            Arguments.of(new GitLabApplicationSpec(), RequirementType.REQUIRED, "postgres", "17", "gitlabhq_production", 30),
            Arguments.of(new MattermostApplicationSpec(), RequirementType.REQUIRED, "postgres", "14", "mattermost", 14),
            Arguments.of(new SupersetApplicationSpec(), RequirementType.REQUIRED, "postgres", "13", "superset", 14),
            Arguments.of(new HarborApplicationSpec(), RequirementType.REQUIRED, "postgres", "13", "registry", 30)
        );
    }

    /**
     * Provides test database connection configurations.
     */
    static Stream<Arguments> databaseConnectionProvider() {
        return Stream.of(
            Arguments.of("test-db.rds.amazonaws.com", 5432, "testdb", "dbuser", "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-password-abc123", "postgres", "14"),
            Arguments.of("prod-db.rds.amazonaws.com", 5432, "proddb", "admin", "arn:aws:secretsmanager:us-east-1:123456789012:secret:prod-db-password-xyz789", "postgres", "15")
        );
    }

    // ========== Database Requirement Tests ==========

    @ParameterizedTest(name = "{index}: {0} should have {1} database requirement")
    @MethodSource("databaseApplicationProvider")
    @DisplayName("Database requirement type should match expected configuration")
    // expectedBackupDays (the source tuple's trailing column, used only by the sibling
    // testBackupRetentionDays below) is deliberately not declared here -- JUnit binds
    // @MethodSource arguments by position up to the test method's own parameter count, so a
    // shorter parameter list simply ignores the source's remaining values.
    void testDatabaseRequirementType(ApplicationSpec app, RequirementType expectedType, String expectedEngine,
                                      String expectedVersion, String expectedDbName) {
        // Cast to DatabaseSpec
        assertTrue(app instanceof DatabaseSpec, app.getClass().getSimpleName() + " should implement DatabaseSpec");
        DatabaseSpec dbSpec = (DatabaseSpec) app;

        // Get database requirement
        DatabaseRequirement req = dbSpec.databaseRequirement();
        assertNotNull(req, "Database requirement should not be null");

        // Validate requirement type
        assertEquals(expectedType, req.type(),
            app.getClass().getSimpleName() + " should have " + expectedType + " database requirement");

        // Validate database engine and version
        assertEquals(expectedEngine, req.engine(), "Database engine should be " + expectedEngine);
        assertEquals(expectedVersion, req.version(), "Database version should be " + expectedVersion);

        // Validate database name
        assertEquals(expectedDbName, req.databaseName(), "Database name should be " + expectedDbName);

        // Validate instance class (should be valid)
        assertNotNull(req.instanceClass(), "Instance class should not be null");
        assertTrue(req.instanceClass().startsWith("db.t3.") || req.instanceClass().startsWith("db.m5."),
            "Instance class should be valid RDS instance type");

        // Validate storage
        assertTrue(req.allocatedStorageGB() >= 20, "Storage should be at least 20GB");
        assertTrue(req.allocatedStorageGB() <= 100, "Storage should be reasonable (<= 100GB for defaults)");

        // Validate publicly accessible is false
        assertFalse(req.publiclyAccessible(), "RDS instances should never be publicly accessible");
    }

    @ParameterizedTest(name = "{index}: {0} backup retention should be {5} days")
    @MethodSource("databaseApplicationProvider")
    @DisplayName("Backup retention days should match application data criticality")
    // codeql[java/unused-parameter] -- expectedType/expectedEngine/expectedVersion/expectedDbName
    // are unused here (see testDatabaseRequirementType above for those), but expectedBackupDays
    // is the tuple's LAST column and JUnit binds @MethodSource arguments by position, so the
    // leading ones can't be dropped without shifting expectedBackupDays out of alignment.
    void testBackupRetentionDays(ApplicationSpec app, RequirementType expectedType, String expectedEngine, String expectedVersion, String expectedDbName, int expectedBackupDays) {
        DatabaseSpec dbSpec = (DatabaseSpec) app;

        int backupDays = dbSpec.backupRetentionDays();

        assertEquals(expectedBackupDays, backupDays,
            app.getClass().getSimpleName() + " should have " + expectedBackupDays + " days backup retention");

        // Validate backup retention is within AWS limits
        assertTrue(backupDays >= 1, "Backup retention must be at least 1 day");
        assertTrue(backupDays <= 35, "Backup retention cannot exceed 35 days");
    }

    // ========== Database Parameters Tests ==========

    @Test
    @DisplayName("GitLab database parameters should be optimized for high-traffic workload")
    void testGitLabDatabaseParameters() {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();
        Map<String, String> params = gitlab.databaseParameters();

        assertNotNull(params, "Database parameters should not be null");
        assertFalse(params.isEmpty(), "GitLab should have optimized database parameters");

        // GitLab needs high connection limit
        assertTrue(params.containsKey("max_connections"), "Should configure max_connections");
        assertEquals("300", params.get("max_connections"), "GitLab needs 300 max connections");

        // Should have shared_buffers optimization
        assertTrue(params.containsKey("shared_buffers"), "Should configure shared_buffers");
        assertTrue(params.get("shared_buffers").contains("DBInstanceClassMemory"),
            "shared_buffers should use instance memory parameter");
    }

    @Test
    @DisplayName("Harbor database parameters should be optimized for registry workload")
    void testHarborDatabaseParameters() {
        HarborApplicationSpec harbor = new HarborApplicationSpec();
        Map<String, String> params = harbor.databaseParameters();

        assertNotNull(params, "Database parameters should not be null");

        // Harbor needs moderate connection limit
        assertTrue(params.containsKey("max_connections"), "Should configure max_connections");
        assertEquals("250", params.get("max_connections"), "Harbor needs 250 max connections");

        // Should have maintenance_work_mem for registry operations
        assertTrue(params.containsKey("maintenance_work_mem"), "Should configure maintenance_work_mem");
        assertEquals("256MB", params.get("maintenance_work_mem"), "Harbor needs 256MB maintenance_work_mem");
    }

    // ========== Environment Variables Tests (Without Database) ==========

    @Test
    @DisplayName("Metabase should fall back to H2 database when no RDS connection provided")
    void testMetabaseFallbackToH2() {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();

        // Get environment variables without database connection
        Map<String, String> env = metabase.containerEnvironmentVariables("metabase.example.com", true, "none", null);

        assertNotNull(env, "Environment variables should not be null");

        // Should use H2 embedded database
        assertEquals("h2", env.get("MB_DB_TYPE"), "Should fall back to H2 database");
        assertTrue(env.get("MB_DB_FILE").contains("metabase.db"), "Should specify H2 database file");

        // Should still configure site URL
        assertEquals("https://metabase.example.com", env.get("MB_SITE_URL"), "Should configure site URL");
    }

    @Test
    @DisplayName("Grafana should fall back to SQLite when no RDS connection provided")
    void testGrafanaFallbackToSQLite() {
        GrafanaApplicationSpec grafana = new GrafanaApplicationSpec();

        // Get environment variables without database connection
        Map<String, String> env = grafana.containerEnvironmentVariables("grafana.example.com", true, "none", null);

        assertNotNull(env, "Environment variables should not be null");

        // Should use SQLite embedded database
        assertEquals("sqlite3", env.get("GF_DATABASE_TYPE"), "Should fall back to SQLite database");
        assertTrue(env.get("GF_DATABASE_PATH").contains("grafana.db"), "Should specify SQLite database file");

        // Should configure server URLs
        assertEquals("https://grafana.example.com", env.get("GF_SERVER_ROOT_URL"), "Should configure root URL");
    }

    @Test
    @DisplayName("GitLab should enable embedded PostgreSQL when no RDS connection provided")
    void testGitLabFallbackToEmbedded() {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();

        // Get environment variables without database connection
        Map<String, String> env = gitlab.containerEnvironmentVariables("gitlab.example.com", true, "none", null);

        assertNotNull(env, "Environment variables should not be null");

        // Should enable embedded PostgreSQL
        String omnibusConfig = env.get("GITLAB_OMNIBUS_CONFIG");
        assertNotNull(omnibusConfig, "Should have GITLAB_OMNIBUS_CONFIG");
        assertTrue(omnibusConfig.contains("postgresql['enable'] = true"), "Should enable embedded PostgreSQL");
        assertTrue(omnibusConfig.contains("external_url"), "Should configure external URL");
    }

    // ========== Environment Variables Tests (With RDS Database) ==========

    @ParameterizedTest(name = "{index}: Testing {6} RDS connection")
    @MethodSource("databaseConnectionProvider")
    @DisplayName("Metabase should use RDS PostgreSQL when database connection provided")
    void testMetabaseWithRdsConnection(String endpoint, int port, String dbName, String username,
                                        String passwordSecretArn, String engine, String version) {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();

        DatabaseConnection dbConn = new DatabaseConnection(
            endpoint, port, dbName, username, passwordSecretArn, engine, version, List.of()
        );

        Map<String, String> env = metabase.containerEnvironmentVariables("metabase.example.com", true, "none", dbConn);

        // Should use PostgreSQL
        assertEquals("postgres", env.get("MB_DB_TYPE"), "Should use PostgreSQL");
        assertEquals(endpoint, env.get("MB_DB_HOST"), "Should use RDS endpoint");
        assertEquals(String.valueOf(port), env.get("MB_DB_PORT"), "Should use RDS port");
        assertEquals(dbName, env.get("MB_DB_DBNAME"), "Should use RDS database name");
        assertEquals(username, env.get("MB_DB_USER"), "Should use RDS username");

        // Password is now injected via ECS secret, not in environment variables
        // The test framework doesn't create ECS containers, so password won't be in env
        // Just verify that MB_DB_PASS is not set with the old CloudFormation resolve syntax
        String password = env.get("MB_DB_PASS");
        if (password != null) {
            assertFalse(password.contains("{{resolve:secretsmanager"),
                "Should not use CloudFormation resolve syntax (doesn't work in containers)");
        }
        // Note: In production, ContainerFactory adds MB_DB_PASS as an ECS secret from Secrets Manager
    }

    @ParameterizedTest(name = "{index}: Testing {6} RDS connection")
    @MethodSource("databaseConnectionProvider")
    @DisplayName("GitLab should use RDS PostgreSQL when database connection provided")
    void testGitLabWithRdsConnection(String endpoint, int port, String dbName, String username,
                                      String passwordSecretArn, String engine, String version) {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();

        DatabaseConnection dbConn = new DatabaseConnection(
            endpoint, port, dbName, username, passwordSecretArn, engine, version, List.of()
        );

        Map<String, String> env = gitlab.containerEnvironmentVariables("gitlab.example.com", true, "none", dbConn);

        String omnibusConfig = env.get("GITLAB_OMNIBUS_CONFIG");
        assertNotNull(omnibusConfig, "Should have GITLAB_OMNIBUS_CONFIG");

        // Should disable embedded PostgreSQL
        assertTrue(omnibusConfig.contains("postgresql['enable'] = false"), "Should disable embedded PostgreSQL");

        // Should configure RDS connection
        assertTrue(omnibusConfig.contains("gitlab_rails['db_adapter'] = 'postgresql'"), "Should use PostgreSQL adapter");
        assertTrue(omnibusConfig.contains("gitlab_rails['db_host'] = '" + endpoint + "'"), "Should configure RDS endpoint");
        assertTrue(omnibusConfig.contains("gitlab_rails['db_port'] = " + port), "Should configure RDS port");
        assertTrue(omnibusConfig.contains("gitlab_rails['db_database'] = '" + dbName + "'"), "Should configure database name");
        assertTrue(omnibusConfig.contains("gitlab_rails['db_username'] = '" + username + "'"), "Should configure username");
        // Password should use ENV variable (injected by ECS from Secrets Manager)
        assertTrue(omnibusConfig.contains("ENV['GITLAB_DATABASE_PASSWORD']"),
            "Should use ENV variable for password (injected by ECS from Secrets Manager)");
        assertFalse(omnibusConfig.contains("{{resolve:secretsmanager"),
            "Should not use CloudFormation resolve syntax (doesn't work in containers)");
    }

    @ParameterizedTest(name = "{index}: Testing {6} RDS connection")
    @MethodSource("databaseConnectionProvider")
    @DisplayName("Mattermost should use RDS PostgreSQL when database connection provided")
    void testMattermostWithRdsConnection(String endpoint, int port, String dbName, String username,
                                          String passwordSecretArn, String engine, String version) {
        MattermostApplicationSpec mattermost = new MattermostApplicationSpec();

        DatabaseConnection dbConn = new DatabaseConnection(
            endpoint, port, dbName, username, passwordSecretArn, engine, version, List.of()
        );

        Map<String, String> env = mattermost.containerEnvironmentVariables("mattermost.example.com", true, "none", dbConn);

        // Should use PostgreSQL driver
        assertEquals("postgres", env.get("MM_SQLSETTINGS_DRIVERNAME"), "Should use PostgreSQL driver");

        // NOTE: Mattermost is distroless (no shell) so it can't do env var substitution.
        // The MM_SQLSETTINGS_DATASOURCE is NOT set here - it's injected by ContainerFactory
        // from an SSM Parameter that RdsFactory creates with the complete connection string.
        // This is because the password needs to be resolved at runtime via dynamic reference.
        assertNull(env.get("MM_SQLSETTINGS_DATASOURCE"),
            "Datasource should NOT be set here - it's injected by ContainerFactory from SSM");
    }

    // ========== Plugin Annotation Tests ==========

    @Test
    @DisplayName("Metabase plugin annotation should declare optional database support")
    void testMetabasePluginAnnotation() {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();
        var plugin = metabase.getClass().getAnnotation(com.cloudforge.core.annotation.ApplicationPlugin.class);

        assertNotNull(plugin, "Should have @ApplicationPlugin annotation");
        assertTrue(plugin.supportsDatabase(), "Should support database");
        assertFalse(plugin.requiresDatabase(), "Should NOT require database (optional)");
    }

    @Test
    @DisplayName("GitLab plugin annotation should declare required database support")
    void testGitLabPluginAnnotation() {
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();
        var plugin = gitlab.getClass().getAnnotation(com.cloudforge.core.annotation.ApplicationPlugin.class);

        assertNotNull(plugin, "Should have @ApplicationPlugin annotation");
        assertTrue(plugin.supportsDatabase(), "Should support database");
        assertTrue(plugin.requiresDatabase(), "Should REQUIRE database");
    }

    @Test
    @DisplayName("All database applications should have consistent plugin annotations")
    void testAllDatabasePluginAnnotations() {
        Map<ApplicationSpec, Boolean> apps = Map.of(
            new MetabaseApplicationSpec(), false,  // OPTIONAL
            new GrafanaApplicationSpec(), false,   // OPTIONAL
            new GitLabApplicationSpec(), true,     // REQUIRED
            new MattermostApplicationSpec(), true, // REQUIRED
            new SupersetApplicationSpec(), true,   // REQUIRED
            new HarborApplicationSpec(), true      // REQUIRED
        );

        for (Map.Entry<ApplicationSpec, Boolean> entry : apps.entrySet()) {
            ApplicationSpec app = entry.getKey();
            boolean shouldRequireDb = entry.getValue();

            var plugin = app.getClass().getAnnotation(com.cloudforge.core.annotation.ApplicationPlugin.class);
            assertNotNull(plugin, app.getClass().getSimpleName() + " should have @ApplicationPlugin annotation");

            // All should support database
            assertTrue(plugin.supportsDatabase(),
                app.getClass().getSimpleName() + " should support database");

            // Check if requirement matches expectation
            assertEquals(shouldRequireDb, plugin.requiresDatabase(),
                app.getClass().getSimpleName() + " database requirement mismatch");
        }
    }

    @Test
    @DisplayName("ApplicationSpec.supportsDatabase() should reflect the plugin annotation, not just the annotation itself")
    void testApplicationSpecSupportsDatabaseReadsAnnotation() {
        ApplicationSpec metabase = new MetabaseApplicationSpec();
        assertTrue(metabase.supportsDatabase(),
            "ApplicationSpec.supportsDatabase() should be true for an app whose @ApplicationPlugin sets it");

        ApplicationSpec jenkins = new JenkinsApplicationSpec();
        assertFalse(jenkins.supportsDatabase(),
            "ApplicationSpec.supportsDatabase() should default to false for an app that doesn't support a database");
    }

    // ========== Database Connection Record Tests ==========

    @Test
    @DisplayName("DatabaseConnection should properly expose read replica information")
    void testDatabaseConnectionReadReplicas() {
        List<String> readReplicas = List.of(
            "read-replica-1.rds.amazonaws.com",
            "read-replica-2.rds.amazonaws.com"
        );

        DatabaseConnection dbConn = new DatabaseConnection(
            "primary.rds.amazonaws.com", 5432, "testdb", "admin",
            "arn:aws:secretsmanager:secret", "postgres", "14", readReplicas
        );

        assertTrue(dbConn.hasReadReplicas(), "Should have read replicas");
        assertEquals(2, dbConn.readReplicaEndpoints().size(), "Should have 2 read replicas");
        assertEquals("read-replica-1.rds.amazonaws.com", dbConn.readReplicaEndpoints().get(0),
            "First read replica should match");
    }

    @Test
    @DisplayName("DatabaseConnection should handle no read replicas correctly")
    void testDatabaseConnectionNoReadReplicas() {
        DatabaseConnection dbConn = new DatabaseConnection(
            "primary.rds.amazonaws.com", 5432, "testdb", "admin",
            "arn:aws:secretsmanager:secret", "postgres", "14", List.of()
        );

        assertFalse(dbConn.hasReadReplicas(), "Should not have read replicas");
        assertTrue(dbConn.readReplicaEndpoints().isEmpty(), "Read replica list should be empty");
    }

    // ========== Backward Compatibility Tests ==========

    @Test
    @DisplayName("Applications should maintain backward compatibility with old containerEnvironmentVariables signature")
    void testBackwardCompatibility() {
        // Old signature: containerEnvironmentVariables(String fqdn, boolean sslEnabled, String authMode)
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();

        // Should work without database connection parameter
        Map<String, String> env = metabase.containerEnvironmentVariables("metabase.example.com", true, "none");

        assertNotNull(env, "Should return environment variables");
        assertEquals("h2", env.get("MB_DB_TYPE"), "Should default to H2 when using old signature");
    }

    // ========== Negative Tests ==========

    @Test
    @DisplayName("Applications with REQUIRED database should set placeholder when database missing")
    void testRequiredDatabaseMissingConnection() {
        // GitLab REQUIRES a database
        GitLabApplicationSpec gitlab = new GitLabApplicationSpec();

        Map<String, String> env = gitlab.containerEnvironmentVariables("gitlab.example.com", true, "none", null);

        String omnibusConfig = env.get("GITLAB_OMNIBUS_CONFIG");
        assertNotNull(omnibusConfig, "Should have GITLAB_OMNIBUS_CONFIG");

        // Should fall back to embedded PostgreSQL (will only work for single instance)
        assertTrue(omnibusConfig.contains("postgresql['enable'] = true"),
            "Should enable embedded PostgreSQL as fallback");
    }

    @Test
    @DisplayName("Mattermost should set error placeholder when database connection missing")
    void testMattermostMissingDatabaseConnection() {
        MattermostApplicationSpec mattermost = new MattermostApplicationSpec();

        Map<String, String> env = mattermost.containerEnvironmentVariables("mattermost.example.com", true, "none", null);

        // Should have error placeholder
        String dataSource = env.get("MM_SQLSETTINGS_DATASOURCE");
        assertNotNull(dataSource, "Should have data source");
        assertTrue(dataSource.contains("MISSING_DATABASE_CONNECTION"),
            "Should indicate missing database connection");
    }

    // ========== Minimum-version semantics ==========

    @ParameterizedTest(name = "minimum {0}: {1} -> {2}")
    @MethodSource("minimumVersionCases")
    @DisplayName("A database version meets the minimum when it is equal or higher")
    void requirementVersionIsAMinimum(String minimum, String candidate, boolean allowed) {
        DatabaseRequirement requirement = DatabaseRequirement.required("postgres", minimum);
        assertEquals(allowed, requirement.meetsMinimum(candidate));
    }

    static Stream<Arguments> minimumVersionCases() {
        return Stream.of(
            Arguments.of("17", "17", true),
            Arguments.of("17", "18", true),
            Arguments.of("17", "17.4", true),
            Arguments.of("17", "16", false),
            Arguments.of("17", "9", false),       // numeric, not lexical: 9 < 17
            Arguments.of("8.0", "8.0.35", true),
            Arguments.of("8.0", "8.0", true),
            Arguments.of("8.0", "5.7", false),
            Arguments.of("10.11", "10.6", false), // 10.6 < 10.11 numerically
            Arguments.of("17", "17-beta", true),  // suffix after the digits is ignored
            Arguments.of("17", "latest", true)    // unreadable versions are not rejected on a guess
        );
    }

    // ========== Default and tested-maximum versions ==========

    /** Every registered application that declares a database, found through the same ServiceLoader the engine uses. */
    static List<DatabaseRequirement> registeredDatabaseRequirements() {
        return ServiceLoader.load(ApplicationSpec.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(spec -> spec instanceof DatabaseSpec)
            .map(spec -> ((DatabaseSpec) spec).databaseRequirement())
            .filter(req -> req.type() != RequirementType.NONE)
            .toList();
    }

    @Test
    @DisplayName("Every registered application's default database version meets its minimum")
    void everyDefaultVersionMeetsItsMinimum() {
        List<DatabaseRequirement> requirements = registeredDatabaseRequirements();
        assertTrue(requirements.size() >= 20, "expected the registered database applications, found " + requirements.size());
        for (DatabaseRequirement req : requirements) {
            assertTrue(req.meetsMinimum(req.effectiveDefaultVersion()),
                req.engine() + " default " + req.effectiveDefaultVersion() + " is below minimum " + req.version());
            if (req.testedMaxVersion() != null) {
                assertFalse(req.exceedsTestedMax(req.effectiveDefaultVersion()),
                    req.engine() + " default " + req.effectiveDefaultVersion() + " is above its tested maximum " + req.testedMaxVersion());
            }
        }
    }

    /**
     * Majors that Amazon RDS no longer supports under standard support (Extended Support is a paid,
     * opt-in-at-create program), as of 2026-10: MySQL 5.7 and Postgres 13 and older. A default on one
     * of these deploys an engine that costs extra or is retired. The minimum may still be this low,
     * since a user can choose it on purpose; only the default is checked.
     */
    @Test
    @DisplayName("No application defaults to an engine version past RDS standard support")
    void noDefaultIsPastRdsStandardSupport() {
        for (DatabaseRequirement req : registeredDatabaseRequirements()) {
            String def = req.effectiveDefaultVersion();
            boolean pastSupport = switch (req.engine()) {
                case "mysql" -> DatabaseRequirement.compareVersions(def, "5.7.999") <= 0;
                case "postgres" -> DatabaseRequirement.compareVersions(def, "13.999") <= 0;
                default -> false;
            };
            assertFalse(pastSupport, req.engine() + " default " + def + " is past RDS standard support");
        }
    }

    @Test
    @DisplayName("An application that declares only a minimum defaults to that minimum")
    void defaultVersionFallsBackToMinimum() {
        DatabaseRequirement req = DatabaseRequirement.required("mysql", "8.0");
        assertEquals("8.0", req.effectiveDefaultVersion());
        assertFalse(req.exceedsTestedMax("9.9"), "no tested maximum recorded, so nothing is flagged");
    }

    @Test
    @DisplayName("The default is separate from the minimum and survives other with-methods")
    void defaultVersionIsKeptSeparateFromMinimum() {
        DatabaseRequirement req = DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withTestedMaxVersion("8.0.40")
            .withStorage(50)
            .withDatabaseName("app");
        assertEquals("5.7", req.version());
        assertEquals("8.0", req.effectiveDefaultVersion());
        assertTrue(req.meetsMinimum("5.7"), "the minimum stays selectable");
        assertFalse(req.meetsMinimum("5.6"));
        assertFalse(req.exceedsTestedMax("8.0.35"));
        assertTrue(req.exceedsTestedMax("8.4"), "above the tested maximum is flagged, not rejected");
    }

    @Test
    @DisplayName("A default below the minimum, or a tested maximum below the default, is rejected")
    void inconsistentVersionsAreRejected() {
        DatabaseRequirement req = DatabaseRequirement.required("postgres", "14");
        assertThrows(IllegalArgumentException.class, () -> req.withDefaultVersion("13"));
        assertThrows(IllegalArgumentException.class, () -> req.withDefaultVersion("15").withTestedMaxVersion("14"));
    }

    private static DatabaseSpec specFor(DatabaseRequirement requirement) {
        return () -> requirement;
    }

    @Test
    @DisplayName("The supported list defaults to the requirement's engine from its minimum upward")
    void supportedDatabasesAreDerivedFromTheRequirement() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("mysql", "5.7")
            .withDefaultVersion("8.0")
            .withTestedMaxVersion("8.0"));

        assertEquals(List.of(
            new DatabaseSpec.DatabaseOption("mysql", "5.7", true),
            new DatabaseSpec.DatabaseOption("mysql", "8.0", true),
            new DatabaseSpec.DatabaseOption("mysql", "8.4", false)), spec.supportedDatabases());
    }

    @Test
    @DisplayName("Without a tested maximum, only versions up to the default count as tested")
    void versionsAboveTheDefaultAreUntestedWhenNoMaximumIsDeclared() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("postgres", "14").withDefaultVersion("15"));

        assertTrue(spec.findSupportedDatabase("postgres", "14").orElseThrow().tested());
        assertTrue(spec.findSupportedDatabase("postgres", "15").orElseThrow().tested());
        assertFalse(spec.findSupportedDatabase("postgres", "16").orElseThrow().tested());
    }

    @Test
    @DisplayName("An application with no database lists no supported databases")
    void noDatabaseMeansNoSupportedDatabases() {
        assertTrue(specFor(DatabaseRequirement.none()).supportedDatabases().isEmpty());
    }

    @Test
    @DisplayName("A full version matches the option for its major version")
    void fullVersionMatchesItsMajorOption() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("mysql", "8.0"));
        assertTrue(spec.findSupportedDatabase("mysql", "8.0.35").isPresent());
        assertTrue(spec.findSupportedDatabase("MySQL", "8.4").isPresent());
        assertFalse(spec.findSupportedDatabase("mysql", "5.7").isPresent());
        assertFalse(spec.findSupportedDatabase("mysql", "8.01").isPresent());
    }

    @Test
    @DisplayName("Unset engine and version resolve to the application's own default")
    void resolveUsesTheApplicationDefault() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("mysql", "5.7").withDefaultVersion("8.0"));
        DatabaseSpec.DatabaseChoice choice = spec.resolveDatabase("app", null, null);
        assertEquals("mysql", choice.engine());
        assertEquals("8.0", choice.version());
    }

    @Test
    @DisplayName("A higher version is accepted, and flagged untested above the tested maximum")
    void resolveAcceptsHigherVersions() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("postgres", "14").withTestedMaxVersion("15"));
        assertTrue(spec.resolveDatabase("app", "postgres", "15").option().tested());
        assertFalse(spec.resolveDatabase("app", "postgres", "17").option().tested());
    }

    @Test
    @DisplayName("A version below the minimum is rejected naming the minimum")
    void resolveRejectsBelowMinimum() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("postgres", "17"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> spec.resolveDatabase("gitlab", null, "16"));
        assertEquals("gitlab requires postgres 17 or higher, but databaseVersion=16. "
            + "Choose 17 or a later version.", e.getMessage());
    }

    @Test
    @DisplayName("An engine the application does not list is rejected with the supported choices")
    void resolveRejectsUnsupportedEngine() {
        DatabaseSpec spec = specFor(DatabaseRequirement.required("postgres", "16"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> spec.resolveDatabase("mattermost", "mysql", null));
        assertTrue(e.getMessage().startsWith("mattermost does not support databaseEngine=mysql"));
        assertTrue(e.getMessage().contains("postgres 16"));
    }

    @Test
    @DisplayName("An application that overrides the list can be deployed on each listed engine")
    void overriddenListSupportsEachEngine() {
        DatabaseSpec spec = new DatabaseSpec() {
            @Override
            public DatabaseRequirement databaseRequirement() {
                return DatabaseRequirement.required("postgres", "15");
            }

            @Override
            public List<DatabaseOption> supportedDatabases() {
                return List.of(
                    new DatabaseOption("postgres", "15", true),
                    new DatabaseOption("mysql", "8.0", true),
                    new DatabaseOption("mysql", "8.4", false));
            }
        };

        DatabaseSpec.DatabaseChoice mysql = spec.resolveDatabase("app", "mysql", null);
        assertEquals("8.0", mysql.version(), "an alternate engine starts at its first listed version");
        assertFalse(spec.resolveDatabase("app", "mysql", "8.4").option().tested());
        assertThrows(IllegalArgumentException.class, () -> spec.resolveDatabase("app", "mariadb", null));
        assertThrows(IllegalArgumentException.class, () -> spec.resolveDatabase("app", "mysql", "5.7"));
    }

    @Test
    @DisplayName("Metabase tells the container which type of database it is connected to")
    void metabaseDatabaseTypeFollowsTheConnectedEngine() {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();
        for (String[] engineAndType : new String[][] {{"postgres", "postgres"}, {"mysql", "mysql"}, {"mariadb", "mysql"}}) {
            DatabaseConnection dbConn = new DatabaseConnection(
                "db.rds.amazonaws.com", 3306, "metabase", "user", "arn:secret", engineAndType[0], "8.0", List.of());
            Map<String, String> env = metabase.containerEnvironmentVariables("m.example.com", true, "none", dbConn);
            assertEquals(engineAndType[1], env.get("MB_DB_TYPE"), engineAndType[0]);
        }
    }

    @Test
    @DisplayName("Metabase offers MySQL and MariaDB as untested alternates to PostgreSQL")
    void metabaseListsUntestedAlternateEngines() {
        MetabaseApplicationSpec metabase = new MetabaseApplicationSpec();
        assertTrue(metabase.findSupportedDatabase("postgres", "15").orElseThrow().tested());
        assertFalse(metabase.findSupportedDatabase("mysql", "8.0").orElseThrow().tested());
        assertFalse(metabase.findSupportedDatabase("mariadb", "10.11").orElseThrow().tested());
        assertFalse(metabase.findSupportedDatabase("mysql", "5.7").isPresent());
    }

    /**
     * The newest major version the CDK library knows for an engine. When a CDK upgrade adds a version
     * RDS has released, this exceeds the catalog and the test below fails, so the catalog is not
     * silently left behind.
     */
    private static String newestCdkMajor(Class<?> engineVersionClass, String majorGetter) throws Exception {
        String newest = null;
        for (Field field : engineVersionClass.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || field.getType() != engineVersionClass) {
                continue;
            }
            Object constant = field.get(null);
            String major = (String) engineVersionClass.getMethod(majorGetter).invoke(constant);
            if (newest == null || DatabaseRequirement.compareVersions(major, newest) > 0) {
                newest = major;
            }
        }
        return newest;
    }

    @Test
    @DisplayName("The catalog lists the newest major version the CDK knows for each engine")
    void catalogIncludesTheNewestMajorVersionTheCdkKnows() throws Exception {
        assertCatalogHas("postgres", newestCdkMajor(
            PostgresEngineVersion.class, "getPostgresMajorVersion"));
        assertCatalogHas("mysql", newestCdkMajor(
            MysqlEngineVersion.class, "getMysqlMajorVersion"));
        assertCatalogHas("mariadb", newestCdkMajor(
            MariaDbEngineVersion.class, "getMariaDbMajorVersion"));
    }

    private static void assertCatalogHas(String engine, String newestInCdk) {
        List<String> catalog = DatabaseEngineCatalog.versions(engine);
        assertTrue(catalog.contains(newestInCdk) || DatabaseRequirement.compareVersions(
                catalog.get(catalog.size() - 1), newestInCdk) >= 0,
            "The CDK knows " + engine + " " + newestInCdk + " but DatabaseEngineCatalog stops at "
                + catalog.get(catalog.size() - 1) + ". Add the new version to the catalog, mark it untested "
                + "on the applications until each has been deployed with it, and add its case to "
                + "RdsFactory's version mapping if the CDK has a constant for it.");
    }

    @Test
    @DisplayName("Every engine the catalog can offer is one the RDS factory can provision")
    void catalogEnginesAreProvisionable() {
        assertEquals(List.of("postgres", "mysql", "mariadb"),
            DatabaseEngineCatalog.engines());
        for (String engine : DatabaseEngineCatalog.engines()) {
            assertFalse(DatabaseEngineCatalog.versions(engine).isEmpty(), engine);
        }
    }
}
