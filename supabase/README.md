# Spool cloud database

Versioned cloud migrations and rollback-only SQL tests. Room remains the app's
source of data. Automatic account-scoped uploads and downloads are connected.
Conflict review and account-scoped Recovery are available from Account. Recovery
creates new copies from versions retained on this device. Individual saved copies
can be removed from Recovery on this installation; no automatic expiry is enabled.

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

## Conflict review and resolution

Migration `20261028134909_inventory_conflict_review` adds the authenticated,
read-only `get_inventory_snapshot(expected_owner, spool_uuid)` RPC. It verifies
`auth.uid()`, pins the empty search path, and revokes public/anonymous execution.
It returns the complete current aggregate, including deleted rows. Existing
revision/idempotency upload rules and write privileges are unchanged.

Account links to a shared conflict screen. Users can compare every spool field,
live/deleted print history, amounts, dates and failure flags, then confirm either
version. Opening, refreshing or cancelling a comparison does not modify inventory.
A fresh connection is required to review and confirm; ordinary inventory saves
continue offline. Both reviewed copies are retained locally and can be previewed from Account /
Recovery. Device-only removal is available; cloud purge is not.

`InventoryConflictResolver` refetches the cloud snapshot on confirmation and
requires it to match the reviewed version. An account/session switch cancels it.
Room then compares the complete reviewed local record, all history, every pending
operation and the frozen batch in one transaction. Changed versions require a new
review. Both copies, pending snapshots and the exact old frozen request are
archived in schema 21 before anything is replaced. Failure rolls back the archive,
queue, inventory, revision and outbox guard together. Migration 20-to-21 preserves
existing data, cursors, conflicts and batches.

Choosing cloud applies the complete reviewed snapshot without an outbox echo or
cursor jump. Choosing local against a live cloud spool creates a new conditional
batch based on the reviewed cloud revision. Its delta preserves common history,
adds/updates local history and deletes cloud-only live prints so the result matches
the selected whole version. A newer cloud edit conflicts normally rather than
being overwritten. Local spool deletion queues a conditional cloud deletion.

Deleted UUIDs are never resurrected. Keeping a local live spool after cloud deletion
creates a new spool and new history UUIDs. Keeping a local print deleted in the
cloud creates a new print UUID. The UI explains these consequences before
confirmation. Confirming two deleted versions creates no replacement. Receipts,
acknowledged deletion snapshots, tombstones and resolution archives are not pruned.
Existing 4-MiB/10,000-changed-history-row upload limits still apply; rejected work
remains queued and retained.

The comparison and confirmation state live in a ViewModel. Configuration changes
retain the active review. Process recreation restores only the selected spool and
refetches it; a confirmation is never replayed. Long histories use lazy rendering.

## Previous download verification

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

## Conflict verification and next step

Step 9 passed 182 selected automated tests (one opt-in live probe skipped), Android
build/lint (zero errors, 56 existing warnings), and shared iOS compilation. Fifteen
new real-Room tests cover both choices, retained copies, conditional deltas,
concurrent local/cloud changes, account switching, deletion/copy semantics,
archive/apply/clone rollback, duplicate confirmation, restart and history beyond
the 100-operation queue inspection limit. Five ViewModel tests cover explicit
confirmation/cancel, stale/offline handling, process recreation and cancellation.
Three new transport tests run on Android and native, and migration tests now cover
versions 10 through 20 including persisted conflicts/cursors.

All 33 conflict SQL assertions passed in a rolled-back rehearsal and after
migration deployment. Existing access, upload and pull suites passed as well.
Anonymous snapshot RPC calls returned HTTP 401 / PostgreSQL 42501. Existing cloud
inventory/tombstone fingerprints and receipt count were unchanged; fixtures rolled
back. No real user token was extracted.

A requested focused Redmi Note 8 Pro pass verified schema 20-to-21 preservation,
comparison/cancel, stale-cloud rejection, both choices, deleted-print identity
replacement, a new copy of a remotely deleted spool, restoration of a locally
deleted spool, and accepting cloud deletion. A missing saved-state route serializer
caused a background/rotation crash during testing; it was fixed and covered by a
route roundtrip regression test. The corrected confirmation survived backgrounding
and landscape/portrait rotation. Test fixtures were cleaned up; original 24 spools
and 31 history rows and primary cloud inventory remained unchanged, with no pending
changes or conflicts. Six secondary-account resolution archives remain intentionally
retained on the device.

The Step 9 phone pass did not cover reliable Android process-death simulation,
iOS runtime, real two-device or full E2E tests. Recovery is implemented in Step 10
below; automatic expiry remains disabled.

## Local recovery (Step 10)

Account / Recovery lists the account's retained deleted spools and both versions
of each saved conflict resolution. Preview shows every spool field and every
print that will be copied. Explicit confirmation creates a new spool and fresh
history UUIDs, keeping the source, existing inventory, server tombstones, pull
cursor and original identities unchanged. Restoration works offline; normal
outbox triggers queue the new aggregate for the existing automatic uploader.
No additional server migration, credential or permission is required.

