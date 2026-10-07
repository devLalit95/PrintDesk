
# UI/UX Design Prompt
## Online Document Printing System

Build a polished, modern, production-quality web interface for an Online Document Upload and One-Click Printing System.

The application has two interfaces:

1. Customer/User interface
2. Administrator interface

The product should feel like a modern SaaS dashboard rather than a basic college project.

Do not overuse gradients, glassmorphism, excessive shadows, oversized cards, or decorative animations. Prioritize usability, information hierarchy, responsiveness, and fast interaction.

---

# 1. Technology Requirements

Use:

- React
- Vite
- Material UI (MUI)
- React Router
- Zustand
- Framer Motion
- Axios

Use MUI components wherever practical instead of creating replacements from scratch.

Use a centralized theme.

Create a global CSS system for:

- Typography
- Spacing
- Scrollbars
- Body/background
- Selection
- Focus states
- Transitions
- Utility classes where necessary
- Responsive behavior

Avoid scattered inline styles.

---

# 2. Visual Theme

Create a professional print-service dashboard theme.

The visual language should communicate:

- Reliability
- Speed
- Simplicity
- Accuracy
- Digital document workflow

Use a clean neutral base with one strong primary accent.

Suggested palette:

```text
Background:
#F7F8FA

Surface:
#FFFFFF

Primary:
#2563EB

Primary Dark:
#1D4ED8

Success:
#16A34A

Warning:
#F59E0B

Error:
#DC2626

Text:
#111827

Secondary Text:
#6B7280

Border:
#E5E7EB
```

Do not hard-code colors throughout components. Define them through the MUI theme.

Support both:

- Light mode
- Dark mode

if practical.

---

# 3. Typography

Use a modern sans-serif font.

Recommended:

```text
Inter
```

Typography hierarchy:

```text
Page Title
Section Heading
Card Heading
Body
Caption
Metadata
Table text
Button text
```

Keep typography compact and readable, especially in the admin dashboard.

---

# 4. Global Layout

Desktop:

```text
┌─────────────────────────────────────────────────────┐
│ Header                                              │
├──────────────┬──────────────────────────────────────┤
│ Sidebar      │ Main Content                         │
│              │                                      │
│ Dashboard    │                                      │
│ Orders       │                                      │
│ Printers     │                                      │
│ Pricing      │                                      │
│ History      │                                      │
│ Settings     │                                      │
│              │                                      │
└──────────────┴──────────────────────────────────────┘
```

Mobile:

```text
┌─────────────────────────┐
│ Header       Menu       │
├─────────────────────────┤
│                         │
│ Main Content            │
│                         │
└─────────────────────────┘
```

Use responsive MUI layout components.

---

# 5. User Interface

The user experience should be extremely simple.

Primary flow:

```text
Upload
   ↓
Configure Print
   ↓
Review Price
   ↓
Submit
   ↓
Token
```

---

# 6. User Home Page

Create a clean landing/upload page.

Hero section:

```text
Print Your Documents
Fast. Simple. Ready to Print.

Upload your document, choose your printing preferences,
and receive a token number.
```

Primary CTA:

```text
Upload Document
```

Do not make the landing page overly marketing-heavy.

---

# 7. Upload Component

Create a large drag-and-drop upload area.

Example:

```text
┌─────────────────────────────────────┐
│                                     │
│             Upload File             │
│                                     │
│     Drag & drop your document       │
│              here                   │
│                                     │
│         [ Browse Files ]            │
│                                     │
│ PDF, DOCX, JPG, PNG                 │
└─────────────────────────────────────┘
```

When uploading:

Show:

```text
Uploading...
██████████████████░░░░ 78%
```

Use MUI LinearProgress.

Show file metadata after upload:

```text
resume.pdf
4 pages
2.4 MB
✓ Ready
```

---

# 8. Print Configuration

After upload, display a configuration card.

```text
Print Settings

Print Type

○ Black & White
○ Color

Copies

[-] 1 [+]

Paper Size

[ A4 ▼ ]

Orientation

○ Portrait
○ Landscape
```

Make B&W and Color visually distinct.

When the user changes print type, update the price immediately.

---

# 9. Price Calculation UI

Create a sticky or clearly visible summary card.

Example:

```text
Print Summary

Document       resume.pdf
Pages          4
Print Type     Color
Copies         1

Price / page   ₹5
Total pages    4

──────────────────

Estimated Total
₹20

[ Continue ]
```

