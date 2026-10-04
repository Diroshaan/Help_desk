# Technical Guide

How the UNIHELP code fits together. Read the [README](README.md) first for how to run it.

## 1. Architecture

The backend is a Spring Boot JSON API. The React frontend is built into
`src/main/resources/static`, so Spring serves both from port 8080. The frontend uses
a `HashRouter`, so refreshing a page only ever asks Spring for `index.html`.

The code is split by feature, not by layer. Each team member owns one package under
`com.helpdesk`, and inside it the layers are always the same:

```
controller/   REST endpoints, kept thin
service/      business rules and transactions
repository/   Spring Data JPA interfaces
entity/       JPA entities (database tables)
dto/          request and response classes
```

Controllers never return entities directly. They return DTOs, so fields like the
password hash can't leak into a response.

Shared code lives outside the feature packages:

| Package | What it holds |
|---|---|
| `auth` | Login, logout, `/api/auth/me`, password change, ending sessions |
| `config` | `SecurityConfig` |
| `common/user` | The user model: `AppUser`, `Student`, `Officer`, `Administrator`, `Role` |
| `common/reference` | Departments and categories, plus the seeder that loads them |
| `common/files` | `FileTypeDetector` - checks uploads by their first bytes |
| `common/exception` | `GlobalExceptionHandler` and shared exceptions |
| `common/validation` | Shared validation rules |
| `notification` | Notifications (see section 4) |

## 2. Users and roles

All users share one `users` table. `AppUser` is an abstract entity with JOINED
inheritance, and `students`, `officers` and `administrators` each hold the fields only
that type has, keyed by the same id. There is no role column: `getRole()` returns
`STUDENT`, `OFFICER` or `ADMIN` depending on the subclass.

Accounts can be:

- active
- suspended (`active = false`) - can be undone by an admin
- removed (`deleted_at` set) - final; the row stays so old tickets still show a name

Students register themselves. Officers and administrators are created by an admin
(F6). The first administrator is created at startup by `AdminBootstrapSeeder` when no
active admin exists, and on H2 `DevQueueDataSeeder` adds a demo officer.

## 3. Login and access rules

- `POST /api/auth/login` checks the email and password through Spring Security
  (`StudentUserDetailsService` loads any type of user) and starts a session. The
  browser sends the session cookie with every later request.
- Passwords are stored as BCrypt hashes.
- Suspended and removed users can't log in, and `SessionRevoker` ends their open
  sessions straight away. An ended session gets a 401 and the frontend signs out.
- CSRF is turned off because this is a JSON API with no server-rendered forms.

`SecurityConfig` holds the URL rules:

| Path | Who |
|---|---|
| `POST /api/students`, `POST /api/auth/login`, `GET /api/auth/me`, static files | Anyone |
| `/api/queue/**`, `/api/officers/**`, article writing endpoints | Officers |
| `/api/admin/**` | Administrators |
| `GET /api/students`, `GET /api/feedback/summary` | Officers and administrators |
| Everything else | Any logged-in user |

Ownership is checked in the services. A student asking for someone else's ticket gets
404, not 403, so ticket ids can't be probed.

## 4. Design patterns

Both are in `com.helpdesk.notification`.

- **Strategy** - `NotificationChannel` is the strategy interface, with
  `PortalNotificationChannel` (saves to the in-app inbox) and
  `EmailNotificationChannel` (logs the email, as there is no mail server).
  `NotificationService` sends through every channel the recipient has switched on.
- **Observer** - services publish events instead of calling the notifier directly:
  `TicketSubmittedEvent`, `TicketStatusChangedEvent`, `PasswordChangedEvent` and
  `FeedbackSubmittedEvent`. Listeners such as `TicketStatusNotifier` and
  `QueueArrivalNotifier` react to them. They run after the transaction commits, and
  in their own transaction, so a failed notification can't undo the real change.

The diagram is `docs/diagrams/notification-patterns.puml`.

## 5. Ticket lifecycle

```
OPEN --(officer takes it)--> IN_PROGRESS --(officer posts resolution)--> RESOLVED
OPEN --(student withdraws)--> WITHDRAWN
```

