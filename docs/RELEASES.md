# Release and signing guide

**Never commit a production keystore, password, token or decoded key to this public repository.**

1. Make the GitHub repository **public** (Settings → General → Danger Zone → Change repository visibility).
2. Generate **one permanent production keystore** on a trusted workstation:

   ```bash
   keytool -genkeypair -v -keystore deezer-release.jks -alias deezer-release \
     -keyalg RSA -keysize 3072 -validity 10000
   ```

3. Add four repository Actions secrets in Settings → Secrets and variables → Actions:
   - `ANDROID_KEYSTORE_BASE64`: full base64 of the JKS (Linux: `base64 -w0 deezer-release.jks`).
   - `ANDROID_KEYSTORE_PASSWORD`: keystore password.
   - `ANDROID_KEY_ALIAS`: alias (example `deezer-release`).
   - `ANDROID_KEY_PASSWORD`: key password.

4. Set `versionCode` to a larger number and `versionName` to the matching semantic version in `app/build.gradle.kts`.
5. Commit and push the change. Add a tag like `v0.1.0` and push it.
6. The **Signed GitHub Release** workflow creates a signed APK and publishes it in Releases. GitHub serves an asset `digest` (SHA-256); the app verifies that digest before presenting Android's installer.
7. Test upgrading a **production signed APK** to a newer **production signed APK**. Debug builds use a different signature and intentionally cannot upgrade signed production builds.

If you lose the production keystore or change the application ID, existing Android installations generally cannot be upgraded in place. Back up the key and its secrets safely.

Android requires user confirmation to install the APK. If Android requests permission to install from this source, approve it and tap the update button again. No silent installation, privilege escalation or CAPTCHA bypass is performed.

## Build and source limitations

The repository includes Gradle Wrapper 8.9 for local builds. GitHub Actions uses the CI-installed Gradle 8.9. Google Play's public HTML might not expose a version at all, APKMirror RSS might require a challenge, and APKPure's unofficial endpoint might change. Source failures are shown in the app, not silently suppressed.

## Version tracking behavior

- Notification frequency **Jamais** cancels background work. Opening the app still performs a manual-mode refresh, but no notification is issued.
- First successful response **per source** creates a baseline; imported historical releases never spam notifications.
- Subsequent unseen versions cause a notification, without duplicating the same version across stores.
- Bêta releases are collected; switch off **Inclure les versions bêta** to hide them and prevent notifications for them.
- Published date is provided by the source; `detectedAt` is our first sighting time.
