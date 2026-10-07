# PrintDesk API Documentation

This document is the frontend-facing API reference for PrintDesk. It distinguishes endpoints currently implemented in the backend from SRS-planned endpoints whose exact request and response contracts have not yet been implemented or agreed.

## 1. Contract status and conventions

| Label | Meaning |
|---|---|
| **Implemented** | Route and request/response shape are present in the current Spring Boot backend. |
| **Planned** | Mentioned in the SRS/product plan, but not yet implemented. Request/response details are intentionally not invented here. |

- Local backend base URL: `http://localhost:8080`.
- REST API prefix: `/api`. No URL version prefix is currently configured.
- JSON requests use `Content-Type: application/json`; JSON responses use `application/json`, except errors, which use `application/problem+json`.
- The upload endpoint uses `multipart/form-data`.
- UUIDs are represented as strings. Timestamps are ISO-8601 UTC instants. Monetary values are decimal numbers in the API and use `INR`.
- Enum values are uppercase strings unless an endpoint explicitly accepts a case-insensitive string.
- Customer upload, estimate, order creation, and token lookup are public. Admin login is public; protected admin routes use `Authorization: Bearer <accessToken>`.
- The backend is stateless. Admin access tokens expire after 30 minutes; there is no refresh-token endpoint.
- Frontend must treat response DTOs as the wire contract. JPA entities are not returned directly.

## 2. API classification

| Classification | Audience | Current availability | Main use |
|---|---|---|---|
| Customer documents | Customer web flow | Implemented | Upload a document; authorized users/agents can later download it. |
| Customer pricing and orders | Customer web flow | Implemented | Estimate price, submit an order, and look up order status by token. |
| Admin authentication | Admin web flow | Implemented | Exchange username/password for a bearer token. |
| Admin order operations | Admin web flow | Partly implemented | Protected order search/list/detail, status statistics, and cancellation. Print/retry are deferred until durable queue and agent support exist. |
| Printer and pricing management | Admin web flow | Planned | Read/update printers and print rates. |
| Print Agent | Agent application; admin UI may display agent state | Planned | Registration, configuration, heartbeat/job status, and WebSocket notification. |
| Management | Operations | Partly implemented | Public health check and authenticated Actuator information. |

## 3. Customer APIs — documents

### 3.1 Upload document — **Implemented**

`POST /api/documents/upload`

- **Authentication:** Public.
- **Content type:** `multipart/form-data`.
- **Request:** One part named `file`; accepted files are PDF, DOCX, JPG, and PNG. Maximum file size is 25 MB.
- **Success:** `201 Created`; `Location: /api/documents/{documentId}`.

