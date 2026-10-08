# Print Agent and Queue API Contract

Status: **partially implemented**. Admin provisioning/rotation/revocation, agent authentication, heartbeat/printer upsert, configuration, durable print enqueue/claim, idempotent lifecycle events, admin retry, uncertain-outcome resolution, and authenticated STOMP job-available notifications are wired. Stale-job/offline recovery and the standalone agent's end-to-end transport loop remain planned. This is the backend/agent wire contract; the frontend-facing API inventory remains in [`../../apis_docs.md`](../../apis_docs.md).

## Common conventions

- REST base path: `/api`; JSON uses `application/json`; errors use `application/problem+json`.
- UUIDs are JSON strings. Timestamps are ISO-8601 UTC instants.
- Outside loopback-only development, REST and WebSocket traffic must use HTTPS/WSS.
- Agent REST calls use `Authorization: Bearer <short-lived-agent-jwt>`. Agent credentials are sent only to the authentication endpoint and must never be logged or returned.
- Agent JWT lifetime is five minutes. The JWT subject is the unique `agentCode` (matching assigned-document authorization); claims include `agentId` and `authorities: ["ROLE_AGENT"]`. Agent status/revocation is checked for protected agent operations.
- Agent identity and secret are provisioned by an administrator out-of-band. The backend stores only a BCrypt hash of the secret. The shared secret is at least 32 UTF-8 bytes.
- The WebSocket is a STOMP connection to `/ws/agents`. The JWT is supplied in the STOMP `CONNECT` `Authorization` native header, never in the URL or query string. Production connections require WSS.
- The HTTP WebSocket handshake is public because authentication occurs on the subsequent STOMP `CONNECT`; only valid active `ROLE_AGENT` JWTs may connect. The server validates agent identity, expiry, and revocation on each inbound frame and before outbound delivery.
- Agents may subscribe only to `/user/queue/jobs`; client `SEND` frames are rejected. The server publishes `JOB_AVAILABLE` to the assigned agent's private queue only after the enqueue transaction commits. Delivery is best-effort; REST claim/polling remains authoritative.
- WebSocket notifications are hints, not a delivery guarantee. After connecting/reconnecting and on each notification, the agent claims work through REST.

## Agent administration — ADMIN bearer token

### Provision an agent — ADMIN only

`POST /api/admin/agents`

Request:

```json
{
  "agentCode": "shop-east-01",
  "secret": "<administrator-provisioned-secret>"
}
```

Response: `201 Created`.

```json
{
  "agentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "agentCode": "shop-east-01",
  "status": "OFFLINE",
  "createdAt": "2026-10-08T02:59:10Z"
}
```

The response never includes the submitted secret or its hash. Agent codes are normalized to lowercase and are unique after normalization; they are 1–64 characters, restricted to letters, digits, `-`, `_`, and `.`. Secrets are 32–72 UTF-8 bytes to fit the BCrypt input limit.

### Rotate an agent secret — ADMIN only

`PUT /api/admin/agents/{agentCode}/credential`

Request: `{"secret":"<new-administrator-provisioned-secret>"}`. Response: `204 No Content`. Rotation immediately makes the previous secret invalid. Existing JWTs remain valid until expiry while the agent remains active.

### Revoke an agent — ADMIN only

`DELETE /api/admin/agents/{agentCode}`

Response: `204 No Content`. Revoked agents cannot authenticate or use existing JWTs. Revocation is terminal; provision a new agent code to replace one.

## Agent authentication and lifecycle

### Authenticate

`POST /api/agents/authenticate` is the only unauthenticated agent route.

Request:

```json
{
  "agentCode": "shop-east-01",
  "secret": "<administrator-provisioned-secret>"
}
```

Response: `200 OK`.

```json
{
  "accessToken": "<short-lived-jwt>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-08T03:04:10Z",
  "agentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "agentCode": "shop-east-01"
}
```

