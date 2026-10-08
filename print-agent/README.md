# PrintDesk Local Print Agent

Standalone Java 21 Maven module for the administrator workstation. The agent authenticates to the backend, reports printer capabilities and heartbeat, claims assigned jobs over REST, downloads documents with its short-lived bearer token, receives STOMP job hints, and reports lifecycle events. It uses a private local execution journal and single-instance lock so a restart never silently resubmits a job that may already have reached the operating system.

## Build and test

Maven must run with a Java 21 JDK. On Linux, select the JDK before running Maven (use the installed Java 21 path on your machine):

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
```

On Windows, set `JAVA_HOME` to the Java 21 installation and ensure `%JAVA_HOME%\bin` is on `PATH`.

Run host printer discovery:

```sh
mvn package
java -jar target/print-agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar --list-printers
```

Java Print Service sees printers installed and exposed to the operating-system account running the agent. Configure printers and drivers on the host first. The adapter checks the requested copy, paper, orientation, color, and duplex options against the selected printer and rejects unsupported settings. It waits for an OS print-job completion event; that event is not proof that paper physically exited the printer. Missing final acknowledgement is reported as `OUTCOME_UNKNOWN`, with no automatic resubmission. Hardware and driver acceptance must be run on both Windows and Linux before declaring physical printing supported.

## Run the connected agent

1. Start the backend and provision a unique agent code and secret through the protected admin API.
2. Install and configure a supported printer under the operating-system account that will run the agent.
3. Supply the required environment variables below through the host's service manager or another protected secret store. Do not commit the secret or place it in a checked-in `.env` file.
4. Start the packaged agent with no arguments:

   ```sh
   java -jar target/print-agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

The agent heartbeats every interval returned by the backend, refreshes its assigned-printer set from each heartbeat response, attempts an authenticated WebSocket connection, and polls assigned printers every five seconds so dropped notifications cannot strand jobs. Document downloads are bounded by the `documentMaxBytes` value returned by backend configuration. It reconnects with exponential backoff and reauthenticates before its short-lived token expires. A valid backend connection and a compatible physical printer are required for actual printing.

Each work directory permits one running agent process. It contains an atomic `.active-print-job.json` recovery journal and temporary downloaded documents. Newly created POSIX work directories/files are restricted to the owner; an existing directory must already have owner-only permissions or the agent refuses to use it. If the process restarts after OS submission may have begun, it reports `OUTCOME_UNKNOWN` for administrator review instead of printing again. Keep the work directory on a local filesystem that supports file locks and atomic rename.

`--list-printers` remains available as a discovery-only smoke command and does not authenticate or print.

## Agent configuration

The connected runtime reads these environment variables:

| Variable | Required | Purpose |
|---|---:|---|
| `PRINTDESK_AGENT_BACKEND_URL` | Yes | Backend origin; HTTPS is required except for loopback-only local development. |
| `PRINTDESK_AGENT_CODE` | Yes | Unique administrator-provisioned identity for this agent. |
| `PRINTDESK_AGENT_SECRET` | Yes | Secret provisioned out-of-band; 32–72 UTF-8 bytes. Never commit it or log it. |
| `PRINTDESK_AGENT_WORK_DIRECTORY` | No | Private temporary-work directory; defaults to `${user.home}/.printdesk/agent-work`. |
| `PRINTDESK_AGENT_LIBREOFFICE_COMMAND` | No | LibreOffice executable used to convert DOCX documents before printing; defaults to `soffice`. |

The backend provisions the agent out-of-band and stores only a BCrypt hash of the secret. The agent uses the short-lived bearer JWT for REST requests and supplies it in the STOMP `CONNECT` authorization header, never in the WebSocket URL. Production requires HTTPS/WSS. The WebSocket notification is only a hint; the agent claims every job through REST before downloading or printing it.

An ambiguous operating-system print outcome must be reported as `OUTCOME_UNKNOWN`; the agent must not automatically resubmit it. An administrator must review it before an authorized retry.

## Current limitations

- A physical printer/driver acceptance test has not been run in this development environment; no compatible printer is currently exposed to Java Print Service.
- The full connected-agent run has not yet been exercised against a provisioned live backend. REST operations, authentication refresh, document size/content checks, event replay, and restart safety have automated tests.
- Stale-agent/offline queue recovery is not yet implemented server-side. A job may remain held for administrator/backend recovery if the agent disappears while a job is active.
- Configured page ranges are rejected rather than silently ignored. Page-range execution is not supported by this agent version.
