# Technical Guide — Web-Based Help Desk System

This document explains how the codebase actually works, so every team member can understand it deeply enough to build their own feature and explain it in the viva. Read this once before you start writing code.

---

## 1. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database (dev) | H2 (in-memory) |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security (session-based) |
| Build tool | Maven |

You don't need to install Maven separately — IntelliJ handles it automatically when you open the project (it detects `pom.xml`).

---

## 2. Project Structure

We use **package-by-feature**, not package-by-layer. This means each person's work lives in one self-contained folder, so six people can work in parallel without stepping on each other's files.

```
src/main/java/com/helpdesk/
├── HelpdeskApplication.java   ← entry point, don't touch unless necessary
├── common/                    ← shared code used by everyone (e.g. Role enum)
├── auth/                      ← login/logout, shared by the whole system
├── config/                    ← Spring configuration (e.g. SecurityConfig)
├── profile/                   ← F1 — Diroshaan
├── ticket/                    ← F2 — Chamikara
├── ticketportal/              ← F3 — Amarasinghe
├── queue/                     ← F4 — Weerabaddana
├── knowledgebase/             ← F5 — Tharmithan
└── admin/                     ← F6 — Perera
```

Inside **your own package**, follow this same internal shape every time:

```
yourpackage/
├── controller/   REST endpoints (HTTP in, HTTP out)
├── service/      business logic (the actual rules)
├── repository/   database access (Spring Data JPA does the SQL for you)
├── entity/       @Entity classes = database tables
└── dto/          request/response shapes (use once your endpoints get more complex)
```

`profile/` is fully built out already — use it as your reference for what your own package should look like.

---

## 3. Getting Started

1. Clone the repo, then `git checkout develop`.
2. Open the folder in IntelliJ IDEA — it auto-detects the Maven project. Wait for "Load Maven Project" to finish (downloads dependencies, ~1-2 min first time).
3. Run `HelpdeskApplication.java` (green play button). Watch for "Started HelpdeskApplication" with no red errors.
4. Visit `http://localhost:8080/h2-console` to browse the database directly.
   - JDBC URL: `jdbc:h2:mem:helpdeskdb` (not the default `jdbc:h2:~/test` — change it)
   - Username: `sa`, Password: blank
5. **Important:** this database is in-memory. It resets to empty every time you stop and restart the app. That's expected — don't worry about "losing" test data.

---

## 4. How Authentication Works

This is the part everyone's endpoints depend on, so understand it properly rather than treating it as magic.

**The flow:**
1. A student registers via `POST /api/students` — this is public, no login needed yet (you can't log in before you have an account).
2. Their password is never stored as plain text. `StudentService` runs it through `BCryptPasswordEncoder` before saving — this turns `"mypassword123"` into something like `$2a$10$N9qo8uLOickgx2ZMRZoMy...`, a one-way hash that can't be reversed.
3. To log in, the student sends `POST /api/auth/login` with their email and password.
4. Spring Security calls `StudentUserDetailsService`, which looks up the student by email and hands their hashed password back to Security's internal checker.
5. Security re-hashes whatever password was submitted and compares the hashes — if they match, login succeeds and a **session cookie** is issued.
6. Every subsequent request from that browser/Postman session includes that cookie automatically, proving "this request is still logged in."

**The key files, and what each one does:**

| File | Purpose |
|---|---|
| `common/Role.java` | Enum: `STUDENT`, `OFFICER`, `ADMIN` — shared system-wide |
| `profile/entity/Student.java` | Has `password` (hashed, never returned in API responses) and `role` fields |
| `profile/service/StudentService.java` | Hashes the password before saving on registration |
| `auth/StudentUserDetailsService.java` | Translates a `Student` row into something Spring Security understands |
| `config/SecurityConfig.java` | The actual rules: which URLs are public, which need login, password encoder setup |
| `auth/AuthController.java` | The real `/api/auth/login` and `/api/auth/logout` endpoints |

**What's public right now (no login required):** student registration, login/logout themselves, and the H2 console (for development only — this would never be public in a real production system).

**Everything else requires a valid session.** If you build a new controller endpoint and don't explicitly make it public, it's automatically protected — that's the safe default.

**Role-based restriction (for Officer/Admin-only endpoints):** `SecurityConfig` can restrict specific URL patterns to specific roles, e.g. `.requestMatchers("/api/admin/**").hasRole("ADMIN")`. Since Officer/Admin accounts don't exist yet (that's F6, coming in Sprint 3), this isn't fully wired up yet — when Perera builds account provisioning, this is the file to extend.

