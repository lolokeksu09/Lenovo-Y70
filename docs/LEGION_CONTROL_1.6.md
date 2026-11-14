# Legion Control 1.6.0

## New functionality
- **Local action journal:** stores the latest 25 applied, undone and recovered settings changes, with timestamps.
- **History export:** exports action names and timestamps to a user-selected TXT using Android's Storage Access Framework.
- **Recovery consistency:** journal entry and undo snapshot are committed together when applying a successful change. Successful manual rollback and recovery are logged only after restoration.
- **Safe display areas:** layout accounts for status/navigation bars and display cutouts on edge-to-edge Android.

## Preserved behavior
- The original AMOLED colors and top navigation stay unchanged.
- No root access, package removal, hidden kernel writes, or thermal modifications.
- Existing Shizuku command allowlist and user confirmations are unchanged.
- Permanent release certificate is pinned in GitHub Actions. Package stays `dev.legion.control`, versionCode is 8.

## Important limitation
The action timeline is informational. **Only the most recent mutation is eligible for Undo**; the journal is not a stack of independently reversible old snapshots.

## Testing
Focused JVM tests cover valid and invalid journal records, ordering, bounding, and UTF-8 round trips. Android CI checks the APK build and signature. Device installation/UI behavior must be tested separately.
