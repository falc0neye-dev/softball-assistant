# Keith's Softball Assistant — Product & Technical Specification

**Version:** 1.0
**Platform:** Android (standalone, sideloaded APK)
**Connectivity:** 100% offline — all data stored locally on device
**Author:** Keith Falcon (product owner)

---

## 1. Overview

Keith's Softball Assistant is a single-user Android app for managing a recreational softball team: roster, game schedule, per-game availability, lineup generation (static and co-ed dynamic), and a live, editable scorecard that tracks plate appearances, base-running results, outs, and innings.

The app must function entirely offline with no accounts, no sync, and no network permissions. The final deliverable is a signed APK that can be installed directly on a phone (sideloaded), not distributed through the Play Store.

---

## 2. Recommended Technical Stack

| Layer | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Modern Android standard |
| UI | Jetpack Compose + Material 3 | Declarative UI, easy drag-and-drop lists, dark mode for free |
| Architecture | MVVM (ViewModel + StateFlow), single-activity | Simple, testable |
| Local database | Room (SQLite) | Structured relational data (teams → players → games → lineups → at-bats) |
| Navigation | Compose Navigation | Screen routing |
| DI | Hilt (or manual DI — app is small) | Optional |
| Min SDK | 26 (Android 8.0) | Broad device coverage |
| Target SDK | 35 | Current requirement |
| Build output | `./gradlew assembleRelease` → signed APK | Sideload-ready |

**Permissions:** none required. Do **not** request INTERNET. Optional: storage access via Storage Access Framework only if backup/export (Section 9) is implemented — SAF requires no permission.

**APK signing:** generate a local keystore, configure `signingConfigs.release` in Gradle so the release APK installs cleanly. Enable minify/shrinkResources for a small APK.

---

## 3. Data Model (Room Entities)

### 3.1 Team
| Field | Type | Notes |
|---|---|---|
| id | Long (PK, autogen) | |
| name | String | |
| createdAt | Long (epoch ms) | |

### 3.2 Player
| Field | Type | Notes |
|---|---|---|
| id | Long (PK) | |
| teamId | Long (FK → Team) | |
| firstName | String | Required |
| lastName | String | Required |
| position | String | Free text or picker (P, C, 1B, 2B, 3B, SS, LF, LC, RC, RF, DH, etc.) |
| sex | Enum: MALE, FEMALE | Drives dynamic lineup logic |
| isActive | Boolean | Soft-delete so historical games keep their data |

### 3.3 Game
| Field | Type | Notes |
|---|---|---|
| id | Long (PK) | |
| teamId | Long (FK) | |
| opponent | String | |
| dateTime | Long | |
| location | String? | Optional |
| homeAway | Enum: HOME, AWAY | Determines top/bottom batting (nice-to-have) |
| notes | String? | |
| status | Enum: SCHEDULED, IN_PROGRESS, FINAL | |
| ourScore / theirScore | Int | Derived/manual |

### 3.4 Availability
| Field | Type | Notes |
|---|---|---|
| gameId + playerId | Composite PK | |
| status | Enum: IN, OUT, TENTATIVE | Default: unset (treated as Tentative in UI) |

### 3.5 Lineup
| Field | Type | Notes |
|---|---|---|
| gameId | Long (PK — one lineup per game) | |
| type | Enum: STATIC, DYNAMIC | |
| entries | Child table: LineupEntry(gameId, battingOrder Int, playerId) | Ordered |

### 3.6 PlateAppearance (scorecard rows)
| Field | Type | Notes |
|---|---|---|
| id | Long (PK) | |
| gameId | Long (FK) | |
| sequence | Int | Global batting sequence in the game; drives ordering and insert/remove |
| inning | Int | Derived from outs, but stored so edits stick |
| playerId | Long | |
| outcome | Enum (Section 6.2) | |
| runnerResult | Enum: ON_BASE, SCORED, OUT, LEFT_ON_BASE | |
| rbi | Int? | Nice-to-have |
| note | String? | e.g., "pinch runner: Jon" |

### 3.7 InningState (or derive at runtime)
Track per-inning: outs recorded, runs scored. Can be computed from PlateAppearances, but storing a lightweight snapshot simplifies manual overrides (e.g., an out on the bases that isn't tied to a batter's row).

---

## 4. Core Screens & Flows

