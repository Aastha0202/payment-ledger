# Payment Ledger — Distributed Double-Entry Ledger with Event Sourcing

A production-quality, fintech-grade payment ledger built with Java 21, Spring Boot 3.x,
PostgreSQL, Kafka, and Redis. Implements exactly-once financial state transitions,
full auditability, and distributed saga orchestration.

---

## Why This Project Exists

This project was built to demonstrate the exact skills that fintech companies care about most: distributed systems correctness,
event-driven architecture, financial data integrity, and production-quality engineering practices.

It directly addresses three gaps that most backend engineers cannot demonstrate:
event streaming with Kafka, distributed correctness with exactly-once semantics,
and system design depth in a regulated domain.

---

## The Business Problem

### The Core Problem — Money Disappearing

When a user transfers money, a naive system does two things:

```
Step 1: Subtract ₹3,000 from Aastha's account
Step 2: Add ₹3,000 to Priya's account
```

If the server crashes between Step 1 and Step 2, Aastha loses ₹3,000 and
Priya never receives it. The money disappears. At Mastercard scale this
happens thousands of times per day without careful system design.

### Three Additional Real Problems

**Duplicate requests** — A user taps "Pay" three times on a slow network.
Without protection, three payments are processed instead of one.

**Race conditions** — Two browser tabs simultaneously read a balance of ₹10,000.
Both approve transfers of ₹8,000 and ₹7,000. The account goes ₹5,000 negative.

**No audit trail** — A customer disputes a transaction. If you only store
current balances, you have no record of what happened or when.

### The 500-Year-Old Solution

Italian merchants in Venice solved this in the 1400s with double-entry bookkeeping.
Every financial transaction touches at least two accounts. The total always equals zero.
Every entry is immutable — you never delete or modify, only append.

```
Aastha sends ₹3,000 to Priya:

account_id      entry_type    amount
─────────────────────────────────────
aastha_account  DEBIT         3000.00   ← money left here
priya_account   CREDIT        3000.00   ← money arrived here
                              ─────────
Sum:                          0.00      ← always balances
```

Every bank in the world, including Mastercard, runs on this principle.
This project implements it in Java and Spring Boot.

---

## The Three Core Guarantees

These are the guarantees this system makes — and what interviewers will ask about:

**1. Exactly-once semantics**
Every transfer either happens completely or not at all. No partial transfers.
No money disappearing. No money created from nothing.

**2. Full auditability**
Every financial event is stored permanently and immutably. You can reconstruct
the complete history of any account from day one.

**3. Idempotency**
Sending the same transfer request ten times has the same result as sending it once.
Duplicate requests are safely ignored.

---

## Four Foundational Concepts

These four concepts are the entire foundation of the system. Every design
decision traces back to one of them.

### 1. Idempotency Keys

**Problem:** User taps "Pay" three times. Three payments charged.

**Solution:** The client generates a unique key (UUID) before sending the request
and attaches it as a header. The server stores this key in Redis.
If the same key arrives again, the server returns the cached previous response
without processing again.

```
One request  with key "abc-123"  →  ₹3,000 transferred once
Three requests with key "abc-123"  →  ₹3,000 transferred once
```

This is exactly how Stripe's `Idempotency-Key` header works.

### 2. The Outbox Pattern

**Problem:** After writing journal entries to Postgres, the server needs to
publish an event to Kafka. If it crashes between these two operations,
the database has the data but Kafka never learns about it — inconsistency.

This is called the **dual write problem**: writing to two separate systems
and expecting both to succeed.

**Solution:** Write everything to ONE system (Postgres) in one transaction.
Let a separate process (the outbox relay) handle Kafka.

```sql
BEGIN TRANSACTION;
  INSERT INTO journal_entries ...;   -- Step 1: money moves
  INSERT INTO outbox (status='PENDING') ...;  -- Step 2: note to send event
COMMIT;  -- Either BOTH succeed or NEITHER does
```

A separate relay service polls the outbox table every 2 seconds,
publishes PENDING rows to Kafka, and marks them PROCESSED.
If the server crashes, the relay picks up where it left off on restart.

### 3. Serializable Isolation

**Problem:** Two browser tabs read Aastha's balance simultaneously.
Both see ₹10,000. Both approve different transfers. Both commit.
Aastha overspends.

This is called a **lost update** — one transaction's write silently
overwrites another's.

**Solution:** Use PostgreSQL's SERIALIZABLE isolation level for all
transfer write operations. Postgres detects when concurrent execution
would produce a different result than serial execution, and aborts
one transaction with an error. That transaction retries and reads
the updated balance correctly.

