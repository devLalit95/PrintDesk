
# Software Requirements Specification (SRS)
## Online Document Upload and One-Click Printing System

### 1. Introduction

#### 1.1 Purpose

The purpose of this system is to provide an online document printing platform where customers can upload documents, select printing preferences such as Black & White or Color, view the estimated printing cost, receive a unique token number, and submit the print request.

On the administrator side, all submitted print requests will be displayed in a centralized dashboard. The administrator will be able to print a document with a single click without opening a browser print preview or print dialog.

The system will use a dedicated Local Print Agent installed on the administrator's computer. The Print Agent will communicate with the backend and send authorized print jobs directly to the configured physical printer.

Payment processing is intentionally excluded from the initial version and will be integrated as a separate module in a future version.

---

# 2. Objectives

The primary objectives are:

1. Allow users to upload documents remotely.
2. Generate a unique token for every print request.
3. Automatically determine the number of pages where technically possible.
4. Allow users to select Black & White or Color printing.
5. Allow users to specify the number of copies.
6. Calculate and display the estimated printing cost.
7. Store all print-order information in the database.
8. Provide an administrator dashboard for managing print requests.
9. Provide one-click printing from the administrator dashboard.
10. Print documents without browser preview or browser print dialogs.
11. Maintain a print queue to prevent simultaneous printer conflicts.
12. Track print-job status.
13. Provide retry functionality for failed print jobs.
14. Support configurable printers and printing rates.
15. Keep the architecture ready for future payment integration.

---

# 3. Scope

## 3.1 In Scope

The first version will include:

- User document upload
- PDF/document/image support
- File validation
- Unique token generation
- Page-count detection where supported
- B&W/Color selection
- Copy selection
- Price calculation
- Print-order creation
- User order/token information
- Admin authentication
- Admin dashboard
- Print queue
- One-click printing
- Local Print Agent
- Printer management
- Print status tracking
- Failed-job retry
- Basic print history
- Basic statistics
- Configurable print rates

## 3.2 Out of Scope for Version 1

The following will not be implemented initially:

- Online payment gateway
- Wallet
- Refund system
- Customer accounts with advanced profiles
- Delivery system
- SMS/WhatsApp notification system
- Multi-branch management
- Advanced analytics
- OCR-based document processing

Payment should be designed as a future module rather than mixed into the initial printing logic.

---

# 4. User Roles

The system will initially contain two primary roles.

## 4.1 Customer/User

The customer can:

- Upload documents
- Select printing preferences
- Select number of copies
- View calculated price
- Submit a print request
- Receive a token number
- View the status of the print request

## 4.2 Administrator

The administrator can:

- Login securely
- View all print requests
- Search requests
- Filter requests
- View document information
- View token numbers
- View page counts
- View B&W/Color selection
- View copy count
- View calculated amount
- Print documents with one click
- Retry failed print jobs
- Cancel pending jobs
- View print history
- Configure printers
- Configure printing rates
- Monitor Print Agent status

---

# 5. High-Level System Architecture

The recommended architecture is:

```text
                         CUSTOMER
                            |
                            | HTTPS
                            v
                  +---------------------+
                  |    React Frontend   |
                  +----------+----------+
                             |
                             | REST API
                             v
                  +---------------------+
                  |   Spring Boot API   |
                  +----------+----------+
                             |
              +--------------+--------------+
              |                             |
              v                             v
        +-----------+                +-------------+
        |   MySQL   |                | File Storage|
        +-----------+                +-------------+
                                            

                         ADMIN
                           |
                           v
                  +---------------------+
                  |   React Admin UI    |
                  +----------+----------+
                             |
                             v
                  +---------------------+
                  |   Spring Boot API   |
                  +----------+----------+
                             |
                        Print Job
                             |
                             v
                  +---------------------+
                  |    Print Agent      |
                  |   Java Application  |
                  +----------+----------+
                             |
                             v
                  +---------------------+
                  | Operating System    |
                  | Printing Subsystem  |
                  +----------+----------+
                             |
                             v
                         PRINTER
```

---

# 6. Recommended Technology Stack

## 6.1 Frontend

Technology:

- React.js
- Vite
- Material UI (MUI)
- React Router
- Zustand for client-side/global state
- Framer Motion for animations
- Axios for API communication

### Why?

React provides component-based UI development.

Vite provides a fast development environment.

Material UI provides production-ready components such as:

- Tables
- Dialogs
- Buttons
- Forms
- Cards
- Progress indicators
- Snackbar notifications

