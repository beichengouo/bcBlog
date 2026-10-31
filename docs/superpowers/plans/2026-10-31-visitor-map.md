# Visitor Map Implementation Plan
> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task.
**Goal:** Add an interactive China-centered world map with PV and unique-IP counts.
**Architecture:** Offline DB-IP MMDB lookup; MySQL daily hashed-IP aggregate; admin-only stats endpoint; Leaflet local GeoJSON map.
**Tech Stack:** Java8/Spring/JDBC, MaxMind DB Reader2.1.0, Vue3/Leaflet1.9.4.
**Spec:** docs/superpowers/specs/2026-10-31-visitor-map-design.md
## Global Constraints
Preserve unrelated AI edits. No historical location fabrication. No raw IP returned/stored. 1/7/30 day windows. Worldwide and China reset views.
## Review Focus
Spoofed proxy headers: trust only local reverse proxy. Unknown/private addresses: no map coordinate. Concurrent reports: atomic SQL upsert. Multi-day same IP: distinct across range. Late responses/unmount: cancel or ignore stale responses and remove map.
## Tasks
- [x] Add VisitorGeoService offline resolver and VisitGeoService daily storage; new tests for invalid/private IP, trusted header selection, cross-day aggregate SQL, day validation, unknown fallback. Initialize additive table and remove >90 days on scheduled task. Persist salted hash secret outside webroot.
- [x] Wire PortalVisitController to record location without changing PV semantics, add admin /dashboard/visitors?days=7 endpoint. Failures must not break visit reporting.
- [x] Add VisitorMap.vue plus Leaflet and local world GeoJSON; insert after visit chart. Metric/day selectors, circles, tooltips, ranked list, unknown counts, loading/error/empty state, reduced motion, ResizeObserver cleanup. Avoid HTML insertion of data values.
- [ ] Run Maven tests/package and frontend build on staged clean tree; check offline resolution with real database and frontend lifecycle. Review feature. Commit/push and deploy with backup; check public/auth boundaries and health.

Review: corrected independent basemap retry and retention-window date wording. Six focused backend tests and Vue state/lifecycle checks pass. Real MMDB verified against official SHA1. Windows MMDB fixture is read in place because memory-mapped files remain locked until GC; production retains memory-map mode for low heap use.
