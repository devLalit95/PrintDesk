# PrintDesk — Current Progress

**Last updated:** 2026-10-08  
**Overall status:** Backend foundation, customer flows, and selected admin order management implemented; queue/agent/printer administration remain pending  
**Current phase:** Phase 3 — customer upload/order flows and admin order-management dashboard delivered in the selected scope; T045 and broader integration remain pending

## 1. Current implementation baseline

| Module | Observed state | Not yet present |
|---|---|---|
| Backend | Java 21 / Spring Boot Maven module. Foundation includes Actuator, Spring MVC/Jackson, Spring Data JPA, stateless JWT Security, Validation, WebSocket, MySQL 8.4/Flyway dependencies, externalized datasource settings, Hibernate schema validation, test-scoped H2, RFC 9457-style errors, core entities/repositories/V1 schema, upload validation/storage, persisted document metadata, PDF/image/DOCX page counting, upload/download APIs, server-owned pricing quotes, order creation/status services, secure tokens, guarded order transitions, public pricing/order/status APIs, one-time admin bootstrap, BCrypt login, 30-minute JWTs, role-gated routes, and protected order-management statistics/list/detail/cancellation with audit records. | Print Agent credential provider, durable queue processing, agent APIs, printer/rate administration, and print/retry operations. |
| Frontend | React/Vite app with MUI theme and routed customer/admin screens; real document upload/progress, server estimate, order submission/token confirmation, token status lookup, admin login with in-memory JWT session expiry, protected order-management dashboard, search/status filters, pagination, details/history, document download, confirmed pending cancellation, centralized API client, and privacy-conscious structured diagnostics. | Printer/agent/pricing management and print/retry UI await backend/agent support; broader responsive/accessibility/browser acceptance coverage remains. |
| Print Agent | No `print-agent/` module is present in the observed project structure. | Agent registration, WebSocket client, printer discovery, OS print adapter, job recovery and reporting. |
| Database / storage | MySQL 8.4 LTS target, environment-based datasource/Flyway configuration, private local storage adapter, and document metadata persistence are in place; V1 schema has passed H2 MySQL-mode validation. | Real MySQL 8.4 integration verification, local DB provisioning, and retention handling. |
| Product behavior | Real document upload, format validation, page counting, metadata persistence, protected download routes, server-side quote calculation, persisted order creation, public token status lookup, guarded status transitions, administrator login/JWT role checks, and API-backed customer/admin order-management flows are implemented and tested. | Print Agent, durable queue operations, printer/rate administration, print/retry actions, and physical printing are not implemented. |

The current environment does not identify the workspace root as a Git repository, so this progress file does not claim a clean/dirty VCS status.

## 2. Documentation and planning status

- [x] Read the SRS and UX design guidance.
- [x] Record the implementation baseline from the existing backend and frontend scaffold.
- [x] Create [plan.md](./plan.md), with backend-first module sequencing, task breakdown, acceptance gates, and SRS-to-plan traceability.
- [x] Create [rules.md](./rules.md), with security, architecture, scope, and quality rules.
- [ ] Confirm remaining open implementation decisions listed in `plan.md` §9.
- [x] Begin backend implementation; completed T001–T019.

## 3. Planned delivery status

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend foundation, data model, secure documents, pricing/orders, admin APIs, durable queue and agent contract | In progress — T001–T020 complete; pending-only cancellation is implemented, while printer/rate administration, print/retry, queue, and agent work remain pending/partial (T021–T028) |
| 2 | Separate Java Print Agent, enrollment, discovery, OS printing, retry and recovery | Not started |
| 3 | Customer and administrator React application | In progress — customer upload/order flow and selected admin dashboard/search/details/download/cancel are implemented; printer/rate UI and physical print actions remain |
| 4 | End-to-end integration, real-printer acceptance, deployment and handover | Not started |

## 4. Validation evidence

