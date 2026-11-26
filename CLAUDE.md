# Boro Notes

- Kotlin + Jetpack Compose + Material 3, Room for storage. Package `com.boro.notes`.
- Layout: width >= 600dp (`TWO_PANE_MIN_WIDTH`) shows list + editor side by side; narrower shows one pane.
- The owner works from a phone only. CI (`.github/workflows/build.yml`) runs tests, builds the APK and publishes it as a GitHub Release; PRs get a `pr-N` prerelease. Keep that flow working.
- The debug keystore in `app/` is committed on purpose so builds install over each other. Don't regenerate it.
- No S Pen: drawing features must work well with a finger (snap-to-grid, auto-straighten).
- Tests: `./gradlew testDebugUnitTest` (ViewModel tests + Robolectric layout tests at cover and inner widths).