1. **Team / Roster screen** — create team; add/edit/deactivate players; list shows name, position, M/F badge. Sort by name or position.
2. **Schedule screen** — list of games (upcoming and past), add/edit game, tap into a game.
3. **Game detail screen** — three tabs: **Availability**, **Lineup**, **Scorecard**.
4. **Availability tab** — full roster with three-state toggle chips (In / Tentative / Out) per player. Header count: "9 In · 2 Tentative · 1 Out".
5. **Lineup tab** — choose Static or Dynamic, generate, then hand-adjust via drag-and-drop. "Start Game" button locks in the lineup and opens the Scorecard.
6. **Scorecard tab** — the live game view (Section 6).

---

## 5. Lineup Generation

Only players marked **In** (optionally include Tentative via a toggle) are eligible.

### 5.1 Static lineup
- User arranges players in a fixed batting order (drag-and-drop).
- The order simply repeats: after the last batter, cycle back to #1.
- Example: Keith, Jon, Ely → Keith, Jon, Ely, Keith, Jon, Ely…

### 5.2 Dynamic (co-ed ratio) lineup
- Players are split into two ordered queues: **Males** and **Females** (each independently reorderable).
- Generation rule: emit **3 males, then 1 female**, repeating. Each queue cycles independently when exhausted.
- Example — Males (Keith, Jon, Steven, Mike, Robert), Females (Ely, Erin):
  `Keith, Jon, Steven, Ely, Mike, Robert, Keith, Erin, Jon, Steven, Mike, Ely, …`
- Because the queues cycle at different rates, the lineup is effectively infinite; the scorecard requests "next batter" from the generator rather than repeating a fixed list.
- **Configurable ratio:** default 3:1 male:female, but expose it as a setting (some leagues use 2:1 or 4:1).
- **Edge cases to handle:**
  - Zero females available → warn, fall back to males-only cycle (or apply the auto-out rule below if enabled).
  - Zero males → females-only cycle.
  - League "auto-out" option (common co-ed rule): if a female slot comes up with no female available, record an automatic out. Off by default; toggle in settings.

### 5.3 Lineup adjustments
- Preview the first N generated slots (e.g., 20) before starting.
- Drag to reorder within each queue; long-press to remove.
- **Late arrival:** add a player mid-game; they join the back of their queue (dynamic) or are inserted at a chosen batting slot (static).
- **Mid-game removal** (injury/leaves early): remove from rotation going forward; past at-bats remain.

---

## 6. Scorecard

### 6.1 Layout
- Vertically scrolling list styled like a classic softball scorecard: one row per plate appearance.
- Each row: batting-sequence number, player name (with M/F accent color), outcome chip, runner-result indicator (small diamond icon: filled = scored, X = out, half = on base).
- **Inning separators:** when 3 outs are recorded, insert a bold divider row — "End of Inning 3 · 2 runs · Total 7–4" — and the next row begins the new inning. Sticky header shows current inning, outs, and score at all times.
- The "next batter" row is pre-populated from the lineup generator and highlighted; tapping it opens the outcome entry sheet.

### 6.2 Outcomes
Minimum required: **Hit, Walk, Fielder's Choice** plus batter **Out**.
Recommended full set (single-tap chips, grouped):
- **On base:** 1B, 2B, 3B, HR, BB (walk), FC, E (reached on error), HBP
- **Out:** Groundout, Flyout, Strikeout, Sac Fly (records out + RBI), FC-out
- A generic "Hit / Walk / FC / Out" simple mode toggle for users who don't want granularity.

### 6.3 Runner result
For every batter who reached base, a second field tracks what happened: **Scored**, **Out on bases**, **Left on base** (auto-set when the inning ends). Runner outs count toward the 3-out inning total. HR auto-sets Scored.

### 6.4 Outs & innings logic
- Out counter increments from: batter outs, runner outs, auto-outs (Section 5.2), and a **manual "+1 out"** button (covers weird plays — appeal outs, baserunning outs between recorded at-bats).
- At 3 outs: auto-insert the inning separator, reset outs, mark all runners still on base as Left On Base, increment inning.
- Manual inning override available (edit menu) in case of scorekeeping mistakes.

### 6.5 Full editability (critical requirement)
Every row supports:
- **Update:** tap any row to change player, outcome, or runner result.
- **Insert:** long-press between rows → "Insert plate appearance here" (e.g., you forgot to record a batter).
- **Remove:** swipe-to-delete with undo snackbar.
- **Reorder:** drag handle in edit mode.
- After any edit, the app **recomputes** inning boundaries, out counts, and scores from the sequence — separators are derived, never manually maintained, so edits can't corrupt the inning structure.
- **Undo/redo stack** for the last ~20 actions during a live game (fat-finger insurance with a glove on).

