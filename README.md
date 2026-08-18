# NavCarStereo

Android/Android Auto client for a [Navidrome](https://www.navidrome.org/) server (Subsonic protocol). Focus around albums — home screen with recently played albums, search, sequential/shuffle playback. No default alphabetical sorting.

## Modules

- `shared`: Subsonic/Navidrome client, domain models, encrypted credentials (AndroidKeyStore), and the `PlaybackService` (Media3 `MediaLibraryService`) that exposes the library to Android Auto.
- `mobile`: phone companion app. The Android Auto UI is projected by the framework from the `MediaBrowser` tree exposed by `shared`.

## Build

```
./gradlew :mobile:assembleDebug   # debug APK, installable right away
./gradlew test                    # unit tests
```

## Setup

Open the mobile app, enter the Navidrome server URL, username, and password. Credentials are encrypted and stored on-device; Android Auto uses them to start `PlaybackService`.

## Release

Pushing a `vX.Y.Z` tag triggers CI ([.github/workflows/release.yml](.github/workflows/release.yml)): runs the tests, builds and signs `mobile-release.apk`, and attaches it to a new [GitHub Release](releases). `test.yml` runs the tests on every push/PR, regardless of branch.

```
git tag v1.2.0
git push origin v1.2.0
```
