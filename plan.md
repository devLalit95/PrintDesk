# Online Document Printing System — Implementation Plan

**Status:** Proposed; implementation has not started  
**Last updated:** 2026-10-07  
**Source requirements:** [SRS.md](./SRS.md)  
**UX guidance:** [Design.md](./Design.md)

## 1. Goal and delivery approach

Deliver the Version 1 online document printing workflow: a customer uploads a supported document, configures printing, sees a server-verified estimate, submits an order and receives a token; an administrator reviews and queues the order; an authorized local Print Agent sends it to a configured printer without a browser print dialog and reports the outcome.

Implementation is **backend-first**. Establish the persistent domain model, business rules, secure file access, and REST/WebSocket contracts before building the agent and connecting the UI. Keep the backend, React frontend, and Java Print Agent as separate modules. Integrate them through versioned, documented contracts rather than duplicating business rules.

The plan covers the SRS Version 1 scope. Payment processing, customer profiles, delivery, messaging, multi-branch operation, OCR, and advanced analytics remain out of scope. Payment-related data must not be added to the initial print workflow; preserve an extension seam only.

## 2. Requirement IDs used by this plan

The SRS does not assign `REQ-XXX` identifiers. For traceability below, this plan assigns stable aliases `REQ-001`–`REQ-018` to the SRS requirements. These aliases do not replace or edit the source SRS.

| ID | Requirement summarized from the SRS |
|---|---|
| REQ-001 | Accept document/image uploads; validate type, size, integrity, and use safe names. |
| REQ-002 | Determine page count when the format permits and retain document metadata separately from file bytes. |
| REQ-003 | Configure B&W/color, copies, and supported advanced print options. |
| REQ-004 | Configure rates and calculate totals on the server; show an estimate to the customer. |
| REQ-005 | Persist print orders and generate unique customer-facing tokens. |
| REQ-006 | Let a customer retrieve an order’s status and confirmation using its token. |
| REQ-007 | Authenticate administrators and enforce role-based access. |
| REQ-008 | Provide admin order lists/details, search/filter, history, and basic dashboard statistics. |
| REQ-009 | Let an admin request printing, retry eligible failures, and cancel eligible pending jobs. |
| REQ-010 | Authenticate/register the Print Agent; deliver jobs and configuration and receive status over REST/WebSocket. |
| REQ-011 | Discover/configure printers and select a default printer. |
| REQ-012 | Process print jobs sequentially and retain queue/job state across restarts. |
| REQ-013 | Print through the local operating-system subsystem without browser preview/dialog or manual printer selection. |
| REQ-014 | Report failures, preserve useful error information, reconnect the agent, and allow controlled retry. |
| REQ-015 | Protect documents and administrative actions with validation, authorization, access control, and audit logging. |
| REQ-016 | Meet the stated performance and reliability needs, including upload feedback and large-file/queue handling. |
| REQ-017 | Keep frontend, backend, and agent separate and allow multiple agents in a future deployment. |
| REQ-018 | Keep payment integration separate and deferred; do not implement payment in Version 1. |

## 3. Technical context and decisions

| Area | Plan |
|---|---|
| Backend | Existing Java 21 / Spring Boot Maven module. Add only required Spring MVC, persistence, validation, security, WebSocket, and database capabilities. |
| Database | MySQL 8.4 LTS, selected. Store order and document metadata and state in the database; store document bytes in managed file storage and persist an opaque storage key. |
| File storage | Start with a backend-managed local storage adapter for development/small deployment, outside public web roots. Define an adapter boundary for future object storage. Do not return filesystem paths to clients. |
| Agent | Separate Java 21 Maven application under `print-agent/`. Use an injectable printer boundary so queue and status logic can be tested without a physical printer. |
| Agent transport | WebSocket for job notification; authenticated REST for registration/configuration, secure document retrieval, health/status updates, and job status. |
| Frontend | Existing React + Vite app; use the SRS/Design stack (MUI, React Router, Zustand, Framer Motion, Axios). Current dependencies already include React, Vite, Router, Zustand, Framer Motion, and Axios; MUI is not currently installed. |
| Currency and price | Display INR as shown in the SRS/Design. Backend owns all arithmetic and rate snapshots. Use decimal-safe money values; never trust a client-submitted total. |
| Tests | Backend unit and MySQL-backed integration tests; frontend lint/build and component/API tests; agent unit tests with a fake printer and a physical-printer acceptance test. Add browser E2E tooling only after selecting and approving the test runner. |
| API contract | Preserve the SRS paths as the initial contract baseline. Define request/response schemas, status/error responses, pagination, and authentication behavior before implementing the frontend integration. |

