# Adaptation des connecteurs APKUpdater 3.x

Amont : https://github.com/rumboalla/apkupdater/tree/3.x

## Google Play
APKUpdater utilise `com.auroraoss:gplayapi:3.5.8`, `AppDetailsHelper` et un jeton anonyme obtenu auprès de `https://auroraoss.com/api/auth` à partir de propriétés techniques Android. Nous adaptons cette approche pour `deezer.android.app`. Les helpers dans `network/play/` sont adaptés du projet APKUpdater (copyright rumboalla et contributeurs, licence GPLv3). Notre dépôt reste sous GPLv3.

L'application communique avec Aurora OSS, service indépendant de Google, auquel elle transmet des informations techniques du téléphone pour obtenir une session anonyme. Elle ne demande pas de compte Google personnel. Le jeton anonyme est conservé dans le stockage privé Android. Le service peut être indisponible ou limiter les requêtes.

## APKMirror
APKUpdater interroge `/wp-json/apkm/v1/app_exists/` au moyen d'un identifiant Basic Authorization intégré à son code. Ce secret, propre au projet tiers, **n'est pas repris**. Le flux RSS public de notre application peut donc toujours retourner HTTP 403. Une méthode publique autorisée est nécessaire pour l'améliorer.

## Validation
Les tests unitaires vérifient le mapping des versions, pas l'authentification réelle. Tester Aurora Google Play sur un téléphone ; vérifier que les erreurs réseau restent visibles et qu'aucune fausse version n'est annoncée.