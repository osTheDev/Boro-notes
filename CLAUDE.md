# Boro Notes

- Kotlin + Jetpack Compose + Material 3, Room for storage. Package `com.boro.notes`.
- Layout: width >= 600dp (`TWO_PANE_MIN_WIDTH`) shows list + editor side by side; narrower shows one pane.
- The owner works from a phone only. CI (`.github/workflows/build.yml`) runs tests, builds the APK and publishes it as a GitHub Release; PRs get a `pr-N` prerelease. Keep that flow working.
- The debug keystore in `app/` is committed on purpose so builds install over each other. Don't regenerate it.
- No S Pen: drawing features must work well with a finger (snap-to-grid, auto-straighten).
- Tests: `./gradlew testDebugUnitTest` (ViewModel tests + Robolectric layout tests at cover and inner widths).
- Sketch engine (`sketch/Geometry.kt`) is plain Kotlin: corner detection on a resampled stroke, straight runs vs smooth (Hermite) runs, grid snap on corners only, endpoint magnet. Its tests (`GeometryTest`) can run in a plain Kotlin JVM project without the Android SDK.
- Database is version 3 (v2 designs table, v3 `notes.content`). Every schema change needs a Migration plus a case in `MigrationTest`; users have real notes on their phone.
- Rich notes (`notes/NoteDoc.kt`): `notes.content` holds a JSON block list (text as HTML, checklist, table, columns, divider); `notes.body` keeps plain text for search and list previews. Null content means a legacy plain note. UI in `ui/NoteEditor.kt` with a collapsible formatting bar grouped into menus.
- Rich text uses `richeditor-compose` pinned at 1.0.0-rc13: newer versions need Kotlin 2.4 / Compose 1.11. Don't bump it without bumping those.