## 4. Design and implementation constraints

1. **Backend owns truth.** Revalidate uploads, calculate all final prices, generate tokens, enforce state transitions, and authorize every document/job operation on the server.
2. **Separate metadata and bytes.** Persist document metadata and storage key; never store file contents as an ordinary database blob or expose a path.
3. **Durable, serialized printing.** Persist queue/job state and allow only one active job per configured printer. Recover safely after backend/agent restart; do not infer that a job printed when its outcome is unknown.
4. **Agent is the hardware boundary.** Only an authenticated agent may claim/execute assigned jobs. The browser must never invoke a local print dialog as a substitute for the agent.
5. **Explicit state transitions.** Centralize order/job transitions and reject invalid transitions. Keep order status and execution-job status distinct if needed to represent retries without erasing history.
6. **Secure by default.** Enforce role authorization, upload limits and allowlists, opaque IDs, access-controlled downloads, agent credentials, input validation, and admin audit events. Do not place credentials or secrets in source control.
7. **Keep modules independent.** Shared concepts are communicated by API/event contracts, not by importing backend classes into frontend or agent code.
8. **Out of scope stays out.** Do not add payment collection, wallets, refunds, customer account profiles, SMS/WhatsApp, OCR, or multi-branch features to Version 1.
9. **Follow the design brief without blocking usability.** Keep mobile upload usable, admin controls workstation-friendly, status accessible without color alone, and animation subtle. Use the existing CSS tokens/theme rather than scattered hard-coded component styles.
10. **Configuration is externalized.** Database credentials, JWT/agent secrets, storage roots, CORS origins, upload limits, and printer defaults come from environment/configuration, not committed secrets.

## 5. Implementation phases

Order is intentional: complete backend data and contract foundations first; then deliver an independently testable Print Agent; then implement customer/admin experiences against the stable API; finally verify the end-to-end workflow.

### Phase 1 — Backend foundation and domain

#### 1.1 Backend application foundation
- **Requirements:** REQ-005, REQ-015, REQ-017
- **Description:** Establish package boundaries, runtime profiles, error response format, configuration conventions, and persistence/test dependencies in `backend/pom.xml` and `backend/src/main/resources/`. Keep Java 21 and the existing Spring Boot version unless a verified compatibility issue requires a separately reviewed change.

#### 1.2 Core persistence model
- **Requirements:** REQ-002, REQ-005, REQ-008, REQ-011, REQ-012, REQ-014, REQ-015
- **Description:** Define entities, repositories, constraints, and schema migration strategy for documents, print orders, print jobs, admins, printers, agents, print rates, and audit events. Apply unique constraints to tokens and agent identity. Preserve job attempts and timestamps. Decide migration tooling before schema work; avoid relying on implicit production schema creation.

#### 1.3 Secure document intake and storage
- **Requirements:** REQ-001, REQ-002, REQ-015, REQ-016
- **Description:** Implement multipart upload, server-side allowlist/size/integrity validation, safe generated names, storage adapter, metadata persistence, protected retrieval, and format-aware page-count detection. Do not expose arbitrary paths. Return upload progress-compatible responses and actionable validation errors.

#### 1.4 Pricing and print-order domain
- **Requirements:** REQ-003, REQ-004, REQ-005, REQ-006, REQ-015
- **Description:** Implement print preference validation, rate lookup, decimal-safe server-side estimate/final calculation, a rate snapshot per order, total-page calculation, unique token generation, order creation, and token-based status lookup. Validate copy count/page range and prevent stale or client-tampered prices.

#### 1.5 Admin identity and authorization
- **Requirements:** REQ-007, REQ-015
- **Description:** Add admin login, credential handling, signed-token/session policy, and role authorization for admin routes. Define initial admin provisioning without shipping a default password. Protect sensitive failures and audit authentication/administrative actions as appropriate.

