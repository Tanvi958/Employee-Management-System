package com.example.Employee_Management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Starts the full app (H2 + sample data) and checks the dashboard, static UI and API docs are served. */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeManagementApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoadsAndSeedsSampleData() throws Exception {
        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees", greaterThan(0)))
                .andExpect(jsonPath("$.departments", greaterThan(0)))
                .andExpect(jsonPath("$.roles", greaterThan(0)));
    }

    @Test
    void servesWebDashboard() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Employee Management System")));
    }

    @Test
    void servesOpenApiDocs() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Employee Management System API"));
    }
}
