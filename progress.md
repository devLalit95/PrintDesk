# PrintDesk — Current Progress

**Last updated:** 2026-10-08  
**Overall status:** Backend implementation in progress  
**Current phase:** Phase 1 — Backend foundation, customer orders, and administrator authentication (T001–T019 complete)

## 1. Current implementation baseline

| Module | Observed state | Not yet present |
|---|---|---|
| Backend | Java 21 / Spring Boot Maven module. Foundation includes Actuator, Spring MVC/Jackson, Spring Data JPA, stateless JWT Security, Validation, WebSocket, MySQL 8.4/Flyway dependencies, externalized datasource settings, Hibernate schema validation, test-scoped H2, RFC 9457-style errors, core entities/repositories/V1 schema, upload validation/storage, persisted document metadata, PDF/image/DOCX page counting, upload/download APIs, server-owned pricing quotes, order creation/status services, secure tokens, guarded order transitions, public pricing/order/status APIs, one-time admin bootstrap, BCrypt login, 30-minute JWTs, and role-gated routes. | Print Agent credential provider, queue processing, agent APIs, admin operations, audit events. |
| Frontend | React/Vite app with a customer-flow UI prototype in `App.jsx`: sample upload progress, B&W/color, copies, paper size/orientation, local estimate, fake token confirmation, and global CSS/theme tokens. Existing dependencies include React, React Router, Zustand, Framer Motion, Axios, Vite, and ESLint. | Replace dummy document/progress/token/pricing with backend behavior; add token lookup, admin application, MUI dependency required by `Design.md`, API modules, auth/order/printer state, and UI tests. |
| Print Agent | No `print-agent/` module is present in the observed project structure. | Agent registration, WebSocket client, printer discovery, OS print adapter, job recovery and reporting. |
| Database / storage | MySQL 8.4 LTS target, environment-based datasource/Flyway configuration, private local storage adapter, and document metadata persistence are in place; V1 schema has passed H2 MySQL-mode validation. | Real MySQL 8.4 integration verification, local DB provisioning, and retention handling. |
| Product behavior | Real document upload, format validation, page counting, metadata persistence, protected download routes, server-side quote calculation, persisted order creation, public token status lookup, guarded status transitions, and administrator login/JWT role checks are implemented and tested; frontend remains a non-integrated customer-flow prototype. | Print Agent, queue operations, admin operations, and physical printing are not implemented. |

The workspace scan did not find `frontend/src/services/api.js`; treat it as absent unless it is added later. The current environment also does not identify the workspace root as a Git repository, so this progress file does not claim a clean/dirty VCS status.

## 2. Documentation and planning status

- [x] Read the SRS and UX design guidance.
- [x] Record the implementation baseline from the existing backend and frontend scaffold.
- [x] Create [plan.md](./plan.md), with backend-first module sequencing, task breakdown, acceptance gates, and SRS-to-plan traceability.
- [x] Create [rules.md](./rules.md), with security, architecture, scope, and quality rules.
- [ ] Confirm open implementation decisions listed in `plan.md` §9.
- [x] Begin backend implementation; completed T001–T019.

## 3. Planned delivery status

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend foundation, data model, secure documents, pricing/orders, admin APIs, durable queue and agent contract | In progress — T001–T019 complete; next are admin operations and the persistent print queue (T020–T028) |
| 2 | Separate Java Print Agent, enrollment, discovery, OS printing, retry and recovery | Not started |
| 3 | Customer and administrator React application | Not started |
| 4 | End-to-end integration, real-printer acceptance, deployment and handover | Not started |

## 4. Validation evidence

- Initial baseline `./mvnw test` used Java 17 and failed because the project targets Java 21.
- Latest full `./mvnw test` with `/usr/lib/jvm/java-21-openjdk-amd64`: **68 tests passed, 0 failed, 0 skipped**.
- `./mvnw dependency:tree -DskipTests` with the Maven wrapper resolved the declared Spring Boot 4.1.1 and MySQL dependencies successfully.
- With Flyway selected, `./mvnw test dependency:tree` under Java 21 passed; Spring Boot Flyway and MySQL Flyway artifacts resolved.
- The first Java 21 test run exposed that the newly enabled JPA auto-configuration needs a datasource URL. Added test-scoped H2 and set the smoke-test URL; this is for context startup only and is not evidence of MySQL compatibility.
- `./mvnw test` under Java 21 passes with Flyway applying V1 in H2 MySQL mode and Hibernate validating entity mappings.
- Actual MySQL 8.4 migration/integration execution has not yet been run.
- LibreOffice DOCX conversion was exercised against the installed local `soffice` executable; production installation/isolation remains an operational prerequisite.
- MockMvc endpoint verification passes: public upload returns 201; anonymous downloads return 401; customers receive 403; unassigned agents receive 404; admins can stream documents successfully.
- Admin bearer-token validation and route role checks are implemented. Print Agent credentials are not yet issued/validated (T025), so do not deploy document downloads for agent use until that provider is configured.

## 5. Open decisions / risks

Track and resolve the implementation decisions in [plan.md](./plan.md) §9, especially:

