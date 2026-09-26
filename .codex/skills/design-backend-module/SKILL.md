---
name: design-backend-module
description: Brainstorm and decide the design of a backend module before any code is written - where the boundary falls, which subdomain it belongs to, which tables it owns, where business rules should live, and how it talks to other modules. Use this skill whenever the request is about shape rather than implementation ("I want to add a speaking module", "should this be its own module", "where does this feature belong", "how should these two modules talk", "should this rule go in the entity"), and when a feature request cannot be placed in any existing module. Use it before the implementation skill, not instead of it - this skill produces decisions and a module-map entry, never production code.
---

# Design a backend module

This is a design conversation, not an implementation task. The output is a set of decisions the user agrees with, plus an entry in the project's module map. No production code, no package skeletons, no migrations.

The target architecture is a three-layer modular monolith: `controller -> service -> repository`, modules first and layers inside them, with business rules kept in entities wherever an invalid state is possible. That is settled - this skill decides module shape within it, not whether to use it.

## Rule zero: discover, never assume

The user's domain is theirs. Nothing here entitles you to invent modules, entities, tables, or business rules they have not described.

1. Read the repository first: existing business packages, migrations, the module map if present, any architecture test.
2. Ask the user for what the code cannot tell you: what the capability is for, who uses it, what must never happen.
3. When a boundary is genuinely ambiguous, present the options with trade-offs and let the user choose. Do not pick silently and move on.
4. If the user has already decided something, take it and build on it. Do not re-litigate a settled decision unless it would cause real damage, and then say so once, plainly.

A module is a long-lived ownership decision. Getting it wrong is expensive to undo, which is why this is a conversation rather than a generated answer.

## Run it as a dialogue

Ask a few questions at a time, not a questionnaire. Let the user's answers redirect the order below - these phases are a checklist of what must end up decided, not a script.

Reflect the user's own vocabulary back to them. The names chosen here become package, class, and table names, so they are part of the design.

Stop and hand off as soon as the decisions are made. Do not keep designing past what the user asked for.

## Phase 1 - Frame the capability

- What can a user do after this exists that they could not before?
- What are the events, in the past tense? *Submission was scored. Level was determined. Streak was broken.*
- Who triggers each - a person, a schedule, another module, an external callback?
- What must never be true? These become the rules, and they drive most later decisions.

Event listing is the fastest way to find a boundary, because boundaries show up where the language changes. See [boundary-discovery.md](references/boundary-discovery.md).

## Phase 2 - Decide whether it is a module at all

1. Does an existing module already own the tables involved? Then this is a feature inside it. Stop and hand off.
2. Does it share the same language, lifecycle, and reasons to change as an existing module? Then it belongs inside that module.
3. Does the same word mean something different here than elsewhere? That is a real boundary - the strongest signal there is.
4. Is it separate mostly because it is technically different - async, external API, file handling? Weak reason. Technical difference is not a business boundary.
5. Is it separate because a different kind of user touches it? Not a reason at all. Admin and end-user features for one capability share a module and differ by controller.

New modules are cheap to add and expensive to merge back. On a genuine coin flip, extend the existing module and revisit when the language actually diverges.

## Phase 3 - Classify the subdomain

Ask the user directly: what makes this product better than its alternatives?

- **Core** - the answer lands here. Spend the design attention here rather than spreading it evenly.
- **Supporting** - needed, but not where the product wins. Solid and modest.
- **Generic** - every product has one and yours is not special. Buy it, use a library, or write plain CRUD.

This classification is the main defence against uniform over-engineering, and it is the part of the design most worth writing down in prose.

## Phase 4 - Decide where the rules live

Per entity, not per module. A module usually holds both kinds.

The deciding question:

> Is there a state that is valid as data but wrong as business?

- **Yes** - the rule belongs in the entity, and the setters guarding those fields go away. *A published exam with no questions. An attempt submitted twice.*
- **No** - plain CRUD, logic in the service. That is a legitimate answer, not a shortcut.

