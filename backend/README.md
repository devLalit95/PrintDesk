# Backend

Spring Boot backend module for PrintDesk. The application targets Java 21 and connects to MySQL using environment-based configuration. Flyway owns schema changes; Hibernate validates mappings against the migrated schema and does not create or update production tables.

## Prerequisites

- Java 21
- The Maven wrapper included in this module
- A MySQL instance and an already-created application database
- LibreOffice installed on the backend host for DOCX page-count conversion (`soffice` by default)

The selected database target is MySQL 8.4 LTS. The runtime JDBC driver version is managed by Spring Boot dependency management.

## Configuration

Set these environment variables before starting the application:

| Variable | Required | Purpose |
|---|---:|---|
| `DB_URL` | Yes | JDBC URL for the application database, for example `jdbc:mysql://localhost:3306/printdesk` |
| `DB_USERNAME` | Yes | Database user with the privileges required to apply Flyway migrations and run the application |
| `DB_PASSWORD` | Yes | Database password; keep it out of source control and logs |
| `PRINTDESK_STORAGE_ROOT` | No | Private directory for uploaded files; defaults to `${user.home}/.printdesk/storage` |
| `LIBREOFFICE_COMMAND` | No | LibreOffice executable path; defaults to `soffice` |
| `SPRING_PROFILES_ACTIVE` | No | Spring profile to activate when an environment-specific profile is introduced |

Create the database before startup. Flyway applies versioned scripts from `src/main/resources/db/migration/`; Hibernate uses `ddl-auto=validate` to check mappings without changing the schema. The initial migration is added with the persistence model.

Uploads are limited to 25 MB per file and the request body limit is 26 MB. The backend validates the file signature and contents rather than trusting the browser-provided media type. It counts PDF pages with PDFBox, treats validated JPG/PNG images as one page, and converts validated DOCX files to PDF with LibreOffice before counting pages. DOCX conversion has a 30-second timeout; uploads that cannot be converted are not accepted without a page count. LibreOffice must be installed and runnable by the backend service account. Run the service with least filesystem/network privileges and isolate the conversion host in production.

Image decoding is limited to 25 million pixels. DOCX archives are limited to 100 MiB expanded size and 10,000 entries before conversion. Files are stored outside the source tree with generated opaque keys; retention/deletion scheduling is not enabled until a retention policy is approved.

The current context-load smoke test uses test-scoped H2 and does not verify MySQL-specific migrations or SQL behavior. MySQL-backed integration tests remain required before considering persistence verified.

## Build and test

Use the Java 21 executable explicitly if the shell default points to an older JDK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```
