# PrintDesk — Current Progress

**Last updated:** 2026-10-08  
**Overall status:** Backend implementation in progress  
**Current phase:** Phase 1 — Backend secure document intake (T001–T010 complete)

## 1. Current implementation baseline

| Module | Observed state | Not yet present |
|---|---|---|
| Backend | Java 21 / Spring Boot Maven module. Foundation includes Actuator, Spring Data JPA, Security, Validation, WebSocket, MySQL 8.4/Flyway dependencies, externalized datasource settings, Hibernate schema validation, test-scoped H2, RFC 9457-style errors, core entities/repositories/V1 schema, upload validation, private local storage, persisted document metadata, and PDF/image/DOCX page counting. | Upload/download HTTP endpoints, pricing/orders, auth policy, queue processing, agent APIs, admin operations, audit events. |
| Frontend | React/Vite app with a customer-flow UI prototype in `App.jsx`: sample upload progress, B&W/color, copies, paper size/orientation, local estimate, fake token confirmation, and global CSS/theme tokens. Existing dependencies include React, React Router, Zustand, Framer Motion, Axios, Vite, and ESLint. | Replace dummy document/progress/token/pricing with backend behavior; add token lookup, admin application, MUI dependency required by `Design.md`, API modules, auth/order/printer state, and UI tests. |
| Print Agent | No `print-agent/` module is present in the observed project structure. | Agent registration, WebSocket client, printer discovery, OS print adapter, job recovery and reporting. |
| Database / storage | MySQL 8.4 LTS target, environment-based datasource/Flyway configuration, private local storage adapter, and document metadata persistence are in place; V1 schema has passed H2 MySQL-mode validation. | Real MySQL 8.4 integration verification, local DB provisioning, HTTP upload/download endpoints, and retention handling. |
| Product behavior | SRS and UX design documents define intended Version 1 behavior; frontend contains a non-integrated customer-flow prototype. | No real file upload, persistent order/token, backend pricing, admin workflow, database, Print Agent, or physical printing flow is evidenced as implemented. |

The workspace scan did not find `frontend/src/services/api.js`; treat it as absent unless it is added later. The current environment also does not identify the workspace root as a Git repository, so this progress file does not claim a clean/dirty VCS status.

## 2. Documentation and planning status

- [x] Read the SRS and UX design guidance.
- [x] Record the implementation baseline from the existing backend and frontend scaffold.
- [x] Create [plan.md](./plan.md), with backend-first module sequencing, task breakdown, acceptance gates, and SRS-to-plan traceability.
- [x] Create [rules.md](./rules.md), with security, architecture, scope, and quality rules.
- [ ] Confirm open implementation decisions listed in `plan.md` §9.
- [x] Begin backend implementation; completed T001–T010.

## 3. Planned delivery status

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend foundation, data model, secure documents, pricing/orders, admin APIs, durable queue and agent contract | In progress — T001–T010 complete; next is upload/download endpoint wiring |
| 2 | Separate Java Print Agent, enrollment, discovery, OS printing, retry and recovery | Not started |
| 3 | Customer and administrator React application | Not started |
| 4 | End-to-end integration, real-printer acceptance, deployment and handover | Not started |

## 4. Validation evidence

- Initial baseline `./mvnw test` used Java 17 and failed because the project targets Java 21.
- Latest `./mvnw test` with `/usr/lib/jvm/java-21-openjdk-amd64`: **25 tests passed, 0 failed, 0 skipped**.
- `./mvnw dependency:tree -DskipTests` with the Maven wrapper resolved the declared Spring Boot 4.1.1 and MySQL dependencies successfully.
- With Flyway selected, `./mvnw test dependency:tree` under Java 21 passed; Spring Boot Flyway and MySQL Flyway artifacts resolved.
- The first Java 21 test run exposed that the newly enabled JPA auto-configuration needs a datasource URL. Added test-scoped H2 and set the smoke-test URL; this is for context startup only and is not evidence of MySQL compatibility.
- `./mvnw test` under Java 21 passes with Flyway applying V1 in H2 MySQL mode and Hibernate validating entity mappings.
- Actual MySQL 8.4 migration/integration execution has not yet been run.
- LibreOffice DOCX conversion was exercised against the installed local `soffice` executable; production installation/isolation remains an operational prerequisite.
- Upload service behavior is tested, but no product HTTP endpoint is wired yet; endpoint runtime probes are not applicable until T011.

## 5. Open decisions / risks

Track and resolve the implementation decisions in [plan.md](./plan.md) §9, especially:

1. Retention/deletion and malware-scanning policy; accepted formats are PDF, DOCX, JPG, and PNG, maximum 25 MB per file.
2. DOCX conversion is selected: LibreOffice headless rendering with a 30-second timeout; deployment must install and isolate the converter.
3. Local MySQL 8.4 development provisioning and production deployment target.
4. Admin and Print Agent credential provisioning/rotation.
5. Supported OS/printer combinations and confirmation semantics for print completion.
6. Retry/backoff and ambiguous print outcome policy.
7. Price rounding and token policy.
8. Frontend browser test runner and supported browser/accessibility targets.

## 7. Completed backend foundation batches

- **T001:** Added a centralized RFC 9457-style error handler for DTO validation, method constraint violations, and malformed request bodies. Client messages do not include submitted values or parser internals.
- **T002:** Added backend starter dependencies and constrained Actuator HTTP exposure to `health,info`; disabled default error-message, binding-error, and stack-trace disclosure.
- **T003:** Added `backend/README.md` documenting Java/Maven prerequisites and required datasource environment variables.
- **T004:** Selected Flyway; added its Spring Boot and MySQL support, required external datasource variables, and schema-validation/no-open-session-in-view settings.
- **T005–T007:** Selected MySQL 8.4 LTS, created core persistence entities and repositories, and added V1 Flyway schema. Migration + Hibernate mapping validation pass under H2 MySQL mode; real MySQL verification remains outstanding.
- **T008:** Added upload request/response DTOs and tested required-file validation. Accepted upload formats are selected but are not enforced until upload service implementation.
- **T009:** Added the local storage adapter with generated UUID keys, directory sharding, atomic writes, SHA-256 digests, streaming limit enforcement, path-key validation, and no-follow reads. Configured 25 MB file / 26 MB request limits.
- **T010:** Added content-signature and structural validation for PDF, DOCX, JPG, and PNG; PDFBox page counting; image decoding/page count; DOCX archive-bomb limits and LibreOffice-to-PDF page counting; sanitized metadata persistence; rejected-upload cleanup; and RFC 9457 errors for invalid, oversized, unavailable, and storage-failure cases.
- All current backend checks: 25 tests passed, including DB-backed PDF metadata persistence and a real local LibreOffice DOCX conversion.
- Added unit coverage for validation/malformed-request error shapes and retained the context-load smoke test.
- Local MySQL provisioning and actual MySQL-backed integration verification remain pending.

## 8. Scope reminders

- Payment processing is deliberately deferred from Version 1.
- The Print Agent, not a web browser, performs physical printing.
- Mark acceptance complete only after a real-printer test proves the supported deployment path does not open a browser preview/dialog.
