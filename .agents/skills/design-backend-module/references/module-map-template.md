# Module Map Template

The module map is the durable output of a design conversation. It lives in the repository - `docs/module-map.md` unless the project already has a better home - so decisions survive past the chat that produced them.

Keep each entry short. A long entry stops being read, and an unread map is worse than none because it looks authoritative while being stale.

## Per-module entry

```markdown
### <module name>

- **Subdomain:** core | supporting | generic
- **Owns writes to:** <tables>
- **Reads from:** <tables> (owned by <module>)
- **Entities with rules:** <names, or "none - plain CRUD">
- **Talks to:** <module> via <ID | read projection | module API | domain event>
- **Read path:** <repository only | separate query package | reads across modules - see exception below>
- **Why:** <one sentence - the reason this shape was chosen>
- **Revisit if:** <the concrete signal that would reopen this decision>
```

`Why` and `Revisit if` are the fields that earn the file's existence. The rest can be re-derived from the code; the reasoning cannot.

## Header for the file

Open the map with the rules that apply everywhere, so an entry never has to restate them:

```markdown
# Module map

Rules that hold at every depth level:
- one module owns writes to a table
- persistence types stay inside their module
- cross-module references are IDs
- transaction boundary at the use case
- schema changes through migrations only
- shared/ holds technical types only, never domain
- query code is read-only; writes go through the owning module

Architecture: three layers - controller -> service -> repository, modules
first and layers inside them. Business rules live in entities where an
invalid business state is possible; everything else is plain CRUD.
Decide rule placement per entity, not per module.
```

## Keeping it honest

- Write the entry when the decision is made, not at the end of the project.
- When a decision changes, edit the entry and update `Why`. Do not append a second, contradictory entry.
- If a `Revisit if` trigger fires, that is a design conversation, not a code review comment.
- If the map and the code disagree, the code is the truth and the map is a bug - fix it in the same change.
