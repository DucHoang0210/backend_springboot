# DRM Backend - Spring Boot 🚀

This is the backend service for the Digital Rights Management (DRM) and Face Recognition System. Built with **Spring Boot**, it provides robust APIs for user authentication, copyright registration, and system management.

## 🛠️ Technologies Used

- **Java 21**
- **Spring Boot** (Web, Data JPA, Security)
- **PostgreSQL** (Database)
- **JWT (JSON Web Tokens)** (Authentication & Authorization)
- **Lombok** (Boilerplate code reduction)
- **Swagger / OpenAPI** (API Documentation)
- **Maven** (Build Tool)

## 📁 Project Structure

```text
backend-springboot/
├── src/
│   ├── main/java/com/example/  # Core logic (Controllers, Services, Models, Security)
│   └── main/resources/         # Configuration files (application.properties)
├── pom.xml                     # Maven dependencies
└── README.md                   # Project Documentation
```

## ⚙️ Prerequisites

- **JDK 21** or higher
- **Maven** (Optional, project includes Maven Wrapper `./mvnw`)
- **PostgreSQL** (Ensure PostgreSQL is running locally or remotely)

## 🚀 How to Run Locally

1. **Configure Database Credentials:**
   Open `src/main/resources/application.properties` (or `.yml`) and update your PostgreSQL connection settings:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/your_database_name
   spring.datasource.username=postgres
   spring.datasource.password=your_password
   ```

2. **Clean and Build:**
   ```bash
   # On Windows
   mvnw.cmd clean install
   
   # On Mac/Linux
   ./mvnw clean install
   ```

3. **Start the Application:**
   ```bash
   # On Windows
   mvnw.cmd spring-boot:run
   
   # On Mac/Linux
   ./mvnw spring-boot:run
   ```
   The server will start at `http://localhost:8080`.

## 📖 API Documentation

This project uses Springdoc OpenAPI to generate interactive API documentation. Once the server is running, you can explore the APIs here:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## 🔒 Security Flow
- The API uses **Stateless JWT Authentication**.
- Clients must first authenticate via the login endpoint to receive a Token.
- Subsequent requests to protected routes must include the token in the HTTP Header: `Authorization: Bearer <your_token>`.

---
*Maintained by [@DucHoang0210](https://github.com/DucHoang0210)*