The total price should animate subtly when it changes.

Use Framer Motion for number/value transitions where appropriate.

Do not use distracting animations.

---

# 10. Token Confirmation Page

After submitting:

Create a strong success state.

```text
✓ Print Request Submitted

Your Token Number

#1052

Document:
resume.pdf

Pages:
4

Print Type:
Color

Copies:
1

Estimated Amount:
₹20

Status:
Pending
```

Provide:

```text
[ Copy Token ]
[ Submit Another Document ]
```

The token should be visually prominent.

---

# 11. User Status Page

Allow the user to check a token.

```text
Enter Token

[ 1052              ]

[ Check Status ]
```

Result:

```text
Token #1052

✓ Request Received
✓ Processing
○ Printing
○ Completed
```

Use an MUI Stepper or custom status timeline.

---

# 12. Admin Dashboard

The admin dashboard is the most important screen.

It should immediately show operational information.

Top statistics:

```text
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Total       │ │ Pending     │ │ Printing    │ │ Printed     │
│ 128         │ │ 12          │ │ 3           │ │ 113         │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

Use MUI Cards.

Each statistic should have:

- Label
- Value
- Small contextual indicator
- Minimal icon

---

# 13. Admin Order Table

Create a professional data table.

Columns:

```text
Token
Document
Pages
Type
Copies
Amount
Status
Created
Action
```

Example:

```text
#1052
resume.pdf
4
Color
1
₹20
Pending
10:32 PM
[ PRINT ]
```

The PRINT button should be visually dominant.

For pending jobs:

```text
[ PRINT ]
```

For printing:

```text
[ PRINTING... ]
```

For completed:

```text
[ PRINTED ✓ ]
```

For failed:

```text
[ RETRY ]
```

---

# 14. One-Click Print Interaction

The print button must be designed around the core product requirement.

Normal interaction:

```text
Click PRINT
     ↓
Button changes to PRINTING...
     ↓
Print Agent receives job
     ↓
Printer starts
     ↓
Status becomes PRINTED
```

Do not open a browser print dialog.

Do not open a preview page.

Do not force an unnecessary confirmation modal for normal printing.

Use a small loading state:

```text
Printing...
[spinner]
```

After success:

```text
Printed ✓
```

Use Snackbar notifications for feedback.

---

# 15. Advanced Print Settings

Provide an Advanced Settings drawer/dialog.

Use MUI Drawer or Dialog.

Settings:

```text
Printer
[ HP LaserJet ▼ ]

Pages
(•) All
( ) Custom

Page Range
[ 1 - 4 ]

Copies
[ 1 ]

Paper
[ A4 ▼ ]

Orientation
(•) Portrait
( ) Landscape

Color
(•) B&W
( ) Color

Duplex
(•) Single-sided
( ) Double-sided
```

The default Print action should not require opening this interface.

---

# 16. Print Queue Interface

Create a queue panel:

```text
Print Queue

#1052  resume.pdf    Printing...
#1053  form.pdf      Waiting
#1054  notes.pdf     Waiting
#1055  photo.jpg     Waiting
```

Use status chips.

Allow admin to:

- View queue
- Cancel pending jobs
- Retry failed jobs

Do not allow unsafe manipulation of a currently printing job unless the backend supports it.

---

# 17. Printer Management Page

Create:

```text
Printers

HP LaserJet Pro
● Online
Default Printer

Canon G3010
● Online

Epson L3250
○ Offline
```

Actions:

```text
Set Default
Test Print
View Status
```

Show Print Agent status separately:

```text
Print Agent
● Connected
Agent ID: PRINT-AGENT-001
Last heartbeat: 5 seconds ago
```

If disconnected:

```text
Print Agent
● Disconnected
```

Use warning/error styling.

---

# 18. Pricing Management

Admin should be able to configure:

```text
Printing Rates

Black & White
₹2 / page

Color
₹5 / page