#### 1.6 Admin operations and configuration APIs
- **Requirements:** REQ-004, REQ-008, REQ-009, REQ-011, REQ-015
- **Description:** Implement order list/detail/search/filter/history/statistics, cancel and print-intent operations, printer CRUD/default selection, and public/admin pricing APIs. Validate filters and pagination and ensure all privileged routes require authorization.

#### 1.7 Durable print queue and agent contract
- **Requirements:** REQ-009, REQ-010, REQ-012, REQ-014, REQ-017
- **Description:** Implement durable job creation/claim/status transitions, sequential dispatch per printer, retry/cancel guards, agent registration/authentication/configuration, WebSocket notification, authenticated file delivery, heartbeat/status endpoints, reconnect-safe delivery, and API contract documentation. Make claim/acknowledgement idempotent to avoid duplicate execution after reconnect.

### Phase 2 — Java Print Agent

#### 2.1 Agent scaffold and secure enrollment
- **Requirements:** REQ-010, REQ-014, REQ-017
- **Description:** Create the standalone Maven application in `print-agent/`; load configuration externally; register/authenticate using a unique agent identity and secret; maintain heartbeat and reconnect behavior; never accept unauthenticated public print commands.

#### 2.2 Printer discovery and selection
- **Requirements:** REQ-011, REQ-013
- **Description:** Implement host printer discovery and report capabilities to the backend. Resolve the configured printer identifier to an installed printer and surface unavailable/unsupported states before execution.

#### 2.3 Job download, validation, and physical print
- **Requirements:** REQ-010, REQ-012, REQ-013, REQ-014
- **Description:** Receive an authorized job notification, retrieve its document over authenticated transport, validate job/options, submit it through the operating-system print subsystem with configured options, and report state. Put the platform-specific printing behind a testable adapter. Never fall back to browser printing.

#### 2.4 Recovery, status, and safe retries
- **Requirements:** REQ-009, REQ-012, REQ-014
- **Description:** Report queued/printing/printed/failed transitions with error details; recover the connection and resume eligible queued work after restart; prevent duplicate printing when execution outcome is ambiguous; support backend-authorized retry with an auditable attempt record.

### Phase 3 — React customer and administrator application

#### 3.1 UI foundation, routing, and shared API layer
- **Requirements:** REQ-006, REQ-007, REQ-008, REQ-017
- **Description:** Align frontend dependencies with the approved design (add MUI if implementation proceeds), create responsive theme/layout, customer/admin route boundaries, centralized Axios client and error handling, and appropriately scoped Zustand stores. Keep temporary form state local.

#### 3.2 Customer upload and print configuration
- **Requirements:** REQ-001, REQ-002, REQ-003, REQ-004
- **Description:** Evolve the existing customer-flow prototype into a mobile-friendly, API-backed flow. Replace the hard-coded sample document and simulated upload with real file selection/upload, validation feedback, progress, returned metadata/page count, and server-backed estimates. Preserve/refine the existing B&W/color, copies, paper-size, orientation, and summary interactions; treat client estimates as advisory.

#### 3.3 Order submission, token confirmation, and status lookup
- **Requirements:** REQ-005, REQ-006
- **Description:** Replace the prototype's local fake token/confirmation with validated API order submission and server-generated token/order summary. Preserve token copy and add token lookup/status using the API. Show loading, empty, invalid-token, and recoverable network states.

#### 3.4 Admin login, dashboard, and order actions
- **Requirements:** REQ-007, REQ-008, REQ-009
- **Description:** Build protected admin navigation, dashboard statistics, searchable/filterable/paginated order table and details, status indicators, print action, retry for eligible failures, cancellation for eligible pending work, and print history. Require explicit accessible feedback for accepted/rejected actions.

#### 3.5 Printer, pricing, and agent management
- **Requirements:** REQ-004, REQ-008, REQ-010, REQ-011
- **Description:** Add printer discovery/configuration/default selection, agent online/offline status, pricing management, and operational empty/loading/error states. Restrict writes to authorized administrators and refresh data after successful updates.

### Phase 4 — Integration, verification, and operational readiness

#### 4.1 Contract and end-to-end validation
- **Requirements:** REQ-001–REQ-017
- **Description:** Verify upload → estimate → order/token → admin queue → agent → print → status update; verify token status lookup, authorization/isolation, sequential queue, cancellation, failed-job retry, and recovery. Use a fake printer for deterministic automated tests, then perform a hardware acceptance test on each supported deployment OS/printer combination.

