# Technical Guide

How the UNIHELP code is organised. See the [README](README.md) for setup.

## 1. Architecture

The backend is a Spring Boot JSON API. The React frontend is built into
`src/main/resources/static`, so Spring serves both on port 8080. The frontend uses a
`HashRouter`, so a page refresh only ever requests `index.html`.

The code is organised by feature. Each function has its own package under
`com.helpdesk`, and every package uses the same layers:

| Layer | Responsibility |
|---|---|
| `controller` | REST endpoints, kept thin |
| `service` | Business rules and transactions |
| `repository` | Spring Data JPA interfaces |
| `entity` | JPA entities (database tables) |
| `dto` | Request and response objects; entities are never returned directly |

Shared packages:

| Package | Contents |
|---|---|
| `auth` | Login, logout, `/api/auth/me`, password change, session revocation |
| `config` | `SecurityConfig` (URL and role rules) |
| `common/user` | `AppUser` with `Student`, `Officer`, `Administrator` subtypes |
| `common/reference` | Departments and categories, and their seeder |
| `common/settings` | `HelpdeskSettings`, the shared system limits (Singleton) |
| `common/files` | `FileTypeDetector`, which checks uploads by their first bytes |
| `common/exception` | `GlobalExceptionHandler` and shared exceptions |
| `common/validation` | Shared validation rules |
| `notification` | Notification delivery (Strategy) and listeners (Observer) |

## 2. Users and roles

All users share the `users` table. `AppUser` uses JOINED inheritance, with
`students`, `officers` and `administrators` tables holding type-specific fields. The
role comes from the subtype: `STUDENT`, `OFFICER` or `ADMIN`.

| State | Meaning |
|---|---|
| Active | Can sign in |
| Suspended | `active = false`; an administrator can reverse it |
| Removed | `deleted_at` set; final, but the row stays so past tickets keep a name |

Students register themselves. Officers and administrators are created by an
administrator (F6). The first administrator is created at startup by
`AdminBootstrapSeeder`; on H2, `DevQueueDataSeeder` also adds a demo officer.

## 3. Security

- Login (`POST /api/auth/login`) starts a server session; the browser sends the session cookie afterwards.
- Passwords are stored as BCrypt hashes.
- Suspending or removing a user ends their open sessions through `SessionRevoker`.
- CSRF protection is off because the app is a JSON API with no server-rendered forms.

| Path | Access |
|---|---|
| Registration, login, `/api/auth/me`, static files | Anyone |
| `/api/queue/**`, `/api/officers/**`, article editing | Officers |
| `/api/admin/**` | Administrators |
| `GET /api/students`, `GET /api/feedback/summary` | Officers and administrators |
| Everything else | Any signed-in user |

Ownership is checked in the services. Requesting another user's record returns
404 rather than 403, so record IDs cannot be probed.

## 4. Design patterns

### Observer

Actions announce what happened, and separate listeners react, so the code that
performs an action does not depend on notifications or history.

| Action | Announced by | Reacting listeners |
|---|---|---|
| Ticket submitted (F2) | `TicketService` → `TicketSubmittedEvent` | `QueueArrivalNotifier` alerts the department's officers |
| Status changed (F4) | `QueueService` → `TicketStatusChangedEvent` | `TicketStatusNotifier` tells the student; `TicketHistoryRecorder` writes the timeline |
| Password changed (F1) | `PasswordService` → `PasswordChangedEvent` | `AccountSecurityNotifier` sends a security alert |
| Feedback submitted (F3) | `FeedbackService` (subject) | `FeedbackReceivedNotifier` (observer) tells the answering officer |

- F1, F2 and F4 use Spring application events. Notifiers run after the transaction
  commits, so a rolled-back change is never announced; the history recorder runs
  inside the transaction, so the status and its history are saved together.
- F3 uses the classic GoF form: `FeedbackSubject` and `FeedbackObserver` interfaces.
  `FeedbackService` keeps a list of observers and notifies each one after saving. A
  failing observer is logged and does not affect the others or the saved rating.

### Strategy

`NotificationService` delivers each message through every `NotificationChannel` the
recipient has enabled. The two strategies are `PortalNotificationChannel` (in-app inbox)
and `EmailNotificationChannel` (logs the email; no mail server is configured). A new
channel, such as SMS, is one new class.

### Singleton

`HelpdeskSettings` (`common/settings`) holds the system-wide limits used by F1 to F5.
It has a private constructor, a private static `volatile` instance and a public
`getInstance()` with double-checked locking. On first use it reads the limits from
`application.properties` once, and every function shares that one object.

Diagram: `docs/diagrams/notification-patterns.puml` (Observer and Strategy).

