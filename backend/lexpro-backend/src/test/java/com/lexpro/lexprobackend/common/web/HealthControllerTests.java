package com.lexpro.lexprobackend.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnApplicationHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("lexpro-backend"));
    }

    @Test
    void shouldReturnProblemDetailForUnknownResource() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
