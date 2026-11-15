# Fork notes

This is a soft fork of [rommapp/argosy-launcher](https://github.com/rommapp/argosy-launcher).
It installs as `dev.saravieira.argosy` (debug: `dev.saravieira.argosy.debug`) next to stock Argosy,
and its in-app updater reads releases from `SaraVieira/argosy-launcher`.

Fork-only changes live in the block at the end of `app/build.gradle.kts`, this file and
`.github/workflows/fork-release.yml`. Everything else should be upstream-ready.

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