[ Edit Rates ]
```

Use an MUI form.

Price changes should be handled by the backend and reflected in the frontend.

---

# 19. Print History

Create a searchable history page.

Filters:

```text
Date
Status
Print Type
Token
Document
Printer
```

Example:

```text
Token | Document | Type | Pages | Amount | Status | Printed At
```

Use pagination.

---

# 20. Search and Filtering

Admin should be able to search by:

- Token
- File name
- Status

Filters:

- Pending
- Queued
- Printing
- Printed
- Failed
- Cancelled

Search should be debounced.

---

# 21. Loading States

Every asynchronous operation must have an appropriate loading state.

Examples:

Upload:

```text
Uploading...
██████████░░░░
```

Dashboard:

```text
Skeleton loaders
```

Print:

```text
Printing...
CircularProgress
```

Login:

```text
Signing in...
CircularProgress
```

Do not leave blank screens while waiting for API responses.

---

# 22. Error States

Create proper error UI.

Example:

```text
Unable to upload document

The file may be too large or unsupported.

[ Try Again ]
```

Printer error:

```text
Printing failed

HP LaserJet appears to be offline.

[ Retry ]
```

Agent disconnected:

```text
Print Agent Offline

The printing service is not connected.
Please check the administrator computer.
```

---

# 23. Animations

Use Framer Motion sparingly.

Recommended animations:

Page transition:

```text
opacity: 0 → 1
y: 8 → 0
```

Cards:

Subtle entrance animation.

Upload:

Drop-zone highlight.

Price:

Subtle number/value transition.

Print:

Button loading state.

Success:

Small checkmark animation.

Avoid:

- Constant floating animations
- Excessive parallax
- Large page transitions
- Long animation durations
- Animation on every table row

The application is an operational tool, so speed and clarity are more important than visual spectacle.

---

# 24. Material UI Requirements

Use MUI components for:

- AppBar
- Drawer
- Button
- IconButton
- Card
- Paper
- Table
- Chip
- Dialog
- Drawer
- Snackbar
- Alert
- Tooltip
- TextField
- Select
- RadioGroup
- Checkbox
- LinearProgress
- CircularProgress
- Skeleton
- Tabs
- Pagination
- Stepper
- Menu
- Avatar

Create reusable components instead of repeating MUI configurations.

---

# 25. Reusable Components

Create:

```text
AppLayout
Sidebar
TopBar
StatCard
UploadDropzone
FileCard
PrintSettings
PriceSummary
TokenCard
OrderTable
StatusChip
PrintButton
PrintQueue
PrinterCard
AgentStatus
PriceCard
SearchBar
FilterBar
EmptyState
ErrorState
LoadingState
ConfirmDialog
NotificationSnackbar
```

---

# 26. State Management

Use Zustand for global state where appropriate.

Possible stores:

```text
authStore
printOrderStore
adminStore
printerStore
uiStore
```

Do not put every local form field into global state.

Use local React state for temporary form data.

---

# 27. API Layer

Create a centralized Axios configuration.

Example:

```text
src/services/
    api.js
    authApi.js
    documentApi.js
    printOrderApi.js
    printerApi.js
    pricingApi.js
```

Handle:

- Authentication
- Request errors
- Response errors
- Token expiration
- Loading state

Do not scatter Axios calls across random components.

---

# 28. Responsive Design

The application must work on:

- Desktop
- Laptop
- Tablet
- Mobile

Admin dashboard should prioritize desktop/tablet because printing operations will generally happen from an administrator workstation.

User upload interface must be highly usable on mobile.

---

# 29. Accessibility

Implement:

- Keyboard navigation
- Visible focus states
- Accessible labels
- Sufficient contrast
- ARIA labels where necessary
- Tooltips for icon-only actions
- Accessible loading and error messages

Never communicate status through color alone.

For example:

```text
● Online
```

should also include text:

```text
Online
```

---

# 30. UX Principle

The most important UX rule is:

**Reduce the number of decisions required from the user and reduce the number of clicks required from the administrator.**

User:

```text
Upload
→ Select B&W/Color
→ Select copies
→ See price
→ Submit
```

Admin:

```text
See request
→ PRINT
```

That simplicity should remain the central design principle throughout the application.

---

# 31. Overall Visual Direction

The final interface should look like a modern production SaaS application for a real printing business.

It should be:

- Clean
- Professional
- Fast
- Responsive
- Information-dense where necessary
- Minimal where possible
- Consistent
- Accessible
- Easy to operate for long periods

Do not make it look like a generic dashboard template.

Create a coherent visual identity around documents, print jobs, tokens, queues, printers, and operational status.

The primary visual focus should always remain on:

```text
DOCUMENT
TOKEN
PRINT SETTINGS
PRICE
STATUS
PRINT ACTION
```

The application should feel calm even when many print jobs are present. The UI should communicate the state of the physical printing operation clearly and immediately.
