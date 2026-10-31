# Visitor location statistics

The dashboard records new visits only. PV is the visit-report count; UV is distinct canonical IP per selected interval, represented by a salted HMAC. Regional UVs are not additive when an IP changes location. IP coordinates are approximate and unavailable locations are counted separately. Raw IPs are not stored in this feature.

## Installation
- Install DB-IP City Lite MMDB in `visitor-data/dbip-city-lite.mmdb` relative to the backend working directory, or configure `bcblog.visitor-data-dir` to a private directory. Keep the directory outside static upload paths and readable only by the backend account.
- Source: https://db-ip.com/db/download/ip-to-city-lite (CC BY 4.0); the dashboard includes required attribution. October 2026 uncompressed SHA1: `df17bd24390108ed1d24dcb7718dc60f6b9abd4f`.
- The application creates an additive `sys_visit_geo` table on startup and a random `visitor-salt` file. Preserve this file across releases/backups to keep deduplication stable. No existing tables are changed.
- Update the MMDB with a verified newer release and restart the backend; no key or external lookup API is required. If the file is unavailable, visits are counted as unknown. Historical unknown entries are not backfilled because raw IPs are deliberately not retained.
- Only loopback reverse proxies may supply `X-Real-IP`; Nginx must overwrite it with `$remote_addr`. Other connections use their socket address.
- Records older than 90 days are deleted daily; the UI exposes today, 7 days and 30 days. Admin auth is required for `/api/admin/dashboard/visitors?days=7`.

## Verification
Run Maven tests with `-Dvisitor.geo.fixture=/absolute/path/to/dbip-city-lite.mmdb` to include the actual offline database lookup test. The default test run skips only this external fixture check. MySQL-compatible H2 tests verify same-day increments, cross-day distinct counts, unknown records and retention.