```
Tab 1 commits  →  Aastha now has ₹2,000
Tab 2 tries to commit  →  Postgres detects conflict
Tab 2 aborts and retries  →  Reads ₹2,000, insufficient funds, rejected
```

We use SERIALIZABLE only for writes (performance cost).
Reads use READ COMMITTED (fast).

### 4. The Saga Pattern

**Problem:** A transfer between two different banks involves two separate
databases. You cannot wrap them in a single Postgres transaction.
If HDFC debits Aastha but ICICI crashes before crediting Priya,
you cannot rollback HDFC's committed transaction automatically.

**Solution:** Define a compensation step for every forward step — before
executing it. If a step fails, run compensation steps in reverse order.

```
Forward steps:
  Step 1: Debit Aastha (HDFC)     →  success
  Step 2: Credit Priya (ICICI)    →  FAILED

Compensation runs in reverse:
  Compensate Step 1: Credit Aastha back  →  success
  Transfer marked FAILED
```

**Compensation is not an undo.** It is a new, equal and opposite
journal entry. The original entries are never touched — they are immutable.

```
journal_entries table after failed transfer + compensation:
───────────────────────────────────────────────────────────
transfer_id     account      entry_type   amount
───────────────────────────────────────────────────────────
txn-001         aastha       DEBIT        3000.00  ← original
txn-001-COMP    aastha       CREDIT       3000.00  ← compensation
txn-001-COMP    priya        DEBIT        3000.00  ← mirror (audit)
───────────────────────────────────────────────────────────
Aastha net: -3000 + 3000 = 0  ✓ money returned
```

We use **orchestration** (not choreography) — a central saga state machine
holds the transfer state explicitly. Every state transition is stored in
the database. If the system crashes mid-saga, it resumes from the last
known state on restart.

**Saga states:**
```
INITIATED → DEBIT_PENDING → DEBIT_DONE → CREDIT_PENDING → COMPLETED
                                               ↓
                                          COMPENSATING → FAILED
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────┐
│                    CLIENT                           │
│          (generates idempotency key)                │
└──────────────────────┬──────────────────────────────┘
                       │ POST /transfers
                       ▼
┌─────────────────────────────────────────────────────┐
│                 API GATEWAY                         │
│         JWT validation · rate limiting              │
└──────┬──────────────────┬───────────────────────────┘
       │                  │
       ▼                  ▼
┌─────────────┐    ┌──────────────┐   ┌────────────────────┐
│   COMMAND   │    │    QUERY     │   │  RECONCILIATION    │
│   SERVICE   │    │   SERVICE    │   │     SERVICE        │
│             │    │              │   │                    │
│ Writes      │    │ Balance +    │   │ Nightly balance    │
│ Idempotency │    │ Statement    │   │ check              │
│ Saga        │    │ reads        │   │ Freeze on mismatch │
└──────┬──────┘    └──────┬───────┘   └────────────────────┘
       │                  │
       ▼                  │
┌─────────────┐           │
│   OUTBOX    │           │
│    RELAY    │           │
│             │           │
│ Polls every │           │
│ 2 seconds   │           │
└──────┬──────┘           │
       │                  │
       ▼                  │
┌─────────────┐           │
│    KAFKA    │           │
│             │           │
│ Partitioned │           │
│ by acct_id  │           │
└──────┬──────┘           │
       │                  │
       ▼                  ▼
┌─────────────┐    ┌──────────────┐
│  PROJECTION │    │   READ DB    │
│   SERVICE   │───▶│              │
│             │    │ Projected    │
│ Dedup check │    │ balances     │
└─────────────┘    └──────────────┘

DATA STORES:
PostgreSQL  →  Source of truth (journal, outbox, sagas)
Redis       →  Idempotency keys (TTL: 24 hours)
OTel+Prometheus  →  Traces and metrics
```

### Write Path (Happy Path)

```
1. Client sends POST /transfers with Idempotency-Key header
2. API Gateway validates JWT
3. Command Service checks Redis — duplicate key? Return cached response.
4. Single SERIALIZABLE Postgres transaction:
     INSERT journal_entries (debit sender, credit receiver)
     INSERT outbox (status=PENDING)
     UPDATE transfer_sagas (status=DEBIT_DONE)
   COMMIT — all three or none
5. Outbox relay polls, finds PENDING row, publishes to Kafka
6. Outbox relay marks row PROCESSED
7. Projection Service consumes Kafka event
8. Checks consumed_events — already seen? Skip.
9. Updates read model balance
```

