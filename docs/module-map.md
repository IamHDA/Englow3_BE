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

## shared (technical package, not a business module)

- **Subdomain:** n/a - infrastructure, not a business capability
- **Owns writes to:** none
- **Reads from:** none
- **Contains:**
  - `config`: `SecurityConfig` (SecurityFilterChain + JWT filter registration), `RedisConfig` (connection factory / template bean), `StorageConfig` (S3 client/presigner bean)
  - `security`: JWT verification for Supabase-issued tokens, minimal `CurrentUser` (id, role)
  - `error`: generic `ErrorCode`, `BaseException`, `ApiErrorResponse`, `GlobalExceptionHandler`
  - `storage`: `ObjectStorageClient` - generic `upload(bucket, key, stream)` / `presign(bucket, key)`, no knowledge of what a key means
- **Explicitly does NOT contain:**
  - business error codes (e.g. `ATTEMPT_ALREADY_SUBMITTED`) - live in the owning module, exception extends `BaseException`
  - `@PreAuthorize` / "who can do what" rules - declared per module at the controller/service that owns the action
  - Redis lock key naming, TTL, and lock semantics (e.g. `attempt-lock:{attemptId}`) - owned by the exam-attempt module; shared only provides the connection bean
  - S3 object key/path conventions and bucket choice per file type - owned by the module that owns the file: user module (avatar/banner), exam module (question audio/image), exam-attempt module (speaking recordings, submission files)
- **Why:** security/error/redis/s3 config are pure infrastructure - they don't belong to any module's business vocabulary, don't change for business reasons, and adding a field to them doesn't force a module to change. Auth itself is delegated to Supabase (JWT issuance, no self-managed sessions/refresh tokens), so shared only needs to verify tokens, not manage them.
- **Revisit if:** any business-specific rule (lock semantics, key naming, authorization logic) starts leaking into `shared/*` - that is domain logic misplaced, move it into the owning module. Also revisit if the project stops relying on Supabase for auth and needs to self-manage sessions/refresh tokens.