---

## 5. Building Your Own Entity (the pattern to copy)

Look at `profile/entity/Student.java`, `profile/repository/StudentRepository.java`, `profile/service/StudentService.java`, and `profile/controller/StudentController.java` side by side — that's the complete pattern. Four files, one job each:

- **Entity** — `@Entity` class, fields with validation annotations (`@NotBlank`, `@Email`, etc.), matching getters/setters.
- **Repository** — an interface extending `JpaRepository<YourEntity, Long>`. You get `save()`, `findById()`, `findAll()`, `deleteById()` for free. Add custom finder methods as needed (e.g. `findByStudentId(...)`).
- **Service** — where your actual business logic lives. Controllers should stay thin and just call service methods.
- **Controller** — `@RestController` with `@GetMapping`/`@PostMapping`/etc. methods that call the service and return `ResponseEntity`.

**Never skip straight to writing a controller without a service layer underneath it** — even if it feels like extra steps for something simple, this separation is what makes the codebase testable and is expected by your module's architecture requirements.

---

## 6. API Conventions

- **URL naming:** plural nouns, e.g. `/api/tickets`, not `/api/getTicket`.
- **HTTP methods:** `GET` to read, `POST` to create, `PUT` to update, `DELETE` to remove.
- **Status codes:** `200` success, `201` created, `204` no content (successful delete), `400` bad input, `403` forbidden (not logged in / wrong role), `404` not found.
- **Never expose entities with sensitive fields directly** — `Student.java` uses `@JsonIgnore` on the password field so it can never leak into an API response, even by accident.

---

## 7. Git Workflow

- `main` — protected, requires a PR + 1 approval. Only updates at real milestones (end of a sprint, progress presentation, final submission).
- `develop` — where all real work merges. Still use a PR even here, for a clean history.
- `feature/<short-description>` — one branch per task, e.g. `feature/ticket-entity`, deleted after merging.

**Commit messages** follow [Conventional Commits](https://www.conventionalcommits.org/): `feat: add ticket entity and CRUD endpoints`, `fix: null check on empty category`, `test: add unit tests for TicketService`.

---

## 8. Testing Your Work

- **H2 Console** (`/h2-console`) — see your tables and run SQL directly. Good for confirming your entity actually created the table you expected.
- **Postman** (or similar) — send real HTTP requests to your endpoints. For anything requiring login, log in first via `/api/auth/login` in the same Postman session so the cookie carries over to your next request.
- Test the "unhappy path" too, not just success — what happens if a required field is missing? If someone tries to access something that isn't theirs?

---

## 9. Current Status

| Function | Owner | Status |
|---|---|---|
| F1 — Student Profile | Diroshaan | Registration, profile edit, deactivation built. Auth/RBAC scaffold built. |
| F2 — Ticket Request Engine | Chamikara | Not started |
| F3 — Ticket Lifecycle Portal | Amarasinghe | Bookmark folders built (entity, repository, service, controller, DTOs — create/rename/delete, ownership checks, case-insensitive uniqueness). Bookmark and Feedback entities/repositories built; no service/controller yet for either. Status tracking not started. No tests or frontend yet. |
| F4 — Queue & Resolution | Weerabaddana | Not started |
| F5 — Knowledge Base | Tharmithan | Not started |
| F6 — Analytics/Admin | Perera | Not started |

If you're picking up your own function for the first time: clone, checkout `develop`, branch off it, and build your entity/repository/service/controller following Section 5 above. Push to your own feature branch and open a PR into `develop` when ready — tag a teammate to review if possible.
