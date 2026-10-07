# Project Rules — PrintDesk

**Last updated:** 2026-10-07  
**Requirements source:** [SRS.md](./SRS.md)  
**UX source:** [Design.md](./Design.md)

These rules guide implementation of the Version 1 online document printing system. If this file conflicts with the SRS, the SRS governs product scope and behavior; record proposed changes rather than silently changing either document.

## 1. Delivery and scope

- Implement backend foundations and contracts before frontend integration. Keep `backend/`, `frontend/`, and `print-agent/` independently buildable.
- Deliver the customer upload-to-token flow and administrator-to-physical-printer flow in small, verifiable increments.
- Payment, wallet/refund, customer profile accounts, delivery, SMS/WhatsApp, multi-branch, OCR, and advanced analytics are out of Version 1 scope.
- Do not report a requirement as complete until its relevant implementation and verification evidence exist. Keep [progress.md](./progress.md) current.

## 2. Architecture boundaries

- Backend owns validation, persistence, authorization, order/token creation, price calculation, status transitions, and queue decisions.
- Frontend communicates with the backend through the centralized API layer; do not embed business rules or issue ad-hoc network requests from arbitrary components.
- Print Agent is the only application component responsible for communicating with the local OS printing subsystem.
- Use explicit REST/WebSocket contracts between backend, frontend, and agent. Avoid shared persistence access or cross-module imports.
- Keep storage and printer integrations behind replaceable interfaces/adapters. Do not couple order logic to a specific filesystem, object store, OS, or printer driver.
- Keep order status and individual print execution attempts coherent; preserve retry/error history rather than overwriting evidence.

## 3. Backend and data integrity

- Use Java 21 and the existing Spring Boot Maven project as the backend baseline.
- Use MySQL as the planned application database. Schema changes must be explicit, reviewable migrations; do not depend on production auto-DDL.
- Persist a unique token constraint, document metadata, rate snapshot, order status, job/attempt data, and required timestamps.
- Compute all money values on the server with decimal-safe types. The client may display estimates but cannot set the authoritative amount.
- Centralize allowed order/job state transitions and reject invalid transitions with clear API errors.
- Validate all request data at the API boundary and again where domain invariants require it. Return consistent, non-sensitive error responses.
- Keep large or slow file/print work from blocking unrelated HTTP requests.

## 4. Files and privacy

- Accept only explicitly approved file types and enforce a configured file-size limit on the server.
- Generate storage names/keys; never trust client filenames as filesystem paths.
- Store document bytes outside public static directories. Persist only metadata and an opaque storage key in the database.
- Download requires authorization appropriate to the caller and associated order/agent. Changing an ID or token must not grant access to another customer’s file.
- Do not log file contents, secrets, authentication tokens, or unnecessary personal data.
- Define retention and deletion behavior before production deployment; do not invent a silent retention policy.

## 5. Authentication and security

- Require HTTPS in deployed environments; externalize credentials and cryptographic secrets.
- Require authenticated role-based access for all admin operations.
- Give each Print Agent an identifiable, revocable credential. Never allow public unauthenticated print commands.
- Do not commit secrets, default production credentials, real customer documents, or local environment files.
- Protect admin actions and downloads against unauthorized access; apply upload validation, input validation, secure filenames, and rate limits where needed.
- Record administrative actions and job state changes with sufficient audit context, without logging secrets.
- Use least privilege for backend, agent, storage, and database credentials.

## 6. Print queue and agent behavior

- Process one job at a time per configured physical printer.
- Persist queue state so service restart does not silently discard accepted work.
- Make dispatch, claim, and status acknowledgement safe against reconnects and duplicate notifications.
- Never mark a job printed solely because a request was sent. Mark success only on the strongest confirmation supported by the OS/driver and report limitations accurately.
- Retain failure reason and attempt history. Apply a configured retry policy; do not retry indefinitely or automatically reprint an ambiguous job.
- Check printer availability and supported capabilities; surface unsupported settings rather than silently substituting behavior.
- Standard admin print action must not open browser preview or a browser print dialog.

## 7. Frontend and UX

- Use the React/Vite stack and align with the design requirements for MUI, React Router, Zustand, Framer Motion, and Axios.
- Use a centralized theme/tokens and shared UI components; avoid scattered inline styling and hard-coded theme colors.
- Keep upload usable on mobile. Optimize admin order/printer controls for workstation and tablet use.
- Provide loading, empty, validation, error, and success states; include upload progress.
- Make keyboard interaction, focus, labels, contrast, and text status available. Do not communicate status through color alone.
- Keep animation subtle and non-blocking. Respect reduced-motion preferences where animation is used.
- Keep global state for shared durable UI/application state only; use local component state for transient forms.

## 8. Testing and change quality

- Add tests with behavior changes: backend unit/integration tests, agent tests using a fake printer, and frontend tests for key user flows.
- Test security boundaries, tampered prices, malformed/oversized uploads, token uniqueness, invalid state transitions, queue ordering, retries, and reconnects.
- Use MySQL-compatible integration verification for production persistence behavior; do not treat an in-memory substitute as proof of MySQL migration correctness.
- Before calling a flow complete, run the smallest applicable test/build/lint command and record its result in [progress.md](./progress.md).
- Never hide errors with broad catches, empty fallback responses, or success-shaped responses. Surface actionable errors while omitting sensitive details.
- Keep changes scoped, preserve existing behavior outside the requirement, and update relevant documentation alongside implementation.

## 9. Requirements gaps are explicit

The following decisions remain open in [plan.md](./plan.md): supported MIME types and file size/retention; page-count behavior for each format; MySQL/migration/deployment versions; admin and agent credential lifecycle; WebSocket security; operating systems/printers; retry and ambiguous outcome handling; token policy; money rounding; browser/accessibility support and E2E runner. Resolve a decision before implementing the affected behavior and record it here or in the SRS.