Unknown, disabled, or revoked credentials receive the same generic `401 INVALID_AGENT_CREDENTIALS` response.

### Heartbeat and printer discovery

`PUT /api/agents/me/heartbeat`

Request:

```json
{
  "observedAt": "2026-10-08T03:00:00Z",
  "printers": [
    {
      "systemName": "Office_Printer",
      "displayName": "Office Printer",
      "capabilities": {
        "color": true,
        "duplex": true,
        "maxCopies": 100,
        "paperSizes": ["A4", "A3", "Letter"]
      }
    }
  ]
}
```

The authenticated agent identity is taken from the JWT, never from the request body. Printer system names are unique per agent; this operation upserts the discovered set and returns stable backend printer IDs.

Response: `200 OK`.

```json
{
  "serverTime": "2026-10-08T03:00:01Z",
  "heartbeatIntervalSeconds": 30,
  "agentStatus": "ONLINE",
  "printers": [
    {
      "printerId": "24d26cd0-11ab-4afe-b849-d774b1865163",
      "systemName": "Office_Printer",
      "displayName": "Office Printer",
      "enabled": true,
      "capabilities": {
        "color": true,
        "duplex": true,
        "maxCopies": 100,
        "paperSizes": ["A4", "A3", "Letter"]
      }
    }
  ]
}
```

### Agent configuration

`GET /api/agents/me/config`

Response: `200 OK`.

```json
{
  "heartbeatIntervalSeconds": 30,
  "maxConcurrentJobsPerAgent": 1,
  "webSocketEndpoint": "/ws/agents",
  "jobDestination": "/user/queue/jobs",
  "documentMaxBytes": 26214400
}
```

## Queue and print-job lifecycle

### Claim the next job for a printer

`POST /api/agents/jobs/claim`

Request: `{"printerId":"24d26cd0-11ab-4afe-b849-d774b1865163"}`.

The backend atomically claims the oldest eligible queued job for that printer. A printer may have at most one active job. A repeated claim by the same agent while its job is still `CLAIMED` returns that same job; it does not create another attempt. `204 No Content` means no job is currently available.

Response: `200 OK`.

```json
{
  "jobId": "e937cce0-781b-451d-9daa-c24f1568ad28",
  "orderId": "81e84461-25da-4487-9e4a-01531e371b6b",
  "attemptNumber": 1,
  "documentId": "39d38d02-2085-44b2-9953-04fa803150d9",
  "contentType": "application/pdf",
  "fileName": "sample.pdf",
  "options": {
    "printType": "BLACK_AND_WHITE",
    "copies": 2,
    "paperSize": "A4",
    "orientation": "portrait",
    "doubleSided": false,
    "pageRange": null
  },
  "status": "CLAIMED"
}
```

Download the document with `GET /api/documents/{documentId}` using the same agent bearer token. The backend authorizes the request only when the document belongs to a job assigned to that agent.

### Report a job event

`POST /api/agents/jobs/{jobId}/events`

Request:

```json
{
  "eventId": "f5418364-f784-4b0a-92bb-1eebfa2d97a0",
  "status": "PRINTING",
  "occurredAt": "2026-10-08T03:00:05Z",
  "errorCode": null,
  "errorMessage": null
}
```

