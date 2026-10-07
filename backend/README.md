# Backend

Spring Boot backend module for PrintDesk. The application targets Java 21 and connects to MySQL using environment-based configuration. Flyway owns schema changes; Hibernate validates mappings against the migrated schema and does not create or update production tables.

For frontend request/response contracts and the implemented-versus-planned API inventory, see the project [API documentation](../apis_docs.md).

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
| `PRINTDESK_JWT_SECRET` | Yes | Base64-encoded HMAC key containing at least 32 random bytes; required at startup |
| `PRINTDESK_JWT_ISSUER` | No | Expected JWT issuer; defaults to `https://printdesk.local` and must be set to the deployed issuer in production |
| `PRINTDESK_ADMIN_BOOTSTRAP_USERNAME` | Conditional | Initial administrator username, used only while the database has no `ADMIN` account |
| `PRINTDESK_ADMIN_BOOTSTRAP_PASSWORD` | Conditional | Initial administrator password, used only while the database has no `ADMIN` account; must contain 12–72 UTF-8 bytes |
| `SPRING_PROFILES_ACTIVE` | No | Spring profile to activate when an environment-specific profile is introduced |

Create the database before startup. Flyway applies versioned scripts from `src/main/resources/db/migration/`; Hibernate uses `ddl-auto=validate` to check mappings without changing the schema. The initial migration is added with the persistence model.

Before first startup, set the JWT key and both bootstrap credentials. The application creates one BCrypt-hashed `ADMIN` account only when no administrator exists; bootstrap credentials are not reapplied after that. `/api/admin/login` accepts a username and password and returns a signed HS256 bearer token valid for 30 minutes. There is no refresh token; administrators must sign in again after expiry. Store the signing key and bootstrap password in a secret manager, never in source control. Startup fails if the JWT signing key is missing, malformed, or shorter than 256 bits.

Uploads are limited to 25 MB per file and the request body limit is 26 MB. The backend validates the file signature and contents rather than trusting the browser-provided media type. It counts PDF pages with PDFBox, treats validated JPG/PNG images as one page, and converts validated DOCX files to PDF with LibreOffice before counting pages. DOCX conversion has a 30-second timeout; uploads that cannot be converted are not accepted without a page count. LibreOffice must be installed and runnable by the backend service account. Run the service with least filesystem/network privileges and isolate the conversion host in production.

Image decoding is limited to 25 million pixels. DOCX archives are limited to 100 MiB expanded size and 10,000 entries before conversion. Files are stored outside the source tree with generated opaque keys; retention/deletion scheduling is not enabled until a retention policy is approved.

## Document API access

- `POST /api/documents/upload` is public and accepts a multipart `file` part. A successful response is `201 Created` with server-detected type, size, page count, and opaque document ID.
- `POST /api/admin/login` is public. All other `/api/admin/**` routes require a valid bearer token with an administrator or operator role.
- `GET /api/documents/{documentId}` streams the file only to an authenticated administrator or the authenticated Print Agent assigned to a job for that document. An unrelated agent receives `404` to avoid revealing document existence.
- Protected requests use `Authorization: Bearer <access-token>`. JWT validation checks the HMAC signature, algorithm, expiry, and configured issuer. Authentication and authorization failures return non-sensitive Problem Details responses.
- The route boundary is stateless and does not use Spring's generated development password or HTTP Basic. Print Agent credential issuance/validation is still pending (T025); do not expose download routes in a deployed environment until that provider is configured.

## Customer pricing and order APIs

- `POST /api/print-orders/estimate` accepts `documentId`, `printType` (`BLACK_AND_WHITE` or `COLOR`), and positive `copies`; it returns a server-calculated INR estimate using the uploaded document's page count and the active rate.
- `POST /api/print-orders` accepts those fields plus `paperSize` (`A4`, `A3`, `Letter`, or `Legal`), `orientation` (`portrait` or `landscape`), and optional `doubleSided`. Customer orders include every uploaded page; page-range controls remain admin-only. The backend creates a pending order and snapshots the active rate and total.
- `GET /api/print-orders/{token}` returns limited order status and summary data. Tokens are 12-character cryptographically random uppercase bearer secrets; do not log them and disclose them only to the submitting customer or authorized administrator.
- These customer routes are public. Public status lookup rate limiting is not yet configured and should be addressed before production exposure.

The current context-load smoke test uses test-scoped H2 and does not verify MySQL-specific migrations or SQL behavior. MySQL-backed integration tests remain required before considering persistence verified.

## Build and test

Use the Java 21 executable explicitly if the shell default points to an older JDK:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw test
```
