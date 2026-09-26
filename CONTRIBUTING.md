# Englow3 Backend Development Guide

This file is the single source of truth for code structure, coding conventions,
implementation workflow, and verification in this repository. If another document
conflicts with this guide, this guide wins.

`docs/module-map.md` records current module and table ownership. It supplements this
guide with project state; it does not define a second set of coding conventions.

## Repository layout

```text
Englow3_BE/
|-- src/main/java/com/englow3/   Spring Boot application
|-- src/main/resources/          configuration and Flyway migrations
|-- src/test/java/com/englow3/   Java tests
|-- ai_service/                  FastAPI inference boundary
|-- data_pipeline/               content generation and validation tools
|-- docker/                      local infrastructure initialization
|-- docs/                        product, operations, and module ownership docs
`-- .github/workflows/           CI/CD
```

Do not create another top-level application for code that belongs to one of these
components. Build output, virtual environments, caches, local worktrees, secrets,
and generated pipeline artifacts stay out of version control.

## Java architecture

The Spring application is a modular monolith. Organize by business module first and
by layer inside the module:

```text
com.englow3
|-- <module>/
|   |-- controller/    HTTP entry points
|   |-- service/       use-case orchestration and transactions
|   |-- repository/    Spring Data repositories and projections
|   |-- entity/        JPA entities, enums, and protected business rules
|   |-- dto/
|   |   |-- request/   HTTP input records
|   |   |-- response/  HTTP output records
|   |   |-- command/   service input records
|   |   `-- result/    service output records
|   |-- query/         complex read-only queries, only when needed
|   `-- worker/        asynchronous workers, only when needed
|-- config/            framework configuration
`-- shared/            technical infrastructure only
```

Do not create empty layers in advance. Extend an existing module unless the new
capability has a distinct business vocabulary, lifecycle, and table ownership.
Admin and learner use cases for the same capability remain in the same module and
use different controllers.

Never introduce generic `utils`, `helpers`, `misc`, `manager`, `common`, or
`CommonService` packages. Keep `shared` limited to technical concerns such as base
errors, security context, paging, logging, and storage clients.

## Service contracts

Every Spring application service must implement an explicit contract. Controllers
and other consumers depend on the contract, never on the `*Impl` class.

Concrete Spring services live under `service/impl` and are named `*Impl`.

Cross-module synchronous calls must target the owning module's `api/` contract.
Public module APIs must not expose persistence entities or move internal enums/types
outward solely to satisfy callers.

## Layer responsibilities

Dependencies flow in one direction:

```text
controller -> service -> repository
                  |
                  `-> entity behavior