Allowed event statuses are `PRINTING`, `PRINTED`, `FAILED`, and `OUTCOME_UNKNOWN`. Each event is idempotent by `(jobId, eventId)`. Replaying the same event returns the original acknowledgement; reusing an event ID with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`. Only the assigned, active agent may report an event.

Response: `200 OK`.

```json
{
  "jobId": "e937cce0-781b-451d-9daa-c24f1568ad28",
  "status": "PRINTING",
  "accepted": true,
  "duplicate": false,
  "updatedAt": "2026-10-08T03:00:05Z"
}
```

Error text is bounded, sanitized, and must not contain document contents, credentials, local absolute paths, or stack traces. `OUTCOME_UNKNOWN` is not automatically requeued or retried; administrator review is required before any further print attempt.

## WebSocket notifications

After authenticating the STOMP `CONNECT` frame with the same bearer JWT, subscribe to `/user/queue/jobs`.

`JOB_AVAILABLE` event:

```json
{
  "eventId": "a3df63b5-5da7-4a88-8ee3-51d0f4ff95ca",
  "eventType": "JOB_AVAILABLE",
  "jobId": "e937cce0-781b-451d-9daa-c24f1568ad28",
  "printerId": "24d26cd0-11ab-4afe-b849-d774b1865163",
  "occurredAt": "2026-10-08T03:00:02Z"
}
```

The agent must tolerate duplicate and delayed notifications. It must claim through REST before downloading or printing, then report `PRINTING` and wait for the successful REST acknowledgement before submitting to the OS print service. On disconnect it reconnects with backoff, authenticates again if its JWT expires, then polls/claims each configured printer; a missed notification must not strand queued work.

## Queue/admin action contract

- `POST /api/admin/print-orders/{orderId}/print` accepts `{"printerId":"<uuid>"}` and creates the first durable attempt only for an eligible order and enabled printer whose agent is online. Success is `202 Accepted` with `{"jobId":"<uuid>","attemptNumber":1,"status":"QUEUED"}`.
- `POST /api/admin/print-jobs/{jobId}/retry` creates a new attempt only for an explicitly retryable `FAILED` job after eligibility checks and audit logging. There are at most three total attempts per order (initial attempt plus two administrator retries). `OUTCOME_UNKNOWN` is never eligible for automatic or ordinary retry.
- `POST /api/admin/print-jobs/{jobId}/resolve-unknown` is an ADMIN-only adjudication after physical review. Request is `{"decision":"CONFIRMED_FAILED","notes":"<bounded audit note>"}` or `{"decision":"CONFIRMED_PRINTED","notes":"<bounded audit note>"}`. The former moves the job/order to an explicitly retryable failed state; the latter closes it as printed. It never submits a print itself and always writes an audit record.
- Jobs are sequential per printer, survive backend restart, and have idempotent claim/event handling. Notifications do not themselves claim work.

## Status transitions

| Object | Allowed transition | Notes |
|---|---|---|
| Agent | `OFFLINE` → `ONLINE` → `OFFLINE`; any non-revoked state → `REVOKED` | Heartbeats establish online state; missed-heartbeat timeout marks offline. Revocation is terminal. |
| Job | `QUEUED` → `CLAIMED` → `PRINTING` → `PRINTED` / `FAILED` / `OUTCOME_UNKNOWN` | Duplicate claim is idempotent. Terminal jobs do not transition back to active. |
| Job retry | `FAILED` → new `QUEUED` attempt | Creates a distinct attempt number and audit event; never reuses the completed job. |
| Order | `PENDING` → `PRINT_REQUESTED` → `QUEUED` → `PRINTING` → `PRINTED` / `FAILED` / `OUTCOME_UNKNOWN` | Cancellation remains limited to pending orders. `OUTCOME_UNKNOWN` remains visible pending ADMIN adjudication; it is not retryable until explicitly resolved as failed. |

## Error responses

Errors use the shared Problem Details response with a stable `code`.

| Status | Code | Meaning |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Invalid agent, printer, event, or job input. |
| `401` | `AUTHENTICATION_REQUIRED` / `INVALID_AGENT_CREDENTIALS` | Missing/expired JWT or rejected bootstrap credentials. |
| `403` | `ACCESS_DENIED` | Wrong role, inactive agent, or job/printer not assigned to the caller. |
| `404` | `RESOURCE_NOT_FOUND` | Resource is unavailable or intentionally hidden. |
| `409` | `INVALID_JOB_TRANSITION` / `IDEMPOTENCY_KEY_REUSED` | Invalid lifecycle transition or an event identifier reused with a different payload. |
