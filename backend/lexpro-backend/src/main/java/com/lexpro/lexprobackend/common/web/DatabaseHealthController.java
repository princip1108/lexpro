package com.lexpro.lexprobackend.common.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health/database")
public class DatabaseHealthController {

    private static final String TABLE_COUNT_SQL = """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'lexpro'
              AND table_type = 'BASE TABLE'
              AND table_name <> 'flyway_schema_history'
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public DatabaseHealthResponse health() {
        String databaseName = jdbcTemplate.queryForObject(
                "SELECT current_database()",
                String.class
        );
        Long tableCount = jdbcTemplate.queryForObject(TABLE_COUNT_SQL, Long.class);

        return new DatabaseHealthResponse("UP", databaseName, tableCount);
    }

    public record DatabaseHealthResponse(
            String status,
            String databaseName,
            Long tableCount
    ) {
    }
}