- F2: a student submits a ticket with a category and priority. The category decides
  the department it is routed to. Students can edit, withdraw or add attachments only
  while the ticket is OPEN. Every status change is saved in `ticket_status_changes`
  and shown as a timeline.
- F4: officers see the queue for their departments, move tickets to IN_PROGRESS, add
  staff notes (never shown to students) and post one resolution per ticket, which
  marks it RESOLVED. Deleting the resolution puts the ticket back to IN_PROGRESS.
- F3: the student reads the resolution, gives one feedback rating per resolved
  ticket, bookmarks tickets into folders, and archives RESOLVED or WITHDRAWN tickets.

`Ticket` has an `@Version` field, so if two people save the same ticket at once the
second save gets a 409 instead of silently overwriting the first.

## 6. Files

Attachments, resolution files and avatars are stored in the database (`MEDIUMBLOB`
on MySQL). The type is checked from the file's first bytes, not its name:

- ticket attachments and resolution files: PDF, PNG, JPEG, GIF, WebP, up to 5MB
- profile pictures: PNG, JPEG, WebP, up to 2MB

## 7. Database

- H2 (default): created from the entities on every start, nothing to set up.
- MySQL (`mysql` profile): Hibernate runs with `ddl-auto=update`, which only adds
  tables and columns. Changes it can't make (dropping or retyping columns, foreign keys
  on id fields, unique constraints on old data) are in `docs/migrations`. Run those in
  date order on a database created by older code; each file says what it does and
  whether it is safe to run again.
- `docs/demo_queries.sql` holds read-only queries for showing the schema.

## 8. API overview

| Function | Main endpoints |
|---|---|
| F1 | `/api/auth/*`, `/api/students`, `/api/students/{id}/avatar`, `/api/officers/me`, `/api/notifications` |
| F2 | `/api/tickets`, `/api/tickets/search`, `/api/tickets/{id}/attachments`, `/api/tickets/{id}/history`, `/api/tickets/{id}/withdraw` |
| F3 | `/api/tickets/{id}/resolution`, `/api/tickets/{id}/feedback`, `/api/bookmarks`, `/api/bookmark-folders`, `/api/tickets/{id}/archive`, `/api/tickets/archived` |
| F4 | `/api/queue`, `/api/queue/{id}/status`, `/api/queue/{id}/assign`, `/api/queue/{id}/resolution`, `/api/queue/{id}/notes`, `/api/admin/officers/{id}/supervisor` |
| F5 | `/api/articles`, `/api/articles/manage`, `/api/articles/{id}/publish`, `/api/articles/{id}/related`, `/api/articles/{id}/bookmark` |
| F6 | `/api/admin/dashboard`, `/api/admin/officers`, `/api/admin/administrators`, `/api/admin/users`, `/api/admin/announcements`, `/api/announcements` |
| Shared | `/api/departments`, `/api/categories` |

Errors come back as JSON from `GlobalExceptionHandler`: 400 for invalid input, 403 when
not logged in or the wrong role, 404 when not found (or not yours), 409 for duplicates
and edit conflicts. `GET /api/auth/me` and an ended session return 401.

## 9. Tests

```bash
mvn test
```

Tests are in `src/test/java`, in the same packages as the code. Unit tests use JUnit 5
and Mockito. Integration tests use `@SpringBootTest` with the `test` profile on H2 and
Spring Security Test to log in as a student, officer or admin. GitHub Actions
(`.github/workflows/build.yml`) runs `mvn -B verify` and builds the frontend on every
pull request into `develop`, and fails if the committed frontend bundle is out of date.

## 10. Frontend

`frontend/src` contains:

- `api.js` - one place for all calls to the backend
- `hooks/useSession.jsx` - the logged-in user, loaded from `/api/auth/me`
- `routes.jsx`, `App.jsx` - the routes and which roles may open each page
- `pages/` - shared pages (login, register, profile, notifications) plus `student/`,
  `officer/`, `kb/` and `admin/`

Run `npm run dev` while working on it, and `npm run build` before committing.
