# 🎓 Web-Based Help Desk System

A centralised support ticket platform for university students, built to replace scattered emails, phone calls, and office visits with one structured, trackable web portal.

**SE2030 — Software Engineering** · Sri Lanka Institute of Information Technology · Group MLBB8G204

---

## 📋 What This System Does

Students submit categorised support tickets with file attachments and track them through to resolution. Help Desk Officers work from organised departmental queues instead of an inbox. Administrators oversee accounts, permissions, and system-wide performance through live dashboards. A searchable knowledge base lets students self-resolve common issues before ever filing a ticket.

## ✨ Core Features

| # | Feature | Description |
|---|---|---|
| F1 | Student Profile & Preferences | Registration, profile management, notification preferences, account deactivation |
| F2 | Advanced Ticket Request Engine | Ticket submission with categories, priorities, and file attachments |
| F3 | Ticket Lifecycle & Interaction Portal | Status tracking, bookmarking, feedback and ratings |
| F4 | Ticket Resolution & Queue Engine | Departmental queues, status transitions, official responses, internal notes |
| F5 | Knowledge Base & FAQ Publishing | Searchable self-service articles, staff publishing tools |
| F6 | Analytics, Provisioning & Announcements | Account provisioning, executive dashboards, system-wide notices |

Plus shared infrastructure: authentication & session handling, input validation, file upload safety checks, and responsive navigation — used across every feature above rather than owned by one person.

## 🛠️ Tech Stack

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![Maven](https://img.shields.io/badge/Build-Maven-blue)
![H2](https://img.shields.io/badge/Database-H2%20(dev)-lightgrey)

Java 17 · Spring Boot 3.2 · Spring Data JPA · Spring Security (session-based) · H2 (development database) · Maven

## 👥 Team

| Member | Student ID | Owns | Role |
|---|---|---|---|
| Diroshaan S | IT25101580 | F1 — Student Profile & Preferences | Product Owner, Repo maintainer |
| Chamikara A. K | IT25102416 | F2 — Advanced Ticket Request Engine | Developer |
| Amarasinghe S. D | IT25103424 | F3 — Ticket Lifecycle & Interaction Portal | Scrum Master |
| Weerabaddana V. P | IT25101250 | F4 — Ticket Resolution & Queue Engine | Developer |
| Tharmithan P | IT25100375 | F5 — Knowledge Base & FAQ Publishing | Developer |
| Perera L. S. N | IT25103172 | F6 — Analytics, Provisioning & Announcements | Developer |

## 🚀 Getting Started

```bash
git clone https://github.com/Diroshaan/Help_desk.git
cd Help_desk
git checkout develop
```

Open the folder in **IntelliJ IDEA** — it auto-detects the Maven project and downloads dependencies on first load. Then run `HelpdeskApplication.java`.

The app starts at **http://localhost:8080**. Browse the database at **http://localhost:8080/h2-console**:
- JDBC URL: `jdbc:h2:mem:helpdeskdb`
- Username: `sa` · Password: *(blank)*

> ⚠️ The database is in-memory — it resets every time the app restarts. This is expected during development.

## 📁 Project Structure

Package-by-feature: each person owns one top-level package, so six people can build in parallel without conflicts.

```
src/main/java/com/helpdesk/
├── HelpdeskApplication.java
├── common/          shared code (e.g. Role enum)
├── auth/            login/logout — shared infrastructure
├── config/          Spring configuration (SecurityConfig)
├── profile/         F1 — Diroshaan
├── ticket/          F2 — Chamikara
├── ticketportal/    F3 — Amarasinghe
├── queue/           F4 — Weerabaddana
├── knowledgebase/   F5 — Tharmithan
└── admin/           F6 — Perera
```

Each package follows the same internal shape: `controller/ → service/ → repository/ → entity/ → dto/`. See `profile/` for a fully worked example.

## 📖 Documentation

- **[docs/TECHNICAL_GUIDE.md](docs/TECHNICAL_GUIDE.md)** — start here. Explains the authentication system in depth, the exact pattern to follow for building your own entity, API conventions, and git workflow.
- **[docs/diagrams/](docs/diagrams/)** — use case, ER, class, and activity diagrams.
- **[docs/meeting-notes/](docs/meeting-notes/)** — standup and sprint notes.

## 🌿 Branch Workflow

- `main` — protected, requires a PR + 1 approval. Only updates at real milestones.
- `develop` — integration branch. All feature work merges here first.
- `feature/<short-description>` — one branch per task, deleted after merging.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `feat: ...`, `fix: ...`, `docs: ...`, `test: ...`.

## 📊 Sprint Status

| Sprint | Weeks | Focus | Status |
|---|---|---|---|
| Sprint 1 | 4–6 | Foundation: schema, auth, provisioning, profiles | 🟡 In progress |
| Sprint 2 | 7–9 | End-to-end ticket workflow | ⬜ Not started |
| Sprint 3 | 10–12 | Officer tooling, knowledge base, tracking | ⬜ Not started |
| Sprint 4 | 13–14 | Admin, reporting, feedback, integration | ⬜ Not started |

---

*Built as part of the SE2030 Software Engineering module. Not a production system — H2 in-memory database and simplified auth are development-stage choices, documented in the project proposal's System Limitations section.*
