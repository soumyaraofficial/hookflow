# ADR-001: Modular monolith + separate worker, with transactional outbox

## Status

Accepted

## Context

HookFlow needs an API and background delivery. Splitting into many
microservices adds network calls and distributed-transaction complexity.

Writing to the database and then directly to Kafka can lose messages
because the database commit and Kafka publish are not one atomic operation.
This is known as the dual-write problem.

## Decision

1. Use one Spring Boot codebase organised into modules such as auth,
   tenant, webhook, event and delivery.
2. Run delivery workers as a separate process from the same codebase.
3. Write events and outbox records in the same PostgreSQL transaction.
4. Publish pending outbox records to Kafka asynchronously.
5. Make outbox publishing and message consumption tolerant of duplicates.

## Consequences

### Positive

- Simpler to build, test and deploy than many microservices.
- Clear module boundaries without premature service distribution.
- The transactional outbox prevents the database/Kafka dual-write gap
  from losing accepted events.
- API and worker capacity can be scaled independently.

### Negative

- Outbox publishing requires polling or another change-detection mechanism.
- Publishing is at-least-once, so duplicates are possible.
- Consumers need idempotency and careful failure handling.
- The API and worker share a codebase and release lifecycle.
- Exactly-once HTTP side effects cannot be guaranteed.