- Initial baseline `./mvnw test` used Java 17 and failed because the project targets Java 21.
- Latest full `./mvnw test` with `/usr/lib/jvm/java-21-openjdk-amd64`: **69 tests passed, 0 failed, 0 skipped**.
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
- Latest full backend check: **69 tests passed, 0 failed, 0 skipped**, including pricing and order behavior, H2-backed persistence, local LibreOffice DOCX conversion, MockMvc upload/download/order/auth flows, and admin order-management authorization/query/cancellation behavior.
- Added unit coverage for validation/malformed-request error shapes and retained the context-load smoke test.
- Local MySQL provisioning and actual MySQL-backed integration verification remain pending.
- Frontend validation after dashboard integration: `npm test -- --run` — **12 tests passed** across 6 files; `npm run lint` passed; `npm run build` passed. Browser smoke checks loaded the customer page and verified unauthenticated `/admin` redirects to login; `/actuator/health` returned `UP`.
- The production build exits successfully with a non-blocking ~743 kB minified JavaScript chunk warning and a Vite notice that OXC configuration takes precedence over the configured esbuild option.
- Production dependency audit (`npm audit --omit=dev`) reports **0 vulnerabilities**. The earlier full install audit reported 3 development/transitive dependency findings (1 moderate, 2 critical); these were not remediated in this UI task.

## 8. Scope reminders

- Payment processing is deliberately deferred from Version 1.
- The Print Agent, not a web browser, performs physical printing.
- Mark acceptance complete only after a real-printer test proves the supported deployment path does not open a browser preview/dialog.

## 9. Frontend implementation batches

- **T037:** Added MUI/Emotion dependencies, shared MUI theme, routed application shell, and responsive shared styling.
- **T038:** Added the Axios client, request identifiers, Problem Details handling, customer/admin API modules, Vite API proxy, and structured privacy-conscious logging.
- **T040–T042:** Replaced prototype interactions with file upload and progress, server pricing estimates, order creation, confirmation-token copy, and token-based order lookup.
- **T043:** Added admin login, guarded admin navigation, in-memory token handling, manual sign-out, and automatic session clearing at JWT expiration. No unsupported dashboard APIs are presented as implemented.
- **T047 (partial):** Frontend tests cover upload/estimate/order creation, invalid file-type rejection, token lookup, admin login, API error handling, logger privacy, token expiration, admin dashboard/detail/cancellation, and dashboard load failure. Printer/agent/pricing UI coverage awaits T045.
- Production build succeeds with the warnings recorded in §7; code splitting can be considered separately.

## 10. Admin order dashboard batch

- **T020:** Added authenticated dashboard statistics, paginated/searchable/status-filtered order listing, order detail and print-attempt history projections. Requests are bounded and responses exclude storage keys, digests, and credential material.
- **T022 (partial):** Added audited cancellation for `PENDING` orders with lifecycle conflict handling. Print/retry are intentionally not exposed until durable queue and authenticated Print Agent work is complete.
- **T023 (partial):** Added integration coverage for admin role protection, list filtering, details, statistics, pending cancellation, repeated-cancel conflict, concurrent-update conflict, and page-size validation. Printer/rate and print/retry tests remain deferred with those features.
- **T044 (complete in the selected order-management scope):** Replaced the admin placeholder with statistics cards, search/status filters, pagination, details/history, authorized document download, and confirmed cancellation. The dashboard explicitly states printing/retry are unavailable; it never marks an order as printing.
- Customer upload control now exposes a single browse button, supports keyboard activation, retains drop support, and permits selecting the same file again after validation.
- **T046 (partial):** Kept the visually clipped native file input out of the accessibility tree/tab order while retaining keyboard-operable Browse files activation; added all-status statistics and an explicit dashboard-data failure state. Whole-application accessibility review remains.
- The customer estimate/order API requires active print rates. No rate-management API or pricing values were added in this order-management-only scope, so customer estimates/orders still require rates to be configured separately.
- Admin API contract and frontend integration are documented in [apis_docs.md](./apis_docs.md).
