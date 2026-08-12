package com.lexpro.lexprobackend.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationResourcesTests {

    private static final Pattern MANUAL_TRANSACTION =
            Pattern.compile("(?m)^(BEGIN|COMMIT);\\s*$");
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?m)^CREATE TABLE\\s+");

    private static final List<ExpectedMigration> MIGRATIONS = List.of(
            new ExpectedMigration(
                    "db/migration/V1__create_core_schema.sql",
                    "E1071691735EB243F6CE93D72B314AC7A218CC4DB06DFA2C007340F9A994CCBA",
                    21
            ),
            new ExpectedMigration(
                    "db/migration/V2__upgrade_core_traceability.sql",
                    "1DF9A40077DBDB2872547E9CFE401DC9CEFA599467533CFE968511FCC087F43B",
                    5
            ),
            new ExpectedMigration(
                    "db/migration/V3__add_workspace_and_rbac.sql",
                    "4EA487AD7B847C54AEC5C6033DBDA7DCC2BFC684D6BA8ED7436E8B40CC3E56D9",
                    6
            ),
            new ExpectedMigration(
                    "db/migration/V4__configure_typical_case_vectors.sql",
                    "5EF3BE0307A89F4093361C32EC0432520C7E3EB2985943347BAA79F91A61CD15",
                    0
            )
    );

    @Test
    void baselineMigrationsMustRemainTraceableAndFlywayManaged() throws IOException {
        for (ExpectedMigration migration : MIGRATIONS) {
            String sql = readClasspathResource(migration.resourcePath());

            assertTrue(
                    sql.contains("-- Source SHA-256: " + migration.sourceSha256()),
                    () -> migration.resourcePath() + " must identify its reviewed DBM source"
            );
            assertFalse(
                    MANUAL_TRANSACTION.matcher(sql).find(),
                    () -> migration.resourcePath() + " must let Flyway manage the transaction"
            );
            assertEquals(
                    migration.expectedCreateTableCount(),
                    CREATE_TABLE.matcher(sql).results().count(),
                    () -> migration.resourcePath() + " has an unexpected CREATE TABLE count"
            );
        }
    }

    private String readClasspathResource(String resourcePath) throws IOException {
        ClassLoader classLoader = FlywayMigrationResourcesTests.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            assertNotNull(input, () -> "Missing classpath resource: " + resourcePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ExpectedMigration(
            String resourcePath,
            String sourceSha256,
            long expectedCreateTableCount
    ) {
    }
}