Framer Motion will be used for subtle UI animations rather than excessive animation.

Zustand can manage:

- Authentication state
- User state
- Admin state
- Print-order state
- UI preferences

---

# 7. Backend

Technology:

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- JWT authentication
- Bean Validation
- MySQL Driver
- WebSocket support

### Why Spring Boot?

Spring Boot is suitable because the system requires:

- REST APIs
- Authentication
- File uploads
- Database operations
- Background jobs
- WebSocket communication
- Business logic
- Print-job management

---

# 8. Database

Recommended database:

**MySQL**

The database will store metadata and application state.

Documents themselves should not unnecessarily be stored as binary database records.

Instead:

```text
Database
    |
    +-- document metadata
    +-- token
    +-- status
    +-- print settings
    +-- price
    +-- timestamps
```

while the actual document is stored in:

- Local server storage for a small deployment, or
- Object storage for production deployment.

The database should store the document's storage path/key.

---

# 9. File Storage

The application should separate file storage from database storage.

Example:

```text
/storage/
    /2026/
        /10/
            /07/
                token-1052.pdf
                token-1053.pdf
```

For production, object storage such as S3-compatible storage can be used.

The backend should never expose arbitrary filesystem paths directly to users.

---

# 10. Print Agent

## 10.1 Purpose

The Print Agent is the most important component for the one-click printing requirement.

A normal web browser cannot reliably perform silent physical printing without showing the browser print interface.

Therefore, a local application/service will run on the administrator's computer.

Its responsibilities are:

1. Authenticate with the backend.
2. Maintain a connection with the backend.
3. Receive authorized print jobs.
4. Download the required document securely.
5. Select the configured printer.
6. Apply supported print settings.
7. Send the document to the operating system's print subsystem.
8. Monitor the print operation where supported.
9. Report status back to the backend.
10. Retry failed jobs according to configured rules.

---

# 11. Print Agent Technology

Recommended:

**Java Print Agent**

The Print Agent will be a separate Java application.

It should not be treated as the main Spring Boot server.

Architecture:

```text
Spring Boot Backend
        |
        | WebSocket / HTTPS
        |
        v
Java Print Agent
        |
        v
Operating System
        |
        v
Printer
```

Java is recommended because the main backend is already based on Java/Spring Boot and Java provides access to printing APIs.

---

# 12. Print Agent Communication

The preferred communication mechanism is WebSocket.

Reason:

Polling:

```text
Agent -> Backend
"Any job?"
Agent -> Backend
"Any job?"
Agent -> Backend
"Any job?"
```

creates unnecessary requests.

WebSocket:

```text
Backend
   |
   | persistent connection
   |
   v
Print Agent

New Print Job
      |
      v
Print Agent
```

allows the backend to notify the agent immediately.

REST APIs will still be used for:

- Authentication
- Configuration
- Status updates
- Health checks
- File downloads

---

# 13. Print Agent Authentication

Every Print Agent should have a unique identity.

Example:

```text
Agent ID:
PRINT-AGENT-001

Authentication Token:
<secure-secret>
```

The backend must verify that a print request is coming from an authorized agent.

The Print Agent should never accept arbitrary print commands from the public internet.

---

# 14. Printer Discovery

The Print Agent should be able to detect available printers on the host computer.

Example:

```text
Available Printers

HP LaserJet Pro
Canon G3010
Epson L3250
Microsoft Print to PDF
```

The administrator can configure one printer as the default printer.

Future versions can support multiple printers.

---

# 15. Printing Workflow

The complete printing workflow will be:

```text
User
 |
 | Upload document
 v
Backend
 |
 | Validate file
 v
Page Count Detection
 |
 | User selects
 | B&W / Color
 | Copies
 v
Price Calculation
 |
 v
Print Order Created
 |
 v
Token Generated
 |
 v
Admin Dashboard
 |
 | Click PRINT
 v
Backend
 |
 | Create Print Job
 v
Print Agent
 |
 | Download document
 v
Printer
 |
 v
Printing
 |
 v
Status Update
 |
 v
Database
```

---

# 16. User Upload Workflow

Step 1:

User opens the upload page.

Step 2:

User selects a document.

Step 3:

Frontend validates basic file information.

Step 4:

File is uploaded to backend.

Step 5:

Backend validates:

- File type
- File size
- File integrity
- Security restrictions

Step 6:

Backend calculates page count where supported.

Step 7:

User selects:

```text
Print Type:
( ) Black & White
( ) Color

Copies:
[ 1 ]
```

