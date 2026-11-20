package com.example.Employee_Management.controller;

import com.example.Employee_Management.repository.AuditLogRepository;
import com.example.Employee_Management.repository.DepartmentRepository;
import com.example.Employee_Management.repository.EmployeeRepository;
import com.example.Employee_Management.repository.RoleRepository;
import com.example.Employee_Management.model.Department;
import com.example.Employee_Management.model.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.seed-data=false",
        "spring.datasource.url=jdbc:h2:mem:csv_audit_tests;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@AutoConfigureMockMvc
@WithMockUser(username = "hr.tester", roles = "HR")
class CsvAndAuditTests {

    @Autowired private MockMvc mvc;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        roleRepository.deleteAll();
        auditLogRepository.deleteAll();
        departmentRepository.save(new Department("Engineering", null));
        roleRepository.save(new Role("Software Engineer", null));
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "employees.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importAddsValidRowsAndReportsTheRest() throws Exception {
        String file = """
                firstName,lastName,email,phone,department,role,salary,joiningDate,status
                Riya,Sharma,riya@example.com,+91 98100 22001,engineering,Software Engineer,85000,2026-01-15,ACTIVE
                "Kumar, Jr.",Dev,dev@example.com,,,,"1,20,000",,on leave
                ,NoFirst,nofirst@example.com,,,,,,
                Bad,Dept,bad@example.com,,Marketing,,,,
                Bad,Date,date@example.com,,,,,15/01/2026,
                Dup,InFile,riya@example.com,,,,,,

                Last,Row,last@example.com,,,,abc,,
                """;
        mvc.perform(multipart("/api/v1/employees/import").file(csv(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failed").value(5))
                .andExpect(jsonPath("$.errors[0].line").value(4))
                .andExpect(jsonPath("$.errors[0].message").value("First name is required"))
                .andExpect(jsonPath("$.errors[1].message").value("Unknown department 'Marketing'"))
                .andExpect(jsonPath("$.errors[2].message", containsString("2026-01-31")))
                .andExpect(jsonPath("$.errors[3].message", containsString("appears twice")))
                .andExpect(jsonPath("$.errors[4].line").value(9))
                .andExpect(jsonPath("$.errors[4].message", containsString("not a number")));

        mvc.perform(get("/api/v1/employees").param("q", "kumar"))
                .andExpect(jsonPath("$.content[0].firstName").value("Kumar, Jr."))
                .andExpect(jsonPath("$.content[0].salary").value(120000))
                .andExpect(jsonPath("$.content[0].status").value("ON_LEAVE"));
        mvc.perform(get("/api/v1/employees").param("q", "riya"))
                .andExpect(jsonPath("$.content[0].department.name").value("Engineering"));

        // Re-importing the same file adds nothing: every e-mail now exists.
        mvc.perform(multipart("/api/v1/employees/import").file(csv(file)))
                .andExpect(jsonPath("$.imported").value(0));
    }

    @Test
    void importRejectsAFileWithoutTheRequiredColumns() throws Exception {
        mvc.perform(multipart("/api/v1/employees/import").file(csv("name,mail\nA,a@example.com\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Missing column 'firstName'")));
        mvc.perform(multipart("/api/v1/employees/import").file(csv("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotImport() throws Exception {
        mvc.perform(multipart("/api/v1/employees/import").file(csv("firstName,email\nA,a@example.com\n")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportRoundTripsThroughImport() throws Exception {
        mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"=cmd\",\"lastName\":\"Quote \\\"Q\\\", Esq\",\"emailId\":\"q@example.com\",\"salary\":5000.5}"))
                .andExpect(status().isCreated());

        String csv = mvc.perform(get("/api/v1/employees/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"employees-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();

        // Header, the formula-safe first name and the quoted last name.
        org.assertj.core.api.Assertions.assertThat(csv)
                .startsWith("firstName,lastName,email,phone,department,role,salary,joiningDate,status\r\n")
                .contains("'=cmd,\"Quote \"\"Q\"\", Esq\",q@example.com,,,,5000.50,,ACTIVE");

        mvc.perform(get("/api/v1/employees/import/template"))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("firstName,lastName,email")));
    }

    @Test
    void updatesRecordOnlyTheFieldsThatChanged() throws Exception {
        String body = mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Asha\",\"emailId\":\"asha@example.com\",\"salary\":90000,\"status\":\"ACTIVE\"}"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");

        // No real change → no audit entry.
        mvc.perform(put("/api/v1/employees/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Asha\",\"emailId\":\"asha@example.com\",\"salary\":90000.00,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/employees/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Asha\",\"emailId\":\"asha@example.com\",\"salary\":95000,\"status\":\"ON_LEAVE\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/employees/{id}/history", id))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].actor").value("hr.tester"))
                .andExpect(jsonPath("$[0].changes").value("salary: 90000 → 95000; status: ACTIVE → ON_LEAVE"))
                .andExpect(jsonPath("$[1].action").value("CREATE"));

        mvc.perform(get("/api/v1/employees/999999/history")).andExpect(status().isNotFound());
    }

    @Test
    void dashboardIncludesPayrollHiresAndActivity() throws Exception {
        mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"emailId\":\"a@example.com\",\"salary\":100000,\"joiningDate\":\""
                        + java.time.LocalDate.now() + "\"}"));
        mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"B\",\"emailId\":\"b@example.com\",\"salary\":50000}"));
        mvc.perform(post("/api/v1/employees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"C\",\"emailId\":\"c@example.com\",\"salary\":70000,\"status\":\"INACTIVE\"}"));

        mvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPayroll").value(150000.0))
                .andExpect(jsonPath("$.averageSalary").value(75000.0))
                .andExpect(jsonPath("$.hiresLast30Days").value(1))
                .andExpect(jsonPath("$.hiresByMonth", hasSize(12)))
                .andExpect(jsonPath("$.hiresByMonth[11].count").value(1))
                .andExpect(jsonPath("$.recentActivity", hasSize(3)))
                .andExpect(jsonPath("$.recentActivity[0].summary").value("Added C"));
    }
}
