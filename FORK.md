# Fork notes

This is a soft fork of [rommapp/argosy-launcher](https://github.com/rommapp/argosy-launcher).
It installs as `dev.saravieira.argosy` (debug: `dev.saravieira.argosy.debug`) next to stock Argosy,
and its in-app updater reads releases from `SaraVieira/argosy-launcher`.

Fork-only changes live in the block at the end of `app/build.gradle.kts`, this file,
`.github/workflows/fork-release.yml` and the `com.nendo.argosy.fork` package (main and test). Upstream
code reaches the fork package in these places:

- `DualScreenManager.launchCompanionApp`, plus its import
- one line in `PlaySessionTracker.startSession`
- the in-game dashboard's Game Twin rail item: `dashboardActions` in `PresentationSlotContent`, and the
  `gameTwinLabel` / `onOpenGameTwin` fields of `DashboardActions` with their rail item in
  `InGameDashboard`

Everything else should be upstream-ready.

## Game Twin on the in-game dashboard

When Game Twin (`com.twinlore.app`) is installed, the in-game dashboard's side rail shows it right after
the sections (Session, States, Trophies, Manual, Guide), under its own app name. A tap opens it the same way
the dock does, on the screen the game isn't on, with the now-playing extras below.

## Game Twin launch log

Every game launch, built-in or external, overwrites `Argosy/launch_debug.log` on primary storage
(`/storage/emulated/0/Argosy/` for the main user) with one Cocoon-format launch block. Game Twin reads it
with no changes on its side:

1. In Game Twin, set the last-played source to **Cocoon**.
2. Pick the `Argosy` folder as the Cocoon folder.
3. Launch a game from Argosy. Game Twin switches to it within a few seconds.

The block carries the game title, the ROM path when the game has a local file, and an
`argosy://launch?game_id=` intent, so starting the game from Game Twin goes back through Argosy. It's
always on and needs Argosy's storage permission.

## Now-playing handoff

On a dual-screen device, an app opened from the in-game dashboard's dock (tap, app drawer or hold menu)
while a game runs receives these intent extras. It's always on. Values that aren't known are left out.

| Extra | Type |
| --- | --- |
| `com.nendo.argosy.extra.NOW_PLAYING_VERSION` | Int, `1`; present on every handoff |
| `com.nendo.argosy.extra.SOURCE_PACKAGE` | String, the sending build's application id |
| `com.nendo.argosy.extra.GAME_TITLE` | String |
| `com.nendo.argosy.extra.PLATFORM_SLUG` | String |
| `com.nendo.argosy.extra.ROM_PATH` | String, the game's `localPath` (not the active variant file) |
| `com.nendo.argosy.extra.EMULATOR_PACKAGE` | String; the built-in emulator uses `EmulatorRegistry.BUILTIN_PACKAGE` |
| `com.nendo.argosy.extra.ROMM_ID`, `IGDB_ID`, `RA_ID` | Long |
| `com.nendo.argosy.extra.SESSION_START_MS` | Long, epoch ms |
| `com.nendo.argosy.extra.HARDCORE` | Boolean |

The intent is the app's normal launcher intent. A `singleTask` app that's already running should get it in
`onNewIntent`; not yet confirmed on a device.

## Remotes

```bash
git remote add upstream https://github.com/rommapp/argosy-launcher.git
git remote set-url --push upstream DISABLED
```

## Staying current with upstream

Rebase, don't merge. Don't use GitHub's "Sync fork" button: it adds a merge commit.

```bash
git fetch upstream
git rebase upstream/main
git push --force-with-lease origin main
```

## Versioning

The version is upstream's `versionName` plus a fork suffix: `3.0.0-fork.1` on a stable base,
`3.0.0-beta.5.fork.1` on a prerelease base. Bump `forkRevision` in `app/build.gradle.kts` for a
fork-only release on the same upstream version. Reset it to 1 after rebasing onto a new upstream
version.

## Releasing

1. Commit any `forkRevision` bump and push `main`.
2. Tag it with the full version name and push the tag:
   ```bash
   git tag v3.0.0-beta.5.fork.1
   git push origin v3.0.0-beta.5.fork.1
   ```
3. `Fork Release` builds and signs the APKs and checks that the tag matches the built
   `versionName`. It then publishes the release, with generated notes and the universal, `-arm64`
   and `-arm32` APKs, so the in-app updater sees it straight away. Don't add x86 APKs: older
   updaters would pick them for the universal build. A tag whose suffix starts with a prerelease
   qualifier (`beta`, `rc`, ...) is marked as a prerelease.

## One-time signing setup

Android only installs an update over an app with the same id and the same signing key. Keep the
keystore and its passwords backed up somewhere safe: losing them means users have to uninstall to
update.

Create the keystore (you choose the passwords at the prompts):

```bash
keytool -genkeypair -v -keystore app/release.keystore -alias argosy -keyalg RSA -keysize 4096 -validity 10000
```

For local release builds, copy `keystore.properties.example` to `keystore.properties` and fill it in.
Both files are git-ignored.

For CI, add these repository secrets under Settings > Secrets and variables > Actions:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -i app/release.keystore` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `argosy` |
| `RELEASE_KEY_PASSWORD` | key password |
| `TITLEDB_API_SECRET`, `CHEATSDB_API_SECRET` | optional; leave unset if you don't have them |

## QuayPass is off

The fork blanks `QUAYPASS_SERVER_PUBKEYS` and skips upstream's release check for it, so QuayPass
credentials never verify and the feature fails closed. Upstream's dev and CI builds behave the same
way. The Settings > Social toggle is still there but can't do anything.
