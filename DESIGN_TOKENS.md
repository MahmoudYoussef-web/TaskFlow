# TaskFlow — Design Tokens (Phase 0, locked before any CSS)

Grounded in what TaskFlow is: a fast, reliable engineering tool. Quiet surfaces,
one deliberate accent, boldness spent only on the drag interaction + priority chips.

## Palette (named, deliberate)

| Token | Light | Dark | Use / justification |
|---|---|---|---|
| `ink` | `#16181D` | `#F2F3F5` | Primary text. Near-neutral, no blue tint drift. |
| `surface` | `#FAFAF8` | `#131518` | App background. Warm-neutral paper in light; true dark-raised in dark (not inverted). |
| `raised` | `#FFFFFF` | `#1B1E24` | Cards, columns, panels. Contrast comes from 1px borders, not shadows. |
| `line` | `#E5E3DC` | `#2A2E37` | Borders only. No `rgba(0,0,0,.1)` blob shadows anywhere. |
| `accent` | `#2456E6` | `#5B85FF` | Sparingly: active column drop target, focused task, primary action. The single saturated hue. |
| `priority-high` | `#C2413A` | `#E0685F` | Chip text/border only, never full fills. |
| `priority-medium` | `#A9761F` | `#D9A441` | Same chip treatment. |
| `priority-low` | `#4F6B58` | `#7FA88C` | Same chip treatment. |

Banned: cream+terracotta combos, acid-green-on-black, decorative gradients, uniform card-kit shadow.

## Type

- UI: Inter (400/500/600). Headings: same family at 650 weight + tighter tracking — pairing by weight, not novelty font.
- Scale: 12 / 14 / 16 / 20 / 28. Column titles 13px semibold. No ALL-CAPS labels except the 3 column headers.
- No eyebrow labels, no `·`-joined meta strings, no `→` on buttons, no italic-accent-word headlines.

## Motion (one orchestrated moment)

- The task detail slide-over (280ms ease-out translate + fade). Everything else instant.
- Drag: column highlight + card tilt 2deg while dragging, snap on drop. Nothing else animates.
- `prefers-reduced-motion`: all transitions off.

## Copy (active voice)

- "Save task", "Move to Done", "Retry job". Empty states: what happened + what to do ("No tasks in Backlog. Press N or click New task to add one.").
- Error: what failed + recovery ("Couldn't save — someone else edited this task. Reloaded the latest version.").

## Anti-AI-tell checklist (review every screen)

- [ ] Would this look identical for a different product? If yes → revise.
- [ ] Boldness lives in exactly one place (drag + priority chips)?
- [ ] No numbered 01/02/03 markers, no per-card hover lifts, no entrance cascades?
- [ ] Dark mode is designed (raised surfaces), not inverted?
