# Chapter 4 diagrams

The files in this folder are based on the checked-out Java source, JPA mappings, frontend pages and scripts, Maven configuration, Spring Security configuration, application properties, and the available manual MySQL SQL script. Figure numbering below is a suggested Chapter 4 sequence.

| Figure | Diagram | Editable Mermaid source | Vector export |
|---|---|---|---|
| 4.1 | System architecture | [system_architecture.mmd](./system_architecture.mmd) | [system_architecture.svg](./system_architecture.svg) |
| 4.2 | Database entity-relationship diagram | [database_erd.mmd](./database_erd.mmd) | [database_erd.svg](./database_erd.svg) |
| 4.3 | Student use-case diagram | [use_case.mmd](./use_case.mmd) | [use_case.svg](./use_case.svg) |
| 4.4 | AI Buddy chat sequence | [ai_chat_sequence.mmd](./ai_chat_sequence.mmd) | [ai_chat_sequence.svg](./ai_chat_sequence.svg) |
| 4.5 | Application flowchart | [application_flowchart.mmd](./application_flowchart.mmd) | [application_flowchart.svg](./application_flowchart.svg) |
| 4.6 | UML class diagram overview | [class_diagram_overview.mmd](./class_diagram_overview.mmd) | [class_diagram_overview.svg](./class_diagram_overview.svg) |

The class diagram is split into editable module-level Mermaid sources to keep it readable: [authentication](./class_diagram_auth.mmd), [chat](./class_diagram_chat.mmd), [quiz and resources](./class_diagram_learning.mmd), and [planner, activity, settings and analytics](./class_diagram_student_services.mmd). The high-level SVG is a vector overview; the detailed source diagrams show class dependencies and JPA multiplicities. Entity multiplicities reflect the owning-side mappings and uniqueness constraints in the Java source.

## Diagram notes

- **Figure 4.1** shows the browser interface, bundled static pages, Spring Security, REST controllers, application services, Spring Data JPA/Hibernate, MySQL, Gemini, SMTP mail, and PDFBox. The current Maven build includes the `frontend` folder as Spring Boot static resources. The JavaScript also configures API requests for a local development server. PDFBox is a backend library rather than a remote service.
- **Figure 4.2** contains all 13 persistent entity/table mappings found in `backend/src/main/java/com/smartai/buddy/entity`. All table names are explicitly set with `@Table`. `id` is the primary key in every entity. `FK` denotes the `@JoinColumn` relationship. Cardinalities follow those owning-side mappings and unique constraints.
- **Figure 4.3** includes only features found in the public pages, authenticated pages, controller endpoints, and corresponding services: account registration/sign-in/out, email verification, password recovery, AI Buddy, Socratic Tutor, planner, quizzes, resources, PDF upload, dashboard/performance, and settings.
- **Figure 4.4** reflects the implemented `POST /api/student/chat` path. The student message is stored as pending before Gemini is called; on success the assistant response is stored and the student message marked complete. On provider failure the user message is marked failed and an error response is returned. Chat conversations and messages are persisted in MySQL.
- **Figure 4.5** captures the actual public-entry and session behavior: `/` and authentication pages are public, student pages and APIs require authentication, failed login remains on the login page, and successful login redirects to the dashboard. Registration routes to email verification; password recovery uses a mailed reset link. Logout invalidates the session and redirects to the public login page. Feature persistence and external calls are conditional on the selected feature.

Quiz PDF uploads are text-extracted with PDFBox and the quiz-material entity stores the extracted source text and optional filename; raw PDF bytes are not mapped as a database column. Resource PDF recommendations also use extraction without creating a separate PDF-storage entity.

## Schema evidence and limits

The entity annotations verify table names, mapped fields, primary keys, join columns, and unique constraints in source. The manual script `backend/src/main/resources/db/manual/authentication-email-verification-password-reset-mysql.sql` explicitly adds `users.email_verified` and creates `email_verification_codes` and `password_reset_tokens`. `spring.jpa.hibernate.ddl-auto=update` is configured for the other entity mappings. No other schema migration or schema snapshot was found in the repository.

The source explicitly maps these entity classes to table names:

| JPA entity class | Declared table |
|---|---|
| `User` | `users` |
| `EmailVerificationCode` | `email_verification_codes` |
| `PasswordResetToken` | `password_reset_tokens` |
| `ChatConversation` | `chat_conversations` |
| `ChatMessage` | `chat_messages` |
| `LearningActivity` | `learning_activities` |
| `StudyPlan` | `study_plans` |
| `StudySession` | `study_sessions` |
| `StudyMaterial` | `quiz_study_materials` |
| `QuizConcept` | `quiz_concepts` |
| `QuizAttempt` | `quiz_attempts` |
| `QuizAttemptQuestion` | `quiz_attempt_questions` |
| `QuizAnswer` | `quiz_answers` |

The SQL script separately confirms the two authentication tables and the `users.email_verified` column. The other mapped tables are expected to be created or updated by Hibernate, but were not confirmed against a live database.

The live MySQL instance was not queried or modified. Consequently, the ERD describes the schema implied by current entity mappings and the checked-in SQL, not a guarantee that an existing database has already applied every mapping. Hibernate's physical naming strategy determines conventional snake-case columns for unannotated camel-case fields; the ERD uses those column names. In particular, composite unique constraints are defined for `(user_id, purpose)` in `email_verification_codes`, `(conversation_id, request_id)` in `chat_messages`, `(material_id, sequence_number)` in `quiz_concepts`, and `(attempt_id, question_position)` and `(attempt_id, concept_id)` in `quiz_attempt_questions`.

The SQL file's header specifies MySQL 8.0.46 as its target; the configured JDBC URL selects MySQL but does not independently verify the running server version. Gemini and mail credentials are environment-driven and are intentionally not included in these diagrams.

## Re-exporting Mermaid sources

The workspace did not have Node.js, npm, or Mermaid CLI available when these deliverables were prepared. To regenerate each SVG from its Mermaid source on a machine with Node.js/npm, run these commands from the repository root:

```powershell
npx --yes @mermaid-js/mermaid-cli -i docs/diagrams/system_architecture.mmd -o docs/diagrams/system_architecture.svg -b white -t base
npx --yes @mermaid-js/mermaid-cli -i docs/diagrams/database_erd.mmd -o docs/diagrams/database_erd.svg -b white -t base
npx --yes @mermaid-js/mermaid-cli -i docs/diagrams/use_case.mmd -o docs/diagrams/use_case.svg -b white -t base
npx --yes @mermaid-js/mermaid-cli -i docs/diagrams/ai_chat_sequence.mmd -o docs/diagrams/ai_chat_sequence.svg -b white -t base
npx --yes @mermaid-js/mermaid-cli -i docs/diagrams/application_flowchart.mmd -o docs/diagrams/application_flowchart.svg -b white -t base
```
