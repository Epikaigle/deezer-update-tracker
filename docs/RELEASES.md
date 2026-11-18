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
5. Update `versionCode` and `versionName` together, commit the change and merge into `main`. The **Production APK (signed)** workflow runs tests, builds with the permanent signing key, verifies `apksigner` and publishes the version tag if it does not already exist. It will never overwrite an already released APK.
6. Alternatively, tags matching the version can trigger the **Signed GitHub Release** workflow. Avoid publishing twice for the same tag. GitHub serves an asset `digest` (SHA-256); the app verifies that digest before presenting Android's installer.
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

## One-time initial production publication

The `Production APK (signed)` workflow runs on trusted pushes to `main`. If all four secrets exist and the PKCS12 signing key works, it runs tests, verifies the release signature, uploads the signed artifact, and publishes the new version exactly once. It never overwrites existing releases. GitHub creates the matching tag when publishing.

**Never share or upload a private signing kit to the public repo.** The GitHub Actions secrets API does not reveal secret values; only a successful signed CI run verifies the setup.

## Device acceptance checklist

Test a real update **v0.1.0 → v0.1.1**, preserving the same Android application ID and signing key:
- Launch v0.1.0, record preferences and local version history.
- Observe v0.1.1 on the Accueil page (GitHub Releases).
- Tap download/install; approve unknown-apps permission if Android asks; confirm OS installation.
- Relaunch: version reads v0.1.1, no duplicate app icon, preferences/history intact.
- Check no warning about signature incompatibility or downgrade.
- Verify manual mode Jamais does not produce background notifications.
- Verify APKMirror, APKPure and Google Play independently while offline, with one source failing, and with beta on/off.
- Test compact phone widths, landscape, tablets, accessibility font scaling, dark mode and TalkBack.

The build and unit tests cannot fully replace UI/device testing.
