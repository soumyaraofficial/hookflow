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

_Filled in on Day 7._
