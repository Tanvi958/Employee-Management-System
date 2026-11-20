package com.example.Employee_Management.controller;

import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end tests of the REST API against an empty in-memory H2 database. */
@SpringBootTest(properties = {"app.seed-data=false", "spring.datasource.url=jdbc:h2:mem:api_tests;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@AutoConfigureMockMvc
@WithMockUser(username = "tester", roles = "ADMIN")
class EmployeeApiTests {

    @Autowired private MockMvc mvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private RoleRepository roleRepository;

    @BeforeEach
    void cleanDatabase() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        roleRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions put(String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createDepartment(String name) throws Exception {
        return idOf(post("/api/v1/departments", "{\"name\":\"" + name + "\"}").andExpect(status().isCreated()));
    }

    private long createRole(String title) throws Exception {
        return idOf(post("/api/v1/roles", "{\"title\":\"" + title + "\"}").andExpect(status().isCreated()));
    }

    private long createEmployee(String first, String last, String email, Long deptId, Long roleId) throws Exception {
        String json = """
                {"firstName":"%s","lastName":"%s","emailId":"%s","departmentId":%s,"roleId":%s,
                 "salary":50000,"joiningDate":"2025-01-10","status":"ACTIVE"}
                """.formatted(first, last, email, deptId, roleId);
        return idOf(post("/api/v1/employees", json).andExpect(status().isCreated()));
    }

    // ---------------------------------------------------------------- employees

    @Test
    void fullEmployeeCrudLifecycle() throws Exception {
        long eng = createDepartment("Engineering");
        long dev = createRole("Developer");

        long id = createEmployee("Tanvi", "Nishad", "Tanvi@Example.com", eng, dev);

        mvc.perform(get("/api/v1/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tanvi Nishad"))
                .andExpect(jsonPath("$.emailId").value("tanvi@example.com"))
                .andExpect(jsonPath("$.department.name").value("Engineering"))
                .andExpect(jsonPath("$.role.name").value("Developer"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists());

        put("/api/v1/employees/" + id, """
                {"firstName":"Tanvi","lastName":"N","emailId":"tanvi@example.com","departmentId":null,
                 "roleId":%d,"salary":65000.50,"status":"ON_LEAVE"}
                """.formatted(dev))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("N"))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.salary").value(65000.50))
                .andExpect(jsonPath("$.status").value("ON_LEAVE"));

        mvc.perform(delete("/api/v1/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mvc.perform(get("/api/v1/employees/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Employee not found with id: " + id));
    }

    @Test
    void createRejectsInvalidBodyWithFieldErrors() throws Exception {
        post("/api/v1/employees", """
                {"firstName":"","emailId":"not-an-email","phone":"abc","salary":-5,"joiningDate":"2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.emailId").value("Email must be a valid address"))
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.salary").exists())
                .andExpect(jsonPath("$.fieldErrors.joiningDate").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        post("/api/v1/employees", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void duplicateEmailIsConflictIgnoringCase() throws Exception {
        createEmployee("A", "One", "dup@example.com", null, null);
        post("/api/v1/employees", "{\"firstName\":\"B\",\"emailId\":\"DUP@example.com\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void updateToAnotherEmployeesEmailIsConflict() throws Exception {
        createEmployee("A", "One", "a@example.com", null, null);
        long b = createEmployee("B", "Two", "b@example.com", null, null);
        put("/api/v1/employees/" + b, "{\"firstName\":\"B\",\"emailId\":\"a@example.com\"}")
                .andExpect(status().isConflict());
        // Keeping your own e-mail is fine.
        put("/api/v1/employees/" + b, "{\"firstName\":\"Bee\",\"emailId\":\"b@example.com\"}")
                .andExpect(status().isOk());
    }

    @Test
    void unknownDepartmentIsNotFound() throws Exception {
        post("/api/v1/employees", "{\"firstName\":\"A\",\"emailId\":\"a@example.com\",\"departmentId\":9999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found with id: 9999"));
    }

    @Test
    void updateAndDeleteOfMissingEmployeeAreNotFound() throws Exception {
        put("/api/v1/employees/424242", "{\"firstName\":\"X\",\"emailId\":\"x@example.com\"}")
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/employees/424242")).andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersSortsAndPages() throws Exception {
        long eng = createDepartment("Engineering");
        long hr = createDepartment("HR");
        long dev = createRole("Developer");
        createEmployee("Aarav", "Sharma", "aarav@example.com", eng, dev);
        createEmployee("Priya", "Verma", "priya@example.com", eng, dev);
        createEmployee("Kavya", "Reddy", "kavya@corp.in", hr, null);

        // text search on full name, case-insensitive
        mvc.perform(get("/api/v1/employees").param("q", "PRIYA VER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Priya"));

        // text search on e-mail
        mvc.perform(get("/api/v1/employees").param("q", "corp.in"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // department filter + sort by first name descending
        mvc.perform(get("/api/v1/employees").param("departmentId", String.valueOf(eng))
                        .param("sort", "firstName").param("direction", "desc"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].firstName").value("Priya"))
                .andExpect(jsonPath("$.content[1].firstName").value("Aarav"));

        // role filter
        mvc.perform(get("/api/v1/employees").param("roleId", String.valueOf(dev)))
                .andExpect(jsonPath("$.totalElements").value(2));

        // paging
        mvc.perform(get("/api/v1/employees").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));

        // status filter
        mvc.perform(get("/api/v1/employees").param("status", "INACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // bad enum value
        mvc.perform(get("/api/v1/employees").param("status", "RETIRED"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- departments + roles

    @Test
    void departmentCrudAndProtectionWhileInUse() throws Exception {
        long eng = createDepartment("Engineering");

        post("/api/v1/departments", "{\"name\":\"engineering\"}").andExpect(status().isConflict());
        post("/api/v1/departments", "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Department name is required"));

        put("/api/v1/departments/" + eng, "{\"name\":\"Platform Engineering\",\"description\":\"Core\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform Engineering"));

        long emp = createEmployee("A", "B", "ab@example.com", eng, null);
        mvc.perform(get("/api/v1/departments"))
                .andExpect(jsonPath("$[0].employeeCount").value(1));

        mvc.perform(delete("/api/v1/departments/{id}", eng))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 employee(s)")));

        mvc.perform(delete("/api/v1/employees/{id}", emp)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/departments/{id}", eng)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/departments/{id}", eng)).andExpect(status().isNotFound());
    }

    @Test
    void roleCrudAndProtectionWhileInUse() throws Exception {
        long dev = createRole("Developer");
        post("/api/v1/roles", "{\"title\":\"DEVELOPER\"}").andExpect(status().isConflict());

        long emp = createEmployee("A", "B", "ab@example.com", null, dev);
        mvc.perform(delete("/api/v1/roles/{id}", dev)).andExpect(status().isConflict());

        put("/api/v1/employees/" + emp, "{\"firstName\":\"A\",\"emailId\":\"ab@example.com\"}")
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/roles/{id}", dev)).andExpect(status().isOk());
    }

    @Test
    void dashboardCountsByStatusAndDepartment() throws Exception {
        long eng = createDepartment("Engineering");
        createDepartment("Empty");
        createEmployee("A", "B", "a@example.com", eng, null);
        post("/api/v1/employees", "{\"firstName\":\"C\",\"emailId\":\"c@example.com\",\"status\":\"ON_LEAVE\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(2))
                .andExpect(jsonPath("$.activeEmployees").value(1))
                .andExpect(jsonPath("$.onLeaveEmployees").value(1))
                .andExpect(jsonPath("$.departments").value(2))
                .andExpect(jsonPath("$.perDepartment[0].name").value("Engineering"))
                .andExpect(jsonPath("$.perDepartment[0].count").value(1))
                .andExpect(jsonPath("$.recentlyAdded", hasSize(2)));
    }
}
