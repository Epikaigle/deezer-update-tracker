# Deezer Update Tracker

Application Android **non officielle et open source** permettant de suivre les versions de Deezer (`deezer.android.app`) depuis plusieurs plateformes, même lorsque Deezer n'est pas installé sur le téléphone.

**[Télécharger la dernière APK signée](https://github.com/Epikaigle/deezer-update-tracker/releases/latest)** · [Voir les publications](https://github.com/Epikaigle/deezer-update-tracker/releases)

## Fonctionnalités

- **Trois sources indépendantes**, présentées dans cet ordre : **Google Play → APKMirror → APKPure**.
- **Historique en trois lignes** : chaque source possède son propre carrousel horizontal de versions, avec recherche et cartes compactes.
- Versions stables, bêta et autres préversions, selon les informations fournies par les sources et les filtres activés.
- **Date de publication** lorsqu'elle est disponible ; les dates de première détection ne sont pas affichées dans l'interface.
- **Variantes** regroupées par version : format de fichier (APK, APKM, APKS, etc.), architecture, densité d'écran, niveau d'API Android et code de version **uniquement quand la source fournit ces données**.
- Alertes Android pour les nouvelles versions, avec dédoublonnage entre les sources et choix de la fréquence. Le mode **Jamais** désactive les vérifications périodiques et les notifications ; une vérification reste possible à l'ouverture ou manuellement.
- Interface volontairement compacte et principalement basée sur des icônes, accompagnées de descriptions d'accessibilité.
- Mise à jour de Deezer Update Tracker depuis **GitHub Releases**, avec vérification du fichier et confirmation d'installation par Android.

Les informations, préférences et versions observées sont conservées localement ; une source indisponible ne supprime pas son historique. Les versions Deezer déjà relevées sont enregistrées sans l'ancienne limite globale de 1 500 variantes, avec une copie de secours locale. La conservation s'applique aux mises à jour de l'application utilisant la même identité Android ; **désinstaller l'application ou effacer ses données supprime cet historique local** (les sauvegardes Android automatiques sont désactivées).

## Fonctionnement des sources

| Source | Informations récupérées | Limites |
| --- | --- | --- |
| **Google Play** | Version courante via **Aurora GPlayAPI** et session anonyme Aurora OSS | Pas d'archive publique des versions passées fournie par ce connecteur. Les versions antérieures apparaissent uniquement si elles ont déjà été observées et enregistrées. La date de sortie peut être absente. |
| **APKMirror** | Dernière publication et, si celle-ci est une bêta, tentative de lecture de la dernière stable via l'API `app_exists` ; variantes et tentative d'accès aux archives publiques | L'API ne fournit pas un historique complet. L'archive publique peut renvoyer HTTP 403, présenter un défi anti-bot ou changer de format. La lecture de la page est limitée aux publications récentes et les tentatives sont espacées. |
| **APKPure** | Historique des versions via un endpoint non officiel | La disponibilité du service et les métadonnées retournées peuvent évoluer. |

Les résultats d'une plateforme ne sont **jamais attribués artificiellement à une autre**. Les formats, architectures et dates inconnus ne sont pas déduits ou inventés. Le premier relevé réussi de chaque source établit un point de départ sans envoyer d'alertes rétroactives ; les nouvelles versions observées ensuite peuvent déclencher des notifications.

L'accès APKMirror s'appuie sur un identifiant partagé présent dans les projets open source [APKUpdater](https://github.com/rumboalla/apkupdater) et [Obtainium](https://github.com/ImranR98/Obtainium). Il ne constitue pas une garantie d'accès permanent : APKMirror peut refuser, limiter ou modifier le service. Les erreurs restent visibles au lieu d'être confondues avec une absence de mise à jour.

## Confidentialité et sécurité

- Aucun compte personnel Google n'est demandé. **Aurora OSS reçoit des informations techniques sur l'appareil** pour établir une session anonyme d'accès à Google Play.
- Les requêtes vers les plateformes sont effectuées depuis l'application Android, sans serveur de suivi personnel ni télémétrie intégrée.
- Les préférences et les relevés sont enregistrés dans le stockage privé de l'application.
- Aucun CAPTCHA ou dispositif de protection anti-bot n'est contourné automatiquement.
- Les APK de cette application publiées sur GitHub sont signées avec la clé de production. Lors d'une mise à jour intégrée, l'APK téléchargée est vérifiée avant de demander l'installation à Android. L'installation nécessite la confirmation de l'utilisateur.

## Installation

1. Ouvrir **[la dernière publication](https://github.com/Epikaigle/deezer-update-tracker/releases/latest)** et télécharger l'APK de production.
2. Installer l'APK sur Android et autoriser l'installation depuis la source utilisée si Android le demande.
3. Dans l'application, choisir les sources et la fréquence de surveillance. Les mises à jour suivantes peuvent être recherchées et installées depuis l'application elle-même.

**Pour mettre à jour une installation existante, utiliser une APK de production signée avec le même certificat.** Une APK debug signée autrement ne peut pas remplacer une installation de production.

## Développement

Le projet est écrit en **Kotlin** et utilise **Jetpack Compose**, **WorkManager** et **Gradle**. Les versions exactes des outils, du SDK et des dépendances sont définies dans les fichiers de configuration du dépôt, qui font autorité.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

L'APK de test se trouve dans `app/build/outputs/apk/debug/`. Les contrôles sont également exécutés par **GitHub Actions**.

### Organisation du développement et publications

- **Branche de travail : `main` uniquement.** Les changements sont enregistrés directement sur `main`, sans branches de fonctionnalité ni pull requests systématiques.
- Les commits ordinaires déclenchent la validation continue. **La publication de production requiert une augmentation explicite** de `versionCode` et `versionName` dans `app/build.gradle.kts` ; changer uniquement le README ne crée pas de nouvelle version.
- Le workflow de production signe l'APK, vérifie le certificat, puis publie sur **GitHub Releases** si la version n'existe pas déjà.
- La clé privée de signature et ses mots de passe ne doivent **jamais** être ajoutés au dépôt. Voir le [guide de signature et de publication](docs/RELEASES.md).

Le **README décrit le fonctionnement du projet**, pas l'historique de chaque publication. Les notes, corrections et changements propres à une version doivent être documentés dans **GitHub Releases**, plutôt qu'ajoutés ici à chaque mise à jour.

## Licence et attribution

Distribué sous licence **[GNU GPLv3](LICENSE)**. Certaines adaptations techniques s'inspirent de [APKUpdater](https://github.com/rumboalla/apkupdater) et d'[Obtainium](https://github.com/ImranR98/Obtainium) ; voir également [les notes sur les connecteurs](docs/APKUPDATER-CONNECTORS.md).

Deezer est une marque de son propriétaire. Ce projet n'est affilié ni à Deezer, ni à Google, ni à Aurora OSS, ni à APKMirror, ni à APKPure.
