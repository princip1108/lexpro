# LexPro Backend

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven
- MyBatis-Plus 3.5.17
- PostgreSQL

The Maven project is in `backend/lexpro-backend`.

## Required environment

The local DBeaver connection uses database `lexpro` on `127.0.0.1:5432`. Configure the following environment variable in the IntelliJ run configuration:

```text
LEXPRO_DB_PASSWORD=<your PostgreSQL password>
```

Optional overrides:

```text
LEXPRO_DB_URL=jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro
LEXPRO_DB_USERNAME=postgres
```

Do not put a real password in `application.properties` or commit it to Git.

## Run in IntelliJ IDEA

1. Open `backend/lexpro-backend` as the Maven project.
2. Select Java 21.
3. Open **Run -> Edit Configurations** and add `LEXPRO_DB_PASSWORD` under environment variables.
4. Run `com.lexpro.lexprobackend.LexproBackendApplication`.

## Command line

With Java 21 available in `JAVA_HOME`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## Current endpoints

```http
GET /api/health
GET /api/health/database
GET /api/v1/users
```

The user endpoint is temporarily unauthenticated and will be protected during the authentication milestone.

## Expected database check

`GET /api/health/database` should return database `lexpro` and table count `32`.

