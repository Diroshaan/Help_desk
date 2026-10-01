# Diagrams

Design diagrams for the UNIHELP help desk (SE2030, group 2026-Y2-S1-MLB-B8G2-04).
Each one is kept as its source (`.puml`, `.drawio`) wherever possible, so it can be
regenerated when the code changes.

| File | What it shows | Source |
|---|---|---|
| `domain-model.png` | Class diagram of the persistent domain: the `AppUser` hierarchy (JOINED inheritance), tickets and their weak entities, the knowledge base, announcements, notifications | IntelliJ + PlantUML, from the entity classes |
| `notification-patterns.puml` | Class diagram of the notification module: **Strategy** (`NotificationChannel` and its channels, `NotificationService`) and **Observer** (events, listeners, publishers) | PlantUML |

## Still to add

Add these from the lab submissions, and use the same file names in the report so the
report and the repository stay consistent:

- `use-case.pdf`: use case diagram (Lab: Usecase)
- `activity.pdf`: activity diagram (Lab: Activity)
- `sequence.pdf`: sequence diagrams (Lab: Sequence)
- `eer.pdf`: EER diagram (DDD assignment 1)

## Keeping them true

- When an entity gains or loses a field or a relationship, update the class diagram
  in the same pull request.
- The two design patterns the project presents are Strategy and Observer. Their code is
  in `src/main/java/com/helpdesk/notification/`, and `notification-patterns.puml` must
  match it.