### Failure Path

```
1. Credit step fails (bank down, account frozen, timeout)
2. Saga status → COMPENSATING
3. New Postgres transaction:
     INSERT compensation journal entries (mirror of originals)
     INSERT outbox (TransferCompensated event)
     UPDATE transfer_sagas (status=FAILED, failure_reason=...)
   COMMIT
4. If compensation also fails → exponential backoff retry
5. If all retries exhausted → DLQ → alert engineering team
```

---

## Database Schema

### Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Primary keys | UUID | Cannot be guessed, safe for distributed systems |
| Monetary amounts | DECIMAL(19,4) | 4 decimal places, no floating point errors |
| Status columns | VARCHAR + CHECK | Easy to add new values via migration; ENUM cannot be rolled back |
| journal_entries | Immutable (no UPDATE/DELETE) | Legal requirement, audit trail |
| Outbox | No domain foreign keys | Generic event carrier, not coupled to business tables |
| Balance | Cached in accounts table | Performance; reconciliation job detects drift |

### Tables

```
users              → Authentication identity, address fields
accounts           → Financial accounts, cached balance (DECIMAL 19,4)
journal_entries    → Immutable debit/credit ledger (RULES enforce no UPDATE/DELETE)
transfer_sagas     → Saga state machine, 7 explicit states
outbox             → Events pending Kafka publication (generic, no FK to domain)
consumed_events    → Kafka deduplication on consumer side
```

### Referential Order (Why Migrations Run V1→V6)

```
users (V1)
  └── accounts (V2)  [FK: user_id → users.id]
        ├── journal_entries (V3)  [FK: account_id → accounts.id]
        └── transfer_sagas (V4)  [FK: sender_id, receiver_id → accounts.id]
              └── outbox (V5)    [no FK — generic]
consumed_events (V6)             [no FK — generic]
```

Flyway runs migrations in version order. If V4 ran before V2,
Postgres would throw `relation "accounts" does not exist`
because transfer_sagas references accounts which doesn't exist yet.

### Key Constraints

```sql
-- Amount always positive (entry_type tells direction)
CONSTRAINT journal_amount_positive CHECK (amount > 0)

-- Immutability enforced at database level
CREATE RULE journal_entries_no_update
    AS ON UPDATE TO journal_entries DO INSTEAD NOTHING;
CREATE RULE journal_entries_no_delete
    AS ON DELETE TO journal_entries DO INSTEAD NOTHING;

-- Status machine enforced at database level
CONSTRAINT transfer_sagas_status_check CHECK (status IN (
    'INITIATED', 'DEBIT_PENDING', 'DEBIT_DONE',
    'CREDIT_PENDING', 'COMPLETED', 'COMPENSATING', 'FAILED'
))
```

---

## Technology Stack and Why Each Was Chosen

| Technology | Role | Why This Choice |
|---|---|---|
| Java 21 | Language | Virtual threads, pattern matching, latest LTS |
| Spring Boot 3.x | Framework | Industry standard, production-ready autoconfiguration |
| PostgreSQL | Source of truth | SERIALIZABLE isolation, JSONB, mature ACID guarantees |
| Kafka | Event bus | Durable, partitioned, exactly-once delivery semantics |
| Redis | Idempotency store | Sub-millisecond TTL-based key expiry, purpose-built for this |
| Flyway | Schema migrations | Version-controlled schema, append-only like journal entries |
| HikariCP | Connection pooling | Fastest Java connection pool, built into Spring Boot |
| OpenTelemetry | Distributed tracing | Vendor-neutral, traces across all services by transfer_id |
| Prometheus + Grafana | Metrics + dashboards | TPS, P95 latency, Kafka consumer lag |
| Testcontainers | Integration testing | Real Postgres and Kafka in tests, no mocks for infrastructure |
| k6 | Load testing | TPS measurement, P95/P99 latency under concurrency |
| Docker Compose | Local infrastructure | One command to start everything |

### Why Polling Outbox Relay Over Debezium (CDC)

We use a simple polling-based outbox relay instead of Debezium (Change Data Capture).

**Polling relay:** Queries `SELECT * FROM outbox WHERE status='PENDING'`
every 2 seconds. Simple, debuggable, no additional infrastructure.

**Debezium:** Reads Postgres WAL (write-ahead log) as a stream.
More real-time but requires Kafka Connect cluster, Zookeeper,
connector configuration, and deep operational knowledge.

For this system polling is sufficient and significantly simpler.
Debezium is a natural evolution path documented in ADR-002.

---

## Reconciliation

