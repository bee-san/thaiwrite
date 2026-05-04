# AGENTS

## Project

ThaiWrite is an Android app for learning Thai script with a writing-first study flow.
Core app code lives under `app/src/main/java/com/bee/thaiwrite`.
Seed content lives under `app/src/main/assets`.

## Build and Test

Use the Gradle wrapper.

- Debug APK: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Release APK: `./gradlew :app:assembleRelease`

Local release builds are unsigned unless these env vars are set:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Release Process

Versioning is tag-driven.
Do not hardcode release version bumps in source files.
`app/build.gradle.kts` reads `thaiwrite.versionName`, and the GitHub release workflow derives that from the pushed tag.

Normal release flow:

1. Make sure `main` is clean and tested.
2. Build-check the release locally with `./gradlew -Pthaiwrite.versionName=X.Y.Z :app:assembleRelease`.
3. Commit the releaseable changes to `main`.
4. Create an annotated tag `vX.Y.Z`.
5. Push `main` and push the tag.

GitHub Actions workflow `.github/workflows/release.yml` publishes the signed APK and release assets from the tag.

## UI Conventions

Dashboard styling lives in `ui/MainShellScreens.kt`.
Study flow styling lives in `ui/AppUi.kt`.
Shared color tokens should come from `ui/theme/Color.kt`, not from duplicate private palettes in multiple files.

When changing study screens:

- Keep `LessonScreen`, `PracticeScreen`, and `ReviewScreen` visually consistent.
- Keep the study scaffold background behind the full screen, including the top app bar.
- Default lesson practice to recall-first behavior. Do not force the writing guide on by default for every new item unless the product direction changes deliberately.

## Study Flow State Rules

Writing assessment is not just visual feedback. `assessWriting()` records a review immediately.
Because of that:

- Do not leave writing "check" actions active after a result is already shown.
- Do not allow duplicate pass/fail submissions for the same review card state.
- Be careful with any UI change that can trigger repeated review logging.

## Data and Scheduling

- FSRS scheduling logic lives in `domain/fsrs/FsrsPassFailScheduler.kt`.
- Snapshot shaping for lessons and due cards lives in `data/repo/LibrarySnapshotBuilder.kt`.
- Repository review writes live in `data/repo/StudyRepository.kt`.

If a UI change affects review progression, verify both the visual state and the underlying review write path.
