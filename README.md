# Employee Management System (Backend)
A RESTful API built with Spring Boot and PostgreSQL for managing employee records.

## 🛠 Tech Stack
- **Framework:** Spring Boot 3
- **Language:** Java 25
- **Database:** PostgreSQL
- **ORM:** Spring Data JPA (Hibernate)

## 🚀 How to Run
1. Clone the repository.
2. Update `src/main/resources/application.properties` with your DB credentials.
3. Run `mvn spring-boot:run` or start the app from your IDE.

## 📊 API Endpoints
- `GET /api/v1/employees` - List all employees
- `POST /api/v1/employees` - Create new employee
- `GET /api/v1/employees/{id}` - Get details by ID
- `PUT /api/v1/employees/{id}` - Update records
- `DELETE /api/v1/employees/{id}` - Remove employee
