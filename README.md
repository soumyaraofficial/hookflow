# HookFlow

Multi-tenant distributed webhook delivery platform.

## What it does

A customer registers a destination URL, subscribes it to event types, and submits
events. HookFlow accepts them durably, delivers signed HTTP POSTs asynchronously,
retries failures, and records every attempt.

## Goals

- Tenant-isolated REST API
- Durable event acceptance (idempotency + transactional outbox)
- Async delivery via Kafka with retries and a dead-letter state

## Non-goals (v1)

- Exactly-once HTTP side effects (receivers must deduplicate)
- Global event ordering
- Many microservices

## Status

Phase 1 - Foundation (in progress)

## Quick start

### Prerequisites

- JDK 21
- Docker Desktop running
- Git

### 1. Configure environment variables

From the repository root:

```bash
cp .env.example .env
```

The example file uses local development credentials. Keep `.env` untracked and do not use these credentials in production.

### 2. Start PostgreSQL

From the repository root:

```bash
docker compose up -d postgres
docker compose ps
```

PostgreSQL is exposed on host port `5433`.

### 3. Run the tests

```bash
cd backend
./mvnw clean verify
```

This builds the backend and runs its automated tests. Docker must be running for tests that use Testcontainers.

### 4. Start the application

From the `backend` directory, load the environment variables and start Spring Boot:

```bash
set -a
source ../.env
set +a
./mvnw spring-boot:run
```

Keep this terminal open while the application is running.

### 5. Check application health

In a second terminal:

```bash
curl -i http://localhost:8080/actuator/health
```

A successful response should have HTTP status `200` and report `"status":"UP"`.

### 6. Stop the application

Press `Ctrl+C` in the application terminal.

To stop PostgreSQL, run this from the repository root:

```bash
docker compose down
```

This does not remove the database volume.

## Development status

Phase 1 - Foundation (in progress; clean-clone verification pending)
