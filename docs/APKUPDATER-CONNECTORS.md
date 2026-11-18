# Adaptation des connecteurs APKUpdater 3.x

Amont : https://github.com/rumboalla/apkupdater/tree/3.x

## Google Play
APKUpdater utilise `com.auroraoss:gplayapi:3.5.8`, `AppDetailsHelper` et un jeton anonyme obtenu auprès de `https://auroraoss.com/api/auth` à partir de propriétés techniques Android. Nous adaptons cette approche pour `deezer.android.app`. Les helpers dans `network/play/` sont adaptés du projet APKUpdater (copyright rumboalla et contributeurs, licence GPLv3). Notre dépôt reste sous GPLv3.

L'application communique avec Aurora OSS, service indépendant de Google, auquel elle transmet des informations techniques du téléphone pour obtenir une session anonyme. Elle ne demande pas de compte Google personnel. Le jeton anonyme est conservé dans le stockage privé Android. Le service peut être indisponible ou limiter les requêtes.

## APKMirror
APKUpdater interroge `/wp-json/apkm/v1/app_exists/` au moyen d'un identifiant Basic Authorization intégré à son code. La version 0.1.3 tente cette API avec **le même identifiant partagé et publiquement distribué** qu'utilisent APKUpdater et Obtainium. Il s'agit d'un identifiant de projet tiers : sa présence dans du code libre ne garantit pas le droit d'accès permanent, et APKMirror peut le révoquer. En cas d'erreur, le flux RSS reste un repli, mais il peut également retourner HTTP 403. Ne jamais affirmer qu'un blocage est résolu sans test réel.

## Validation
Les tests unitaires vérifient le mapping des versions, pas l'authentification réelle. Tester Aurora Google Play sur un téléphone ; vérifier que les erreurs réseau restent visibles et qu'aucune fausse version n'est annoncée.

## Historique et formats depuis v0.1.3

- Le POST `app_exists` permet de lire la version actuelle et les variants `apks[]` (`version_code`, `arches`, `dpis`, `minapi`, dates et liens lorsqu'ils sont présents).
- L'API ne fournit pas nécessairement tout l'historique des anciennes versions. Le RSS complète l'archive lorsque disponible et les versions observées sont conservées localement.
- Les types de fichier APK, APKM, APKS et XAPK **ne sont affichés que si explicitement présents** (type, extension, métadonnée) ; un lien `-android-apk-download` ne suffit pas à supposer qu'il s'agit d'un APK simple.
- Les versions provenant de Google Play, APKPure et APKMirror s'affichent en colonnes, la date de première détection reste stockée pour le dédoublonnage mais n'est plus affichée.
- Après chaque modification du connecteur, valider l'accès API réel depuis un téléphone avant de conclure à la résolution de HTTP 403.


## v0.1.4 — pages publiques APKMirror

Le connecteur conserve l'API `app_exists` pour la dernière version et ses variantes. Il tente en complément une lecture limitée des entrées `appRow` de la page publique de l'application (liens de publications, versions, dates affichées). Ce n'est pas une API d'archive : HTML susceptible de changer, filtre strict sur le préfixe de lien Deezer, maximum huit versions, et aucune tentative de contournement Cloudflare. En cas de 403, les versions antérieures ne peuvent pas être récupérées immédiatement ; l'application conserve les archives locales et ne les remplace jamais par des versions provenant d'APKPure en les présentant comme APKMirror.

Google Play fournit la version actuelle ; l'historique Google Play n'augmente que lorsqu'une nouvelle version a été réellement détectée par cette source.