#### 4.2 Deployment/configuration and handover
- **Requirements:** REQ-015, REQ-016, REQ-017, REQ-018
- **Description:** Document local setup for MySQL, backend, frontend, and agent; environment variables and storage permissions; TLS/CORS and secret provisioning; migration/backup expectations; supported file/print options; and known limitations. Confirm payment remains deferred.

## 6. Executable task breakdown

Tasks are ordered by dependency and module. `[P]` marks work that may proceed in parallel once its stated prerequisite contracts are stable.

### Phase 1 — Backend

- [x] T001 [Plan:1.1] Define package boundaries, configuration profiles, and standard API error envelope in `backend/src/main/java/com/example/backend/`.
- [x] T002 [Plan:1.1] Add required persistence, validation, security, WebSocket, and test dependencies to `backend/pom.xml` after verifying compatibility with the pinned Spring Boot parent; add the migration dependency with T004 after the migration tool is selected.
- [x] T003 [P] [Plan:1.1] Document required backend environment variables and local service prerequisites in `backend/README.md`.
- [x] T004 [Plan:1.2] Add Flyway and MySQL Flyway support with externalized datasource settings and schema validation in `backend/pom.xml` and `backend/src/main/resources/`.
- [x] T005 [Plan:1.2] Create document, print-order, print-job, printer, agent, admin, print-rate, and audit-event entities under `backend/src/main/java/com/example/backend/entity/`.
- [x] T006 [Plan:1.2] Define persistence repositories and uniqueness/index constraints under `backend/src/main/java/com/example/backend/repository/`.
- [x] T007 [Plan:1.2] Add migration scripts for the initial schema under `backend/src/main/resources/db/migration/`.
- [x] T008 [Plan:1.3] Define upload/request/response DTOs and validation rules under `backend/src/main/java/com/example/backend/dto/`.
- [x] T009 [Plan:1.3] Implement secure local file storage adapter and storage-key handling under `backend/src/main/java/com/example/backend/storage/`.
- [ ] T010 [Plan:1.3] Implement upload validation, file persistence, metadata extraction, and supported-format page counting under `backend/src/main/java/com/example/backend/service/`.
- [ ] T011 [Plan:1.3] Add upload and authorized document-download endpoints under `backend/src/main/java/com/example/backend/controller/`.
- [ ] T012 [P] [Plan:1.3] Add focused upload-validation, page-count, storage-path isolation, and unauthorized-download tests under `backend/src/test/java/com/example/backend/`.
- [ ] T013 [Plan:1.4] Implement decimal-safe rate lookup, total-page calculation, and server-owned estimate/order price snapshot in `backend/src/main/java/com/example/backend/service/`.
- [ ] T014 [Plan:1.4] Implement token generation, order creation, valid state transitions, and public token status lookup in `backend/src/main/java/com/example/backend/service/`.
- [ ] T015 [Plan:1.4] Add customer pricing, order submission, and token status endpoints under `backend/src/main/java/com/example/backend/controller/`.
- [ ] T016 [P] [Plan:1.4] Add pricing, token uniqueness, amount tampering, order validation, and status transition tests under `backend/src/test/java/com/example/backend/`.
- [ ] T017 [Plan:1.5] Implement administrator credential verification, secure token issuance, and initial admin provisioning configuration under `backend/src/main/java/com/example/backend/security/`.
- [ ] T018 [Plan:1.5] Configure route authorization and security error handling under `backend/src/main/java/com/example/backend/security/` and `backend/src/main/java/com/example/backend/config/`.
- [ ] T019 [Plan:1.5] Add authentication and role-boundary tests under `backend/src/test/java/com/example/backend/`.
- [ ] T020 [Plan:1.6] Implement admin order search/filter/detail/history/statistics endpoints under `backend/src/main/java/com/example/backend/controller/` and `backend/src/main/java/com/example/backend/service/`.
- [ ] T021 [Plan:1.6] Implement printer configuration/default and pricing read/write endpoints under `backend/src/main/java/com/example/backend/controller/` and `backend/src/main/java/com/example/backend/service/`.
- [ ] T022 [Plan:1.6] Implement guarded print, retry, and cancel operations with audit events under `backend/src/main/java/com/example/backend/service/`.
- [ ] T023 [P] [Plan:1.6] Add admin filtering/pagination, printer/rate authorization, and action eligibility tests under `backend/src/test/java/com/example/backend/`.
- [ ] T024 [Plan:1.7] Implement persistent job enqueue/claim, per-printer sequential execution guards, idempotent acknowledgements, and recovery in `backend/src/main/java/com/example/backend/service/`.
- [ ] T025 [Plan:1.7] Implement agent registration, credential validation, configuration, heartbeat, and job status endpoints under `backend/src/main/java/com/example/backend/controller/`.
- [ ] T026 [Plan:1.7] Implement authenticated WebSocket job notifications and connection lifecycle in `backend/src/main/java/com/example/backend/websocket/`.
- [ ] T027 [Plan:1.7] Publish request/response, error, event, and status-transition contract documentation in `backend/docs/api-contract.md`.
- [ ] T028 [P] [Plan:1.7] Test sequential dispatch, restart recovery, retry limits/eligibility, duplicate notification handling, and agent authorization under `backend/src/test/java/com/example/backend/`.