The reconciliation job is the safety net for everything else.

**What it does:**
```sql
SELECT
    a.id,
    a.balance AS cached_balance,
    SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount
             WHEN je.entry_type = 'DEBIT'  THEN -je.amount
        END) AS calculated_balance
FROM accounts a
LEFT JOIN journal_entries je ON je.account_id = a.id
GROUP BY a.id, a.balance
HAVING a.balance != SUM(...)
```

Any account this query returns has a cached balance that disagrees
with the journal. This should never happen. If it does, it means
a bug slipped through all other safeguards.

**Graduated response:**
```
Mismatch found
  → Log with account_id, expected vs actual, discrepancy amount
  → Insert row into reconciliation_failures table
  → Send alert to engineering team (Slack/PagerDuty)
  → If discrepancy > threshold → FREEZE account automatically
  → If discrepancy < threshold → Flag for morning review
```

---

## Infrastructure Setup

### Prerequisites

```
Java 21 (Amazon Corretto 21)
Maven 3.9+
Docker Desktop
IntelliJ IDEA
```

### Start Local Infrastructure

```bash
cd infra
docker-compose up -d
docker-compose ps  # Both should show (healthy)
```

```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: payment_ledger
      POSTGRES_USER: aastha
      POSTGRES_PASSWORD: aastha
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aastha -d payment_ledger"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass aastha
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "aastha", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  redis_data:
```

### Why Health Checks Matter

Without health checks, Docker reports containers as "running" the moment
they start — even before Postgres accepts connections. Spring Boot starts,
tries to connect immediately, and crashes. Health checks make Docker wait
until the service is actually ready before marking it healthy.

### Run Command Service

```bash
cd services/command-service
mvn spring-boot:run
```

Flyway runs all migrations automatically on startup. On first run:
```
Migrating schema "public" to version 1 - create users
Migrating schema "public" to version 2 - create accounts
...
Successfully applied 6 migrations to schema "public"
```

On subsequent runs:
```
Schema "public" is up to date. No migration necessary.
```

---

## Key application.yml Decisions

```yaml
jpa:
  hibernate:
    ddl-auto: validate   # Hibernate NEVER creates/modifies tables.
                         # Flyway owns schema. Hibernate only validates.
                         # If set to 'create' or 'update', Hibernate
                         # bypasses Flyway and breaks migration history.
  open-in-view: false    # Do NOT hold DB connection open during HTTP
                         # response rendering. Reduces connection pool
                         # exhaustion under load.
```

---

## Project Structure

```
payment-ledger/
├── services/
│   ├── command-service/          ← writes, idempotency, saga
│   │   └── src/main/resources/
│   │       └── db/migration/
│   │           ├── V1__create_users.sql
│   │           ├── V2__create_accounts.sql
│   │           ├── V3__create_journal_entries.sql
│   │           ├── V4__create_transfer_sagas.sql
│   │           ├── V5__create_outbox.sql
│   │           └── V6__create_consumed_events.sql
│   ├── projection-service/       ← Kafka consumer, read model
│   └── reconciliation-service/   ← nightly balance verification
├── infra/
│   └── docker-compose.yml
├── load-tests/                   ← k6 scripts (Phase 6)
├── docs/
│   └── adr/                      ← Architecture Decision Records
│       ├── ADR-001-database-schema-design.md
│       └── ADR-002-outbox-relay-vs-debezium.md
└── README.md
```

---

## Flyway Rules — Never Break These

```
1. Migration files are APPEND ONLY.
   Never edit a file after it has been run.
   Flyway stores checksums. Edited files cause startup failures.

2. Naming convention is mandatory.
   V{number}__{description}.sql  (double underscore)
   V4__add_currency_column.sql   ✓
   V4_add_currency_column.sql    ✗  (single underscore — not found)

3. Version numbers must be unique.
   Two developers creating V4 simultaneously = merge conflict.
   Resolve in code review BEFORE merging. One becomes V5.

4. To fix a mistake in V3, create V7.
   Never go back and edit V3.
```

---

## Interview Talking Points

### "How do you guarantee exactly-once semantics?"

> We use three layers. First, idempotency keys in Redis prevent duplicate
> requests from being processed — the client generates the key, the server
> rejects duplicates with the cached response. Second, the outbox pattern
> ensures database writes and Kafka events are always consistent — both
> happen in one Postgres transaction, eliminating the dual write problem.
> Third, the projection service checks consumed_events before processing
> any Kafka message, preventing duplicate event application on the
> consumer side.

### "How do you handle concurrent transfers from the same account?"

