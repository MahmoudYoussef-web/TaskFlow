# Changelog

## Unreleased

- `GET /api/v1/tasks/{id}/reminder`: live ScheduledJob state per task
- Task panel: editable tags, due-date clear, status switch, attempt bar
- Board: search, priority/tag/overdue filters, quick move actions, shortcuts (`/`, `N`)
- Jobs admin view: status chips, execution timeline, manual retry, `esc` to close
- Split-layout auth screens, richer overview, dark mode pass

## 0.1.0 — 2026-09-27

First working vertical slice, verified end-to-end on docker-compose:

- Auth: register/login/rotating refresh, RBAC, first account bootstraps admin
- Tasks: CRUD, drag-and-drop status with `@Version` optimistic locking (409)
- Scheduling: due-date → job, 10s poller, Redis-locked workers, backoff retry,
  idempotency key, `PERMANENTLY_FAILED` + terminal event
- Events → audit timeline + notifications + activity feed
- React board with detail slide-over, dashboard, jobs view, notifications bell
- 7 unit + 4 integration tests (`mvn verify`); Playwright demo recordings
- Fixes found by testing the running system: CORS bean wiring, transactional
  event listeners, dnd-kit click swallowing, stale reminder box
