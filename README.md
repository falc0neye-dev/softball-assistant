# Keith's Softball Assistant

A single-user, **100% offline** Android app for managing a recreational softball team:
roster, schedule, per-game availability, static & co-ed dynamic lineup generation, and a
live, fully editable scorecard. No accounts, no sync, no network permission — everything
lives in a local database on the phone.

Built to the spec in [keiths-softball-assistant-spec.md](keiths-softball-assistant-spec.md)
with visual design from [mockups/](mockups/).

## Features

- **Roster** — add/edit/deactivate players with position and M/F badge (soft delete keeps
  history intact); sortable; season stats (GP, AB, H, BB, R, AVG) computed automatically
  from scorecards.
- **Schedule** — games with opponent, date/time, location, home/away, notes; duplicate a
  game for doubleheaders; W/L badges on finished games.
- **Availability** — three-state In / Tentative / Out chips per player with live summary
  counts, plus one-tap *copy from last game*.
- **Lineup** — static fixed order or dynamic co-ed ratio (default 3 M : 1 F, configurable
  2:1–5:1). Male and female queues cycle independently and are drag-reorderable. Optional
  league *auto-out* rule when a female slot comes up empty. Late arrivals join the back of
  their queue; no-shows can be removed mid-game (long-press the Now Batting card). Share
  the batting order as text to the group chat.
- **Scorecard** — two taps per batter (tap outcome chip on the Now Batting card). Full
  outcome set (1B/2B/3B/HR/BB/FC/E/HBP, groundout/flyout/K/sac fly/FC-out), runner results
  tracked with mini diamond glyphs (filled = scored, half = on base, X = out, hollow =
  LOB). Inning separators, out counts, and the line score are **always derived from the
  row sequence** — insert, edit, delete, or reorder any row and everything recomputes.
  20-step undo. Manual "+1 out" for baserunning outs. Opponent runs per inning via +/-.
  Haptic buzz on out #3 and runs scored.
- **Backup** — export/import the entire database as JSON via the system file picker
  (no permissions). `"schemaVersion": 1` is validated on import.

## Building

Requirements: JDK 17+ (Android Studio's bundled JDK works) and the Android SDK
(platform 36). Point `local.properties` at your SDK (`sdk.dir=...`).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests (lineup engine, scorecard engine, stats)
./gradlew assembleRelease      # signed release APK → app/build/outputs/apk/release/app-release.apk
```

## Signing & the keystore — READ THIS

The release APK is signed with [release.keystore](release.keystore); passwords are in
[keystore.properties](keystore.properties). **Both are committed to this repo on purpose**:
for a sideloaded personal app, losing the key is the real risk — Android will refuse any
update signed with a different key, and the only way out is uninstalling, **which wipes
the database**. Keeping the key in this (private) repo is the off-device backup the spec
requires (§12.3).

Trade-off to be aware of: anyone with repo access can sign updates as this app. If that
ever bothers you, rotate to a new key (requires uninstall/reinstall — export a JSON backup
first) and keep the new keystore out of git in a password manager instead.

## Sideloading onto a phone

1. Build: `./gradlew assembleRelease`
2. Copy `app/build/outputs/apk/release/app-release.apk` to the phone (USB, Drive, etc.),
   or install directly over USB: `adb install app-release.apk`
3. On the phone, open the APK; allow **Install unknown apps** for the file manager when
   prompted (Settings → Apps → Special access).
4. Updates: install a newer APK signed with the same key right over the old one — data is
   preserved.

## Before relying on it for real games (spec §12.4)

- Dry-run a full 7-inning game from an old paper scoresheet, including at least one
  mistaken entry fixed via insert/edit/delete.
- Kill the app mid-inning and reopen — everything is written through to the DB on every
  action, so the game must resume exactly where it was.
- Export a backup and keep it somewhere safe. Repeat occasionally; there is no cloud.

## Project layout

```
app/src/main/java/com/keithfalcon/softball/
├── data/          Room entities, DAOs, database, JSON backup
├── logic/         Pure Kotlin: LineupEngine, ScorecardEngine, StatsEngine (all unit-tested)
└── ui/            Compose screens: roster, schedule, game (availability/lineup), scorecard, settings
app/src/test/      Unit tests — includes the exact spec §5.2 lineup example
app/schemas/       Exported Room schema (v1) for future migrations
mockups/           SVG design mockups the UI follows
```