```

- A controller handles HTTP only: validate a request, build a command, call one
  service use case, and map its result to a response.
- A controller never contains business logic, accesses a repository, or opens a
  transaction.
- A service coordinates a use case and owns its transaction boundary. It accepts
  commands or plain values and returns result records, never web request/response
  types.
- A repository contains persistence operations and projections, not business logic.
- An entity protects rules that would otherwise allow an invalid business state.
  Cross-entity rules, I/O, and orchestration remain in the service.
- Entities receive plain values such as `UUID` and `Instant`; they never receive a
  repository, service, HTTP type, or Spring-managed collaborator.

When an entity protects state, do not expose setters for guarded fields and do not
use Lombok `@Data` or `@Setter` on it. Prefer named behavior and creation methods.
Plain CRUD entities may remain plain; do not invent invariants.

## Module boundaries

- Exactly one module writes a given table.
- Other modules request state changes through the owning module's service.
- Cross-module references are IDs, normally `UUID`; do not use `@ManyToOne` across
  module boundaries.
- Persistence entities never leave their module or appear in an HTTP response.
- Fetch records for a set of IDs in one call; do not create service-level N+1 calls.
- Cross-module reporting joins must be read-only and documented in
  `docs/module-map.md`.
- A circular dependency is a boundary problem. Recheck ownership before introducing
  an event to break the cycle.

Before adding a feature, identify the owning module and tables in
`docs/module-map.md`. If ownership is genuinely unclear, settle it before coding.

## HTTP, DTOs, and errors

- Controller methods return `ResponseEntity<T>`, including ordinary 200 responses.
- Successful responses are not wrapped in a generic success envelope.
- Validate request records with Jakarta validation and `@Valid`.
- Request/response records belong to HTTP. Command/result records belong to the
  service boundary.
- A `DomainException` carries only a stable code and message. It contains no
  `HttpStatus`, `ResponseEntity`, or `ResponseStatusException`.
- `GlobalExceptionHandler` is the only place that maps domain failures to HTTP.
- Do not expose exception internals, class names, SQL, or secrets to clients.

Injected repositories use a `Repo` suffix, for example
`UserRepository userRepo`, rather than vague plural names such as `users`.

## Persistence and migrations

- Flyway is the only schema authority; Hibernate `ddl-auto` stays `validate`.
- Never edit a migration that has already run. Add a new versioned migration.
- Keep demo and bulk seed data out of versioned schema migrations.
- Add database constraints for rules that must never be violated.
- Index lookup and foreign-key-like columns where query patterns require it.
- Use repositories for normal lookup, projection, and paging. Add `query/` only for
  genuinely complex filtering, aggregation, window functions, or declared
  cross-module reads. Query code is read-only.
- `open-in-view` is disabled. Load everything needed by the result inside the
  service transaction.

## Transactions, concurrency, and async work

- Put `@Transactional` on service methods, never controllers or repositories.
- Keep transactions short and do not call AI providers, object storage, or other
  slow external systems inside a database transaction.
- Use `@Version` where concurrent updates can lose data.
- Use database constraints for uniqueness and other non-negotiable invariants.
- Use atomic database updates for counters and accumulators.
- Make workers and callbacks idempotent because delivery may happen more than once.
- For slow or failure-prone work: persist pending intent, commit, enqueue after the
  commit, process with bounded retries, and expose a terminal failure state.

## Tests

Tests cover behavior at the layer that owns it:

- Entity tests construct entities directly and verify business rules without
  Spring, mocks, or a database.
- Service tests verify orchestration, branching, repository interactions, and error
  paths. Do not duplicate entity-rule tests by mocking the same exception.
- Routine mapping-only controllers do not need tests. Use `@WebMvcTest` when HTTP
  behavior itself matters, such as security, validation, serialization, or status
  mapping.
- Repository integration tests are appropriate for custom queries whose behavior
  cannot be established by inspection.
- Test failure paths as well as the happy path.

## Python components

`ai_service` uses Python 3.11, Ruff, and pytest. Keep provider integrations behind
the existing provider modules, validate external responses, and never log provider
keys or user content unnecessarily.

`data_pipeline` owns generators, schemas, validators, and QA reports. Generated
output is not hand-edited; change the generator or source and regenerate it. Keep
large sources, media, local environments, and transient output untracked.

## Implementation workflow

Before coding:

1. Read this guide and the relevant entry in `docs/module-map.md`.
2. Inspect the target module and one existing vertical slice.
3. Identify the owner, tables, endpoint contract, transaction boundary,
   concurrency risks, migration, and tests.

Implement the smallest complete vertical slice in this order:

1. New migration, when the schema changes.
2. Entity behavior and repository.
3. Service transaction and orchestration.
4. Controller and DTO records.
5. Focused tests.

Do not add parallel abstractions that perform nearly the same job as an existing
class. Raise an architectural conflict instead of quietly working around it.

## Verification

Run the checks for every component touched:

```powershell
# Spring application
mvn formatter:validate
mvn --batch-mode clean verify

# FastAPI service
Set-Location ai_service
python -m ruff check app tests
python -m ruff format --check app tests
python -m pytest

# Data pipeline
Set-Location data_pipeline
python -m pytest -q
```

Before reporting completion, confirm:

- module and table ownership remain clear;
- layer and DTO boundaries are respected;
- migrations, constraints, and concurrency handling match the use case;
- no secret, cache, generated output, or build artifact was added;
- format checks and relevant tests passed;
- assumptions and deliberately omitted work are reported.

## Git workflow

Create feature and fix branches from `dev`, open pull requests into `dev`, and
promote tested changes from `dev` to `main`. CI is the minimum merge gate; do not
report a change complete without running the relevant checks locally when possible.