### 6.6 Score tracking
- Runs auto-tally from "Scored" runner results (our side).
- Opponent score: simple per-inning +/- entry (we're not scoring their at-bats in detail).
- Line score strip at top: runs per inning for both teams, R/H totals.
- "End Game" button → sets status FINAL, freezes the sheet (still editable via an explicit "reopen" action).

---

## 7. Creative / Nice-to-Have Features

Prioritized roughly by value-to-effort:

1. **Copy availability & lineup from last game** — most weeks the same crew shows up; one tap saves re-entry.
2. **Share lineup as text** — generate a formatted batting order to paste into the team group chat (uses Android share sheet; no network needed by the app itself).
3. **Player stats** — auto-computed from scorecards: games played, AB, H, BB, AVG, OBP, runs. Season summary screen + per-player card. This is a big payoff from data you're already entering.
4. **Fielding positions per inning** — optional grid assigning defensive positions each inning, with a "sat last inning" indicator for fair rotation. Warn on duplicates/missing positions.
5. **Game timer** — many rec leagues have a time limit (e.g., 50 min / no new inning after); show elapsed time in the scorecard header with a configurable warning.
6. **Substitutions & pinch runners** — swap a runner without changing the batter's stat line (uses the `note` field + a runner-swap action).
7. **Minimum-female warning** — co-ed leagues often require N females on the field; warn at lineup time if the In count is below the configured minimum.
8. **Home/Away half-innings** — if you track opponent innings, show Top/Bottom correctly based on the game's home/away setting.
9. **Duplicate game** — clone a game entry for doubleheaders.
10. **Dark mode** — free with Material 3 dynamic theming; scoreboards get used at dusk.
11. **Haptic feedback** on out #3 and runs scored — satisfying and glanceable.
12. **Season archive** — mark a season, roll stats up by season, keep old seasons browsable.

---

## 8. UX / Design Notes

- Material 3, large touch targets — this gets used one-handed, standing at a fence, possibly with batting gloves.
- Scorecard entry must be ≤ 2 taps for the common case (tap next batter → tap outcome chip).
- High-contrast outcome chips; color + icon (not color alone) for M/F and scored/out indicators.
- Confirmation dialogs only for destructive actions (delete player, delete game); everything else uses undo snackbars.
- Empty states with clear CTAs ("No players yet — add your first teammate").

---

## 8A. UI Mockups & Visual Design System

### Design tokens
| Token | Value | Usage |
|---|---|---|
| Chalk | `#F6F4ED` | App background |
| Ink | `#24291F` | Primary text |
| Field Green | `#1F5632` | Primary color: app bars, "In" state, scored, primary buttons |
| Infield Clay | `#B85C38` | Accent: active tab, FAB, inning separators, "now batting" card |
| Amber | `#D69A2D` | Female indicator, Tentative state |
| Steel | `#3E5C76` | Male indicator |
| Out Red | `#A93B26` | Outs, "Out" availability, destructive actions |

**Signature elements:** (1) mini baseball-diamond glyphs on every scorecard row — filled = scored, half-filled = on base, red X = out, hollow = left on base; (2) inning separators rendered as a chalk-dashed line on an infield-clay strip.

**Typography:** heavy-weight sans (e.g., Inter/Roboto 800–900) for scores and headers; monospaced digits (Roboto Mono) for the line score and batting-sequence numbers, evoking a paper scorebook.

### Mockups

**Roster** — player cards with position chip and M/F badge; extended FAB; bottom nav (Roster / Schedule).

![Roster screen](01-roster.svg)

**Game → Availability tab** — three-state segmented control per player (In / Tent / Out), live summary chips, one-tap "copy from last game."

![Availability screen](02-availability.svg)

**Game → Lineup tab (Dynamic)** — Static/Dynamic toggle, configurable M:F ratio chip, independently reorderable male & female queues with drag handles, generated batting-order preview with female slots highlighted, Start Game CTA.

![Lineup screen](03-lineup.svg)

**Game → Scorecard tab** — sticky header (score, inning, out dots), line-score strip, plate-appearance rows with outcome chips + diamond glyphs, clay/chalk inning separator, highlighted "Now Batting" card with one-tap outcome chips, Undo and manual +1 Out actions, glyph legend.

![Scorecard screen](04-scorecard.svg)

---

## 9. Data Persistence, Backup & Integrity

- All data in a single Room/SQLite database in app-private storage. Survives app updates; wiped on uninstall.
- **Export/Import backup:** serialize the full DB to a JSON file via the system file picker (Storage Access Framework — no permissions needed). Import validates schema version. This is the safety net for phone upgrades since there's no cloud.
- Room migrations defined from v1 onward so future updates never lose data.
- Autosave on every action — no explicit save button anywhere; process death mid-game must lose nothing (write-through to DB, restore ViewModel state from DB on relaunch).

---

## 10. Build & Delivery Requirements

- Single Gradle module, Kotlin DSL.
- Deliverable: **signed release APK** (`app-release.apk`) installable via "Install unknown apps."
- Instructions included for: generating the keystore, building (`./gradlew assembleRelease`), and sideloading.
- App name: **Keith's Softball Assistant**; a simple softball-themed launcher icon (adaptive icon, ball + clipboard motif).
- No analytics, no crash reporting SDKs, no network libraries.
- Target APK size: under ~10 MB.

---

## 11. Out of Scope (v1)

- Multi-user / cloud sync / accounts
- Play Store distribution
- Detailed opponent scorekeeping (pitch-by-pitch)
- Pitch counts, spray charts, advanced sabermetrics
- Tablet-optimized layout (should still function, not optimized)

---

## 12. Build Sequencing & Pre-Development Checklist

### 12.1 Build order (vertical slices, riskiest first)

| Stage | Deliverable | Notes |
|---|---|---|
| 0 | Project scaffold + signing | Gradle project, Compose, Room wired up; generate release keystore and **back it up off-device immediately** (see 12.3) |
| 1 | Data layer | Room entities, DAOs, migration scaffolding (schema v1). Review this before any UI exists — data-model fixes are cheap now, expensive later |
| 2 | Lineup generator (pure logic + tests) | Pure Kotlin function: (male queue, female queue, ratio) → batter sequence. Unit tests must pass the exact §5.2 example plus edge cases: zero females, one female, auto-out rule, mid-game player removal, late arrival |
| 3 | Scorecard prototype | Rough working scorecard with **hardcoded players** — outcome entry, runner results, 3-out separator, edit/insert/delete with recompute. This is 70% of the app's risk; surface UI problems (chip sizing, scroll, edit flows) before plumbing hardens around them |
| 4 | Roster & schedule screens | Team, players, games, availability |
| 5 | Integration | Availability → lineup → Start Game → real scorecard; live-game lineup edits (no-show handling per 12.2) |
| 6 | Backup/export | JSON export/import via SAF — build **early enough to use as a dev tool** (seed test data, reproduce bugs). Include `"schemaVersion": 1` from day one |
| 7 | Polish | Stats, share lineup, copy-from-last-game, timer, haptics, dark mode, icon |

**Prompting strategy when generating code:** provide this full spec, but request output in the stages above and review each stage before proceeding — especially Stage 1 (data layer) and Stage 2 (generator tests).

### 12.2 Design decisions locked before coding

- **No-show flow:** a player marked In who doesn't show must be removable from the rotation *from within the live scorecard* (not only the pre-game Lineup tab). Past at-bats are retained; the generator skips them going forward.
- **Separators are always derived:** inning boundaries, out counts, and scores recompute from the plate-appearance sequence after every edit — never stored as manually-maintained rows.
- **Autosave everything:** no save buttons; every action writes through to the DB. Process death mid-game must lose nothing.

### 12.3 Keystore warning (critical for sideloaded apps)

Android requires all updates to an installed app to be signed with the **same key**. If the keystore is lost, the app cannot be updated — only uninstalled, which **wipes the local database**. On the day the keystore is created: back up the `.jks`/`.keystore` file and its passwords somewhere off-device (password manager + cloud copy). The JSON backup/export (§9, Stage 6) is the recovery net for this scenario — build it before relying on the app for real games.

### 12.4 Real-world testing

- Test on a physical phone, outdoors, in direct sunlight, one-handed — emulator contrast is misleading for a fence-side scorekeeping app.
- Dry-run a full 7-inning game from a past paper scoresheet before first live use, including at least one mistaken entry fixed via insert/edit/delete.
- Kill the app mid-inning and confirm perfect resume.

---

## 13. Acceptance Criteria (summary)

1. Create a team, add ≥ 12 players with all fields; edit and deactivate players.
2. Create games with date/time/opponent; set availability per player per game.
3. Generate a static lineup that cycles correctly, and a dynamic 3:1 lineup matching the example in §5.2 exactly.
4. Start a game; record outcomes and runner results; on the 3rd out an inning separator appears automatically with correct run totals.
5. Insert, edit, delete, and reorder any scorecard row; innings and scores recompute correctly afterward.
6. Kill the app mid-game; reopen; the game resumes with zero data loss.
7. Export a backup, wipe app data, import the backup, and see everything restored.
8. Build produces a signed APK that installs and runs on a physical device with no network access.
