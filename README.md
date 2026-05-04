# ThaiWrite

ThaiWrite is an Android app for memorising the Thai writing system with a writing-first workflow. It teaches the core alphabet in locked baby steps, checks handwriting on device with ML Kit, schedules reviews with FSRS, and includes a tiny starter deck of Thai family words and names.

## What it does

- Teaches the 44 Thai consonants first, then 8 starter vowels, then 4 tone marks, then a small words-and-names deck.
- Uses lesson progression that stays locked until the current lesson is mastered.
- Gives each item three study angles:
  - recognition card
  - audio card
  - writing card
- Uses on-device Thai handwriting recognition for writing checks.
- Uses Android TextToSpeech for Thai audio prompts and caches generated audio locally.
- Keeps reminders and streaks on device.
- Checks GitHub Releases for newer signed APKs and can hand the downloaded APK to Android's installer.

## Starter word deck

The repo currently seeds a deliberately tiny example set:

- `แม่`
- `พ่อ`
- `ยาย`
- `แมว`
- `บ้าน`
- `น้ำ`
- `ใจ`
- `นา`
- `บีม`
- `แอน`

You can extend that set in [items.json](app/src/main/assets/items.json) and the lesson order in [curriculum.json](app/src/main/assets/curriculum.json).

## Local build

Prerequisites:

- Java 17
- Gradle or the included wrapper
- Android SDK with API 36 platform and matching build tools

Set your SDK path in `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Build a release APK locally:

```bash
ANDROID_KEYSTORE_PATH=/path/to/release.keystore \
ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... \
ANDROID_KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

If you build from a fork, point the in-app updater at your own GitHub repo:

```properties
thaiwrite.githubOwner=your-github-user-or-org
thaiwrite.githubRepo=your-repo-name
```

If you want a local release APK to report the same version string as a GitHub tag:

```properties
thaiwrite.versionName=0.1.0
```

## GitHub release APKs

The project is set up for GitHub-only Android distribution. Release builds are published as APK assets on GitHub Releases.

The in-app updater checks GitHub's releases API, picks the newest suitable non-draft non-prerelease APK release, downloads it, verifies the optional GitHub SHA-256 digest when present or the uploaded `.sha256` sidecar when available, and opens Android's package installer. Android still requires user approval for the install; this is not a silent privileged update flow.

Required repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Relevant workflows:

- [ci.yml](.github/workflows/ci.yml)
- [release.yml](.github/workflows/release.yml)

`ci.yml` now does three things on pushes, PRs, and manual runs:

- runs unit tests
- builds a debug APK
- builds a release-candidate APK and uploads both APK outputs as workflow artifacts

`release.yml` publishes signed APKs to GitHub Releases:

- automatically on pushed tags like `v0.1.1`
- manually from the Actions tab with a required tag input such as `v0.1.1`

The release workflow also uploads these extra assets:

- `app-release.apk`
- `app-release.apk.sha256`
- ProGuard/R8 `mapping.txt`
- `output-metadata.json`
