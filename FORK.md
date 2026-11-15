# Fork notes

This is a soft fork of [rommapp/argosy-launcher](https://github.com/rommapp/argosy-launcher).
It installs as `dev.saravieira.argosy` (debug: `dev.saravieira.argosy.debug`) next to stock Argosy,
and its in-app updater reads releases from `SaraVieira/argosy-launcher`.

Fork-only changes live in the block at the end of `app/build.gradle.kts`, this file,
`.github/workflows/fork-release.yml` and the `com.nendo.argosy.fork` package (main and test). The fork
package is reached from upstream code in exactly one place: `DualScreenManager.launchCompanionApp`,
plus its import. Everything else should be upstream-ready.

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
   `versionName`. It then creates a **draft** release with the universal, `-arm64` and `-arm32`
   APKs. Don't add x86 APKs: older updaters would pick them for the universal build. A tag whose
   suffix starts with a prerelease qualifier (`beta`, `rc`, ...) is marked as a prerelease.
4. Write the release notes and publish the draft. The updater ignores drafts.

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
