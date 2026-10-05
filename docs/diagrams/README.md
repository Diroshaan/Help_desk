# Diagrams

| File | What it shows |
|---|---|
| `domain-model.png` | Class diagram of the entities: the user hierarchy (JOINED inheritance), tickets and their attachments, notes and feedback, the knowledge base, announcements and notifications. |
| `notification-patterns.puml` | The notification module: Strategy (`NotificationChannel` and its channels, `NotificationService`) and Observer (events and listeners). |

The `.puml` file can be opened with the PlantUML plugin in IntelliJ.

Still to add from the lab work: use case, activity and sequence diagrams, and the
EER diagram.

If you change an entity or the notification classes, update the matching diagram in
the same pull request.
