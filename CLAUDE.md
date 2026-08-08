# Englow3 backend

## Controllers

Every controller method returns `ResponseEntity<T>`, including the plain 200 cases
(`ResponseEntity.ok(...)`). House style - keep it uniform rather than mixing bare
records with wrapped ones.

Responses are not wrapped in a success envelope: the HTTP status carries success or
failure, and errors already have one shape via `ApiErrorResponse`.

## Errors

A business exception carries a `code` and a message, nothing else. No web type
(`HttpStatus`, `ResponseEntity`, `ResponseStatusException`) may appear in an
entity or a service - deciding that a broken rule is a 409 rather than a 422 is
a delivery concern, and it lives entirely in `GlobalExceptionHandler`.

## Testing

Two layers, each with its own responsibility. No controller tests.

**Entity tests** - the business rules themselves. Construct the entity directly:
no Spring, no mocks, no database. One test per rule, plus the happy path.

**Service tests** - orchestration only: which entity method is called with which
arguments, how the flow branches, what the repositories return and which errors
that raises. Mock the entity here.

Two things a service test must not do:

- re-assert a rule the entity test already covers - it belongs one layer down
- stub an entity to throw the very exception the test then asserts; that test
  stays green after the rule is deleted, so it proves nothing

This replaces the "Controller tests" line in
`.claude/skills/implement-english-learning-backend/references/implementation-patterns.md`.
