# CLAUDE.md

Offline-first Android app for managing Keith's rec softball team: roster, schedule,
per-game availability, co-ed lineup generation, defense grid, and a live editable
scorecard. Full product spec in `keiths-softball-assistant-spec.md`; visual design in
`mockups/*.svg` (design tokens in spec §8A). Single user, sideloaded APK, **no network
permission — never add INTERNET or any network library.**

## Build & test

No system JDK — use Android Studio's bundled one:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest     # unit tests (lineup/scorecard/stats engines)
./gradlew assembleDebug
./gradlew assembleRelease       # signed APK → app/build/outputs/apk/release/app-release.apk
```

SDK at `~/Library/Android/sdk` (set in `local.properties`). Emulator AVD
`Medium_Phone_API_36.1` exists for smoke tests; it does not persist state between boots,
so re-seed test data each session (debug builds allow `run-as com.keithfalcon.softball`
to swap `databases/softball.db`; remember WAL — checkpoint before pushing a seeded db).

Verify release signature: `apksigner verify app/build/outputs/apk/release/app-release.apk`
(keytool can't read APK sig scheme v2).

## Signing (intentional oddity)

`release.keystore` + `keystore.properties` are **committed on purpose** as the off-device
key backup (spec §12.3): a lost key strands updates and a reinstall wipes the local DB.
Don't gitignore them. Rationale + rotation notes in README.

## Architecture

Single module, MVVM, manual DI (no Hilt): ViewModels get `SoftballApp.database` via
`ui/common/ViewModelFactory.kt#softballViewModel`. Compose M3 + Navigation, Room + KSP,
kotlinx-serialization for backup.

```
app/src/main/java/com/keithfalcon/softball/
├── data/       Room entities (Entities.kt), DAOs (Daos.kt), AppDatabase, BackupDao,
│               backup/BackupManager (JSON export/import via SAF)
├── logic/      PURE KOTLIN, no Android imports: LineupEngine, ScorecardEngine, StatsEngine
└── ui/         roster/, schedule/, game/ (GameDetailScreen tabs: Availability | Lineup |
                Defense | Scorecard), scorecard/, settings/, theme/ (spec tokens in Color.kt)
```

## Load-bearing conventions

- **All game logic lives in `logic/` as pure functions with unit tests** in
  `app/src/test/`. `LineupEngineTest` encodes the exact spec §5.2 example — it must keep
  passing verbatim.
- **Scorecard state is always derived, never stored** (spec §12.2): inning boundaries,
  outs, scores, and LOB display recompute from the ordered `PlateAppearance` sequence via
  `ScorecardEngine.derive()`. Edits rewrite the whole sequence (`resequence` +
  `replaceForGame` in one transaction). Never persist separators or counters.
- **The lineup generator is stateless**: next batter = `LineupEngine.nextSlot(config,
  history)` where history is rebuilt from recorded PAs (MANUAL_OUT rows excluded,
  AUTO_OUT rows are `null` entries). This is what makes process death and mid-game
  queue edits safe.
- **Autosave everything**: every user action writes through to Room immediately; there
  are no save buttons. UI state flows from `observe*()` DAO Flows.
- **Room schema changes**: bump `@Database` version + add an `AutoMigration` (schemas
  exported to `app/schemas/`, v1 and v2 committed). Also extend `BackupData` — new fields
  need defaults so old JSON backups keep importing (`schemaVersion` stays 1).
- Playerless scorecard rows: `Outcome.AUTO_OUT` (empty female slot rule) and
  `Outcome.MANUAL_OUT` (+1 out button) have `playerId = null` and are excluded from stats.

## Releases (sideload)

Bump `versionCode`/`versionName` in `app/build.gradle.kts` (updates require increasing
versionCode), `assembleRelease`, then attach the APK to a GitHub release
(`gh release create vX.Y ...`). History: v1.0 initial, v1.1 up-next preview,
v1.2 defense grid.

## Workflow

Features go on `feature/*` branches with a PR (`gh pr create`). Verify UI changes on the
emulator with screenshots before opening the PR; run `testDebugUnitTest` before every
commit.
