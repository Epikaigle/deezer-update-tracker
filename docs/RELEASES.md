# Compilation, signature et publication Android

Ce document explique **la procédure permanente** de publication. Les changements propres à une version sont conservés dans [GitHub Releases](https://github.com/Epikaigle/deezer-update-tracker/releases), et non dans le README.

## Principes

- Toutes les modifications se font **directement sur `main`**, sans branche temporaire.
- Les commits sur `main` exécutent la validation Android (`.github/workflows/android.yml`).
- Le workflow de production (`.github/workflows/production.yml`) démarre uniquement lorsque des fichiers de l'application ou de sa configuration de construction changent, ou sur déclenchement manuel. **Les modifications du README et de la documentation ne déclenchent pas une nouvelle publication signée.**
- Une **nouvelle version** exige une augmentation de `versionCode` et une mise à jour cohérente de `versionName` dans `app/build.gradle.kts`. Le workflow ignore toute release déjà publiée pour ce nom de version ; il n'écrase pas une APK existante.
- Les APK de production successives conservent le **même identifiant Android et le même certificat de signature**. Les APK debug, signées différemment, ne peuvent pas remplacer une installation de production.

## Secrets et signature

Les quatre secrets GitHub Actions nécessaires sont :

| Secret | Utilisation |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Contenu Base64 du fichier de signature de production |
| `ANDROID_KEYSTORE_PASSWORD` | Mot de passe du magasin de clés |
| `ANDROID_KEY_ALIAS` | Alias de la clé de production |
| `ANDROID_KEY_PASSWORD` | Mot de passe de la clé |

La configuration Android lit un magasin de clés **PKCS#12** ; les secrets et la clé privée ne doivent **jamais être enregistrés dans le code, le README, les logs ou les fichiers publics**. Conserver une sauvegarde sûre du magasin de clés : si la clé est perdue ou remplacée, une mise à jour Android en place peut devenir impossible.

Le workflow de production décode temporairement la clé dans l'environnement GitHub Actions, exécute les tests, compile la release, vérifie la signature avec `apksigner`, compare le certificat avec celui de la première publication officielle, produit une somme SHA-256 et supprime le fichier temporaire de clé.

## Procédure de publication

1. Terminer les modifications et les tests sur `main`.
2. Modifier **uniquement si une nouvelle APK doit être publiée** les propriétés `versionCode` (valeur strictement supérieure) et `versionName` dans `app/build.gradle.kts`.
3. Enregistrer le changement sur `main`.
4. Vérifier les résultats des workflows **Android CI** et **Production APK (signed)** dans l'onglet Actions. La compilation, les tests, la vérification du certificat et la publication doivent réussir.
5. Récupérer l'APK signée et le fichier de sommes de contrôle dans [GitHub Releases](https://github.com/Epikaigle/deezer-update-tracker/releases).
6. Tester sur un vrai téléphone la mise à jour depuis **la version de production précédente** sans désinstaller l'application. Vérifier la conservation de l'historique, des préférences et du même identifiant de package.

Un workflow alternatif (`.github/workflows/release.yml`) est également présent pour les tags `v*`. Éviter toute publication concurrente pour un même tag : utiliser de préférence le processus automatique sur `main`.

## Vérifications de non-régression

- APKMirror, APKPure et Google Play sont interrogés indépendamment ; une erreur d'une source n'efface pas son historique local.
- La première lecture d'une source établit le point de départ sans notifications rétroactives ; les nouvelles versions sont dédoublonnées entre sources.
- Le mode **Jamais** désactive la surveillance périodique et les notifications ; l'actualisation à l'ouverture reste disponible.
- Les dates affichées proviennent des sources ; en leur absence, elles restent inconnues. L'interface ne montre pas la date de première détection.
- L'historique présente les sources en lignes dans l'ordre **Google Play, APKMirror, APKPure**, avec les variantes techniques lorsqu'elles sont effectivement connues.
- Tester les écrans étroits et larges, les modes clair/sombre, le redimensionnement des polices, TalkBack, les erreurs HTTP et les sources hors ligne.
- Confirmer l'installation manuellement dans Android. Aucun workflow ou build CI ne remplace un test réel de mise à jour.

Les connecteurs externes peuvent être modifiés, bloqués ou limités par leurs fournisseurs. La compilation réussie d'une APK ne garantit pas l'accès en temps réel à chaque source.
