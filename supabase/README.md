# Spool cloud database

This directory contains the versioned cloud schema and rollback-only access tests.
The app still reads and writes Room. This schema does not start uploads, download
inventory, change authentication, or move existing phone data.

## Schema

- `public.inventory_spools`: account ID + stable spool UUID, inventory values,
  server revision/timestamps, and soft-deletion timestamp.
- `public.inventory_usage_logs`: account ID + stable history UUID, with a composite
  foreign key to a spool in the same account. No device-local integer IDs.
- `public.inventory_tombstones`: server-created snapshots for deleted spools and
  history, with a 30-day recovery window. They remain stored until a later retention
  implementation can safely purge them without resurrecting old device data.

Authenticated requests can read their own rows and insert/edit their own live
inventory through explicit column grants and RLS. Identity fields and server
metadata cannot be updated by clients. Anonymous requests have no table access.
Clients cannot hard-delete inventory or write/delete recovery snapshots. Server
triggers capture snapshots and soft-delete a spool's live history atomically.
Deleted rows reject stale edits and require a future explicit recovery operation.
Deleting an auth account cascades its cloud data without affecting other accounts.

The privileged deletion function lives in the unexposed `spool_private` schema,
has an empty search path, takes ownership from the checked triggering row, and has
no client execution grant. The other trigger function uses invoker privileges.
No database password, management token, service-role key, or user session belongs
in this directory or in the mobile app.

## Migration and verification

`migrations/20261028012227_inventory.sql` was applied to the Spool project through
Supabase's migration API and is recorded under that version in migration history.
Do not rerun it against an already migrated database. Use a new migration for
subsequent schema changes. Apply migrations transactionally using Supabase's
migration tooling; the SQL file also includes transaction boundaries for manual
execution. When using the migration API, omit those outer boundaries because the
API manages the transaction and history record.

Run `tests/inventory_access.sql` as an administrator against a Supabase database
with this migration installed, for example:

```sh
psql "$SPOOL_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/inventory_access.sql
```

Keep connection credentials in an environment variable or password manager. Do
not put a connection string into source control or terminal output. The script
creates two synthetic auth users inside one transaction, uses `SET LOCAL ROLE`
and JWT subject settings to exercise the real `authenticated`/`anon` grants and
RLS, and rolls everything back. Fixture ID collisions fail instead of modifying
an existing user. A final `PASS` result is returned only after all checks succeed.
This SQL test is not a pgTAP test and does not require pgTAP or a local Docker stack.

The migration was rehearsed with those tests in a rolled-back transaction before
application, then tested again after deployment. All three public REST endpoints
were also checked using the app's publishable key without a user session and
rejected anonymous reads. No real-account inventory was uploaded or modified.

## Next step

Implement the upload protocol before enabling mobile writes: acknowledge exact
operation IDs only after server success, deduplicate retries, apply related history
and weight changes atomically, check base revisions and preserve conflicts, and
apply downloaded changes without generating upload loops. Server timestamps are
metadata, not a sufficient standalone transaction-ordered pull cursor. Recovery
UI/operations, safe retention, background scheduling, and full restore remain later
work. Keep local queues until those guarantees exist.

References: [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security),
[column permissions](https://supabase.com/docs/guides/database/postgres/column-level-security),
[migrations](https://supabase.com/docs/guides/local-development/database-migrations),
[migration API](https://supabase.com/docs/reference/api/v1-apply-a-migration).
