# Employee Management System

A full-stack employee management app: a secured **Spring Boot REST API** with a **web dashboard** for managing
employee records, departments and job roles. It follows a layered MVC design
(controller → service → repository) over **PostgreSQL** with Spring Data JPA (Hibernate), and uses
**Spring Security with JWT** and role-based access control.

## Features

- **Sign-in with JWT and three roles.** *Admin* can do everything. *HR* can create, edit, delete and import
  employees. *Viewer* can only read. Passwords are hashed with BCrypt. The API is stateless: every
  request carries a signed token.
- **Employees:** create, view, edit and delete, with department, role, phone, salary, joining date and status
  (Active / On leave / Inactive).
- **Search, filter, sort and paginate:** search by name or e-mail; filter by department, role or status;
  sort by name, salary, joining date or status.
- **Employee profile:** details, tenure, and the full change history of that employee.
- **Audit trail:** every create, update, delete and import is recorded with who did it, when, and exactly
  which fields changed (e.g. `salary: 90000 → 95000; status: ACTIVE → ON_LEAVE`). Admins get a filterable
  activity log.
- **CSV export and import:** export the filtered list; import new employees from a CSV file. Valid rows are
  added, and the rest are reported with their line number and reason. Exported cells are protected
  against spreadsheet formula injection.
- **Dashboard:** headcount, monthly payroll, average salary, hires per month (last 12 months), an
  employment-status chart, headcount and average salary by department, and recent activity.
- **Departments, roles and users:** full CRUD. A department or role can't be deleted while employees still use
  it. You can't delete your own account or remove the last admin.
- **Validation and clear errors:** Jakarta Bean Validation and a global exception handler, so every error
  returns the same JSON shape (`400` with per-field messages, `401`, `403`, `404`, `409`).
- **API docs:** Swagger UI at `/swagger-ui.html`, with an Authorize button for the JWT.
- **Runs with zero setup:** uses an in-memory H2 database, sample data and demo accounts by default; switch to
  PostgreSQL with environment variables.
- **Tested:** 26 integration tests (MockMvc) cover CRUD, validation, conflicts, search and paging, logins
  and role permissions, the audit trail, CSV import/export and the dashboard.
- **Deployable:** a Dockerfile, plus a Render blueprint that also provisions PostgreSQL and a JWT secret.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA (Hibernate), Bean Validation |
| Security | Spring Security, OAuth2 Resource Server (JWT, HS256), BCrypt, role-based access control |
| Database | PostgreSQL (production), H2 (local default and tests) |
| Frontend | HTML, CSS, vanilla JavaScript, SVG charts (served by Spring Boot from `static/`) |
| Docs and ops | springdoc-openapi (Swagger UI), Spring Boot Actuator health check, Docker, Render |
| Tests | JUnit 5, Spring Boot Test, MockMvc, Spring Security Test |

## Run it

You need Java 21 or later and Maven (or use the bundled `mvnw`).

```bash
git clone https://github.com/Tanvi958/Employee-Management-System.git
cd Employee-Management-System
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080 and sign in with a demo account (there are buttons for them on the sign-in page):

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | Admin: full access |
| `hr` | `hr123456` | HR: manage employees |
| `viewer` | `viewer123` | Viewer: read only |

The app starts with 5 departments, 6 roles and 20 sample employees. Set `SEED_DATA=false` to start empty;
then only the admin account is created.

> These demo passwords are for trying the app locally. Anywhere public, set `JWT_SECRET` and
> `ADMIN_PASSWORD`, and `SEED_DATA=false` if you don't want the demo HR and viewer accounts.

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
| `JWT_SECRET` | a development value | Key that signs login tokens (32+ characters). **Set it in production.** |
| `JWT_EXPIRY_MINUTES` | `480` | How long a login lasts |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | First admin, created when there are no users |
| `SEED_DATA` | `true` | Load sample data and the demo HR/viewer accounts when the database is empty |
| `PORT` | `8080` | HTTP port |
| `SHOW_SQL` | `false` | Log SQL statements |

### Run the tests

```bash
./mvnw test
```

### Docker

```bash
docker build -t employee-management .
docker run -p 8080:8080 -e JWT_SECRET=change-me-to-a-long-random-string-123456 employee-management
```

### Deploy on Render

Push the repository to GitHub, then in Render choose **New → Blueprint** and select it. `render.yaml` creates
the web service and a free PostgreSQL database, connects them, and generates a random `JWT_SECRET`.

## API

Base path: `/api/v1`. Every endpoint except `POST /auth/login` needs `Authorization: Bearer <token>`.

| Method | Endpoint | Who | Description |
|---|---|---|---|
| POST | `/auth/login` | anyone | `{username, password}` → `{token, expiresAt, user}` |
| GET | `/auth/me` | signed in | The current user |
| GET | `/employees` | signed in | List employees. Query params: `q`, `departmentId`, `roleId`, `status`, `page` (0-based), `size` (max 100), `sort` (`id`, `firstName`, `lastName`, `emailId`, `salary`, `joiningDate`, `status`, `createdAt`), `direction` (`asc`/`desc`) |
| GET | `/employees/{id}` | signed in | One employee |
| GET | `/employees/{id}/history` | signed in | Audit trail of one employee |
| GET | `/employees/export` | signed in | CSV of the employees matching the same filters as the list |
| GET | `/employees/import/template` | signed in | CSV template |
| POST | `/employees/import` | Admin, HR | Multipart field `file` → `{imported, failed, errors[{line, message}]}` |
| POST | `/employees` | Admin, HR | Create an employee (`201` + `Location` header) |
| PUT | `/employees/{id}` | Admin, HR | Update an employee |
| DELETE | `/employees/{id}` | Admin, HR | Delete an employee |
| GET | `/departments`, `/roles` | signed in | Lists, with employee counts |
| POST, PUT, DELETE | `/departments/**`, `/roles/**` | Admin | Manage departments and roles (`409` while in use) |
| GET | `/dashboard` | signed in | Totals, payroll, hires per month, per-department stats, recent activity |
| GET | `/audit` | Admin | Paged audit log; optional `entityType` (`EMPLOYEE`, `DEPARTMENT`, `ROLE`, `USER`) |
| GET, POST, PUT, DELETE | `/users/**` | Admin | Manage user accounts |

Example:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"hr","password":"hr123456"}' | jq -r .token)

curl -X POST http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"firstName":"Tanvi","lastName":"Nishad","emailId":"tanvi@example.com",
       "departmentId":1,"roleId":1,"salary":60000,"joiningDate":"2026-01-15","status":"ACTIVE"}'

curl -H "Authorization: Bearer $TOKEN" -F file=@new-hires.csv http://localhost:8080/api/v1/employees/import
```

CSV columns: `firstName,lastName,email,phone,department,role,salary,joiningDate,status`. Only
`firstName` and `email` are required; department and role must match existing names.

Error response (`400`):

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
  controller/   REST controllers: auth, employees (incl. CSV), departments, roles, users, audit, dashboard
  service/      business rules: uniqueness, in-use checks, search, CSV, audit diffs, user safety rules
  security/     Spring Security config, JWT issuing, current-user lookup
  repository/   Spring Data JPA repositories and search specifications
  model/        JPA entities: Employee, Department, Role, AppUser, AuditLog
  dto/          request/response records (the API never exposes entities directly)
  exception/    custom exceptions and the global error handler
  config/       sample-data and user seeders, OpenAPI info
src/main/resources/
  static/       the web dashboard (index.html, css/, js/)
  application.properties
```
