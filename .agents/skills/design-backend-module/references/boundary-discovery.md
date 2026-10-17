# Boundary Discovery

## Contents

1. Event listing
2. Signals that a boundary is real
3. Signals that a boundary is wrong
4. Same word, different meaning
5. Splitting and merging later

## Event listing

The cheapest way to find boundaries is to list what happens, before deciding what classes exist. Run it with the user, not for them.

1. Ask for every notable thing that happens, phrased in the past tense, in the user's own words. *Answer was submitted. Score was calculated. Streak was broken. Recording was rejected.*
2. Put them in rough time order. Order is enough; precision is not needed.
3. Mark who or what caused each one: a person, a schedule, another part of the system, an external callback.
4. Mark the ones the user calls out as "this must never happen" - those are invariants and they decide modeling depth later.
5. Group events that share vocabulary, share a lifecycle, and change for the same reasons.

Those groups are candidate modules. They are candidates, not conclusions - confirm them with the user before writing anything down.

Two things this surfaces early, which is most of its value:

- Events with no owner. Something must produce them, and nothing does yet.
- Events that arrive from outside on their own schedule. These usually need an async workflow rather than a request/response path, and that changes the design.

## Signals that a boundary is real

- The same word means different things on either side of it.
- The two sides change for different reasons, at different times, driven by different people.
- One side could plausibly be replaced by a bought product without the other side caring.
- Data crosses the line, but behavior does not - one side only needs to know something happened, not how it works.
- The two sides have different consistency needs: one must be immediate, the other can settle later.

## Signals that a boundary is wrong

- A single user action requires writes on both sides to be correct together. That is one consistency boundary being cut in half.
- Every change to one side forces a matching change to the other.
- One side reaches into the other's tables to write, or duplicates its persistence mapping to read.
- The two sides call each other synchronously in a cycle.
- The split was made for a technical reason - "this part is async", "this part calls an external API". Technical difference belongs in infrastructure. It is not a business boundary.
- One module exists only to hold things that did not fit elsewhere.

When several of these appear, raise it with the user as a boundary question rather than working around it in code. Working around a wrong boundary is how the workaround becomes permanent.

## Same word, different meaning

This is the strongest boundary signal and the easiest one to miss, because the instinct to avoid duplication argues against it.

When one noun carries different meanings in different parts of the system, the right answer is usually two models with a translation between them - not one shared model with optional fields for whichever meaning does not apply.

The tell: a single entity accumulating nullable fields, or a status enum whose values only make sense in one part of the flow. That is two concepts sharing a table.

Discuss the cost honestly with the user. Two models plus a translation is more code than one shared model. It pays off when the two sides evolve independently, and does not when they are genuinely the same thing seen twice.

## Splitting and merging later

Boundaries drawn before real usage are guesses, and some will be wrong. Design so that being wrong is survivable:

- Splitting one module into two is usually tractable, especially if writes were already owned by one path.
- Merging two modules that grew apart is painful, because both schemas and every consumer are involved.

That asymmetry is why fewer, larger modules is the safer starting position. Let a module grow until the language inside it visibly diverges, then split along the line the language shows you - rather than guessing the line in advance.

Record in the module map what would trigger a revisit, so the next person has a reason to reopen the question instead of assuming the current shape was carefully chosen.
