package com.lexpro.lexprobackend.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatabaseHealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatabaseHealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnDatabaseHealth() throws Exception {
        when(jdbcTemplate.queryForObject(eq("SELECT current_database()"), eq(String.class)))
                .thenReturn("lexpro");
        when(jdbcTemplate.queryForObject(contains("table_name <> 'flyway_schema_history'"), eq(Long.class)))
                .thenReturn(32L);

        mockMvc.perform(get("/api/health/database"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lexpro")))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.databaseName").value("lexpro"))
                .andExpect(jsonPath("$.tableCount").value(32));
    }
}
