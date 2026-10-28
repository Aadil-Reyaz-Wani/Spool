# Spool cloud database

Versioned cloud migrations and rollback-only SQL tests. Room remains the app's
source of data. Automatic account-scoped uploads and downloads are connected.
Conflict resolution and recovery UI are not enabled yet.

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

Room schema 20 stores immutable upload batches, their exact operation IDs and the
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

## Downloads and restoration

Migration `20261028075451_inventory_pull` adds a private per-account transactional
counter and coalesced aggregate change feed. Counter updates serialize on the
account row until commit, avoiding gaps caused by timestamp/sequence allocation
before a transaction commits. Feed positions roll back with failed writes.
Existing live and soft-deleted aggregates are seeded without changing inventory.
No feed/tombstone pruning is enabled.

`pull_inventory(expected_owner, cursor)` requires an authenticated matching subject
and a nonnegative cursor. Only authenticated execution is granted; private tables
and trigger functions are inaccessible to mobile roles. A page contains one full
spool/history snapshot, its next position and whether more pages were visible.
Parent revisions make duplicate/replayed pages harmless. Deleted parents and
history remain available to offline devices. Missing records are never inferred
from a truncated history response: the SQL aggregates the complete history.

`InventoryDownloader` captures the active account/session scope, cancels when it
changes, and rechecks it before Room writes. The transport captures the matching
unexpired account/token pair, validates owner/UUID relationships, cursors,
revisions, unique history UUIDs and finite nonnegative values, and sanitizes errors.
The cursor, parent/history changes and base revision commit in one transaction.
Stable UUIDs map to existing local IDs or newly allocated device IDs. Global UUID
collisions with guests/other accounts fail safely without transferring data or
advancing the cursor.

Remote application temporarily suppresses that account's outbox triggers inside
the same transaction. The guard is removed before commit; rollback/process death
cannot leave capture disabled. Subsequent local edits queue normally and upload
against the pulled revision. Pending local writes/deletions and frozen batches
are never overwritten or acknowledged by downloads. A newer remote revision is
saved beside a frozen local request as a conflict; the cursor can continue to
other aggregates. Resolving a conflict remains a later user action.

The existing coordinator/Android worker runs uploads before downloads, so lost
upload replies are retried through their receipts before a pull can misclassify
them as conflicts. Each pass downloads at most 20 pages within the shared 90-second
budget; remaining work retries later. Resume, connection events and Sync now wake
work. Foreground polling checks every minute while idle; the existing 15-minute
Android periodic work checks for changes even without local pending edits.
Android controls background timing. iOS authentication/background tasks remain
unavailable, although shared code compiles.

## Verification and next step

Step 8 passed 154 automated tests (one opt-in network probe skipped), Android debug
build, lint (zero errors, 56 existing warnings), and shared iOS simulator compilation.
Fourteen new real-Room download tests cover restoration, IDs/fields/history,
restart, deletions/stale replay, concurrent local edits, preserved conflicts,
account-switch cancellation, guest/other-account collisions, cursor-write rollback,
outbox suppression/re-enabling, pulled base revisions, bounded background restore,
and upload-receipt ordering. Migration coverage now includes versions 10 through
19, preserving current frozen batches, revisions, operation IDs and inventory.
Transport tests reject malformed/foreign responses and classify auth/network errors.
An entry-form regression test preserves stale drafts across recreation. The form
compares its original editable baseline before saving; a transactional compare
and update also rejects changes arriving between that check and the write.

All 27 pull SQL assertions passed in a rolled-back deployment rehearsal and again
against the deployed endpoint. Existing access/upload SQL suites also passed after
deployment, with all test fixtures rolled back. Anonymous public REST execution
returned HTTP 401 / PostgreSQL 42501. Existing inventory/tombstone fingerprints and
receipt count were unchanged; no real user session was extracted. A requested focused
Redmi Note 8 Pro pass also verified upgrade preservation, automatic restore into an
empty account, remote edits/deletions, reconnect, stale-draft rejection, conflict
preservation across restart/account switching, and no upload echoes. Remote writes
were simulated through the public upload RPC; two physical devices were not used.
Throwaway fixtures were cleaned up, with original 24 spools/31 logs unchanged,
pending/conflicts zero and network settings restored. A pre-existing currency
formatter defect (1.20 displayed as 1.19) was fixed using rounded integer cents
and rechecked on the phone. iOS runtime/full E2E were not tested.

Next: conflict-resolution and recovery UI/retention. Do not claim those
later features are available. No automatic deletion of receipts, tombstones or
recovery snapshots is enabled.

References: [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[column permissions](https://supabase.com/docs/guides/database/postgres/column-level-security),
[migrations](https://supabase.com/docs/guides/local-development/database-migrations),
[migration API](https://supabase.com/docs/reference/api/v1-apply-a-migration),
[WorkManager constraints and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
