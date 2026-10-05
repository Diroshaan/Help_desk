# UNIHELP – University Help Desk System

UNIHELP is a web-based help desk for university students. Students raise support
tickets and follow them to an answer, help desk officers work through department
queues, and administrators manage accounts and monitor the service. A knowledge base
lets students find answers before they open a ticket.

SE2030 Software Engineering, SLIIT – Group 2026-Y2-S1-MLB-B8G2-04.

## Team and functions

| | Function | Owner |
|---|---|---|
| F1 | Accounts, profiles and notifications (also the React frontend) | Diroshaan S (IT25101580) |
| F2 | Ticket submission, attachments and status timeline | Chamikara A. K (IT25102416) |
| F3 | Ticket lifecycle portal: answers, feedback, bookmarks, archive, search | Amarasinghe S. D (IT25103424) |
| F4 | Officer queue, routing, resolutions and staff notes | Weerabaddana V. P (IT25101250) |
| F5 | Knowledge base articles | Tharmithan P (IT25100375) |
| F6 | Admin dashboard, account provisioning and announcements | Perera L. S. N (IT25103172) |

## Tech stack

- **Backend:** Java 17, Spring Boot 3.2.5 (Web, Data JPA, Security, Validation), Maven
- **Database:** H2 (in memory, default) or MySQL 8 (`mysql` profile)
- **Frontend:** React 18 and Vite, built into `src/main/resources/static`
- **Tests and CI:** JUnit 5, Mockito, Spring Security Test, GitHub Actions

## Getting started

Requirements: JDK 17 or newer and Maven (IntelliJ IDEA's bundled Maven is fine).

```bash
git clone https://github.com/Diroshaan/Help_desk.git
cd Help_desk
mvn spring-boot:run
```

Open http://localhost:8080. In IntelliJ you can run `HelpdeskApplication` instead.

The default profile uses an in-memory H2 database, so no setup is needed and the data
resets on every restart.

| Account | How to sign in |
|---|---|
| Administrator | `admin@helpdesk.local`; the password is printed once in the startup log (or set `HELPDESK_BOOTSTRAPADMIN_PASSWORD`) |
| Demo officer (H2 only) | `officer.demo@helpdesk.local` / `Officer@123` |
| Student | Register from the sign-up page |

The H2 console is at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:helpdeskdb`,
user `sa`, no password).

## Running on MySQL

1. Copy `.env.example`, fill in your values, and set them as environment variables
   (in IntelliJ: Run configuration → Environment variables).
2. Activate the `mysql` profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

On a new local server, create the database first with `CREATE DATABASE helpdeskdb;`.
For a database created by older code, run the scripts in `docs/migrations` in date order.

Never commit real passwords. `.env` files are ignored by Git; only `.env.example` is shared.

## Frontend development

The built frontend is committed, so the steps above are enough to run the app.
To change it:

```bash
cd frontend
npm ci
npm run dev     # http://localhost:5173, API calls go to Spring on port 8080
npm run build   # rebuilds src/main/resources/static – commit the result
```

## Tests

```bash
mvn test        # or: mvn verify (what CI runs)
```

Tests run on H2 with the `test` profile and need no database or network.

## Project structure

```
src/main/java/com/helpdesk/
  auth/           login, logout, password change
  config/         security rules
  common/         shared user model, reference data, settings, file checks, errors
  profile/        F1 profiles
  notification/   F1 notifications
  ticket/         F2 tickets (and F3 ticket search)
  ticketportal/   F3 answers, feedback, bookmarks, archive
  queue/          F4 queue and resolutions
  knowledgebase/  F5 articles
  admin/          F6 dashboard and administration
src/main/resources/  configuration and the built frontend
src/test/java/       unit and integration tests
frontend/            React source
docs/                migrations, demo queries and diagrams
```

See the [Technical Guide](TECHNICAL_GUIDE.md) for the architecture, design patterns and API.

## Contributing

- `develop` is the integration branch; `main` holds milestone releases.
- Create one branch per task (`feat/...` or `fix/...`) and open a pull request into `develop`.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`).
- CI must pass before merging.
