# UNIHELP - Web-Based Help Desk System

A help desk web app for university students. Students raise support tickets and follow
them to an answer, help desk officers work through department queues, and
administrators manage accounts and watch how the service is doing. A knowledge base
lets students find answers to common questions before they open a ticket.

SE2030 Software Engineering, SLIIT - group 2026-Y2-S1-MLB-B8G2-04.

## Functions and owners

| | Function | Owner | What it covers |
|---|---|---|---|
| F1 | Accounts, profile and notifications | Diroshaan S (IT25101580) | Registration, login, profile and avatar, contact numbers, password change, account deletion, officer profile, notifications inbox. Also the React frontend and the shared user model. |
| F2 | Ticket submission | Chamikara A. K (IT25102416) | Submitting, editing and withdrawing tickets, file attachments, ticket search, status timeline. |
| F3 | Ticket lifecycle portal | Amarasinghe S. D - Dakshin (IT25103424) | Viewing the officer's answer, feedback and ratings, bookmarks and bookmark folders, archiving closed tickets. |
| F4 | Officer queue and resolutions | Weerabaddana V. P - Vimansa (IT25101250) | Department queues, routing by category, assigning and progressing tickets, writing resolutions, staff notes, officer supervisors. |
| F5 | Knowledge base | Tharmithan P (IT25100375) | Writing, publishing and archiving articles, tags and categories, related articles, search, saved articles. |
| F6 | Admin dashboard | Perera L. S. N - Sanuthmi (IT25103172) | Dashboard statistics, provisioning officer and admin accounts, suspending and removing users, announcements. |

## Tech stack

- Java 17, Spring Boot 3.2.5 (Web, Data JPA, Security, Validation), Maven
- H2 in-memory database for development, MySQL 8 for the shared/demo database
- React 18 + Vite frontend (in `frontend/`), built into `src/main/resources/static`
- JUnit 5, Mockito and Spring Security Test for tests
- GitHub Actions builds and tests every pull request into `develop`

## Running it

You need JDK 17 or newer and Maven (IntelliJ IDEA's bundled Maven is fine).

### On H2 (default, no setup)

```bash
git clone https://github.com/Diroshaan/Help_desk.git
cd Help_desk
mvn spring-boot:run
```

Or open the folder in IntelliJ and run `HelpdeskApplication`. Then go to
http://localhost:8080.

- The database is in memory, so it is empty again after every restart.
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:helpdeskdb`,
  user `sa`, no password).
- A demo officer is created on H2: `officer.demo@helpdesk.local` / `Officer@123`.
- The first admin account is created on startup (`admin@helpdesk.local` by default).
  Its password is printed once in the startup log, unless you set
  `HELPDESK_BOOTSTRAPADMIN_PASSWORD` (and optionally `HELPDESK_BOOTSTRAPADMIN_EMAIL`,
  `HELPDESK_BOOTSTRAPADMIN_DISPLAYNAME`).
- Students register themselves from the sign-up page.

### On MySQL

Turn on the `mysql` profile and give the connection details as environment variables
(the value in brackets is used if you leave one out):

| Variable | Default |
|---|---|
| `MYSQL_HOST` | `localhost` |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DB` | `helpdeskdb` |
| `MYSQL_USER` | `root` |
| `MYSQL_PASSWORD` | (empty) |
| `MYSQL_SSL_MODE` | `REQUIRED` (use `PREFERRED` for a local server without TLS) |

```bash
MYSQL_HOST=... MYSQL_PORT=... MYSQL_DB=... MYSQL_USER=... MYSQL_PASSWORD=... \
  mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

In IntelliJ, set "Active profiles" to `mysql` and add the variables under
Environment variables. Never commit the password. On a local MySQL, create the
database first with `CREATE DATABASE helpdeskdb;`. If the database was created with
older code, run the scripts in `docs/migrations` in date order (each file says how).

### Frontend

The built frontend is committed, so the steps above are enough to use the app. To work
on it:

```bash
cd frontend
npm ci
npm run dev     # http://localhost:5173, /api calls go to Spring on port 8080
npm run build   # writes the bundle into src/main/resources/static - commit it
```

## Tests

```bash
mvn test        # or: mvn verify (what CI runs)
```

The tests use H2 and the `test` profile, so they need no database or network setup.

## Folder layout

```
src/main/java/com/helpdesk/
  HelpdeskApplication.java
  auth/            login, logout, current user, password change (F1)
  config/          SecurityConfig - URL and role rules
  common/          shared user model, departments and categories, file checks, errors
  profile/         F1 student and officer profiles, activity log
  notification/    F1 notifications (Strategy + Observer)
  ticket/          F2 tickets, attachments, status history
  ticketportal/    F3 answer view, feedback, bookmarks, archive
  queue/           F4 queues, routing, resolutions, staff notes, supervision
  knowledgebase/   F5 articles
  admin/           F6 dashboard, provisioning, announcements
src/main/resources/
  application.properties, application-mysql.properties
  static/          built React app
src/test/java/     unit and integration tests
frontend/          React source
docs/
  migrations/      MySQL scripts for databases created by older code
  demo_queries.sql read-only queries for the database demo
  diagrams/        domain model and notification pattern diagrams
  meeting-notes/
  legacy-html/     the first static pages, kept for reference
```

Each feature package uses the same layers: `controller`, `service`, `repository`,
`entity`, `dto`. More detail is in [TECHNICAL_GUIDE.md](TECHNICAL_GUIDE.md).

## Branches

- `main` - milestone releases only, through a reviewed pull request.
- `develop` - all feature work is merged here.
- `feat/<short-name>` - one branch per task.

Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`).
