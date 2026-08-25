# Web-Based Help Desk System

SE2030 Software Engineering | Group MLBB8G204

A Spring Boot web application for centralising student support requests at Sabaragamuwa Metropolitan University.

## Team

| Member | Function | Package |
|---|---|---|
| Diroshaan S (Product Owner, Repo maintainer) | F1 - Student Profile & Preferences | `profile` |
| Chamikara A. K | F2 - Advanced Ticket Request Engine | `ticket` |
| Amarasinghe S. D (Scrum Master) | F3 - Ticket Lifecycle & Interaction Portal | `ticketportal` |
| Weerabaddana V. P | F4 - Help Desk Ticket Resolution & Queue Engine | `queue` |
| Tharmithan P | F5 - Knowledge Base & FAQ Publishing Portal | `knowledgebase` |
| Perera L. S. N | F6 - System Analytics, Provisioning & Announcements | `admin` |

## Running the project locally

1. Clone the repo and switch to the `develop` branch.
2. Open the project folder in IntelliJ IDEA (or your IDE of choice) - it will detect the `pom.xml` automatically.
3. Run it:
   - **From your IDE:** right-click `HelpdeskApplication.java` -> Run
   - **From a terminal:** `./mvnw spring-boot:run` (Mac/Linux) or `mvnw.cmd spring-boot:run` (Windows)
4. The app starts at **http://localhost:8080**
5. Browse the database at **http://localhost:8080/h2-console**
   - JDBC URL: `jdbc:h2:mem:helpdeskdb`
   - Username: `sa`, Password: *(leave blank)*

## Project structure

Each person owns one top-level package under `src/main/java/com/helpdesk/`, following the same internal shape:

```
<yourpackage>/
├── controller/   REST endpoints
├── service/      business logic
├── repository/   Spring Data JPA interfaces
├── entity/       @Entity database model classes
└── dto/          request/response objects (never expose entities directly)
```

See `profile/` for a complete working example (Student entity + repository + service + controller) built for F1 - copy this pattern for your own package.

## Branch workflow

- `main` - always working, demo-ready
- `develop` - integration branch, everyone merges here via Pull Request
- `feature/<function>-<short-description>` - one branch per task (e.g. `feature/ticket-attachment-upload`)

At least one teammate reviews a PR before it merges into `develop`.

## Docs

- `docs/diagrams/` - use case, ER, class, activity diagrams
- `docs/meeting-notes/` - standup and sprint notes