Expect only a handful of entities to carry rules. If every entity in the design has them, the rules are probably being invented.

[rule-placement.md](references/rule-placement.md) covers what this protects, what it does not, and the concurrency question it never answers.

Guardrail worth stating out loud: **do not half-apply it.** If an entity holds rules, every write goes through it. A rule with a public setter beside it reads as a guarantee and is not one.

## Phase 5 - Assign ownership

- Which tables does this module write? Exactly one module may write a given table.
- Which tables does it only read, and who owns those?
- Which entities carry rules, and which are plain?
- Can two requests collide on the same row? If so, decide now whether that needs a version column, a database constraint, or both. This is a design decision, not an implementation detail.

If two modules both want to write one table, the design is not finished.

## Phase 6 - Decide how it talks to other modules

Pick per relationship, not once for the module:

| Option | Fits when | Costs |
|---|---|---|
| Store the other entity's ID | A reference, nothing more | Nothing enforces the ID still exists |
| Call the other module's service | Needs its data or its behavior, synchronously | Direct coupling; watch for cycles |
| Spring application event | A reaction, not a request | Not durable; ordering is looser |
| Persisted job | A reaction that must not be lost | More machinery |

Never a JPA relationship across a module boundary, and never writing another module's tables.

If a cycle appears, check the boundary before reaching for events. Two modules that can never be untangled are probably one module.

## Read paths

Reads deserve their own decision rather than inheriting the write path.

- Simple reads and projections use the repository.
- Complex reads - optional filter combinations, aggregation, window functions - use dedicated query code in a package separate from the repository, and strictly read-only.

**A query joining tables owned by different modules belongs to no module.** Do not resolve this by inventing a module for it. Present the shapes and ask:

| Shape | Buys | Costs |
|---|---|---|
| Each module exposes its own figures; the caller composes | Boundaries stay intact | No cross-cutting filter, sort, or paging |
| One read-only module permitted to join across owned tables | Simple, full query power | Depends on other modules' schemas |
| A dedicated read table or view, maintained from events | Cleanest coupling, best performance | Most machinery; eventual consistency |

Which fits depends on how many such queries exist, how much they matter, and how much time the user has - none of which this skill knows.

Whatever is chosen, record it as a named exception with its limits: which schemas it may read, and what happens when those change.

## Shared code

`shared` is the most reliable way to wreck a module structure, because everything depends on it and nothing owns it.

Something may live there only if it passes all three:

1. It belongs to no module's business vocabulary.
2. It never changes for a business reason.
3. Adding a field to it forces no module to change.

Test three is the sharpest. Failing it means the shared thing is domain, not infrastructure.

Legitimate: base error types and codes, paging types, a minimal current-user type. Not legitimate, however reasonable it sounds:

- **A shared entity.** "Every module needs User" is how the ID-only rule gets broken without anyone noticing.
- **A shared business enum.** Two modules using the same `Level` today is usually the same-word-different-meaning signal - one side will eventually need a value the other must not have.
- **A DTO passed between modules.** The owning module publishes its own record; moving it to `shared` erases who owns it.
- **A base entity that grows business fields.** Audit columns are tolerable; `status` or `ownerId` on everything is a domain model imposed system-wide.

Do not create `shared` up front and wait for it to fill - it will. Duplicate until the third occurrence, then extract. Past roughly ten files, something in it belongs to a module.

## Phase 7 - Record the decision

Write the outcome to `docs/module-map.md`, unless the repository already has somewhere better. Decisions that live only in a chat get re-argued in the next code review.

Use [module-map-template.md](references/module-map-template.md). Keep it to a few lines per module. The reasoning matters most - it is what tells a future reader whether the decision still holds.

Also record what was deliberately left open, and what would make the team revisit it.

## Close the conversation

Summarize in plain language, confirm the user agrees, and say explicitly what was decided versus deferred. Then hand off - implementing the first slice is a separate task for the implementation skill, which reads the module map rather than re-deriving any of this.
