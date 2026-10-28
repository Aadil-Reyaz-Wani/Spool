# Spool cloud database

Versioned cloud migrations and rollback-only SQL tests. Room remains the app's
source of data. Automatic uploads are connected; downloads and restore are not enabled yet.

## Schema and access

- `inventory_spools` stores account ID + spool UUID, inventory fields, server
  timestamps/revision and soft-deletion metadata.
- `inventory_usage_logs` stores account ID + history UUID and a composite foreign
  key to a spool in the same account. Device-local integer IDs are never uploaded.
- `inventory_tombstones` stores server-created deletion snapshots with a 30-day
  recovery window. No automatic purge is enabled.
- `spool_private.inventory_upload_receipts` stores a SHA-256 request fingerprint
  and the original response, keyed by account and immutable batch ID.

Authenticated requests can read their own public rows through RLS. Direct client
inserts, updates and hard deletions are revoked. All inventory writes go through
`upload_inventory_batch`, which checks the JWT subject against the expected owner,
serializes same-account writes and checks the last acknowledged spool revision.
The spool and related history changes apply in one transaction. A revision mismatch
or deleted UUID returns a conflict snapshot without overwriting cloud data. Every
successful/conflicting batch gets a receipt: retries return the original response,
and reusing a batch ID for different content is rejected.

The upload function is security definer with an empty search path and explicit
account predicates on every data lookup/write. Only authenticated users have
execution permission. Its helpers and receipts live in the unexposed
`spool_private` schema with no client access. Deletion triggers capture snapshots
and soft-delete live child history atomically. Clients cannot change identities,
forge server metadata, modify recovery snapshots or resurrect deleted UUIDs.
Deleting an auth account cascades only that account's data and receipts.

No database password, management token, service-role key or user session belongs
in this directory or the mobile app.

## Migrations and verification

Both migrations are deployed to Spool with matching Supabase migration history:

- `20261028012227_inventory.sql`: inventory/history/recovery tables and RLS.
- `20261028014401_inventory_upload.sql`: upload RPC, receipts and RPC-only writes.

Use a new migration for later changes; do not rerun these on a migrated database.
Apply migrations transactionally. Files include outer transaction boundaries for
manual execution; omit those when using the Supabase migration API, which manages
the transaction and history entry.

Run each test as an administrator against a Supabase database with both migrations
installed:

```sh
psql "$SPOOL_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/inventory_access.sql
psql "$SPOOL_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/inventory_upload.sql
```

Keep credentials in an environment variable or password manager, never in source
control or terminal output. Each test creates two synthetic auth users inside a
transaction, exercises real authenticated/anonymous roles with JWT subjects and
rolls everything back. Fixture ID collisions fail instead of touching existing
users. The final PASS result appears only if all assertions succeed. These are
plain SQL tests, not pgTAP tests; they do not need a local Docker stack.

The upload migration was rehearsed with both suites in rolled-back transactions
before deployment. Public REST endpoints are also checked without a user session.
No real-account inventory is uploaded or modified by these tests.

## Automatic uploads

Koin starts `InventoryUploadCoordinator` at application initialization. It observes
the current account's durable queue, schedules OS work, and enables in-process
uploads while the shared Compose lifecycle is resumed. New changes, reconnect
events, refreshed authentication, resume and manual retry wake foreground work.
Transient failures back off from 30 seconds to 5 minutes while the app is visible.
Network waits never block local saves.

Android uses unique WorkManager requests with a connected-network constraint and
exponential backoff. One-time work handles pending changes; 15-minute periodic
work covers process death and the final-enqueue race while a previous job finishes.
The OS decides execution timing. Work is bound to an account and session revision,
cancelled on account changes, and revalidated by the worker before uploading.
Stale persisted jobs cancel themselves instead of using the new account.
Each drain processes at most 20 aggregates or 90 seconds, leaving more work queued
for a later pass. Foreground and worker drains share a mutex.

The Account screen shows checking/uploading/waiting/error state, pending-operation
and conflicting-spool counts, and a retry button for uploadable work. Counts are
filtered to the displayed account. Sign-out with pending work explains that it
stays on the device and resumes only for the same account. Guest import explicitly
explains that imported data will upload when connected.

iOS compiles the shared foreground coordinator, but sign-in is still unavailable
and no iOS background task is registered. Do not promise iOS background execution.

The explicit `InventoryUploader.uploadNext(expectedScope)` remains the
one-aggregate protocol entry point. Guest scopes cannot upload. The transport
captures one account/token pair, uses a 30-second timeout and never logs
credentials, inventory or server bodies.

Room schema 19 stores immutable upload batches, their exact operation IDs and the
last acknowledged server revision per account/spool. Preparation is transactional.
Network waits never hold the inventory lock, so local saves remain available.
Edits made during an upload stay queued for the next batch. A lost response,
cancellation, restart or local acknowledgement failure reuses the original batch.
A valid server acknowledgement advances the local revision and removes only that
batch's acknowledged upserts in one transaction. Acknowledged deletion snapshots
remain stored locally and are excluded from subsequent uploads.

Conflicts persist the remote snapshot alongside the frozen local request and
pending operations. That spool stops uploading until explicit resolution; others
can continue. Switching accounts cancels the request and prevents local
acknowledgement under a different scope. A request already accepted by the server
can still commit for its original account; its receipt makes retry safe on return.

Requests allow at most 4 MiB of spool/history JSON and 10,000 changed history rows.
Larger aggregates are rejected and kept queued until a future safe splitting
protocol is available. Receipts and acknowledged deletion snapshots have no
automatic expiry.

## Next step

The scheduling change passed 84 automated tests, Android build/lint and shared
iOS compilation. Eight new integration cases use real temporary Room databases
and simulated network/OS scheduling. A requested focused Redmi Note 8 Pro pass
also verified initial upload, offline edits/retry/restart, automatic reconnect,
actual WorkManager upload while the launcher was foreground, pending sign-out
warnings, and two-account isolation with pending work resuming on return.
A throwaway spool and four prints were deleted through the app; cloud soft
deletion uploaded successfully. Original 24 spools and 31 history records remain
unchanged locally and in the cloud, with zero pending changes. UI action movement
and cold deep-link Back navigation found during testing were fixed and rechecked.
Phone conflict injection, iOS runtime and full app end-to-end tests were not run.
This upload-only service is not completed cloud sync.

Downloads must avoid upload loops, persist pulled base revisions and use a cursor
that handles transaction ordering. Server timestamps alone are insufficient.
Restore/merge previews, conflict resolution UI, recovery operations, retention and
iOS authentication remain later steps.

References: [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[column permissions](https://supabase.com/docs/guides/database/postgres/column-level-security),
[migrations](https://supabase.com/docs/guides/local-development/database-migrations),
[migration API](https://supabase.com/docs/reference/api/v1-apply-a-migration),
[WorkManager constraints and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
