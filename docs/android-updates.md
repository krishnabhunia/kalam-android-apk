# Android updates and permanent signing

The package-conflict report was reproduced by inspecting the published APKs:

| APK | Android versionCode | Signing certificate SHA-256 |
| --- | --- | --- |
| 1.22.0 | 29 | `415527987baa3502a6be573392e6303ac07f843a3cfd6075d94618867c171bf9` |
| 1.23.0 | 33 | `04a523b9caabeee3b6dca1cc351833096d9fedd4c1df5db91e5d7770ee5e95d5` |

The previous workflow ran assembleDebug on fresh runners, generating a different
debug signing key on each runner. Android requires a matching certificate to
update an installed package. Changing the filename or version does not fix this.

## One-time signing setup

Use the original private signing key if it was retained. The public certificate
inside an APK cannot recreate its private key. If the temporary runner's key was
not saved, generate a permanent release key once and keep an offline backup.
Never generate a replacement key for a normal update.

Configure these GitHub repository Actions secrets before building installable
artifacts with the new workflow:

- `ANDROID_KEYSTORE_BASE64`: base64 of the permanent keystore's binary contents.
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: the permanent key alias.
- `ANDROID_KEY_PASSWORD`: the key password.

Set the repository Actions variable `ANDROID_SIGNING_CERT_SHA256` to the key's
certificate SHA-256 fingerprint (64 hex characters; colons are accepted).
Use keytool's certificate output to obtain it. Do not share the private key or
passwords in a PR, issue, chat message, or repository file.

On Windows, `tools/setup_android_signing.ps1 -Mode Existing` imports the original
key configuration. If no original key exists, `-Mode Generate` creates a key
once in `%LOCALAPPDATA%/KalamSigning`. It refuses to overwrite an existing key.
The helper encrypts saved passwords for the current Windows user. Use
`-Mode CopySecret -SecretName <name>` to copy each value locally, and enter it
into the matching GitHub secret yourself; the helper never prints passwords.
Default Fingerprint mode displays the public fingerprint for the variable.
Keep a secure offline copy of the keystore and the actual passwords: Windows
encrypted credentials alone cannot be recovered by another Windows account.

The signing step uses that protected key for every beta and stable APK. It
verifies the fingerprint and refuses to publish if configuration is missing or
incorrect. It never falls back to a new debug key. PR tests/builds use a read-only
GitHub token; a separate publishing job has release write permission.

If migrating from an unavailable old key, first back up clocks to Downloads in
the currently installed app (Clocks on 1.22; Settings → Clock backup on 1.23).
One uninstall/reinstall may be needed to adopt the
permanent key; restore the clocks afterward. Future same-key, higher-versionCode
updates preserve private app data. The app updater never uninstalls Kalam.

## In-app controls

Open **Settings → App updates**. The collapsed card shows only its title and
arrow. The expanded section shows the installed version, Check GitHub for
updates, and an optional Include beta versions switch (off by default).

Kalam also checks when the app starts. It reads published GitHub Releases,
compares semantic versions numerically, and ignores drafts, older versions,
foreign assets and releases without a usable SHA-256 checksum. PR artifacts
are not public GitHub releases and do not appear in this feed automatically.

To publish an optional beta, a maintainer can explicitly run the Software
workflow on the desired branch with **publish_beta** enabled. It publishes a
GitHub prerelease without changing the stable latest release. Ordinary PR runs
only produce the beta artifact. Stable releases publish after merging to main.

Download fetches the single versioned ZIP, checks GitHub's SHA-256 and size,
extracts only the expected Android APK into private cache, and verifies its
package, versionCode, versionName and signing certificate against the installed
app. Incompatible updates show an explanation and cannot reach the installer.
Install opens Android's permission screen if needed, then the system installer.
Android requires user confirmation; ordinary apps cannot silently install.

## Validation

The unit tests use a captured public GitHub release response and the two actual
certificate fingerprints above. They cover stable/beta precedence, beta numeric
ordering, downgrade prevention, bad assets/checksums, wrong packages/certificates
and unsafe archives. Local APK builds and repeated-key signing are verified
separately. A real device is still required to check the permission screen,
installation prompt, install-over-update and preservation of all app data.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing),
[Android update requirements](https://developer.android.com/google/play/app-updates),
[GitHub Releases API](https://docs.github.com/en/rest/releases/releases).
