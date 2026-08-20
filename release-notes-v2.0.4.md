# Nomi v2.0.4

This release restores reliable Health Connect synchronization and adds retrospective backfill.

- Steps and weights now sync whenever their own permission is granted, even if another Health Connect category is denied.
- Nomi imports all weight history the provider allows, including extended history when supported and approved.
- Previously unsent local and onboarding weights are retried automatically with stable, duplicate-resistant record IDs.
- The complete food journal is backfilled, and explicit Sync now repairs records removed from Health Connect.
- Overlapping startup, permission, manual, and food-change refreshes are coalesced so a foreground retry is never lost.
- Weight pagination, imported-record corrections, partial-access UI, and midnight activity rollover are fixed.

The APK is signed with the same certificate as prior stable releases.

SHA-256: `6DB7941C405944177BE24E42E0572BAC10C7255C17723BB0CB608EE6C00B859B`