Room schema 22 adds private account-scoped deleted-version storage and restore
receipts. A BEFORE DELETE trigger captures a signed-in local spool and its whole
live history before cascade; remote-apply suppression prevents download echoes.
Deleted aggregates delivered by the existing pull RPC are cached in the same
cursor/application transaction, including empty-account restore. Repeated pages
keep the first retained deleted version. A failed capture aborts deletion, and a
failed restore rolls back its copy, receipt and queue. A receipt permits only one
restoration per saved version, even after restart or deleting the restored copy;
the latter deletion becomes a separate recoverable version.

Migration 21-to-22 preserves existing tables, inventory, outbox, conflicts and
cursors, backfilling retained local deletion payloads, including acknowledged
ones. Conflict archives remain in their existing table. Account and session scope
checks prevent reading or restoring another account's versions. Restore compares
the exact retained source and re-decodes the preview before writing. Only the
selected source ID persists across process recreation; confirmation is never
replayed. The Recovery route is covered by the actual navigation serializer test.

Recovery availability is limited to saved copies on the current device. Existing
phones do not reset their pull cursor, so remote deletions consumed before this
upgrade may be unavailable unless another retained copy exists. A new device
can cache deleted cloud aggregates as its initial download proceeds. Local saved
conflict copies and restore receipts are not themselves synchronized. Deleted
snapshots can include previously deleted prints; the preview and confirmation
explicitly state that every displayed print will be copied under a new identity.
Live conflict versions exclude prints already deleted in that version. No records
are automatically purged, regardless of advisory server expiry metadata.

Verification: 141 selected tests passed (66 data/native, 14 database/native,
61 shared Android host), including 18 new recovery/migration/ViewModel cases.
Android build, shared iOS compilation and lint passed (zero errors, 56 existing
warnings). Migration preservation covers versions 10 through 21; all previous
schema entity definitions remain unchanged in version 22.

Focused Android phone checks (28-29 October 2026) passed upgrade preservation
(exactly 24 existing spools and 31 prints), preview/cancel, confirmation rotation,
account/guest isolation, restoring a cloud conflict version as a fresh copy with
all six prints, automatic upload verified against the cloud snapshot, and
receipt-based duplicate prevention after reopening. Deleting the disposable copy
retained its six-print device snapshot and downloaded cloud tombstone. Cleanup
restored the original signed-in account and exact original inventory; pending
uploads/conflicts are zero and database integrity/foreign-key checks passed.

The follow-up offline Android pass (29 October 2026) also passed. With no active
network verified before confirmation, a deleted device copy restored with fresh
IDs and all four retained prints; five new-copy operations remained queued.
The copy survived stopping/reopening offline. After reconnecting with Spool in
the background, the OS retry job uploaded automatically without opening the app
or tapping Sync now. The actual cloud snapshot matched every copied field and
history UUID; pending changes became zero without duplicate rows or receipts.
Disposable copies were deleted through the app and verified tombstoned in the
cloud. Final inventory is exactly the original 24 spools and 31 prints, with no
pending changes/conflicts and restored network settings. No product defect was
observed in the covered cases.

Complete screenshot layout review was obscured by a floating call. Local-side
archive restoration, iOS runtime, real two-device behavior and full E2E were not
phone-tested.

An unobscured visual review can be completed separately when the phone is available.


## Device-only recovery removal (Step 11)

Saved copies remain until manually removed. Recovery offers "Remove from this
device" from the list and preview, with a confirmation identifying the brand,
source kind and saved date. Removal also works for copies with an unreadable
preview. The confirmation explicitly limits removal to this installation and
explains that cloud copies may be available elsewhere or after reinstalling.
No automatic expiry, cloud deletion or account deletion is introduced.

Room schema23 adds an owner/source exclusion table without changing existing
entities. One transaction records the exclusion and deletes the selected retained
deleted-version row or clears only its selected conflict snapshot column. The
other conflict version and archive metadata remain intact. List, preview and
restore exclude removed sources; a trigger suppresses recapture of that same
source during normal sync on this installation. Repeated cloud pages still
advance the cursor. Removal is idempotent, scoped to the current account/session,
and can run offline. Failures roll back both payload removal and exclusion.

Active inventory/history, restored copies, pending operations/frozen requests,
restore receipts, remote revisions and pull cursors are untouched by removal.
It never queues a cloud delete or wakes the uploader. Sync records can retain
inventory payloads needed by the existing protocol. This is removal from local
Recovery, not secure erasure or deletion of every copy of that inventory. Clearing
app data/reinstalling removes local exclusions; a downloaded cloud snapshot may
then be available again.

