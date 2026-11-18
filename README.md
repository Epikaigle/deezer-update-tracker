# Deezer Update Tracker

Application Android communautaire **non officielle** pour surveiller les publications de Deezer (`deezer.android.app`) indépendamment de l'application installée.

## Fonctionnalités

- Trois sources surveillées indépendamment : **APKMirror** (API JSON de variantes, complétée par le RSS si accessible), **APKPure** (historique de versions) et **Google Play** (métadonnées via Aurora GPlayAPI et jeton anonyme).
- Versions stables **et bêta**, dates de publication lorsqu'elles sont disponibles, **codes de version Android**, types APK/APKM/APKS/XAPK, architectures, DPI et Android minimum lorsqu'ils sont renseignés, liens et historique local.
- Notifications Android sur les nouvelles versions, sans doublon entre sources.
- Choix des sources à activer, accessible dans l'onglet Paramètres.
- Interface en **3 onglets** (Accueil, Historique, Paramètres). L'historique comporte **3 grandes colonnes** (Google Play, APKPure, APKMirror) avec défilement horizontal sur téléphone ; chaque version regroupe ses variantes et sa **date de sortie uniquement**, sans date de détection.
- Fréquence : **Jamais** (aucun travail en arrière-plan ni notification, actualisation à l'ouverture), 30 min, 1 h, 3 h ou 12 h.
- Recherche de mises à jour de **Deezer Update Tracker** via GitHub Releases et installation d'une APK signée depuis l'application, après confirmation Android.
- Fonctionne sans installer Deezer ni accéder à la liste des applications du téléphone.

> ⚠️ **Statut : prototype en développement.** Les sites tiers peuvent renvoyer des CAPTCHA/403 ou ne pas exposer de version exploitable. L'accès API APKMirror emploie l'identifiant partagé **publié** par APKUpdater (comme Obtainium), qui n'est pas une autorisation spécifique à notre application : il peut être limité, refusé ou révoqué. Une source inaccessible est signalée en erreur et n'est jamais interprétée comme « aucune mise à jour ». Aurora OSS peut limiter ou refuser les connexions anonymes. Aucune promesse de couverture exhaustive ni de détection en temps réel.

## Sources et confidentialité

Les requêtes sont émises **depuis le téléphone** vers les sites concernés. Pour Google Play, l'app envoie à **Aurora OSS** un profil technique Android (modèle, build et autres informations système) pour obtenir une session anonyme ; aucun compte Google personnel n'est requis. Aucun compte, aucune télémétrie et aucun serveur personnel. Les versions et les préférences restent en stockage privé Android. Aucun CAPTCHA n'est contourné et aucun secret privé de projet tiers n'est copié. L'endpoint APKPure non officiel utilise l'en-tête de client public `projecta`, documenté dans les adaptateurs open source ; son fonctionnement peut changer à tout moment.

La date de publication affichée vient de la source ; si elle est indisponible, seule la date de **première détection** est affichée.

## Développer et compiler

- JDK 17 et Android SDK 35. Le **Gradle Wrapper 8.9** est inclus.
- `./gradlew :app:assembleDebug` (l'APK de développement ne peut **pas** mettre à jour une APK publique signée avec une autre clé).
- `./gradlew :app:testDebugUnitTest` (tests de détection, dédoublonnage, préférences et dates).

GitHub Actions construit automatiquement une APK **debug** de test après chaque push. Sur `main`, le workflow `Production APK (signed)` compile, teste et publie automatiquement une **nouvelle** version de production quand son numéro de version a été augmenté. Les mêmes secrets signent toutes les versions. Une version déjà publiée n'est jamais écrasée. Voir [docs/RELEASES.md](docs/RELEASES.md).

## Configuration des versions

Package cible : `deezer.android.app`. Package de cette application : `io.github.epikaigle.deezertracker`.

Les releases officielles de ce dépôt sont à l'adresse https://github.com/Epikaigle/deezer-update-tracker/releases. Le premier lancement enregistre les versions déjà connues **sans notification rétroactive**. Google Play donne une version courante, non une archive complète ; l'API APKMirror donne la dernière version et ses variantes, son RSS n'ajoute les anciennes que s'il est accessible. Aucun format APK/APKM/APKS n'est inventé lorsque les métadonnées ne le précisent. Le paramètre « Jamais » annule le travail périodique.

## Inspirations et licences

Le design des connecteurs et de la surveillance est inspiré d'[APKUpdater](https://github.com/rumboalla/apkupdater) (branche `3.x`) et d'[Obtainium](https://github.com/ImranR98/Obtainium). Ces deux projets sont sous licence **GNU GPLv3**. Les connecteurs ici sont des implémentations autonomes : aucun mot de passe d'API ou secret privé issu de ces projets n'est copié.

Ce projet est distribué sous GNU GPLv3. **Deezer** est une marque de son propriétaire ; ce projet n'est ni affilié ni approuvé par Deezer, Google, APKMirror ou APKPure.

## Validation de la mise à jour intégrée

La version `v0.1.0` est signée et distribuée sur GitHub Releases. La version `v0.1.1` corrige l'UX et la logique de suivi. Pour tester la mise à jour, installer **v0.1.0 de production**, ouvrir l'écran Accueil, vérifier que **v0.1.1** est proposée et confirmer son installation depuis l'application. Vérifier ensuite que les paramètres et l'historique local sont conservés. Un build de CI ne remplace pas ce test réel sur téléphone.