Step 8:

System calculates the estimated amount.

Step 9:

User submits the print request.

Step 10:

Backend creates a unique token.

Example:

```text
Token: 1052
```

Step 11:

User receives confirmation:

```text
Print request submitted.

Token: #1052
Document: resume.pdf
Pages: 4
Type: Color
Copies: 1
Estimated Amount: ₹20
Status: Pending
```

---

# 17. Pricing System

Pricing must be configurable.

Example:

```text
Black & White: ₹2/page
Color: ₹5/page
```

Formula:

```text
Total Pages =
Document Pages × Copies

Total Amount =
Total Pages × Price Per Page
```

Example:

```text
Document pages = 5
Copies = 2
Print type = B&W
B&W rate = ₹2

Total pages = 5 × 2
            = 10

Total amount = 10 × ₹2
             = ₹20
```

The frontend can show a live estimate, but the backend must perform the final calculation.

This prevents price manipulation from the client side.

---

# 18. Print Order Data

Each print order should contain:

```text
Order ID
Token Number
Document ID
File Name
Page Count
Print Type
Copies
Price Per Page
Total Pages
Total Amount
Status
Created At
Updated At
Printed At
```

---

# 19. Print Status

Recommended states:

```text
PENDING
PRINT_REQUESTED
QUEUED
PRINTING
PRINTED
FAILED
CANCELLED
```

Example:

```text
Token 1052
Status: PRINTING
```

After successful printing:

```text
Token 1052
Status: PRINTED
```

---

# 20. Admin Dashboard

The main dashboard should display:

```text
Total Orders
Pending
Printing
Printed
Failed
Today's Pages
Today's Estimated Revenue
```

The primary order table:

```text
Token | Document | Pages | Type | Copies | Amount | Status | Action
```

Example:

```text
1052 | resume.pdf | 4 | Color | 1 | ₹20 | Pending | PRINT
1053 | form.pdf   | 3 | B&W   | 2 | ₹12 | Pending | PRINT
1054 | photo.jpg  | 1 | Color | 1 | ₹5  | Printed | VIEW
```

---

# 21. One-Click Print Requirement

This is a critical requirement.

When the administrator clicks:

```text
[ PRINT ]
```

the system must:

1. Create/activate the print job.
2. Send the job to the Print Agent.
3. Print using the configured printer.
4. Avoid browser print preview.
5. Avoid browser print dialog.
6. Avoid manual printer selection.
7. Update the print status.

The administrator should not have to perform another confirmation step for normal printing.

---

# 22. Print Queue

The system must support sequential print processing.

Example:

```text
Job 1052 -> PRINTING
Job 1053 -> QUEUED
Job 1054 -> QUEUED
Job 1055 -> QUEUED
```

After 1052 finishes:

```text
Job 1052 -> PRINTED
Job 1053 -> PRINTING
```

This prevents multiple documents from being sent to the printer simultaneously.

---

# 23. Failed Print Jobs

If printing fails:

```text
Status: FAILED
```

The administrator should see:

```text
[ RETRY ]
```

Possible failure reasons:

- Printer offline
- Printer unavailable
- Paper issue
- Invalid document
- Agent disconnected
- Printer communication error

The system should store an error message for debugging.

---

# 24. Advanced Printing Options

The first version should support the most important options:

- Printer
- B&W/Color
- Copies
- Page range
- Portrait/Landscape
- Paper size
- Single-sided/Double-sided where supported

The normal workflow should remain simple.

Advanced options should not interfere with the default one-click print workflow.

---

# 25. Security Requirements

The system should implement:

- HTTPS
- JWT authentication
- Role-based authorization
- Secure file upload
- File type validation
- File size limits
- Secure file names
- Access-controlled document downloads
- Print Agent authentication
- Input validation
- Rate limiting where appropriate
- Audit logging for administrative actions

Users must never be able to access another user's document simply by changing a URL or ID.

---

# 26. API Design

Example APIs:

### User APIs

```http
POST /api/documents/upload
POST /api/print-orders
GET  /api/print-orders/{token}
```

### Admin APIs

```http
POST /api/admin/login
GET  /api/admin/print-orders
GET  /api/admin/print-orders/{id}
POST /api/admin/print-orders/{id}/print
POST /api/admin/print-orders/{id}/retry
POST /api/admin/print-orders/{id}/cancel
```

### Printer APIs

```http
GET  /api/admin/printers
POST /api/admin/printers
PUT  /api/admin/printers/{id}
```