### Phase 2 — Print Agent

- [ ] T029 [Plan:2.1] Create the standalone Java 21 Maven application and package structure in `print-agent/pom.xml` and `print-agent/src/main/java/`.
- [ ] T030 [Plan:2.1] Implement externalized agent identity/secret configuration, registration, authenticated heartbeat, and reconnect loop under `print-agent/src/main/java/`.
- [ ] T031 [Plan:2.2] Implement host printer discovery and report printer identities/capabilities through the backend contract under `print-agent/src/main/java/`.
- [ ] T032 [Plan:2.2] Add printer discovery/configuration tests using an injectable printer provider under `print-agent/src/test/java/`.
- [ ] T033 [Plan:2.3] Implement WebSocket job notification handling, authenticated document download, and local job validation under `print-agent/src/main/java/`.
- [ ] T034 [Plan:2.3] Implement an operating-system print adapter that applies supported options without browser UI under `print-agent/src/main/java/`.
- [ ] T035 [Plan:2.4] Implement job lifecycle reporting, durable/reconnect-safe processing, and explicit uncertain-outcome handling under `print-agent/src/main/java/`.
- [ ] T036 [P] [Plan:2.3,2.4] Add fake-printer tests for job success, failure, reconnect, duplicate delivery, and status reporting under `print-agent/src/test/java/`.

### Phase 3 — Frontend

- [ ] T037 [Plan:3.1] Add the missing MUI dependencies required by `Design.md` and establish the shared theme and customer/admin route structure while retaining/reusing the existing global design tokens under `frontend/package.json` and `frontend/src/`.
- [ ] T038 [Plan:3.1] Create a centralized Axios API client and endpoint modules under `frontend/src/services/`.
- [ ] T039 [Plan:3.1] Add narrowly scoped authentication/order/admin/printer stores under `frontend/src/store/`; keep transient form state local.
- [ ] T040 [Plan:3.2] Replace the hard-coded sample file and simulated progress with real responsive upload, metadata, progress, and validation/error feedback under `frontend/src/components/` and `frontend/src/pages/`.
- [ ] T041 [Plan:3.2] Connect the existing print-type, copies, paper-size, orientation, and estimate UI to validated settings and server-backed pricing under `frontend/src/components/` and `frontend/src/pages/`.
- [ ] T042 [Plan:3.3] Replace local fake-token submission with API-backed order creation; preserve/refine confirmation and token copy, and add customer token-status lookup under `frontend/src/pages/`.
- [ ] T043 [Plan:3.4] Implement admin login, protected navigation, and session-expiry handling under `frontend/src/pages/`, `frontend/src/layouts/`, and `frontend/src/services/`.
- [ ] T044 [Plan:3.4] Implement dashboard statistics, order table/details, search/filter/pagination, status, print, retry, cancel, and history views under `frontend/src/pages/` and `frontend/src/components/`.
- [ ] T045 [Plan:3.5] Implement printer/agent management and pricing configuration views under `frontend/src/pages/` and `frontend/src/components/`.
- [ ] T046 [Plan:3.1,3.2,3.3,3.4,3.5] Ensure responsive behavior, keyboard access, visible focus, accessible status text, and loading/empty/error states throughout `frontend/src/`.
- [ ] T047 [P] [Plan:3.2,3.3,3.4,3.5] Add frontend tests for upload interactions, estimate rendering, token status, protected routes, and admin actions under `frontend/src/`.