1. Retention/deletion and malware-scanning policy; accepted formats are PDF, DOCX, JPG, and PNG, maximum 25 MB per file.
2. DOCX conversion is selected: LibreOffice headless rendering with a 30-second timeout; deployment must install and isolate the converter.
3. Local MySQL 8.4 development provisioning and production deployment target.
4. Selected admin authentication: one-time environment bootstrap credentials only if no ADMIN account exists; JWT access tokens expire after 30 minutes with re-login and no refresh token. Password reset/rotation and Print Agent credential lifecycle remain open. The allowed download roles and job-assignment rule are selected.
5. Supported OS/printer combinations and confirmation semantics for print completion.
6. Retry/backoff and ambiguous print outcome policy.
7. Rate rounding, token format, and customer page-range policy are resolved: final-total `HALF_UP`, 12-character secure globally unique token, and customer orders print the entire document; rate/total snapshots are retained at order creation.
8. Frontend browser test runner and supported browser/accessibility targets.
9. Public token-status API rate limiting is not configured and must be considered before production exposure.

## 7. Completed backend foundation batches

- **T001:** Added a centralized RFC 9457-style error handler for DTO validation, method constraint violations, and malformed request bodies. Client messages do not include submitted values or parser internals.
- **T002:** Added backend starter dependencies and constrained Actuator HTTP exposure to `health,info`; disabled default error-message, binding-error, and stack-trace disclosure.
- **T003:** Added `backend/README.md` documenting Java/Maven prerequisites and required datasource environment variables.
- **T004:** Selected Flyway; added its Spring Boot and MySQL support, required external datasource variables, and schema-validation/no-open-session-in-view settings.
- **T005–T007:** Selected MySQL 8.4 LTS, created core persistence entities and repositories, and added V1 Flyway schema. Migration + Hibernate mapping validation pass under H2 MySQL mode; real MySQL verification remains outstanding.
- **T008:** Added upload request/response DTOs and tested required-file validation. Accepted formats, content validation, and page counting are implemented through T010.
- **T009:** Added the local storage adapter with generated UUID keys, directory sharding, atomic writes, SHA-256 digests, streaming limit enforcement, path-key validation, and no-follow reads. Configured 25 MB file / 26 MB request limits.
- **T010:** Added content-signature and structural validation for PDF, DOCX, JPG, and PNG; PDFBox page counting; image decoding/page count; DOCX archive-bomb limits and LibreOffice-to-PDF page counting; sanitized metadata persistence; rejected-upload cleanup; and RFC 9457 errors for invalid, oversized, unavailable, and storage-failure cases.
- **T011–T012:** Added the public multipart upload endpoint, authenticated streamed download endpoint, administrator/assigned-agent authorization, stateless route security, and endpoint/security tests. Spring-generated development credentials and HTTP Basic are disabled.
- **T013:** Added server-owned pricing quotes using the active persisted per-page rate, validated positive page/copy counts, guarded total-page multiplication overflow, and returned the per-page rate and calculated total for an order-time snapshot. The final amount is rounded to two decimal places with `HALF_UP`; INR is enforced because orders currently persist INR only. Tests cover normal multiplication, rounding, invalid counts, overflow, missing active rates, and unsupported currency.
- **T014:** Added order request/response DTOs, all-page customer order creation, 12-character cryptographically random globally unique tokens using an ambiguity-reduced alphabet, persisted price/rate snapshots, public token status lookup service, explicit ProblemDetail mappings, and guarded order-state transitions. Integration tests verify H2 persistence, token format, snapshot immutability after a rate update, and status progression. Page-range settings remain admin-only.
- **T015:** Added public estimate (`POST /api/print-orders/estimate`), order creation (`POST /api/print-orders`), and token status (`GET /api/print-orders/{token}`) endpoints. Added stateless public-route authorization, request validation, documented request/response behavior, and MockMvc checks for 200/201/400/404 outcomes.
- **T016:** Added focused pricing, token collision, order validation, amount-tampering, and lifecycle-transition coverage. The HTTP integration test sends forged per-page/total amounts and confirms the persisted order still uses the server-calculated rate.
- **T017:** Added BCrypt-based admin credential verification, timing-equalized generic login failures, one-time environment bootstrap, base64 HMAC key validation, and signed 30-minute HS256 JWT access-token issuance.
- **T018:** Added stateless bearer-token decoding with issuer/expiry validation, role-based admin/operator/agent route gates, and consistent non-sensitive 401/403 Problem Details responses.
- **T019:** Added tests for bootstrap idempotence and invalid setup, generic login failures, valid JWT claims and expiry, authenticated admin routes, and denied role access.
- All current backend checks: 68 tests passed, including pricing and order behavior, H2-backed order/document persistence, real local LibreOffice DOCX conversion, and MockMvc upload/download/order/admin-auth flows.
- Added unit coverage for validation/malformed-request error shapes and retained the context-load smoke test.
- Local MySQL provisioning and actual MySQL-backed integration verification remain pending.

## 8. Scope reminders

- Payment processing is deliberately deferred from Version 1.
- The Print Agent, not a web browser, performs physical printing.
- Mark acceptance complete only after a real-printer test proves the supported deployment path does not open a browser preview/dialog.