## 5. Ticket lifecycle

```
OPEN --(officer picks up)--> IN_PROGRESS --(officer posts resolution)--> RESOLVED
OPEN --(student withdraws)--> WITHDRAWN
```

- **F2:** the student submits a ticket with a category and priority. The category
  routes it to a department. Edits, withdrawal and attachments are allowed only while
  OPEN. Each status change is recorded and shown as a timeline.
- **F4:** officers see their departments' queues, pick tickets up, add staff notes
  (never shown to students) and post one resolution per ticket, which marks it RESOLVED.
- **F3:** the student reads the resolution, rates it once, bookmarks tickets into
  folders, searches their tickets and archives RESOLVED or WITHDRAWN tickets.

`Ticket` has a `@Version` field, so concurrent saves return 409 instead of overwriting.

## 6. Configuration

| Setting | Where | Default |
|---|---|---|
| Upload limit (attachments, resolution files) | `spring.servlet.multipart.max-file-size` | 5MB |
| Profile picture limit | `helpdesk.limits.avatar-max-size` | 2MB |
| Ticket search page size / maximum | `helpdesk.limits.ticket-default-page-size` / `ticket-max-page-size` | 20 / 100 |
| Article page size maximum | `helpdesk.limits.article-max-page-size` | 50 |
| Highest page number | `helpdesk.limits.max-page` | 10000 |
| MySQL connection | Environment variables (see `.env.example`) | local server |

`HelpdeskSettings` reads the limits from the base `application.properties` only.

## 7. Files

Attachments, resolution files and profile pictures are stored in the database
(`MEDIUMBLOB` on MySQL). The real type is detected from the file's first bytes:

- attachments and resolution files: PDF, PNG, JPEG, GIF or WebP
- profile pictures: PNG, JPEG or WebP

## 8. Database

- **H2 (default):** created from the entities on every start.
- **MySQL (`mysql` profile):** Hibernate runs with `ddl-auto=update`, which only adds
  tables and columns. Other changes (constraints, column types, data fixes) are in
  `docs/migrations`; run them in date order on databases created by older code.
- `docs/demo_queries.sql` contains read-only queries for demonstrating the schema.

## 9. API overview

| Function | Main endpoints |
|---|---|
| F1 | `/api/auth/*`, `/api/students`, `/api/officers/me`, `/api/notifications` |
| F2 | `/api/tickets`, `/api/tickets/{id}/attachments`, `/api/tickets/{id}/history`, `/api/tickets/{id}/withdraw` |
| F3 | `/api/tickets/search`, `/api/tickets/{id}/resolution`, `/api/tickets/{id}/feedback`, `/api/bookmarks`, `/api/bookmark-folders`, `/api/tickets/{id}/archive`, `/api/tickets/archived` |
| F4 | `/api/queue`, `/api/queue/{id}/status`, `/api/queue/{id}/assign`, `/api/queue/{id}/resolution`, `/api/queue/{id}/notes`, `/api/admin/officers/{id}/supervisor` |
| F5 | `/api/articles`, `/api/articles/manage`, `/api/articles/{id}/publish`, `/api/articles/{id}/related`, `/api/articles/{id}/bookmark` |
| F6 | `/api/admin/dashboard`, `/api/admin/officers`, `/api/admin/administrators`, `/api/admin/users`, `/api/admin/announcements`, `/api/announcements` |
| Shared | `/api/departments`, `/api/categories` |

Errors are returned as JSON by `GlobalExceptionHandler`:

| Status | Meaning |
|---|---|
| 400 | Invalid input or a broken business rule |
| 401 | Not signed in, or the session has ended |
| 403 | Wrong role |
| 404 | Not found, or not yours |
| 409 | Duplicate or concurrent edit |
| 413 | Upload too large |

## 10. Testing and CI

- Tests live in `src/test/java`, in the same packages as the code.
- Unit tests use JUnit 5 and Mockito.
- Integration tests use `@SpringBootTest` with the `test` profile on H2.
- GitHub Actions (`.github/workflows/build.yml`) runs `mvn -B verify` and builds the
  frontend on every pull request into `develop`. It fails if the committed frontend
  bundle does not match the source.

## 11. Frontend

| Path | Purpose |
|---|---|
| `frontend/src/api.js` | All calls to the backend |
| `frontend/src/hooks/useSession.jsx` | The signed-in user |
| `frontend/src/routes.jsx`, `App.jsx` | Routes and role guards |
| `frontend/src/pages/` | Shared pages, plus `student/`, `officer/`, `kb/` and `admin/` |

Use `npm run dev` while developing and `npm run build` before committing.
