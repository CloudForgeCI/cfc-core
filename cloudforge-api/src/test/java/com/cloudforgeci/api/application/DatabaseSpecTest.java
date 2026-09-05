package com.cloudforgeci.api.application;

import com.cloudforge.core.interfaces.ApplicationSpec;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
            Arguments.of(new GitLabApplicationSpec(), RequirementType.REQUIRED, "postgres", "16", "gitlabhq_production", 30),
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
}