Example request:

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@sample.pdf"
```

Response (`DocumentUploadResponse`):

```json
{
  "documentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "fileName": "sample.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 18240,
  "pageCount": 3
}
```

`pageCount` is the server-detected page count. Do not send a client-provided page count when requesting a price or creating an order.

### 3.2 Download document — **Implemented**

`GET /api/documents/{documentId}`

- **Authentication:** Bearer JWT with `ROLE_ADMIN`, or an authenticated `ROLE_AGENT` assigned to a print job for that document.
- **Path parameter:** `documentId` — UUID returned by upload.
- **Success:** `200 OK`, binary response with the stored content type, `Content-Disposition: attachment`, and `Cache-Control: no-store`.
- **Errors:** `401` unauthenticated; `403` unsupported role; `404` document not found or agent not assigned.
- **Frontend use:** Not part of the customer upload workflow. Admin UI may download through an authenticated request; agent downloads are implemented as backend authorization but agent credentials are still planned.

## 4. Customer APIs — pricing and orders

### 4.1 Estimate print price — **Implemented**

`POST /api/print-orders/estimate`

- **Authentication:** Public.
- **Request DTO:** `PriceEstimateRequest`.
- **Success:** `200 OK`; quote is calculated using the document's persisted page count and active server-side rate.

Request:

```json
{
  "documentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "printType": "BLACK_AND_WHITE",
  "copies": 2
}
```

Response (`PriceEstimateResponse`):

```json
{
  "printType": "BLACK_AND_WHITE",
  "documentPages": 3,
  "copies": 2,
  "totalPages": 6,
  "pricePerPage": 2.50,
  "totalAmount": 15.00,
  "currency": "INR"
}
```

Allowed `printType` values are `BLACK_AND_WHITE` and `COLOR`. The total uses server-side pricing; the browser's estimate is advisory.

### 4.2 Create print order — **Implemented**

`POST /api/print-orders`

- **Authentication:** Public.
- **Request DTO:** `CreatePrintOrderRequest`.
- **Success:** `201 Created`; `Location: /api/print-orders/{token}`.
- **Pricing:** Server derives page count and rate, calculates the total, and snapshots the rate and total. Client-supplied price fields are not part of the request contract.
- **Page selection:** Customer orders include all pages in the uploaded document.

Request:

```json
{
  "documentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "printType": "BLACK_AND_WHITE",
  "copies": 2,
  "paperSize": "A4",
  "orientation": "portrait",
  "doubleSided": false
}
```

Response (`CreatePrintOrderResponse`):

```json
{
  "token": "7KX3M9P2QW6A",
  "fileName": "sample.pdf",
  "pageCount": 3,
  "printType": "BLACK_AND_WHITE",
  "copies": 2,
  "totalPages": 6,
  "pricePerPage": 2.50,
  "totalAmount": 15.00,
  "currency": "INR",
  "status": "PENDING"
}
```

### 4.3 Look up order status — **Implemented**

`GET /api/print-orders/{token}`

- **Authentication:** Public. Treat the token as a bearer secret; do not log or expose it unnecessarily.
- **Path parameter:** `token` — the 12-character order token returned by order creation.
- **Success:** `200 OK`.
- **Not found:** `404 Not Found` for an unknown or malformed token.

Response (`PrintOrderStatusResponse`):

```json
{
  "token": "7KX3M9P2QW6A",
  "status": "PENDING",
  "printType": "BLACK_AND_WHITE",
  "pageCount": 3,
  "copies": 2,
  "totalPages": 6,
  "totalAmount": 15.00,
  "createdAt": "2026-10-08T02:59:10Z",
  "updatedAt": "2026-10-08T02:59:10Z",
  "printedAt": null
}
```

Current order status values: `PENDING`, `PRINT_REQUESTED`, `QUEUED`, `PRINTING`, `PRINTED`, `FAILED`, `CANCELLED`.

## 5. Admin APIs — authentication

### 5.1 Admin login — **Implemented**

`POST /api/admin/login`

- **Authentication:** Public.
- **Request DTO:** `AdminLoginRequest`.
- **Success:** `200 OK`.
- **Invalid credentials:** `401 Unauthorized`, with the same generic error for an unknown username and an incorrect password.

Request:

```json
{
  "username": "lalit",
  "password": "<admin password>"
}
```

Response (`AdminLoginResponse`):

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-08T03:29:10Z"
}
```

Send the token on subsequent protected requests:

```http
Authorization: Bearer <accessToken>
```

All `/api/admin/**` routes other than login require an authenticated `ROLE_ADMIN` or `ROLE_OPERATOR`. There is currently no password-change, password-reset, logout, or token-refresh API.

## 6. Admin APIs — order dashboard and actions

All endpoints in this section require an administrator/operator bearer token. Pagination is zero-based and sorted newest-first.

| Method | Path | Intended frontend use | Contract status |
|---|---|---|---|
| `GET` | `/api/admin/dashboard/stats` | Read total, pending, in-progress, printed, failed, and cancelled order counts. | Implemented. |
| `GET` | `/api/admin/print-orders` | Search by token/filename and filter by status; paginate results. | Implemented. |
| `GET` | `/api/admin/print-orders/{id}` | View order details and print-attempt history. | Implemented. |
| `POST` | `/api/admin/print-orders/{id}/cancel` | Cancel an order that is still `PENDING`; cancellation is audited. | Implemented. |
| `POST` | `/api/admin/print-orders/{id}/print` | Request printing for an eligible order. | Deferred until the durable queue and authenticated Print Agent are implemented. |
| `POST` | `/api/admin/print-orders/{id}/retry` | Retry an eligible failed print job. | Deferred until the durable queue and authenticated Print Agent are implemented. |

