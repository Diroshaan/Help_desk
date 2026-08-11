# Web-Based Help Desk — Full Project Structure

This maps out **every file the finished project should have**, across all 6 team members' functions, following the "package-by-feature" pattern already established in F1.

**Legend:** ✅ Built & merged · 🟡 Partially built · ⬜ Not started yet

---

## 1. Full Folder Tree

```
helpdesk-system/
└── src/main/java/com/helpdesk/
    │
    ├── profile/            ← F1: Diroshaan (Student Profile & Preferences)
    ├── ticket/              ← F2: Chamikara (Advanced Ticket Request Engine)
    ├── ticketportal/        ← F3: Amarasinghe (Ticket Lifecycle & Interaction Portal)
    ├── queue/               ← F4: Weerabaddana (Ticket Resolution & Queue Engine)
    ├── knowledgebase/       ← F5: Tharmithan (Knowledge Base & FAQ Portal)
    ├── admin/               ← F6: Perera (Analytics, Provisioning & Announcements)
    │
    ├── auth/                ← Shared: login/logout/session
    ├── common/              ← Shared: Role enum, shared exceptions
    ├── config/              ← Shared: security & app configuration
    │
    └── HelpdeskApplication.java   ← Main entry point

    src/main/resources/
    └── application.properties     ← Database connection settings
```

**Why this structure?** Instead of grouping *all* controllers together, *all* entities together, etc. (package-by-layer), each feature gets its own folder containing everything it needs (package-by-feature). This means six people can work on six different folders without stepping on each other's files — good for avoiding merge conflicts.

---

## 2. Function-by-Function Breakdown

### `profile/` — F1: Student Profile & Preferences (Diroshaan) ✅

| File | Purpose | Status |
|---|---|---|
| `Student.java` | Entity — defines what a student record looks like (ID, name, department, contact, role, password) | ✅ |
| `StudentRepository.java` | Talks directly to the database — save/find/delete students | ✅ |
| `StudentService.java` | Business logic — registration rules, profile updates, deactivation | ✅ |
| `StudentController.java` | API endpoints (`/api/students/...`) — includes self-access check (IDOR fix) | ✅ |

---

### `ticket/` — F2: Advanced Ticket Request Engine (Chamikara) ⬜

| File | Purpose | Status |
|---|---|---|
| `Ticket.java` | Entity — ticket description, category, status, attachments | ⬜ |
| `Attachment.java` | Entity — file metadata for uploaded PDFs/images | ⬜ |
| `TicketRepository.java` | Database access for tickets | ⬜ |
| `AttachmentRepository.java` | Database access for attachments | ⬜ |
| `TicketService.java` | Logic — submit, edit while OPEN, category tagging, file validation (max 5MB, PDF/image only) | ⬜ |
| `TicketController.java` | Endpoints for submitting/editing/withdrawing tickets | ⬜ |

---

### `ticketportal/` — F3: Ticket Lifecycle & Interaction Portal (Amarasinghe) ⬜

| File | Purpose | Status |
|---|---|---|
| `Bookmark.java` | Entity — a student's saved/bookmarked ticket | ⬜ |
| `Feedback.java` | Entity — rating/feedback left on a resolved ticket | ⬜ |
| `BookmarkRepository.java` | Database access for bookmarks | ⬜ |
| `FeedbackRepository.java` | Database access for feedback | ⬜ |
| `TicketPortalService.java` | Logic — filtering by status, folder organization, feedback rules | ⬜ |
| `TicketPortalController.java` | Endpoints for tracking, bookmarking, feedback | ⬜ |

---

### `queue/` — F4: Ticket Resolution & Queue Engine (Weerabaddana) ⬜

| File | Purpose | Status |
|---|---|---|
| `Resolution.java` | Entity — an officer's official response to a ticket | ⬜ |
| `StaffNote.java` | Entity — internal notes visible only to officers | ⬜ |
| `ResolutionRepository.java` | Database access for resolutions | ⬜ |
| `StaffNoteRepository.java` | Database access for staff notes | ⬜ |
| `QueueService.java` | Logic — status transitions (OPEN→IN_PROGRESS→RESOLVED), queue routing | ⬜ |
| `QueueController.java` | Endpoints under `/api/queue/**` — **already protected by `hasRole("OFFICER")` in SecurityConfig, ready for you to build against** | ⬜ |

---

### `knowledgebase/` — F5: Knowledge Base & FAQ Publishing Portal (Tharmithan) ⬜

| File | Purpose | Status |
|---|---|---|
| `Article.java` | Entity — FAQ article content, category, keywords | ⬜ |
| `ArticleRepository.java` | Database access for articles | ⬜ |
| `KnowledgeBaseService.java` | Logic — publishing, search, archiving | ⬜ |
| `KnowledgeBaseController.java` | Endpoints for browsing/searching/managing FAQs | ⬜ |

---

### `admin/` — F6: Analytics, Provisioning & Announcements (Perera) ⬜

| File | Purpose | Status |
|---|---|---|
| `Announcement.java` | Entity — a noticeboard announcement | ⬜ |
| `AnnouncementRepository.java` | Database access for announcements | ⬜ |
| `AdminService.java` | Logic — account provisioning, role changes, analytics aggregation | ⬜ |
| `AdminController.java` | Endpoints under `/api/admin/**` — **already protected by `hasRole("ADMIN")` in SecurityConfig, ready for you to build against** | ⬜ |

---

## 3. Shared / Cross-Cutting Files (F7)

| File | Purpose | Status |
|---|---|---|
| `common/Role.java` | Enum: `STUDENT`, `OFFICER`, `ADMIN` | ✅ |
| `auth/StudentUserDetailsService.java` | Converts a student's stored role into a Spring Security authority | ✅ |
| `auth/AuthController.java` | Login (`POST /api/auth/login`) and logout (`POST /api/auth/logout`) endpoints | ✅ |
| `config/SecurityConfig.java` | Central security rules — session policy, RBAC per endpoint | ✅ (RBAC just enabled) |
| `application.properties` | Database connection settings — **needs updating from H2 to MySQL/PostgreSQL** | 🟡 |

---

## 4. Instructions for Teammates Building Their Function

Each new function should follow this exact 4-file pattern (matching F1):

1. **Entity** — plain data class annotated `@Entity`, mapped to a database table.
2. **Repository** — an interface extending `JpaRepository<YourEntity, Long>`. Spring auto-generates the database queries.
3. **Service** — where the actual logic lives (validation, business rules). Injected into the Controller via constructor.
4. **Controller** — the REST API layer. Annotated `@RestController`, delegates to the Service, returns proper HTTP status codes (200, 201, 403, 404, etc.).

**Important:** if your endpoints should be restricted by role (e.g., Officer-only or Admin-only), you don't need to write that logic yourself — `SecurityConfig.java` already enforces it based on your URL path (`/api/queue/**` → OFFICER, `/api/admin/**` → ADMIN). Just build your controller under the matching path.

Refer to `TECHNICAL_GUIDE.md` in the repo for the full walkthrough of how auth, validation, and the entity/repository/service/controller pattern work, using F1 as the working example.