> We use PostgreSQL's SERIALIZABLE isolation level for all transfer
> write operations. Postgres detects when concurrent transactions would
> produce a result different from serial execution and aborts one,
> forcing it to retry with the updated balance. We scope SERIALIZABLE
> only to write operations — reads use READ COMMITTED for performance.

### "How do you handle a failed transfer between two banks?"

> We use the Saga pattern with orchestration. Every forward step has a
> pre-defined compensation step. If the credit step fails, the saga
> moves to COMPENSATING state and fires a reverse journal entry —
> a new equal and opposite entry, never a modification of the original.
> Compensation retries with exponential backoff. If all retries are
> exhausted, the saga stays in COMPENSATING, fires a DLQ alert, and
> the account is flagged for manual review. The complete journal
> history is always preserved for audit.

### "How do you ensure your database and message broker stay consistent?"

> We use the Outbox Pattern. Journal entries and the outbox event are
> written in the same Postgres SERIALIZABLE transaction. A separate
> relay service polls the outbox table and publishes to Kafka.
> This means we never write to two systems simultaneously — Postgres
> is the single source of truth. The worst case is at-least-once
> delivery to Kafka, which we handle with idempotency keys on the
> consumer side via the consumed_events table.

### "Why event sourcing over a simple state table?"

> We use a journal-based approach (not full event sourcing) because
> it gives us immutable audit history without the operational complexity
> of a full event store. Every balance is derivable from journal entries.
> We cache the current balance for read performance and use a nightly
> reconciliation job to verify the cache matches the journal — this
> catches any bugs that slip through all other safeguards.

---

## Build Progress

```
✅ Phase 1: Project setup
   ✅ Monorepo structure
   ✅ Three Spring Boot services scaffolded
   ✅ Docker Compose (Postgres + Redis with health checks)
   ✅ Six Flyway migrations (V1-V6)
   ✅ application.yml configured
   ✅ Spring Boot starts clean on port 8081

⬜ Phase 2: Command service (Weeks 2-3)
   ⬜ Domain models (Account, JournalEntry, TransferSaga)
   ⬜ Repository layer
   ⬜ Idempotency check with Redis
   ⬜ POST /accounts
   ⬜ POST /transfers (atomic transaction)
   ⬜ JWT security
   ⬜ Global exception handler
   ⬜ Testcontainers integration tests

⬜ Phase 3: Kafka + Projections (Week 4)
   ⬜ Kafka producer in command service
   ⬜ Outbox polling scheduler
   ⬜ Projection service Kafka consumer
   ⬜ consumed_events deduplication
   ⬜ GET /accounts/{id}/balance
   ⬜ GET /accounts/{id}/statement

⬜ Phase 4: Saga + Compensation (Week 5)
   ⬜ Saga state machine
   ⬜ Compensation journal entry logic
   ⬜ DLQ topic + consumer
   ⬜ Exponential backoff retry
   ⬜ Failure scenario integration tests

⬜ Phase 5: Reconciliation + Observability (Week 6)
   ⬜ Nightly reconciliation job
   ⬜ OpenTelemetry tracing
   ⬜ Prometheus metrics
   ⬜ Structured logging with correlation IDs

⬜ Phase 6: Load Testing + Polish (Weeks 7-8)
   ⬜ k6 load test with real TPS numbers
   ⬜ Architecture diagram in README
   ⬜ All ADRs complete
   ⬜ Swagger/OpenAPI documentation
   ⬜ Blog post on outbox pattern
```

---

## Resume Bullet Points (Fill In After Each Phase)

```
• Designed and implemented an event-sourced payment ledger with
  exactly-once semantics via idempotency keys, outbox pattern,
  and serializable isolation — handling [X] TPS at P95 < [Y]ms

• Implemented distributed saga orchestration with compensation
  logic for cross-account transfers, including DLQ handling and
  exponential backoff retry across [N] microservices

• Built CDC pipeline using polling-based outbox relay with Kafka,
  ensuring zero data loss between PostgreSQL and downstream
  projection service via at-least-once delivery + consumer dedup

• Engineered nightly reconciliation job comparing cached balances
  against double-entry journal entries, with automated account
  freezing on discrepancy detection

• Established 85%+ test coverage using JUnit, Mockito, and
  Testcontainers with real PostgreSQL and Kafka instances
```

---

*Built with Java 21 · Spring Boot 4.1.0 · PostgreSQL 16 · Redis 7 · Kafka*
*Target: Mastercard, Visa, Razorpay, PhonePe, and fintech engineering roles*