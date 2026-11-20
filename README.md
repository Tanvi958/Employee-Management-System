# Employee Management System

A full-stack employee management app: a **Spring Boot REST API** with a **web dashboard** for managing
employee records, departments and job roles. It follows a layered MVC design
(controller → service → repository) over **PostgreSQL** with Spring Data JPA (Hibernate).

## Features

- **Employees:** create, view, edit and delete, with department, role, phone, salary, joining date and status
  (Active / On leave / Inactive).
- **Search, filter, sort and paginate:** search by name or e-mail; filter by department, role or status;
  sort by name, salary, joining date or status.
- **Departments and roles:** full CRUD. A department or role can't be deleted while employees still
  belong to it.
- **Dashboard:** headcount, status breakdown, headcount by department, and recently added employees.
- **Validation and clear errors:** request validation (Jakarta Bean Validation) and a global exception handler
  that returns the same JSON error shape every time (`400` with per-field messages, `404`, `409` for
  duplicate e-mails/names).
- **API docs:** Swagger UI at `/swagger-ui.html` (springdoc-openapi).
- **Runs with zero setup:** uses an in-memory H2 database and sample data by default; switch to PostgreSQL
  with three environment variables.
- **Tested:** 14 integration tests (MockMvc) cover CRUD, validation, conflicts, search and paging.
- **Deployable:** Dockerfile, plus a Render blueprint that also provisions PostgreSQL.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation |
| Database | PostgreSQL (production), H2 (local default and tests) |
| Frontend | HTML, CSS, vanilla JavaScript (served by Spring Boot from `static/`) |
| Docs and ops | springdoc-openapi (Swagger UI), Spring Boot Actuator health check, Docker |
| Tests | JUnit 5, Spring Boot Test, MockMvc |

## Run it

You need Java 21 or later and Maven (or use the bundled `mvnw`).

```bash
git clone https://github.com/Tanvi958/Employee-Management-System.git
cd Employee-Management-System
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080 for the dashboard and http://localhost:8080/swagger-ui.html for the API docs.
The app starts with 5 departments, 6 roles and 12 sample employees (set `SEED_DATA=false` to start empty).

### Use PostgreSQL

```bash
createdb employee_db
export DB_URL=jdbc:postgresql://localhost:5432/employee_db
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
./mvnw spring-boot:run
```

Tables are created automatically (`spring.jpa.hibernate.ddl-auto=update`).

> **Upgrading from the first version?** The old `employees` table stored department and role as text.
> This version links employees to `departments` and `roles` tables, so drop the old table
> (`DROP TABLE employees;`) or start from a fresh database.

| Variable | Default | Meaning |
|---|---|---|
| `DB_URL` | in-memory H2 | JDBC URL of the database |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / empty | Database login |
| `PORT` | `8080` | HTTP port |
| `SEED_DATA` | `true` | Load sample data when the database is empty |
| `SHOW_SQL` | `false` | Log SQL statements |

### Run the tests

```bash
./mvnw test
```

### Docker

```bash
docker build -t employee-management .
docker run -p 8080:8080 employee-management
```

### Deploy on Render

Push the repository to GitHub, then in Render choose **New → Blueprint** and select it. `render.yaml` creates
the web service and a free PostgreSQL database and connects them.

## API

Base path: `/api/v1`

| Method | Endpoint | Description |
|---|---|---|
| GET | `/employees` | List employees. Query params: `q`, `departmentId`, `roleId`, `status`, `page` (0-based), `size` (max 100), `sort` (`id`, `firstName`, `lastName`, `emailId`, `salary`, `joiningDate`, `status`, `createdAt`), `direction` (`asc`/`desc`) |
| GET | `/employees/{id}` | Get one employee |
| POST | `/employees` | Create an employee (returns `201` and a `Location` header) |
| PUT | `/employees/{id}` | Update an employee |
| DELETE | `/employees/{id}` | Delete an employee |
| GET, POST | `/departments` | List (with employee counts) or create departments |
| GET, PUT, DELETE | `/departments/{id}` | Get, update or delete a department (`409` if employees still belong to it) |
| GET, POST | `/roles` | List (with employee counts) or create roles |
| GET, PUT, DELETE | `/roles/{id}` | Get, update or delete a role (`409` if employees still hold it) |
| GET | `/dashboard` | Totals, status counts, headcount per department, recently added |

Example request:

```bash
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Tanvi","lastName":"Nishad","emailId":"tanvi@example.com",
       "departmentId":1,"roleId":1,"salary":60000,"joiningDate":"2026-01-15","status":"ACTIVE"}'
```

Example error response (`400`):

```json
{
  "timestamp": "2026-11-20T15:20:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/employees",
  "fieldErrors": { "emailId": "Email must be a valid address" }
}
```

## Project structure

```
src/main/java/com/example/Employee_Management/
  controller/   REST controllers: employees, departments, roles, dashboard
  service/      business rules: unique e-mails/names, in-use checks, search, paging
  repository/   Spring Data JPA repositories and search specifications
  model/        JPA entities: Employee, Department, Role, EmployeeStatus
  dto/          request/response records (the API never exposes entities directly)
  exception/    custom exceptions and the global error handler
  config/       sample-data seeder, OpenAPI info
src/main/resources/
  static/       the web dashboard (index.html, css/, js/)
  application.properties
```