Verification:153 selected tests passed (data/native73, database/native15, shared
Android host65), including12 new cases covering migration22 preservation,
individual conflict-side removal, failed-write rollback, owner/session isolation,
corrupt copies, duplicate confirmation and saved-state recreation, retained
receipts, offline removal and suppression after restart/repeated cloud pages.
The recapture test also verifies that exclusion cannot block a subsequent real
spool/history deletion or its outbox. Android build/lint and shared iOS compilation
passed.

A focused Android phone pass (29 October 2026) also passed upgrade22-to-23
preservation, cancellation, list/preview removal of two retained test copies,
persistence after reopening and normal sync, and read-only cloud verification
that every saved spool field and all four retained prints stay unchanged there.
All other application tables, including receipts, outbox and cursors, remained
unchanged. Final inventory is the exact original24 spools/31 prints, pending0,
conflicts0, integrityok and foreign-key errors0.

The pass found clipped removal-confirmation text in landscape. The text area now
scrolls; portrait/landscape screenshots and UI trees confirm the whole explanation
is accessible and action buttons stay visible. Rotation/cancellation after the
fix preserve every data table. Android build/lint,65 shared host tests and shared
iOS compilation passed again after this UI-only fix.

Conflict-side/unreadable-copy removal, offline removal, failed writes and account
switches were covered by automated tests rather than this phone pass. No iOS
runtime, actual two-device or full E2E pass; no server mutation from removal.


## Profile display-name editing (Step 12)

Account / Edit name prefills the currently displayed name and offers Save name
or Cancel. Signup remains optional and unchanged; missing provider names are
allowed. Only name is editable: provider-managed email remains read-only, and
there is no photo, avatar, phone number or additional registration form.

Saving calls the authenticated Supabase Auth updateUser endpoint with only
user metadata data.spool_display_name. This application-owned key takes priority
over provider display_name/full_name/name, so later Google responses containing
an updated provider name do not replace the user's saved choice. No email,
password, roles or ownership fields are sent. Updates use the current user's
credentials, check the expected permanent account ID, and serialize with sign-in
and sign-out. Metadata is display data only and never authorizes inventory access.
No new database schema, migration, table, permission, secret or dependency.

Name saves require connectivity; this step does not add an offline profile-upload
queue. The last successfully saved name remains in the existing cached auth
session for offline display. Editing and failed-save drafts survive configuration
changes and supported saved-state recreation through an account-bound
SavedStateHandle. Drafts are cleared on account changes or Cancel; saving never
replays automatically after recreation. Blank, over80-character and control-filled
names are rejected; names are trimmed and Unicode is supported. A provider name
longer than80 characters can be shortened before saving. Busy actions ignore
repeated taps, failures/timeouts retain the draft, and errors never expose tokens
or provider exception details. The existing themed text field now supports an
enabled parameter so the draft is disabled during a save. The editor's content
scrolls for limited-height layouts.

Verification:218 selected tests passed plus1 existing opt-in live probe skipped
(network Android36 passed/1 skipped, network native35, data native73, shared
Android host74). Fifteen new distinct cases (6 SDK/MockEngine cases on Android
and iOS,9 ViewModel cases) cover name-only request payloads, SDK session caching,
subsequent provider-name changes, wrong account/invalid input, failure/retry,
serialization with sign-out, prefill/cancel, duplicate taps, saved-state drafts,
account switches/late replies, missing/long names and timeout feedback. The existing
real-Room ownership test also verifies name/email changes preserve inventory,
pending rows and the inventory session revision. Android build/lint passed
(zero errors,56 existing warnings), and shared iOS compilation passed.
A focused Android phone pass (29 October2026) passed prefill/cancel, blank-name
validation, an actual temporary name save verified in Supabase, cached-name
restoration after reopening, and preservation after real Google sign-out/sign-in.
Offline Save name with no active network shows an error and retains the draft;
network settings were restored. The original visible name was restored using the
app; its new custom metadata key retains that original name. Permanent account
ID, email and Google provider name stayed unchanged.

The pass found that the landscape keyboard collapsed the old dialog's input
area. The editor now uses a themed BasicAlertDialog/Surface with the heading,
field and actions in one scrollable column. Post-fix phone screenshots/UI trees
verify visible editable text, reachable Save/Cancel actions, retained rotation
draft and cancellation. Android build/lint,74 shared host tests and shared iOS
compilation passed again after the UI-only fix.

Every Spool application data table remained identical to its pre-test state:
original24 spools/31 prints, queues, receipts, recovery copies and sync cursors.
Pending0/conflicts0/integrityok/FKerrors0; no new Spool crash. No iOS runtime,
two-device propagation or full E2E pass was performed.

Reference: [Supabase Kotlin profile metadata update](https://supabase.com/docs/reference/kotlin/auth-updateuser).

References: [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[column permissions](https://supabase.com/docs/guides/database/postgres/column-level-security),
[migrations](https://supabase.com/docs/guides/local-development/database-migrations),
[migration API](https://supabase.com/docs/reference/api/v1-apply-a-migration),
[WorkManager constraints and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