### Phase 4 — Integration and readiness

- [ ] T048 [Plan:4.1] Add/execute backend integration tests against MySQL for schema, upload, pricing, orders, security, queue, and agent contracts under `backend/src/test/`.
- [ ] T049 [Plan:4.1] Add contract-level integration checks between backend and fake Print Agent for dispatch, reporting, recovery, and retries under `backend/src/test/` and `print-agent/src/test/`.
- [ ] T050 [Plan:4.1] Validate customer upload-to-token and token-status flows against a running backend using the selected browser/E2E runner.
- [ ] T051 [Plan:4.1] Validate admin-to-agent-to-printer flow on the target OS with a real configured printer; verify no browser preview/dialog is opened.
- [ ] T052 [Plan:4.1] Run backend tests, frontend lint/build, and Print Agent tests; resolve failures attributable to this implementation.
- [ ] T053 [Plan:4.2] Document setup, environment configuration, schema migration, storage, TLS/secrets, agent installation, supported options, and troubleshooting in root `README.md` and module READMEs.
- [ ] T054 [Plan:4.2] Review the release against the SRS acceptance checklist and record deferred payment integration as out of scope in `progress.md`.

## 7. Project structure target

```text
PrintDesk/
├── SRS.md
├── Design.md
├── plan.md
├── rules.md
├── progress.md
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/example/backend/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/
│   │   ├── service/
│   │   ├── storage/
│   │   └── websocket/
│   ├── src/main/resources/db/migration/
│   └── src/test/java/com/example/backend/
├── print-agent/
│   ├── pom.xml
│   └── src/{main,test}/java/
└── frontend/
    ├── package.json
    └── src/
        ├── components/
        ├── layouts/
        ├── pages/
        ├── services/
        ├── store/
        ├── theme/
        └── utils/
```

Do not create empty package trees in advance; add directories with the first implementation that uses them. The target structure is architectural guidance, not a requirement to rename the existing Java package.

## 8. Verification strategy and acceptance gates

### Automated checks

- **Backend unit tests:** price arithmetic/rounding, token uniqueness behavior, input validation, state-transition guards, queue ordering, retry/cancel eligibility, and authorization.
- **Backend integration tests:** MySQL migrations/repositories, upload metadata and storage isolation, secured downloads, admin APIs, WebSocket authentication, durable queue recovery, and contract response shapes.
- **Agent tests:** registration/reconnect, printer discovery, authenticated downloads, fake-printer success/failure, sequential job handling, duplicate delivery, and status updates.
- **Frontend checks:** `npm run lint` and `npm run build`; focused UI/API tests once a test runner is selected.
- **Browser workflow:** upload → choose options → estimate → submit → receive token → look up status; admin login → inspect queue → request print/retry/cancel.

### Release acceptance

1. A valid supported file is uploaded and safely stored; invalid/oversized files are rejected with a clear error.
2. Page count is reported for supported formats; unsupported detection is explicit and does not silently claim a count.
3. The customer selects type/copies and receives a server-calculated amount; a forged client total cannot change the persisted amount.
4. Order, token, pricing snapshot, and status persist; token is unique and usable for customer status lookup.
5. An unauthenticated user cannot access admin operations, another customer’s document, or agent-only operations.
6. Admin sees and filters orders and can invoke only currently allowed print/retry/cancel operations.
7. Jobs execute sequentially for a printer and survive service restart without silent loss or duplicate dispatch.
8. The authenticated Print Agent prints through the operating system without opening browser preview or a browser print dialog.
9. Success/failure reaches the backend and frontend; a failed job retains a useful error and a safe retry path.
10. A physical-printer acceptance test passes on each OS/printer combination claimed as supported.
11. The deployment uses external secrets and TLS for network traffic; no credentials or customer files are committed.
12. Payment functionality is absent from Version 1.

## 9. Decisions required before implementation

The SRS gives product-level requirements but does not settle these implementation details. Resolve them before the relevant code is committed; record selected decisions and do not silently invent production policy:

