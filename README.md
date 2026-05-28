# MiniPay Payment Processing System (MPPS)

A production-grade digital wallet and payment processing backend built with **Spring Boot 3.5.12**, **Java 21**, **PostgreSQL**, and **RabbitMQ**. This project implements numerous enterprise integration patterns and is an excellent reference for backend engineering concepts commonly discussed in system design and senior engineering interviews.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [How to Run](#how-to-run)
- [API Endpoints](#api-endpoints)
- [Database Schema](#database-schema)
- [Deep Dive: Design Patterns & Concepts](#deep-dive-design-patterns--concepts)
  - [1. Idempotency — The Three-State State Machine](#1-idempotency--the-three-state-state-machine)
  - [2. Transactional Outbox Pattern — The Dual-Write Problem Solved](#2-transactional-outbox-pattern--the-dual-write-problem-solved)
  - [3. Event-Driven Processing with RabbitMQ](#3-event-driven-processing-with-rabbitmq)
  - [4. Dead Letter Queue (DLQ) — Handling Message Failures](#4-dead-letter-queue-dlq--handling-message-failures)
  - [5. Pessimistic Locking — Preventing Race Conditions](#5-pessimistic-locking--preventing-race-conditions)
  - [6. Webhook Delivery & Retry — At-Least-Once Delivery](#6-webhook-delivery--retry--at-least-once-delivery)
  - [7. AOP-Based Audit Logging — Cross-Cutting Concerns](#7-aop-based-audit-logging--cross-cutting-concerns)
  - [8. Rate Limiting — Token Bucket Algorithm](#8-rate-limiting--token-bucket-algorithm)
  - [9. Database Triggers for `updated_at`](#9-database-triggers-for-updatedat)
  - [10. PostgreSQL Native ENUM Types](#10-postgresql-native-enum-types)
  - [11. Validation — Defense in Depth](#11-validation--defense-in-depth)
  - [12. Standardized API Responses](#12-standardized-api-responses)
  - [13. Request/Response Logging Filter](#13-requestresponse-logging-filter)
  - [14. Base Entity with JPA Auditing](#14-base-entity-with-jpa-auditing)
- [Project Structure](#project-structure)
- [Interview Talking Points](#interview-talking-points)

---

## Project Overview

MPPS is a **monolithic payment processing system** designed for the Nigerian market (validates Nigerian phone numbers, supports NGN as a primary currency). It allows:

- **User management** — create accounts with email/phone uniqueness checks, BCrypt password hashing
- **Multi-currency wallets** — 32 seeded currencies (NGN, USD, EUR, GBP, etc.)
- **Transactions** — credit, debit, and transfer operations with full idempotency guarantees
- **Async processing** — transactions are created synchronously (PENDING) and processed asynchronously via RabbitMQ
- **Webhook notifications** — external systems are notified of transaction outcomes with retry logic
- **Audit trail** — every transaction initiation is logged automatically via AOP
- **Rate limiting** — 5 requests per minute per IP address

---

## Technology Stack

| Layer              | Technology                                                               |
| ------------------ | ------------------------------------------------------------------------ |
| **Language**       | Java 21                                                                  |
| **Framework**      | Spring Boot 3.5.12                                                       |
| **Build Tool**     | Maven 3.9.12                                                             |
| **Database**       | PostgreSQL (with JSONB, native ENUM types, triggers)                     |
| **Migrations**     | Flyway                                                                   |
| **ORM**            | Spring Data JPA / Hibernate 6                                            |
| **Messaging**      | RabbitMQ (Spring AMQP) with Dead Letter Queue                            |
| **Security**       | Spring Security (BCryptPasswordEncoder for password hashing only)        |
| **Validation**     | Jakarta Bean Validation 3.0 (JSR 380)                                    |
| **Rate Limiting**  | Bucket4j 8.10.1 (Token Bucket algorithm)                                 |
| **API Docs**       | SpringDoc OpenAPI 2.8.4                                                  |
| **Boilerplate**    | Lombok                                                                   |
| **JSON**           | Jackson                                                                  |

---

## System Architecture

```
                          ┌─────────────────────────────────────────────────────────────┐
                          │                      Spring Boot Application                 │
                          │                                                             │
  POST /api/v1/           │  ┌──────────┐     ┌──────────────┐     ┌─────────────────┐  │
  transactions            │  │Controller│────▶│   Service    │────▶│ @Auditable AOP  │  │
  ───────────────────────▶│  │          │     │  (saves tx   │     │  (audit log)    │  │
                          │  │          │     │   PENDING)   │     └─────────────────┘  │
                          │  └──────────┘     └──────┬───────┘                         │
                          │                         │                                   │
                          │                         │ publish Spring ApplicationEvent   │
                          │                         ▼                                   │
                          │              ┌───────────────────────┐                      │
                          │              │ @TransactionalEvent   │                      │
                          │              │ Listener(AFTER_COMMIT) │                      │
                          │              │ converts to JSON       │                      │
                          │              │ sends to RabbitMQ      │                      │
                          │              └───────────┬───────────┘                      │
                          └──────────────────────────┼──────────────────────────────────┘
                                                     │
                                                     ▼
                                            ┌────────────────┐
                                            │   RabbitMQ     │
                                            │  ┌──────────┐  │
                                            │  │ Exchange  │  │
                                            │  │  (Direct) │  │
                                            │  └────┬─────┘  │
                                            │       │         │
                                            │  ┌────▼─────┐  │     ┌──────────────┐
                                            │  │  Queue   │──┼────▶│  DLX → DLQ  │
                                            │  │ (Durable)│  │     │ (dead letter)│
                                            │  └────┬─────┘  │     └──────────────┘
                                            └───────┼────────┘
                                                    │
                          ┌─────────────────────────┼──────────────────────────────────┐
                          │  TransactionWorker      │                                   │
                          │  (@RabbitListener)      │                                   │
                          │                         ▼                                   │
                          │  ┌───────────────────────────────────────┐                  │
                          │  │  PESSIMISTIC_WRITE lock on wallet(s)  │                  │
                          │  │  Mutate balance(s)                    │                  │
                          │  │  Mark transaction SUCCESS/FAILED      │                  │
                          │  │  Update idempotency key cache         │                  │
                          │  └───────────────────────────────────────┘                  │
                          │                         │                                   │
                          │                         ▼                                   │
                          │  ┌───────────────────────────────────────┐                  │
                          │  │  WebhookDispatcher                    │                  │
                          │  │  POST to external URL                 │                  │
                          │  │  Retry up to 5x every 10 min          │                  │
                          │  └───────────────────────────────────────┘                  │
                          └────────────────────────────────────────────────────────────┘
```

**Key architectural decision:** Transaction creation (POST endpoint) and transaction processing (balance mutation) are decoupled. The API returns immediately with a `PENDING` transaction record. The actual balance mutation happens asynchronously in a RabbitMQ message consumer. This means:

1. The API is fast — no blocking on external dependencies
2. Retries are handled by RabbitMQ infrastructure, not application code
3. The system is resilient to temporary downstream failures

---

## How to Run

### Prerequisites

- Java 21
- PostgreSQL running on `localhost:5432` with database `mpps_db`, user `root`, password `root`
- RabbitMQ running on `localhost:5672` with default credentials (`guest`/`guest`)

### Steps

```bash
# 1. Clone and navigate
git clone <repo-url> && cd mpps-backend

# 2. Run with Maven wrapper
./mvnw spring-boot:run

# 3. Access Swagger UI
open http://localhost:8080/swagger
```

The application uses **Flyway** to automatically run all database migrations on startup. No manual SQL scripts needed.

---

## API Endpoints

All endpoints return `ApiSuccessResponse<T>` or `ApiErrorResponse` (Java records). All responses are wrapped:

```json
{
  "success": true,
  "message": "Transaction created successfully",
  "data": { ... }
}
```

### Users

| Method | Endpoint                  | Description                        | Status |
| ------ | ------------------------- | ---------------------------------- | ------ |
| POST   | `/api/v1/users`           | Create a new user                  | 201    |
| GET    | `/api/v1/users?email=...` | Get user by email                  | 200    |
| GET    | `/api/v1/users/{userId}`  | Get user by UUID                   | 200    |

### Wallets

| Method | Endpoint                         | Description                | Status |
| ------ | -------------------------------- | -------------------------- | ------ |
| POST   | `/api/v1/wallets`                | Create a wallet            | 201    |
| GET    | `/api/v1/wallets?userId=...`     | Get all wallets for a user | 200    |
| GET    | `/api/v1/wallets/{walletId}`     | Get wallet by UUID         | 200    |

### Transactions

| Method | Endpoint                                            | Description                     | Status |
| ------ | --------------------------------------------------- | ------------------------------- | ------ |
| POST   | `/api/v1/transactions`                              | Create a transaction (async)    | 201    |
| GET    | `/api/v1/transactions?walletId=...`                 | Get transactions for a wallet   | 200    |
| GET    | `/api/v1/transactions/{transactionId}`              | Get transaction by UUID         | 200    |
| GET    | `/api/v1/transactions/reference/{reference}`        | Get transaction by reference    | 200    |

---

## Database Schema

### Entity-Relationship Diagram (Logical)

```
┌──────────┐       ┌──────────┐       ┌──────────────┐       ┌───────────────┐
│  users   │       │ wallets  │       │ transactions │       │ idempotency_  │
├──────────┤       ├──────────┤       ├──────────────┤       │ keys          │
│ id (PK)  │──1:N─▶│ user_id  │──1:N─▶│ wallet_id    │       ├───────────────┤
│ email    │       │ currency │       │ type (ENUM)  │       │ key (UNIQUE)  │
│ password │       │ balance  │◀─FK───│ status(ENUM) │       │ response(JSONB│
│ phone    │       │          │       │ amount       │       │ expires_at    │
│ name     │       │          │       │ reference    │       └───────────────┘
└──────────┘       │          │       │ idempotency_ │
                   │          │       │ key (UNIQUE) │       ┌───────────────┐
┌──────────┐       └──────────┘       │ metadata     │       │ audit_logs    │
│currencies│                          │ (JSONB)      │       ├───────────────┤
├──────────┤                          └──────┬───────┘       │ action        │
│code (PK) │                                 │               │ status        │
│ name     │                          ┌──────▼───────┐       │ details(JSONB)│
│ symbol   │                          │ webhook_     │       └───────────────┘
└──────────┘                          │ deliveries   │
                                      ├──────────────┤
                                      │ transaction  │──FK──▶ transactions
                                      │ url          │
                                      │ payload(JSONB│
                                      │ status (ENUM)│
                                      │ attempt_count│
                                      └──────────────┘
```

**6 tables total:**

| # | Table | Purpose |
|---|-------|---------|
| 1 | `users` | User accounts with BCrypt-hashed passwords |
| 2 | `currencies` | Catalog of 32 supported currencies (seeded) |
| 3 | `wallets` | Per-user, per-currency balance containers |
| 4 | `transactions` | All financial operations (CREDIT/DEBIT/TRANSFER) |
| 5 | `idempotency_keys` | Idempotency state machine with 24h TTL |
| 6 | `webhook_deliveries` | Outbound webhook delivery records with retry tracking |
| 7 | `audit_logs` | AOP-captured audit trail |

---

## Deep Dive: Design Patterns & Concepts

### 1. Idempotency — The Three-State State Machine

#### What problem does idempotency solve?

In distributed systems, network failures cause clients to retry requests. Without idempotency, a retry of "debit 100 NGN" would debit **200 NGN instead of 100**. Idempotency guarantees that **the same request submitted multiple times produces the same result as submitting it once**.

#### How it works in this project

Every transaction requires a **client-generated `UUID` idempotency key** (sent as `idempotencyKey` in the request body). The system implements a **3-state state machine**:

```
                     Client sends request
                     with idempotency key
                              │
                              ▼
                    ┌─────────────────┐
                    │  check(key)     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
     │ KEY_NOT_    │ │ IN_FLIGHT   │ │ RESPONSE_   │
     │ FOUND       │ │             │ │ FOUND       │
     │             │ │ Key exists  │ │ Key exists  │
     │ No record   │ │ but response│ │ AND response│
     │ exists (or  │ │ is null     │ │ is populated│
     │ expired)    │ │             │ │             │
     └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
            │               │               │
            ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ Proceed with │ │ Return 409   │ │ Return cached│
    │ processing  │ │ Conflict     │ │ response as- │
    │ store(key)  │ │ "Transaction │ │ is (no re-   │
    │ (sets it to  │ │ already being│ │ processing)  │
    │ IN_FLIGHT)   │ │ processed"   │ │              │
    └──────┬───────┘ └──────────────┘ └──────────────┘
           │
           ▼
    ┌──────────────┐
    │ After        │
    │ processing   │
    │ complete:    │
    │ update(key,  │
    │ responseJSON)│
    └──────────────┘
```

#### Key implementation details

The implementation is in `IdempotencyServiceImpl.java:30-49`:

```java
public IdempotencyResult check(UUID idempotencyKey) {
    Optional<IdempotencyKey> key = idempotencyKeyRepository.findByKey(idempotencyKey);

    if (key.isPresent()) {
        // Expired keys are treated as non-existent — triggers re-processing
        if (key.get().getExpiresAt().isBefore(OffsetDateTime.now())) {
            return new IdempotencyResult(IdempotencyStatus.KEY_NOT_FOUND, null);
        }
        if (key.get().getResponse() != null) {
            // Completed request — return the exact same response
            return new IdempotencyResult(IdempotencyStatus.RESPONSE_FOUND, key.get().getResponse());
        }
        // In-progress — block concurrent duplicates
        return new IdempotencyResult(IdempotencyStatus.IN_FLIGHT, null);
    }
    return new IdempotencyResult(IdempotencyStatus.KEY_NOT_FOUND, null);
}
```

**Three states explained:**

1. **`KEY_NOT_FOUND`** — The key doesn't exist in the DB (or has expired after 24 hours). The system proceeds with processing.

2. **`IN_FLIGHT`** — The key exists but `response` is `NULL`. This means another thread/request is currently processing this same transaction. The system returns HTTP 409 Conflict to prevent duplicate processing. This is the critical state that prevents the **thundering herd problem**.

3. **`RESPONSE_FOUND`** — The key exists AND has a cached JSON response. The original response is deserialized and returned directly without re-executing the business logic.

#### Why is the `IN_FLIGHT` state important?

Without it, two concurrent requests with the same key could both pass the `KEY_NOT_FOUND` check and process the same transaction twice. The `store()` method inserts a row with `response = NULL` to atomically claim the key:

```java
public void store(UUID idempotencyKey) {
    idempotencyKeyRepository.save(
        IdempotencyKey.builder()
            .key(idempotencyKey)
            .expiresAt(OffsetDateTime.now().plusDays(1))  // 24h TTL
            .build()  // response is null → IN_FLIGHT state
    );
}
```

If a second request arrives between `store()` and `update()`, the `check()` will see `response == null` and return `IN_FLIGHT`, blocking it.

#### The `update()` method — Completing the cycle

```java
public void update(UUID idempotencyKey, String response) {
    IdempotencyKey entity = idempotencyKeyRepository.findByKey(idempotencyKey)
        .orElseThrow(() -> new NotFoundException("..."));
    entity.setResponse(response);  // Now the key transitions to RESPONSE_FOUND
    idempotencyKeyRepository.save(entity);
}
```

After the transaction worker finishes processing, it serializes the final `TransactionResponse` to JSON and stores it against the key. Subsequent retries will receive this cached response.

#### Cleanup (Cron job)

```java
@Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
public void cleanUpExpiredKeys() {
    idempotencyKeyRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
}
```

Without cleanup, the `idempotency_keys` table would grow unbounded. The 24-hour TTL is a pragmatic choice — idempotency guarantees for longer than a day are rarely needed for financial transactions (clients should get a response well before that).

#### Interview point: Idempotency key generation

The **client** generates the idempotency key (UUID v4), not the server. This is critical: if the server generated it, the client couldn't retry with the same key because each request would get a new key. The pattern relies on the client's ability to regenerate the same key for the same logical operation.

#### Where idempotency runs in the flow

1. **In `TransactionServiceImpl.createTransaction()`** — checked at the HTTP layer (before any business logic)
2. **In `TransactionWorker.convertToTransactionString()`** — updated at the async processing layer (after balance mutation succeeds or fails)

This means idempotent responses cover both the synchronous API call and the eventual async result.

---

### 2. Transactional Outbox Pattern — The Dual-Write Problem Solved

#### The problem

When you need to **write to a database AND publish a message** to a queue, you face the **dual-write problem**:

```
         ┌─────────────┐
         │  Write to   │──▶ Success
         │  Database   │
         └─────────────┘
                │
                ▼
         ┌─────────────┐
         │  Publish to │──▶ FAILURE (network blip, broker down)
         │  RabbitMQ   │
         └─────────────┘

Result: Database has the record but the message was never sent.
         The transaction is stuck in PENDING forever.
```

You cannot wrap them in a single transaction because RabbitMQ is an external system (no XA/2PC). If one succeeds and the other fails, you have an inconsistent state.

#### The solution in this project

This project implements a **simplified Transactional Outbox** using Spring's `@TransactionalEventListener`:

```java
// TransactionServiceImpl.java - the method is @Transactional
@Transactional
@Auditable(action = "INITIATE_TRANSACTION")
public TransactionResponse createTransaction(CreateTransactionRequest request) {
    // ... validation, idempotency check ...

    Transaction savedTransaction = transactionRepository.save(transaction);  // ① DB write

    TransactionEvent event = new TransactionEvent(...);

    applicationEventPublisher.publishEvent(event);  // ② Spring ApplicationEvent (synchronous)

    return finalizeAndReturn(response, request.idempotencyKey());
}
```

The key line is: `applicationEventPublisher.publishEvent(event)`. This publishes a **Spring ApplicationEvent** — a synchronous in-process event that Spring holds onto.

The consumer (`TransactionEventPublisher`) is annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`:

```java
// TransactionEventPublisher.java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publish(TransactionEvent event) {
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.TRANSACTION_EXCHANGE,
        RabbitMQConfig.TRANSACTION_ROUTING_KEY,
        event
    );
}
```

#### The execution order

```
┌─────────────────────────────────────────────────────────────────────┐
│ @Transactional method boundary                                       │
│                                                                      │
│ 1. save(transaction) → SQL INSERT issued                            │
│ 2. publishEvent(event) → Spring stores the event in memory           │
│ 3. finalizeAndReturn() → saves idempotency response                  │
│                                                                      │
│ Hibernate flushes and commits the transaction                        │
│                                                                      │
│ ── COMMIT SUCCESS ──                                                 │
│                                                                      │
│ 4. Spring fires @TransactionalEventListener → RabbitMQ message sent │
└─────────────────────────────────────────────────────────────────────┘
```

If the database commit fails (step 3), the `@TransactionalEventListener` **never fires**, and RabbitMQ **never receives the message**. The message is published **only after Hibernate confirms the commit**.

If the RabbitMQ send fails (step 4), the DB transaction is already committed. This is not perfectly atomic — but it's a **very reasonable tradeoff** for most systems. The transaction is in the database (PENDING state) and can be retried or manually recovered.

#### Why not a separate outbox table?

The classic outbox pattern uses a dedicated table (e.g., `outbox_messages`) where events are INSERTed in the same transaction as the business data, then a separate process polls this table and publishes to the queue. This project simplifies by using Spring's transaction synchronization — leveraging `@TransactionalEventListener` as a conceptual "in-memory outbox" that fires only on commit.

#### What about the phase options?

`TransactionPhase` has four options:
- `BEFORE_COMMIT` — fires just before Hibernate flushes. Messages sent here might go out even if the commit subsequently fails.
- `AFTER_COMMIT` **(used here)** — fires only after the database confirms the commit. Safe: no message without data.
- `AFTER_ROLLBACK` — fires when the transaction is rolled back. Useful for cleanup/compensation.
- `AFTER_COMPLETION` — fires regardless of commit or rollback.

`AFTER_COMMIT` is the right choice for outbox because it guarantees the data is persisted before the message is sent.

---

### 3. Event-Driven Processing with RabbitMQ

#### Why async processing for transactions?

- **Performance:** The API returns immediately (in milliseconds) rather than blocking while balance mutations complete.
- **Resilience:** If the database is temporarily slow, the message stays in RabbitMQ and is retried rather than failing the HTTP call.
- **Retry:** RabbitMQ provides built-in retry logic with configurable backoff.

#### RabbitMQ topology

```
                    ┌──────────────────────┐
                    │  transaction_exchange │ (DirectExchange)
                    │      (Direct)         │
                    └──────────┬───────────┘
                               │ routing key: "transaction_routing_key"
                               ▼
                    ┌──────────────────────┐
                    │  transaction_queue    │ (Durable)
                    │  (x-dead-letter-*)    │
                    └──────────┬───────────┘
                               │ on failure (after 5 retries)
                               ▼
                    ┌──────────────────────┐
                    │ transaction_exchange  │
                    │      .dlq            │ (DLX — Direct)
                    └──────────┬───────────┘
                               │ routing key: "transaction.dlq.routing.key"
                               ▼
                    ┌──────────────────────┐
                    │ transaction_queue.dlq │ (Durable — Dead Letter)
                    └──────────────────────┘
```

Defined in `RabbitMQConfig.java`:

- **Exchange:** `transaction_exchange` (DirectExchange)
- **Queue:** `transaction_queue` (durable — survives broker restarts)
- **Binding:** `transaction_routing_key`
- **DLX:** `transaction_exchange.dlq`
- **DLQ:** `transaction_queue.dlq`

**Direct exchanges** route messages to queues based on exact routing key matches. A message published with key `transaction_routing_key` goes only to the queue bound with that exact key.

#### Message serialization

Messages are serialized as JSON using `Jackson2JsonMessageConverter`. The `TransactionEvent` is a Java record:

```java
public record TransactionEvent(
    UUID transactionId,
    UUID fromWalletId,
    UUID toWalletId,
    TransactionType transactionType,
    BigDecimal amount,
    UUID idempotencyKey
) {}
```

When publishing:
```java
rabbitTemplate.convertAndSend(exchange, routingKey, event);
// Jackson2JsonMessageConverter automatically converts TransactionEvent → JSON bytes
```

When consuming (`TransactionWorker.java`):
```java
@RabbitListener(queues = RabbitMQConfig.TRANSACTION_QUEUE)
public void processTransaction(TransactionEvent event) {
    // Jackson2JsonMessageConverter automatically converts JSON bytes → TransactionEvent
}
```

#### Message processing flow

```java
@RabbitListener(queues = RabbitMQConfig.TRANSACTION_QUEUE)
@Transactional
public void processTransaction(TransactionEvent event) {
    try {
        switch (event.transactionType()) {
            case CREDIT  → handleCredit(event);   // Lock wallet, add funds, mark SUCCESS
            case DEBIT   → handleDebit(event);    // Lock wallet, check balance, subtract, mark SUCCESS
            case TRANSFER → handleTransfer(event); // Lock BOTH wallets, move money, mark SUCCESS
        }
    } catch (BadRequestException | NotFoundException | IllegalArgumentException e) {
        // BUSINESS FAILURE: mark as FAILED, don't retry
        transaction.setStatus(TransactionStatus.FAILED);
    } catch (Exception e) {
        // INFRASTRUCTURE FAILURE: rethrow to trigger RabbitMQ retry
        throw new RuntimeException("Infrastructure error", e);
    }
}
```

**Critical distinction between two types of failures:**

1. **Business failures** (insufficient funds, wallet not found, invalid transaction type) → caught locally, transaction marked `FAILED`, **not re-queued**. Retrying would produce the same result — it's a waste.

2. **Infrastructure failures** (database connection lost, deadlock, Hibernate error) → rethrown as `RuntimeException`, which triggers RabbitMQ's retry mechanism. These may succeed on retry.

#### Retry configuration

From `application.yaml`:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          max-attempts: 5
          max-interval: 30s
          multiplier: 2.0         # Exponential backoff
        default-requeue-rejected: false  # Don't infinitely re-queue
```

The retry intervals with multiplier 2.0 and max 30s would be approximately: 1s, 2s, 4s, 8s, 16s, 30s (but only 5 attempts total). If all 5 fail, the message goes to the DLQ.

This prevents **poison messages** (messages that can never be processed successfully) from being retried forever and consuming resources.

---

### 4. Dead Letter Queue (DLQ) — Handling Message Failures

The DLQ pattern ensures that no message is silently lost. After exhausting the 5 retry attempts, RabbitMQ automatically routes the message to the Dead Letter Queue.

**How it's configured:**

```java
@Bean
public Queue transactionQueue() {
    return QueueBuilder.durable(TRANSACTION_QUEUE)
            .withArgument("x-dead-letter-exchange", TRANSACTION_DLX)           // Where to send failed messages
            .withArgument("x-dead-letter-routing-key", TRANSACTION_DLQ_ROUTING_KEY) // Routing key for DLQ
            .build();
}
```

This is a **RabbitMQ-native feature** — configured as queue arguments. The broker itself handles the routing. No application code is needed. When the consumer rejects a message (or the retry limit is exceeded), RabbitMQ:

1. Takes the original message
2. Wraps it with `x-death` headers (count, reason, time, originating queue)
3. Publishes it to the DLX with the specified routing key
4. The DLX routes it to the DLQ

The `default-requeue-rejected: false` setting is critical — without it, rejected messages would be re-queued to the head of the original queue, potentially creating an infinite loop.

**Monitoring:** In production, you'd set up alerts on the DLQ — any messages arriving there indicate something went wrong that needs manual investigation.

---

### 5. Pessimistic Locking — Preventing Race Conditions

#### The problem

Two concurrent transfer requests for the same wallet:

```
Thread 1: Read balance = 1000 NGN
Thread 2: Read balance = 1000 NGN    ← Reads BEFORE Thread 1 commits
Thread 1: setBalance(700)            ← Commits
Thread 2: setBalance(500)            ← Overwrites Thread 1's debit!
Result: Balance = 500 (should be 200 if both 300 debits processed)
```

This is a classic **lost update** problem.

#### Optimistic vs Pessimistic locking

- **Optimistic:** Add a `version` column. Each UPDATE checks the version hasn't changed. If it has, throw `OptimisticLockException` and retry. Good for **low-contention** scenarios.

- **Pessimistic:** Lock the row for the duration of the transaction. Other transactions wait. Good for **high-contention** scenarios like wallet balances.

#### This project uses Pessimistic Write locks

```java
// WalletRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
```

`PESSIMISTIC_WRITE` translates to `SELECT ... FOR UPDATE` in SQL. This acquires an **exclusive row-level lock** that:
- Blocks other `SELECT ... FOR UPDATE` on the same row
- Is released at the end of the transaction (COMMIT or ROLLBACK)
- Prevents any concurrent modifications to the locked rows

#### Where it's used

In `TransactionWorker.java`, every handler uses `findByIdForUpdate()`:

```java
// Credit: locks the target wallet
Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId()).orElseThrow(...);

// Debit: locks the source wallet
Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId()).orElseThrow(...);

// Transfer: locks BOTH wallets
Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId()).orElseThrow(...);
Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId()).orElseThrow(...);
```

**For transfers, locking order matters.** Locking both wallets always in the same order (e.g., lower UUID first) would prevent **deadlocks**. The current implementation locks them in the order they appear, which could cause a deadlock if two transfers between the same wallets happen in opposite directions concurrently. (This is a known limitation — a production system would sort by wallet ID to ensure consistent locking order.)

#### BALANCE CHECK is done TWICE

Notice the balance is checked in both places:
1. **In `TransactionServiceImpl.createTransaction()`** (synchronous, pre-commit validation) — catches obvious insufficient balance cases early.
2. **In `TransactionWorker.handleDebit()` / `handleTransfer()`** (asynchronous, under the pessimistic lock) — the **authoritative check**.

The async check is the real gate because by the time the worker processes, other transactions may have changed the balance. The synchronous check is a nice-to-have early rejection that saves RabbitMQ overhead for obviously impossible transactions.

---

### 6. Webhook Delivery & Retry — At-Least-Once Delivery

#### What are webhooks?

After a transaction completes (SUCCESS or FAILED), external systems (fraud detection, analytics, customer notification services) need to know about it. Webhooks are HTTP callbacks: the system POSTs the transaction result to a configured URL.

#### Webhook delivery lifecycle

```
┌─────────────────┐
│ Transaction     │
│ marked SUCCESS  │──▶ WebhookDispatcherService.dispatch(transaction)
└─────────────────┘
                            │
                            ▼
            ┌───────────────────────────────┐
            │ Save WebhookDelivery (PENDING) │  ← Record in DB first (outbox)
            │ POST to target URL             │
            └───────────────┬───────────────┘
                            │
               ┌────────────┼────────────┐
               ▼            ▼            ▼
          ┌─────────┐ ┌─────────┐ ┌──────────┐
          │ 2xx     │ │ non-2xx │ │ Exception│
          │ SUCCESS │ │ FAILED  │ │ FAILED   │
          └─────────┘ └────┬────┘ └────┬─────┘
                           │           │
                           └─────┬─────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │ attempt_count++         │
                    │ last_attempt_at = now() │
                    │ status = FAILED          │
                    └────────────┬───────────┘
                                 │
                    ┌────────────▼───────────┐
                    │ WebhookRetryJob runs   │
                    │ every 10 minutes        │
                    │                         │
                    │ Retries FAILED deliveries│
                    │ with attempt_count < 5  │
                    │                         │
                    │ After 5 failures:       │
                    │ status = PERMANENTLY_   │
                    │          FAILED          │
                    └─────────────────────────┘
```

#### Key design decisions

**1. Payload is saved to the database before sending.** This is another outbox — even if the HTTP call fails, the payload is preserved. The retry job reads it from the DB, not from memory.

```java
String payload = objectMapper.writeValueAsString(TransactionMapper.toResponse(transaction));
delivery.setPayload(payload);
webhookDeliveryRepository.save(delivery);  // Save first
// Then attempt HTTP POST
```

**2. Retry uses a scheduled job, not RabbitMQ.** Webhooks are HTTP calls to external systems. Using RabbitMQ for webhook retry would conflate two different concerns. A simple `@Scheduled` poller is appropriate.

```java
// WebhookRetryJob.java
@Scheduled(fixedDelay = 600000)  // Every 10 minutes
public void processFailedWebhooks() {
    List<WebhookDelivery> failed = webhookDeliveryRepository
        .findByStatusAndAttemptCountLessThan(WebhookStatus.FAILED, 5);
    for (WebhookDelivery delivery : failed) {
        webhookDispatcherService.retryDispatch(delivery);
    }
}
```

**3. Max 5 attempts, then PERMANENTLY_FAILED.** After 5 failures, the system gives up. This prevents infinite retries clogging the system. A human would need to investigate PERMANENTLY_FAILED webhooks.

```java
private void handleFailure(WebhookDelivery delivery) {
    if (delivery.getAttemptCount() >= 4) {  // This is the 5th failure
        delivery.setStatus(WebhookStatus.PERMANENTLY_FAILED);
    } else {
        delivery.setStatus(WebhookStatus.FAILED);  // Will be retried
    }
}
```

**4. Webhook delivery is fire-and-forget.** The transaction's success is not contingent on webhook delivery. The webhook failing does not roll back the transaction. This is correct — webhooks are side effects, not business invariants.

---

### 7. AOP-Based Audit Logging — Cross-Cutting Concerns

#### The problem

You need to log every transaction initiation attempt — who did what, when, and whether it succeeded. You could add logging code inside every method, but that violates the **Single Responsibility Principle** and clutters business logic.

#### The solution: Aspect-Oriented Programming (AOP)

AOP allows you to define **cross-cutting concerns** (logging, security, transactions) separately from business logic. Spring AOP uses proxies to "weave" advice into your methods at runtime.

**Step 1: Define a custom annotation**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();  // e.g., "INITIATE_TRANSACTION"
}
```

**Step 2: Annotate the method**

```java
@Auditable(action = "INITIATE_TRANSACTION")
public TransactionResponse createTransaction(CreateTransactionRequest request) { ... }
```

**Step 3: Write the aspect**

```java
@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(auditable)")
    public Object logAuditActivity(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String action = auditable.action();
        String status = "SUCCESS";
        String details = null;

        try {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                details = objectMapper.writeValueAsString(args[0]);  // Serialize method argument
            }
            Object result = joinPoint.proceed();  // ← Actually executes the method!
            return result;
        } catch (Exception e) {
            status = "FAILED";
            throw e;  // Rethrow — let GlobalExceptionHandler handle it
        } finally {
            saveAuditLog(action, status, details);  // Runs regardless of success/failure
        }
    }
}
```

#### How `@Around` advice works

```
@Around("@annotation(auditable)")
┌────────────────────────────────────────────────────┐
│ BEFORE: Serialize method arguments to JSON         │
│         Set status = "SUCCESS"                     │
│                                                    │
│         joinPoint.proceed()  ← executes the actual │
│                                business method     │
│                                                    │
│ AFTER (success): status stays "SUCCESS"            │
│ AFTER (exception): status = "FAILED", rethrow      │
│                                                    │
│ FINALLY: save AuditLog(action, status, details)   │
│          to the database                           │
└────────────────────────────────────────────────────┘
```

**Key insight:** The `finally` block ensures the audit log is written **regardless of success or failure**. Even if the business method throws an exception, the audit trail is preserved.

#### Why AOP and not a service call?

You could call a `auditLogService.log(action, status, details)` inside the method. But:
- Every developer has to remember to do it — easy to forget
- If the method has multiple `return` statements, you need the call in every branch
- Adding/removing audit is a code change to the business method

AOP keeps audit logic out of business code. The `@Auditable` annotation is declarative — "audit this method" is a single-line decision.

---

### 8. Rate Limiting — Token Bucket Algorithm

#### The algorithm

This project implements the **Token Bucket** algorithm using the Bucket4j library:

```
         "Bucket" (capacity = 5 tokens)
         ┌───────────────────────────┐
         │ ●  ●  ●  ●  ●            │ ← 5 tokens initially
         └───────────────────────────┘
              │
              │ Each request tries to consume 1 token
              ▼
    ┌─────────────────────┐
    │ bucket.tryConsume(1)│
    └─────────┬───────────┘
              │
     ┌────────┴────────┐
     ▼                 ▼
   ┌─────┐         ┌──────┐
   │TRUE │         │FALSE │
   │Allow│         │ 429  │
   │req  │         │Block │
   └─────┘         └──────┘

         Tokens are refilled at rate: 5 per minute (greedy refill)
```

#### How it's configured

```java
// RateLimitingService.java
private Bucket newBucket(String key) {
    Bandwidth limit = Bandwidth.classic(
        5,                                    // capacity: 5 tokens
        Refill.greedy(5, Duration.ofMinutes(1)) // refill: 5 tokens per minute
    );
    return Bucket.builder().addLimit(limit).build();
}
```

- **Classic bandwidth:** Simple fixed-window-like bucket with burst handling.
- **Greedy refill:** All 5 tokens become available at the start of each minute window (not gradual drip).

#### How it's applied

The rate limiter is implemented as a Spring `HandlerInterceptor` — it runs before every request to `/api/v1/**`:

```java
// RateLimitInterceptor.java
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String clientIp = request.getRemoteAddr();         // Use IP as the key
    Bucket bucket = rateLimitingService.resolveBucket(clientIp);

    if (bucket.tryConsume(1)) {
        return true;  // Allow
    } else {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());  // 429
        response.getWriter().write("Too many requests. Please try again later.");
        return false;  // Block
    }
}
```

Registered in `WebConfig.java`:

```java
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/v1/**");
}
```

#### Storage

Buckets are stored in-memory using a `ConcurrentHashMap<String, Bucket>`. This means:
- Rate limits are **per application instance**, not shared across instances.
- If the application restarts, all rate limit state is lost.
- For production, you'd replace this with Redis-backed Bucket4j configuration for distributed rate limiting.

#### Filter vs Interceptor

This project uses a `HandlerInterceptor` rather than a Servlet `Filter`. The difference:
- **Filter:** Runs in the Servlet container, before Spring's dispatcher. Can operate on the raw request/response. Used here: `RequestResponseLoggingFilter` extends `OncePerRequestFilter`.
- **Interceptor:** Runs within Spring MVC, after the handler is resolved. Has access to the handler method. Used here: `RateLimitInterceptor` implements `HandlerInterceptor`.

---

### 9. Database Triggers for `updated_at`

Every table has a PostgreSQL trigger that automatically updates the `updated_at` column on any row modification:

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
```

This guarantees that `updated_at` is always correct at the database level, even if:
- A developer forgets to set it in application code
- A raw SQL update is run manually
- A migration script modifies rows

Note: Spring Data JPA's `@LastModifiedDate` (on `BaseEntity`) also sets `updated_at`, but this acts as a safety net. The trigger is the **authoritative** mechanism because it runs at the database level, closer to the data.

---

### 10. PostgreSQL Native ENUM Types

This project uses **native PostgreSQL ENUMs** rather than storing enum values as plain text:

```sql
CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT', 'TRANSFER');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REVERSED');
CREATE TYPE webhook_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'PERMANENTLY_FAILED');
```

**Advantages over VARCHAR:**
- **Type safety** — PostgreSQL rejects invalid values at the database level
- **Storage efficiency** — ENUMs are stored as 4 bytes internally, not variable-length text
- **Self-documenting** — `\dT+ transaction_type` shows all allowed values

**Hibernate mapping:** Uses `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
private TransactionType type;
```

This tells Hibernate 6 to use PostgreSQL's native ENUM type rather than `VARCHAR`.

**Trade-off:** Adding a new ENUM value requires `ALTER TYPE ... ADD VALUE`, which can be tricky in migrations (no `IF NOT EXISTS`). For rapidly evolving enums, VARCHAR might be simpler.

---

### 11. Validation — Defense in Depth

This project implements validation at multiple layers:

#### Layer 1: Request body validation (Jakarta Bean Validation)

```java
public record CreateUserRequest(
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$",
        message = "Password must be at least 8 characters, include upper, lower, number and special character"
    )
    @NotBlank String password,

    @Pattern(
        regexp = "^(\\+234|234|0)[789][01]\\d{8}$",
        message = "Invalid Nigerian phone number"
    )
    String phoneNumber,
    ...
) {}
```

The `@Valid` annotation on controller parameters triggers validation. Failures are caught by `GlobalExceptionHandler.handleValidationExceptions()` and returned as HTTP 400.

#### Layer 2: Query parameter validation

```java
@GetMapping
public ApiSuccessResponse<UserResponse> getUserByEmail(
    @RequestParam
    @Email(message = "Email should be valid")
    String email
) { ... }
```

This requires `@Validated` on the controller class. Without it, `@Email` on a `@RequestParam` is silently ignored.

#### Layer 3: Business logic validation

```java
if (request.fromWalletId() != null && request.fromWalletId().equals(request.toWalletId())) {
    throw new BadRequestException("Cannot transfer to the same wallet");
}
```

Not all validation can be expressed declaratively. Cross-field validation (like "from != to") belongs in the service layer.

#### Layer 4: Database constraints

```sql
amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0)
```

This is the ultimate safety net. Even if all application-layer validation is bypassed, the database rejects negative amounts.

#### The exception hierarchy

```
RuntimeException
├── BadRequestException        → HTTP 400
├── NotFoundException          → HTTP 404
├── AlreadyExistsException     → HTTP 409
└── ConflictException          → HTTP 409
```

Each custom exception is mapped to a specific HTTP status in `GlobalExceptionHandler`. This means service-layer code throws semantic exceptions (e.g., `NotFoundException`), and the framework handles translating them to HTTP responses automatically.

---

### 12. Standardized API Responses

All API responses follow a consistent envelope. This project uses **Java records** for immutability and conciseness:

```java
// Success
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiSuccessResponse<T>(boolean success, String message, T data) {}

// Error
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, String message, List<String> errors) {}
```

Example success response:
```json
{
  "success": true,
  "message": "Transaction created successfully",
  "data": {
    "transactionId": "...",
    "status": "PENDING",
    "reference": "TXN-20260528-a1b2c3d4",
    ...
  }
}
```

Example error response:
```json
{
  "success": false,
  "message": "Validation Failed",
  "errors": ["Email is required", "Invalid email format"]
}
```

**Why `@JsonInclude(NON_NULL)`?** It omits `null` fields from the JSON output. For success responses, `errors` is `null` — no need to send `"errors": null`. For error responses, `data` is `null` — same reason. This keeps responses clean.

**Server error config:**

```yaml
server:
  error:
    include-stacktrace: never
    include-message: never
    include-binding-errors: never
```

This prevents Spring Boot's default error page from leaking internal details. All errors go through `GlobalExceptionHandler` for controlled, consistent responses.

---

### 13. Request/Response Logging Filter

A `OncePerRequestFilter` logs every HTTP request and response:

```java
@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) {
        // Wrap request/response to cache body content (can only be read once otherwise)
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);  // Process the request
        } finally {
            long timeTaken = System.currentTimeMillis() - startTime;
            log.info("API REQUEST: method={}, uri={}, status={}, timeTaken={}ms, requestBody={}, responseBody={}",
                     request.getMethod(), request.getRequestURI(), response.getStatus(),
                     timeTaken, requestBody, responseBody);

            responseWrapper.copyBodyToResponse();  // CRITICAL: client gets empty response otherwise!
        }
    }
}
```

**Key implementation detail:** `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper` are necessary because HTTP request/response bodies are streams — you can read them only once. These wrappers cache the body in memory so you can log it AND still send it to the client.

The `copyBodyToResponse()` call at the end is critical — without it, the client receives an empty body.

---

### 14. Base Entity with JPA Auditing

All entities (except `Currency`) extend `BaseEntity`:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

- **`@MappedSuperclass`**: Fields are inherited by subclasses but the superclass is NOT an entity itself (no table).
- **`@EntityListeners(AuditingEntityListener.class)`**: Spring Data JPA's listener that automatically populates `@CreatedDate` and `@LastModifiedDate`.
- **`GenerationType.UUID`**: Hibernate generates UUIDs at the application level before INSERT. This avoids the performance issues of auto-increment IDs in distributed systems.
- **`OffsetDateTime`**: Always store timestamps with timezone offset. Avoids DST and timezone conversion bugs.
- **`updatable = false`** on `createdAt`: Prevents accidental updates to the creation timestamp.

Enabled in the main class:
```java
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
```

The `dateTimeProvider` bean is defined in `ApplicationConfig.java` to use `OffsetDateTime.now()`:

```java
@Bean
public DateTimeProvider dateTimeProvider() {
    return () -> Optional.of(OffsetDateTime.now());
}
```

---

## Project Structure

```
src/main/java/com/minipay/mpps/
├── MppsApplication.java                         # Main class (excludes Security, enables auditing & scheduling)
├── common/
│   ├── BaseEntity.java                          # @MappedSuperclass with UUID PK, timestamps
│   ├── TransactionReferenceGenerator.java       # TXN-{timestamp}-{UUID8} format generator
│   ├── config/
│   │   ├── ApplicationConfig.java               # Beans: BCryptPasswordEncoder, DateTimeProvider, RestTemplate
│   │   ├── WebConfig.java                       # Registers RateLimitInterceptor
│   │   └── RabbitMQConfig.java                  # Queue, DLQ, Exchange, Binding, JSON converter
│   ├── dto/
│   │   ├── ApiSuccessResponse.java              # Standard success envelope
│   │   ├── ApiErrorResponse.java                # Standard error envelope
│   │   └── CurrencyInfo.java                    # Currency code/name/symbol projection
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java          # @RestControllerAdvice for all exceptions
│   │   ├── BadRequestException.java             # HTTP 400
│   │   ├── NotFoundException.java               # HTTP 404
│   │   ├── ConflictException.java               # HTTP 409
│   │   └── AlreadyExistsException.java          # HTTP 409 (resource duplicate)
│   └── filter/
│       └── RequestResponseLoggingFilter.java    # Logs all requests/responses with timing
│
├── user/                                        # User management feature
│   ├── User.java                                # JPA entity
│   ├── UserService.java / UserServiceImpl.java  # Interface + implementation
│   ├── UserController.java                      # REST endpoints
│   ├── UserRepository.java                      # Spring Data JPA
│   ├── dto/{CreateUserRequest, UserResponse}.java
│   └── mapper/UserMapper.java                   # Static entity→DTO mapper
│
├── currency/                                    # Currency catalog feature
│   ├── Currency.java                            # Entity (no BaseEntity — code is PK)
│   └── CurrencyRepository.java
│
├── wallet/                                      # Wallet management feature
│   ├── Wallet.java                              # Entity (balance, currency FK, user FK)
│   ├── WalletService.java / WalletServiceImpl.java
│   ├── WalletController.java
│   ├── WalletRepository.java                    # Includes PESSIMISTIC_WRITE query
│   ├── dto/{CreateWalletRequest, WalletResponse}.java
│   └── mapper/WalletMapper.java
│
├── transaction/                                 # Transaction processing feature
│   ├── Transaction.java                         # Entity (type ENUM, status ENUM, JSONB metadata)
│   ├── TransactionType.java                     # CREDIT, DEBIT, TRANSFER
│   ├── TransactionStatus.java                   # PENDING, SUCCESS, FAILED, REVERSED
│   ├── TransactionService.java / TransactionServiceImpl.java  # Core business logic
│   ├── TransactionController.java
│   ├── TransactionRepository.java
│   ├── dto/{CreateTransactionRequest, TransactionResponse}.java
│   └── mapper/TransactionMapper.java
│
├── idempotency/                                 # Idempotency subsystem
│   ├── IdempotencyKey.java                      # Entity (key, response JSONB, expiresAt)
│   ├── IdempotencyStatus.java                   # KEY_NOT_FOUND, RESPONSE_FOUND, IN_FLIGHT
│   ├── IdempotencyService.java / IdempotencyServiceImpl.java  # State machine
│   ├── IdempotencyKeyRepository.java            # Cleanup: deleteByExpiresAtBefore
│   └── dto/IdempotencyResult.java               # Status + cached response
│
├── messaging/                                   # RabbitMQ messaging
│   ├── TransactionEvent.java                    # Event record (immutable)
│   ├── TransactionEventPublisher.java           # @TransactionalEventListener(AFTER_COMMIT)
│   └── consumer/
│       └── TransactionWorker.java               # @RabbitListener — processes CREDIT/DEBIT/TRANSFER
│
├── audit/                                       # AOP-based audit logging
│   ├── Auditable.java                           # Custom @Auditable annotation
│   ├── AuditAspect.java                         # @Around advice
│   ├── AuditLog.java                            # Entity (action, status, details JSONB)
│   └── AuditLogRepository.java
│
├── webhook/                                     # Webhook delivery subsystem
│   ├── WebhookDelivery.java                     # Entity (transaction FK, url, payload JSONB, attempts)
│   ├── WebhookStatus.java                       # PENDING, SUCCESS, FAILED, PERMANENTLY_FAILED
│   ├── WebhookDispatcherService.java            # Initial delivery + retry logic
│   ├── WebhookRetryJob.java                     # @Scheduled(fixedDelay = 600000)
│   └── WebhookDeliveryRepository.java
│
└── ratelimit/                                   # Rate limiting
    ├── RateLimitInterceptor.java                # HandlerInterceptor — checks token bucket
    └── RateLimitingService.java                 # Bucket4j: 5 req/min per IP

src/main/resources/
├── application.yaml                             # All configuration
└── db/migration/
    ├── V1__create_users_table.sql
    ├── V2__create_currencies_table.sql
    ├── V3__create_wallets_table.sql
    ├── V4__create_transactions_table.sql        # Native ENUM types + CHECK constraints
    ├── V5__add_triggers.sql                     # set_updated_at() trigger on all tables
    ├── V6__add_indexes.sql
    ├── V7__seed_currencies.sql                  # 32 currencies inserted
    ├── V8__create_idempotency_keys.sql
    ├── V9__create_webhook_deliveries_table.sql
    └── V10__create_audit_logs_table.sql
```

---

## Interview Talking Points

### System Design Questions

**Q: How would you handle duplicate payment requests?**
- Explain the three-state idempotency pattern: KEY_NOT_FOUND → IN_FLIGHT → RESPONSE_FOUND
- Mention that the client generates the key (UUID v4), not the server
- The IN_FLIGHT state prevents concurrent duplicate processing
- Cached responses mean identical responses for identical keys
- 24h TTL with cron cleanup prevents unbounded storage

**Q: How do you guarantee a message is published when a database write succeeds?**
- Transactional Outbox pattern using `@TransactionalEventListener(AFTER_COMMIT)`
- The message is only sent after Hibernate confirms the DB commit
- If DB commit fails, the event listener never fires
- Alternative: dedicated outbox table + polling (more reliable but more complex)

**Q: How do you handle concurrent balance updates?**
- Pessimistic locking: `SELECT ... FOR UPDATE` on wallet rows
- Both wallets are locked during a transfer
- ACID guarantees within the transactional boundary
- Important to mention locking order consistency to prevent deadlocks

**Q: What happens when a message consumer fails?**
- Distinguish business failures (mark FAILED, don't retry) from infrastructure failures (rethrow, trigger RabbitMQ retry)
- Exponential backoff: 5 attempts, multiplier 2.0, max interval 30s
- After exhausting retries → Dead Letter Queue for manual inspection
- `default-requeue-rejected: false` prevents infinite loops

**Q: How do you notify external systems about transaction results?**
- Webhook delivery with retry
- Payload saved to DB before HTTP call (outbox pattern)
- Scheduled job retries FAILED deliveries every 10 minutes
- After 5 failures → PERMANENTLY_FAILED (abandon, notify ops)

**Q: How do you protect against abuse?**
- Token bucket rate limiter: 5 requests/minute per IP address
- Explain Bucket4j: capacity=5, greedy refill every minute
- HandlerInterceptor vs Filter distinction
- For distributed systems: replace ConcurrentHashMap with Redis

### Java/Spring Questions

**Q: What is `@TransactionalEventListener` and how does it differ from `@EventListener`?**
- `@EventListener` fires immediately when the event is published
- `@TransactionalEventListener(phase = AFTER_COMMIT)` fires only after the DB transaction commits
- Phases: BEFORE_COMMIT, AFTER_COMMIT, AFTER_ROLLBACK, AFTER_COMPLETION
- This is Spring's built-in way to implement the transactional outbox pattern

**Q: How does Spring AOP work?**
- Uses JDK dynamic proxies (for interfaces) or CGLIB proxies (for classes)
- `@Around` advice wraps the method call with `ProceedingJoinPoint.proceed()`
- `@Aspect` + `@Component` registers the aspect
- Pointcut expression: `@annotation(auditable)` matches any method with `@Auditable`
- `finally` block ensures audit log is written regardless of exception

**Q: What are Java records and why use them for DTOs?**
- Immutable by design (all fields are `final`)
- Auto-generated constructor, getters, `equals()`, `hashCode()`, `toString()`
- Concise syntax: `public record Point(int x, int y) {}`
- Thread-safe (immutable) — safe to share between threads
- This project uses records for ALL DTOs and the TransactionEvent

**Q: Explain `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`**
- Hibernate 6 annotation that maps Java enums to PostgreSQL native ENUM types
- Without it, Hibernate would use VARCHAR by default
- Requires matching `CREATE TYPE` in Flyway migration
- More efficient storage and type-safe at the database level

**Q: What is `open-in-view: false`?**
- Disables Hibernate's OSIV (Open Session In View) pattern
- When enabled, the JPA session stays open for the entire HTTP request lifecycle
- This causes N+1 queries and lazy loading in view rendering (Jackson serialization triggers lazy loads)
- Setting to `false` forces explicit eager fetching or DTO mapping in the service layer

### Database Questions

**Q: Why JSONB instead of separate tables?**
- `metadata` on transactions and `details` on audit logs use JSONB
- JSONB is binary JSON — supports indexing, querying with `->` and `->>`, and is faster than plain `json`
- Good for semi-structured data where the schema varies
- For relational data with foreign keys, use normalized tables

**Q: Why database triggers for `updated_at`?**
- Defense in depth: guarantees correctness even if application code forgets
- Runs at the database level, closest to the data
- `BEFORE UPDATE` ensures the timestamp is set before the row is written

**Q: What are the trade-offs of PostgreSQL native ENUMs vs VARCHAR?**
- ENUM: type-safe, smaller storage, better performance. But adding values requires ALTER TYPE.
- VARCHAR: flexible, easy to change. But no database-level validation.
- This project uses ENUMs for stable, well-defined types (transaction_type, transaction_status)

### General Engineering Questions

**Q: What is the Repository pattern?**
- Spring Data JPA repositories abstract away data access
- `JpaRepository<T, ID>` provides CRUD methods: `save`, `findById`, `findAll`, `deleteById`
- Custom query methods are derived from method names: `findByEmail`, `existsByPhoneNumber`
- Repository layer decouples business logic from data access

**Q: What is constructor injection and why is it preferred?**
- `@RequiredArgsConstructor` (Lombok) generates a constructor for all `final` fields
- Spring auto-wires dependencies via this constructor
- Benefits over field injection: immutability (make fields `final`), testability (can pass mocks in constructor), no reflection
- This project uses constructor injection everywhere (via `@RequiredArgsConstructor`)

**Q: What is Flyway and why use it?**
- Database migration tool — versioned SQL scripts
- Ensures every environment (dev, staging, production) has the same schema
- Runs automatically on application startup
- Migration files are immutable once applied (never edit a deployed migration)
- This project has 10 migrations (V1 through V10)
