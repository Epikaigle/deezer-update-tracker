# Deezer Update Tracker

Application Android communautaire **non officielle** pour surveiller les publications de Deezer (`deezer.android.app`) indépendamment de l'application installée.

## Fonctionnalités

- Trois sources surveillées indépendamment : **APKMirror** (flux RSS public), **APKPure** (historique de versions) et **Google Play** (métadonnées publiques lorsque la version est affichée).
- Versions stables **et bêta**, avec dates publiées lorsqu'elles sont disponibles, **codes de version Android** si la source les fournit, date de détection, liens et historique local.
- Notifications Android sur les nouvelles versions, sans doublon entre sources.
- Fréquence : **Jamais** (aucun travail en arrière-plan ni notification, actualisation à l'ouverture), 30 min, 1 h, 3 h ou 12 h.
- Recherche de mises à jour de **Deezer Update Tracker** via GitHub Releases et installation d'une APK signée depuis l'application, après confirmation Android.
- Fonctionne sans installer Deezer ni accéder à la liste des applications du téléphone.

> ⚠️ **Statut : prototype en développement.** Les sites tiers peuvent renvoyer des CAPTCHA/403 ou ne pas exposer de version exploitable. Une source inaccessible est signalée en erreur et n'est jamais interprétée comme « aucune mise à jour ». Google Play peut ne pas exposer le numéro de version dans son HTML public. Aucune promesse de couverture exhaustive ni de détection en temps réel.

## Sources et confidentialité

Les requêtes sont émises **depuis le téléphone** vers les sites concernés. Aucun compte, aucune télémétrie et aucun serveur personnel. Les versions et les préférences restent en stockage privé Android. Aucun CAPTCHA n'est contourné, aucune API protégée et aucun identifiant emprunté à un projet tiers ne sont utilisés.

La date de publication affichée vient de la source ; si elle est indisponible, seule la date de **première détection** est affichée.

## Développer et compiler

- JDK 17 et Android SDK 35. Le **Gradle Wrapper 8.9** est inclus.
- `./gradlew :app:assembleDebug` (l'APK de développement ne peut **pas** mettre à jour une APK publique signée avec une autre clé).
- `./gradlew :app:testDebugUnitTest`.

GitHub Actions construit automatiquement une APK **debug** de test après chaque push. La publication initiale et les futures versions de production sont **signées** avec une clé permanente stockée uniquement dans GitHub Actions Secrets. La première release est créée par le workflow `Production APK (signed)` après validation de sa signature, à condition que les quatre secrets soient correctement configurés. Voir [docs/RELEASES.md](docs/RELEASES.md).

## Configuration des versions

Package cible : `deezer.android.app`. Package de cette application : `io.github.epikaigle.deezertracker`.

Les releases officielles de ce dépôt sont à l'adresse https://github.com/Epikaigle/deezer-update-tracker/releases. Le premier lancement enregistre les versions déjà connues **sans notification rétroactive**. Le paramètre « Jamais » annule le travail périodique.

## Inspirations et licences

Le design des connecteurs et de la surveillance est inspiré d'[APKUpdater](https://github.com/rumboalla/apkupdater) (branche `3.x`) et d'[Obtainium](https://github.com/ImranR98/Obtainium). Ces deux projets sont sous licence **GNU GPLv3**. Les connecteurs ici sont des implémentations autonomes : aucun mot de passe d'API ou secret privé issu de ces projets n'est copié.

Ce projet est distribué sous GNU GPLv3. **Deezer** est une marque de son propriétaire ; ce projet n'est ni affilié ni approuvé par Deezer, Google, APKMirror ou APKPure.