`{id}` is the order database UUID, distinct from the customer-facing token. `page` defaults to `0` and is limited to `0..1,000,000`; `size` defaults to `20` and is limited to `1..100`; optional `status` is a `PrintOrderStatus`, and optional case-insensitive `search` matches token or document filename (up to 100 characters). Invalid pagination/search/status values return `400`. An unknown order UUID returns `404`. Cancelling a non-pending order returns `409 INVALID_ORDER_TRANSITION`; a concurrent update returns `409 CONCURRENT_ORDER_UPDATE`.

Order-list response (`AdminPrintOrderPage`):

```json
{
  "items": [
    {
      "id": "47d239e8-5da7-4ad7-8b4c-1dc20401ad1f",
      "token": "7KX3M9P2QW6A",
      "fileName": "sample.pdf",
      "pageCount": 3,
      "printType": "BLACK_AND_WHITE",
      "copies": 2,
      "totalPages": 6,
      "paperSize": "A4",
      "orientation": "portrait",
      "doubleSided": false,
      "totalAmount": 15.00,
      "currency": "INR",
      "status": "PENDING",
      "createdAt": "2026-10-08T02:59:10Z",
      "updatedAt": "2026-10-08T02:59:10Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Details (`AdminPrintOrderDetail`) also provide document ID/content type/size, rate snapshot, page range, printed timestamp, and `attempts` ordered newest-first. Attempt data includes its ID/number, status, optional error, and queue/start/completion timestamps. Storage keys, document hashes, and credential hashes are never exposed.

Dashboard statistics response (`AdminDashboardStats`):

```json
{
  "totalOrders": 12,
  "pendingOrders": 4,
  "inProgressOrders": 2,
  "printedOrders": 5,
  "failedOrders": 1,
  "cancelledOrders": 0
}
```

`inProgressOrders` aggregates `PRINT_REQUESTED`, `QUEUED`, and `PRINTING`.

Cancellation request: empty `POST /api/admin/print-orders/{id}/cancel`. The response is the updated `AdminPrintOrderDetail`, with status `CANCELLED`. The backend writes an audit record with the authenticated admin ID and old/new status. Print/retry APIs are deliberately not exposed yet; the UI must not imply physical printing can be started.

## 7. Printer and pricing APIs

The following paths are specified by the SRS but **not implemented**:

| Method | Path | Intended frontend use | Contract status |
|---|---|---|---|
| `GET` | `/api/admin/printers` | List configured/discovered printers and their enabled/default state. | Planned; response schema TBD. |
| `POST` | `/api/admin/printers` | Add/configure a printer. | Planned; request/response schema TBD. |
| `PUT` | `/api/admin/printers/{id}` | Update printer settings/default selection. | Planned; request/response schema TBD. |
| `GET` | `/api/pricing` | Read current public print rates for estimates/UI. | Planned; response schema TBD. |
| `PUT` | `/api/admin/pricing` | Update print rates. | Planned; request/response schema TBD. |

Admin printer/rate mutations are intended to require the admin/operator bearer-token roles. Rate units, list envelopes, update semantics, and validation error specifics have not yet been defined as API contracts.

## 8. Print Agent APIs and events

The SRS specifies the following Print Agent interface, but no agent REST controller, agent-authentication flow, or WebSocket contract is currently implemented:

| Method / protocol | Path | Intended use | Contract status |
|---|---|---|---|
| `POST` | `/api/print-agent/register` | Register/enroll an agent. | Planned; request/response and credential lifecycle TBD. |
| `GET` | `/api/print-agent/config` | Retrieve agent configuration. | Planned; response schema TBD. |
| `POST` | `/api/print-agent/status` | Report agent heartbeat/availability. | Planned; request/response schema TBD. |
| `POST` | `/api/print-agent/jobs/{id}/status` | Report print-job lifecycle/result. | Planned; request/response schema TBD. |
| WebSocket | `/ws/print-agent` | Deliver authenticated job notifications. | Planned; authentication, destinations, event payloads, and reconnect/acknowledgement behavior TBD. |

These routes are for the separate Print Agent, not normal browser calls. The admin UI should consume safe admin dashboard/printer/agent APIs rather than connecting to the agent's WebSocket directly.

## 9. Management and observability APIs

| Method | Path | Authentication | Response / purpose |
|---|---|---|---|
| `GET` | `/actuator/health` | Public | `200 OK` when the health endpoint reports `UP`; used for service health checks. |
| `GET` | `/actuator/info` | Authenticated | Actuator info endpoint is exposed, but application info content is not currently specified. |

Only Actuator `health` and `info` are exposed over HTTP. Other Actuator endpoints are not part of the frontend API.

## 10. Common errors

Validation failures and application errors use Spring `ProblemDetail` with an application `code`. Example:

```json
{
  "type": "urn:printdesk:problem:validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/print-orders",
  "code": "VALIDATION_ERROR",
  "fieldErrors": {
    "copies": "Copies must be positive."
  }
}
```

`fieldErrors` is included for request-validation errors and omitted when it does not apply. Security errors use a generic Problem Details response:

| HTTP status | Code | Meaning |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Request validation failed. |
| `401` | `AUTHENTICATION_REQUIRED` | Missing, malformed, expired, or otherwise invalid authentication. |
| `401` | `INVALID_ADMIN_CREDENTIALS` | Admin username/password pair was not accepted. |
| `403` | `ACCESS_DENIED` | Authenticated caller does not have the required role. |
| `409` | `INVALID_ORDER_TRANSITION` | The order cannot be cancelled from its current lifecycle state. |
| `404` | Endpoint-specific | Resource/token is unavailable or intentionally hidden. |

Frontend should branch on HTTP status and stable `code`, not parse human-readable `detail` text. Do not display server stack traces or include bearer tokens in logs.

## 11. Wire DTOs and domain entities

The frontend should use the DTOs below as API contracts. These DTOs are immutable Java records in the current backend. Persistence entities are server-side domain/persistence models and must **not** be serialized directly to the browser.

### Implemented DTOs

| DTO | API role |
|---|---|
| `UploadDocumentRequest` | Multipart upload request wrapper; the HTTP part name is `file`. |
| `DocumentUploadResponse` | Upload result and server-detected document metadata used by quote/order requests. |
| `PriceEstimateRequest` | Request document ID, print type, and copy count for a price quote. |
| `PriceEstimateResponse` | Calculated page totals, per-page rate, amount, and currency. |
| `CreatePrintOrderRequest` | Customer order preferences; contains no authoritative price fields. |
| `CreatePrintOrderResponse` | Created order summary and customer tracking token. |
| `PrintOrderStatusResponse` | Public token status summary. |
| `AdminPrintOrderSummary` | Immutable row summary for the order dashboard. |
| `AdminPrintOrderPage` | Page of order summaries plus pagination metadata. |
| `AdminPrintOrderDetail` | Safe order/document metadata plus print-attempt history. |
| `AdminPrintJobAttempt` | One print attempt as shown in order history. |
| `AdminDashboardStats` | Aggregate order counts shown by the admin dashboard. |
| `AdminLoginRequest` | Admin username/password login request. |
| `AdminLoginResponse` | Bearer token and expiration returned after login. |

### Persisted entities behind the API

| Entity | Domain role | Frontend exposure |
|---|---|---|
| `DocumentEntity` | Private document metadata and opaque storage reference. | Selected safe metadata is projected to `DocumentUploadResponse`; never expose storage key or digest. |
| `PrintOrderEntity` | Order preferences, token, status, page/rate/total snapshots, and timestamps. | Project to create/status/admin response DTOs; do not expose entity fields directly. |
| `PrintRateEntity` | Active print type rate and currency. | Planned pricing DTO projection; not directly serialized. |
| `PrintJobEntity` | Queue attempt, assigned printer/agent, status, and timing/error data. | Planned admin/agent DTO projections; do not expose persistence version or unrestricted error data. |
| `PrinterEntity` | Discovered/configured printer and default/enabled selection. | Planned printer DTO projection. |
| `PrintAgentEntity` | Agent identity, credential hash, online state, and heartbeat. | Planned safe agent DTO projection; never expose credential hash. |
| `AdminAccountEntity` | Admin identity, password hash, role, and enabled flag. | Never returned directly; only the login DTO is currently exposed. |
| `AuditLogEntity` | Administrative/system/agent action history. | Planned admin history projection; not directly serialized. |

Shared enums currently used in API/domain contracts:

- `PrintType`: `BLACK_AND_WHITE`, `COLOR`.
- `PrintOrderStatus`: `PENDING`, `PRINT_REQUESTED`, `QUEUED`, `PRINTING`, `PRINTED`, `FAILED`, `CANCELLED`.
- `AdminRole`: `ADMIN`, `OPERATOR`.
- Planned agent/job enums: `PrintAgentStatus` (`ONLINE`, `OFFLINE`, `REVOKED`) and `PrintJobStatus` (`QUEUED`, `CLAIMED`, `PRINTING`, `PRINTED`, `FAILED`, `CANCELLED`, `OUTCOME_UNKNOWN`). They are not yet emitted by an agent API.

## 12. Main frontend request flow

<!-- mermaid-checked: every participant uses `participant Id as "Label"`, no \n in aliases/messages/notes, every alt/opt/loop closed by end, no `:` inside any alias -->
~~~mermaid
sequenceDiagram
    participant Customer as "Customer Frontend"
    participant DocAPI as "Document API"
    participant OrderAPI as "Print Order API"
    participant AdminUI as "Admin Frontend"
    participant AuthAPI as "Admin Login API"
    participant Backend as "Spring Boot Backend"
    participant MySQL as "MySQL Database"

    Customer->>DocAPI: POST /api/documents/upload with file
    DocAPI->>Backend: Validate file and count pages
    Backend->>MySQL: Persist document metadata
    MySQL-->>Backend: Document identifier
    Backend-->>Customer: 201 DocumentUploadResponse
    Customer->>OrderAPI: POST /api/print-orders/estimate
    OrderAPI->>Backend: Calculate using persisted pages and active rate
    Backend->>MySQL: Read document and active rate
    MySQL-->>Backend: Page count and rate
    Backend-->>Customer: 200 PriceEstimateResponse
    Customer->>OrderAPI: POST /api/print-orders
    OrderAPI->>Backend: Create order with server price snapshot
    Backend->>MySQL: Persist order and token
    MySQL-->>Backend: Created order
    Backend-->>Customer: 201 CreatePrintOrderResponse
    Customer->>OrderAPI: GET /api/print-orders/token
    OrderAPI->>Backend: Find public status by token
    Backend->>MySQL: Read order summary
    MySQL-->>Backend: Order state
    Backend-->>Customer: 200 PrintOrderStatusResponse
    AdminUI->>AuthAPI: POST /api/admin/login
    AuthAPI->>Backend: Verify credentials and issue signed JWT
    Backend->>MySQL: Read admin account
    MySQL-->>Backend: Admin role and password hash
    Backend-->>AdminUI: 200 AdminLoginResponse
    Note over AdminUI,Backend: Dashboard queries and pending cancellation require the admin bearer token
    AdminUI->>Backend: GET /api/admin/print-orders and /api/admin/dashboard/stats
    Backend-->>AdminUI: AdminPrintOrderPage and AdminDashboardStats
~~~

## 13. Frontend integration gaps

Admin order list/detail/statistics and pending-order cancellation are implemented and integrated. Print/retry operations remain unavailable until the durable queue and authenticated Print Agent exist; printer/rate management, agent enrollment/status, and WebSocket job events are also pending. Customer estimates and order creation require an active server-side print rate; no default rate values are invented. The backend has no password-change or public pricing-read endpoint. Customer status-token lookup is public and has no configured rate limit; account for that operational risk before production exposure.