1. Accepted formats are PDF, DOCX, JPG, and PNG and the maximum size is 25 MB per file. Retention/deletion policy and malware-scanning requirement remain open.
2. Resolved per user: PDF pages are counted with PDFBox; validated JPG/PNG images count as one page; validated DOCX files are converted to PDF with LibreOffice for rendered page count. DOCX conversion times out after 30 seconds and fails closed if LibreOffice cannot run; malformed/unsupported content is rejected.
3. Flyway and MySQL 8.4 LTS are selected. Local development provisioning and production deployment target remain open.
4. Admin bootstrap method, JWT lifetime/refresh/revocation policy, and password reset/rotation approach.
5. Agent credential issuance/rotation/revocation and whether a deployment has one or multiple agents from day one.
6. WebSocket authentication/authorization mechanism and TLS/network exposure constraints.
7. Supported operating systems, printer drivers, duplex/color capability mapping, and behavior when requested settings are unsupported.
8. Retry limit/backoff and handling for ambiguous “printer accepted job but agent lost acknowledgement” outcomes.
9. Token entropy/readability and whether tokens are globally unique or scoped.
10. Whether prices are rounded per page or only at order total, and how rate changes affect already-submitted orders. Plan recommendation: snapshot the rate used at order creation.
11. Accessibility/browser support targets and the browser E2E test runner.

## 10. Requirement mapping

| Requirement | SRS source | Plan items | Implementation evidence |
|---|---|---|---|
| REQ-001 | §§3.1, 16 | 1.3, 3.2, 4.1 | Upload API/storage service; upload components; upload acceptance tests |
| REQ-002 | §§8–9, 16 | 1.2, 1.3, 3.2, 4.1 | Document metadata/page-count service; protected storage key |
| REQ-003 | §§16–18, 24 | 1.4, 2.3, 3.2 | Order settings DTO/domain; agent print options; configuration UI |
| REQ-004 | §17, §26 | 1.4, 1.6, 3.2, 3.5 | Pricing service/API; immutable order rate snapshot; estimate and pricing UI |
| REQ-005 | §§16, 18, 28 | 1.2, 1.4, 4.1 | Persisted order/token; unique database constraint; order submission tests |
| REQ-006 | §§4.1, 16, 26 | 1.4, 3.3, 4.1 | Token status endpoint and customer confirmation/status pages |
| REQ-007 | §§4.2, 25–26 | 1.5, 1.6, 3.4, 4.1 | Admin login/security configuration; protected routes and authorization tests |
| REQ-008 | §§4.2, 20, 26 | 1.6, 3.4, 4.1 | Admin query/statistics/history APIs and dashboard/table |
| REQ-009 | §§4.2, 21, 23, 26 | 1.6, 1.7, 2.4, 3.4, 4.1 | Guarded actions, retry/cancel API, admin actions and tests |
| REQ-010 | §§10–13, 26 | 1.7, 2.1, 2.3, 2.4, 3.5, 4.1 | Agent credentials, REST/WebSocket contract, connection state and agent status UI |
| REQ-011 | §§4.2, 14, 24, 26 | 1.2, 1.6, 2.2, 3.5 | Printer persistence/discovery/configuration and management UI |
| REQ-012 | §§19, 22, 27, 30 | 1.2, 1.7, 2.4, 4.1 | Durable jobs, per-printer serialization, restart/ordering tests |
| REQ-013 | §§10, 15, 21 | 2.2, 2.3, 4.1 | OS print adapter and real-printer acceptance evidence |
| REQ-014 | §§10, 19, 23, 30 | 1.2, 1.7, 2.1, 2.4, 3.4, 4.1 | Persisted failure details, reconnect/retry logic, visible recovery flow |
| REQ-015 | §25 | 1.2–1.7, 2.1, 2.3, 3.1, 3.4, 3.5, 4.2 | Authorization, file access control, validation, credentials, audit events, secure deployment guide |
| REQ-016 | §30 | 1.3, 1.7, 2.4, 3.2, 4.1, 4.2 | Upload feedback, non-blocking queue, recovery tests, operational guidance |
| REQ-017 | §§5, 10–12, 30–31 | 1.1, 1.7, 2.1–2.4, 3.1, 4.2 | Separate backend/frontend/agent modules and stable contracts |
| REQ-018 | §§3.2, 29 | 4.2 | Explicit exclusion from scope and payment-independent order/print flow |
