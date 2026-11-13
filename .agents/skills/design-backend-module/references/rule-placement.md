# Where Business Rules Live

## Contents

1. The deciding question
2. What putting a rule in the entity buys
3. What it does not buy
4. What stays in the service
5. Conventions that make it hold
6. Naming it accurately

## The deciding question

For any entity, ask:

> Is there a state that is valid as data but wrong as business?

- **Yes** - the rule belongs in the entity. *A published exam with no questions. An attempt submitted twice. A streak continuing across a missed day.*
- **No** - plain CRUD, logic in the service. *Renaming a category. Reordering a list. Toggling a flag nobody reasons about.*

This is better than "is this important?" because two people answer it the same way. Importance is an opinion; an invalid state is a fact, and the user can usually name one immediately or not at all.

Expect a handful of entities to carry rules and most to be plain. If every entity in a design has rules, they are being invented.

## What putting a rule in the entity buys

Not correctness - reachability.

With the rule in a service, anyone adding a second write path must know the rule exists, find it, and copy it correctly. The usual failure is not forgetting the rule but never learning it existed: reading the new service gives no hint that publishing has conditions.

With the rule in the entity and the guarded setters removed, there is exactly one way to reach that state, and it carries the check. The compiler enforces it - a second write path cannot even be written.

The cost is close to zero: the same lines, moved.

## What it does not buy

Be honest about the gaps, both when designing and when explaining the design:

- **Concurrency.** Two transactions each load their own copy, each pass the check, and both commit. Entity rules do nothing here. This needs a version column, a database constraint, or both - decide it during design, not later.
- **Direct SQL writes.** Query or migration code that updates the table bypasses the entity entirely. This is why query code must be read-only.
- **Deliberate bypass.** Java package access, framework-generated setters, or reaching through an object graph can all defeat it.
- **Lombok.** `@Data` or `@Setter` regenerates exactly the setters the rule depends on.

So the accurate claim is that this turns an easy accidental mistake into one that has to be made on purpose. Anything that must never happen still needs a database constraint.

## What stays in the service

- Anything spanning several entities - scoring that needs both the exam and the attempt.
- Anything requiring I/O - loading, calling another module, storage, external providers.
- Anything with no rule to enforce.

Entities receive plain values: an `Instant`, a `UUID`. Never a repository, a service, or a clock. An entity that performs I/O has stopped being an entity.

The service does not disappear. It still opens the transaction, loads, calls, coordinates, and maps to records - it just stops carrying the branching rules about one entity's state.

## Conventions that make it hold

Decide these during design, because they are what makes the rule real rather than decorative:

- No setters for guarded fields, and no `@Data` or `@Setter` on the entity.
- Creation through a named static factory; the JPA no-arg constructor stays `protected`.
- Identity assigned in code at creation, so the object has identity from the moment it exists.
- No repository for an entity that is only ever reached through another.
- Writes to those tables go through the entity - never through query or migration code.

## Naming it accurately

This shape is a **rich domain model**, in contrast to an anemic one where entities hold only data and services hold all behavior.

It is one pattern associated with DDD, not DDD itself. There is no separate domain model here, no repository abstraction in the DDD sense, no aggregate boundaries being enforced by the framework. Describing the architecture as "three layers, with business rules in entities where invariants exist" is accurate. Describing it as "DDD" invites questions the design does not answer.

Accuracy is worth more than the bigger label - the design is easier to defend when its name matches what it does.