### Pricing APIs

```http
GET /api/pricing
PUT /api/admin/pricing
```

### Print Agent APIs

```http
POST /api/print-agent/register
GET  /api/print-agent/config
POST /api/print-agent/status
POST /api/print-agent/jobs/{id}/status
```

WebSocket:

```text
/ws/print-agent
```

---

# 27. Database Structure

Recommended core tables:

```text
users
admins
documents
print_orders
print_jobs
printers
print_agents
print_rates
audit_logs
```

Relationship:

```text
User
 |
 +---- Documents
          |
          +---- Print Orders
                    |
                    +---- Print Jobs
                              |
                              +---- Printer
                              |
                              +---- Print Agent
```

---

# 28. Suggested Print Order Table

```text
print_orders

id
token
document_id
page_count
print_type
copies
price_per_page
total_pages
total_amount
status
created_at
updated_at
printed_at
```

---

# 29. Future Payment Architecture

Payment is intentionally not implemented in Version 1.

However, the database already stores:

```text
total_amount
```

Later:

```text
print_orders
      |
      +---- payment
               |
               +-- payment_id
               +-- gateway
               +-- amount
               +-- status
               +-- transaction_id
```

Future workflow:

```text
Upload
  ↓
Select B&W/Color
  ↓
Calculate Amount
  ↓
Payment
  ↓
Payment Successful
  ↓
Print Order Created
  ↓
Admin
  ↓
One-click Print
```

This keeps payment independent from the core print engine.

---

# 30. Non-Functional Requirements

## Performance

- Upload interface should provide progress feedback.
- Admin dashboard should load quickly.
- Print commands should reach the Print Agent with minimal delay.
- Print queue must process jobs sequentially.
- Large files should not block the entire application.

## Reliability

- Failed jobs should be recoverable.
- Agent reconnection should be automatic.
- Print status should survive application restart.
- The system should not lose print jobs after a temporary network failure.

## Scalability

The backend should be designed so multiple Print Agents can exist later.

```text
Backend
  |
  +--- Agent 001 -> Printer A
  |
  +--- Agent 002 -> Printer B
  |
  +--- Agent 003 -> Printer C
```

## Maintainability

Frontend, backend, and Print Agent should remain separate modules.

```text
frontend/
backend/
print-agent/
```

---

# 31. Recommended Project Structure

```text
online-printing-system/

├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── layouts/
│   │   ├── services/
│   │   ├── store/
│   │   ├── hooks/
│   │   ├── utils/
│   │   └── theme/
│   └── package.json
│
├── backend/
│   ├── src/main/java/
│   │   └── com/printing/
│   │       ├── controller/
│   │       ├── service/
│   │       ├── repository/
│   │       ├── entity/
│   │       ├── dto/
│   │       ├── security/
│   │       ├── websocket/
│   │       ├── exception/
│   │       └── config/
│   └── pom.xml
│
└── print-agent/
    ├── src/
    │   ├── printer/
    │   ├── websocket/
    │   ├── api/
    │   ├── queue/
    │   ├── config/
    │   └── service/
    └── pom.xml
```

---

# 32. Acceptance Criteria

The system will be considered successful when the following workflow works:

```text
1. User uploads a document.
2. System validates the document.
3. System determines page count where supported.
4. User selects B&W or Color.
5. User selects number of copies.
6. System calculates estimated price.
7. User submits the request.
8. System generates a unique token.
9. Order appears in the admin dashboard.
10. Admin sees the document, token, pages, print type,
    copies, amount and status.
11. Admin clicks PRINT.
12. Print Agent receives the job.
13. Print Agent sends it to the configured physical printer.
14. Browser preview does not open.
15. Browser print dialog does not open.
16. Printer starts printing.
17. System changes status to PRINTED.
18. If printing fails, status becomes FAILED.
19. Admin can retry the failed job.
20. Multiple print requests are processed through a queue.
```

---

# 33. Final Product Definition

The final Version 1 product can be summarized as:

```text
UPLOAD
   ↓
PRINT OPTIONS
   ↓
PRICE
   ↓
TOKEN
   ↓
ADMIN QUEUE
   ↓
ONE-CLICK PRINT
   ↓
LOCAL PRINT AGENT
   ↓
PHYSICAL PRINTER
   ↓
PRINT STATUS
```

The most important design principle is:

**The website manages documents and print orders. The Local Print Agent manages the physical printer.**

This separation makes the system more reliable, secure, and extensible.
