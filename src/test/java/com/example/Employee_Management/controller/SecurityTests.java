package com.example.Employee_Management.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real logins against the seeded demo accounts (admin, hr, viewer), and what each role may do.
 * Uses its own database so other test classes don't interfere.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security_tests;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    private MockMvc mvc;

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    @Test
    void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ADMIN\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));

        String token = login("admin", "admin123");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.lastLoginAt").exists());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiNeedsATokenButTheUiAndDocsArePublic() throws Exception {
        mvc.perform(get("/api/v1/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to continue"));
        mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        String viewer = login("viewer", "viewer123");
        mvc.perform(get("/api/v1/employees").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/employees").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"X\",\"emailId\":\"x@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role is not allowed to do this"));
        mvc.perform(get("/api/v1/users").header("Authorization", viewer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit").header("Authorization", viewer)).andExpect(status().isForbidden());
    }

    @Test
    void hrManagesEmployeesButNotDepartmentsOrUsers() throws Exception {
        String hr = login("hr", "hr123456");
        String body = mvc.perform(post("/api/v1/employees").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hired\",\"lastName\":\"ByHr\",\"emailId\":\"hired.byhr@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");

        // The audit trail records who made the change.
        mvc.perform(get("/api/v1/employees/{id}/history", id).header("Authorization", hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("hr"))
                .andExpect(jsonPath("$[0].action").value("CREATE"));

        mvc.perform(delete("/api/v1/employees/{id}", id).header("Authorization", hr)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/departments").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Legal\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", hr)).andExpect(status().isForbidden());
    }

    @Test
    void adminManagesUsersWithSafetyRules() throws Exception {
        String admin = login("admin", "admin123");

        mvc.perform(get("/api/v1/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItems("admin", "hr", "viewer")))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        String body = mvc.perform(post("/api/v1/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"New.Manager\",\"fullName\":\"New Manager\",\"password\":\"manager123\",\"role\":\"HR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.manager"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        login("new.manager", "manager123");

        mvc.perform(post("/api/v1/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.manager\",\"fullName\":\"Dup\",\"password\":\"manager123\",\"role\":\"HR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"fullName\":\"\",\"password\":\"short\",\"role\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());

        // Promote, then reset the password; the old one stops working.
        mvc.perform(put("/api/v1/users/{id}", id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Manager\",\"password\":\"changed123\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new.manager\",\"password\":\"manager123\"}"))
                .andExpect(status().isUnauthorized());
        login("new.manager", "changed123");

        // An admin can't delete themselves or demote the last admin.
        String me = mvc.perform(get("/api/v1/auth/me").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        Number adminId = JsonPath.read(me, "$.id");
        mvc.perform(delete("/api/v1/users/{id}", adminId).header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/users/{id}", adminId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Administrator\",\"password\":\"\",\"role\":\"HR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There must be at least one admin"));

        mvc.perform(delete("/api/v1/users/{id}", id).header("Authorization", admin)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/audit").param("entityType", "USER").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.content[1].changes", containsString("role: HR → VIEWER")))
                .andExpect(jsonPath("$.content[1].changes", containsString("password changed")));
    }
}
