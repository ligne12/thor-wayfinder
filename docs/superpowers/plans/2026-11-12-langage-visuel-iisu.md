# Wayfinder au langage visuel d'iiSU — plan de réalisation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wayfinder prend l'apparence d'iiSU (clair et sombre) et la suit quand elle change dans iiSU, sans perdre de fonction.

**Architecture:** Le rendu change à la source dans `app/src/main/java/app/wayfinder/ui/` : le thème est construit à partir d'une valeur `IisuLook`, lue dans les réglages privés d'iiSU par `PServiceBridge.exec`. Tout champ illisible retombe sur une valeur par défaut. Les écrans ne sont pas réécrits ; seul le panneau rapide reçoit de nouvelles tailles.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.06.00), Gradle (`./gradlew :app:assembleDebug`), JDK 17, JUnit 4 (ajouté pour le seul test d'analyse), adb.

**Spec:** `docs/superpowers/specs/2026-11-12-langage-visuel-iisu-design.md`

## Global Constraints

- Fork privé : aucun `git push` vers `upstream`, aucune publication d'APK (licence PolyForm Strict).
- Branche de travail : `iisu-look`. Messages de commit en anglais, sans URL de session.
- Modifications limitées à `ui/`, `QuickPanelWindow.kt`, `PanelShortcuts.kt`, `Hub.kt` (masquage des réglages de thème), `AppSettings.kt` (mode forcé), plus les fichiers créés ici.
- Le code de stockage de « Your own colours » et du noir pur reste en place, inutilisé.
- La lecture d'iiSU est en lecture seule : aucune écriture dans `/data/data/com.iisulauncher`.
- Sur le Thor : une action, une capture, une vérification. Vérifier l'appli au premier plan avant toute touche. Ne pas toucher à GameNative.
- Aucune couleur ni arrondi écrit en dur hors de `ui/` (règle du projet ; son test `SourceRulesTest` n'est pas publié dans le dépôt, donc à respecter à la main).
- Valeurs par défaut, clair / sombre : fond de repli `F2F2F2` / `1C1C20` ; carte `FFFFFF` / `2B2B2B` ; tuile `E4E4E6` / `3A3A40` ; texte `2B2B2B` / `F2F2F2`. Arrondis : cartes 24, tuiles 20.

## Review Focus

1. Fichier de réglages iiSU absent, vide ou abîmé : le panneau s'ouvre avec les valeurs par défaut, sans erreur. Test : `parse_emptyOrBroken_givesDefaults` (tâche 4).
2. Service AYN indisponible (démarrage, SELinux) : valeurs par défaut, clair/sombre selon Android. Test : `resolveDark_withoutLook_followsSystem` (tâche 4).
3. Lecture lente : `PServiceBridge.exec` est bloquant ; elle ne doit jamais retarder l'ouverture du panneau. Vérification : tâche 4, étape 6.
4. Accent d'iiSU très clair ou très sombre : le texte posé dessus reste lisible (`TokenArgb.onAccent`). Test : `accent_extremes_keepReadableInk` (tâche 4).
5. Bascule clair/sombre pendant qu'un panneau est ouvert : le panneau rouvert a le bon thème, sans mélange. Vérification : tâche 4, étape 7.

---

### Task 0: Essai de lecture des réglages d'iiSU

Aucun code d'appli. Le Thor a un réglage d'usine « Run script as root » (Settings › Thor), qui passe par le même service que `PServiceBridge`.

**Files:**
- Create: `tools/iisu_probe.sh`
- Create: `docs/superpowers/specs/iisu-settings-format.md`
- Create: `app/src/test/resources/iisu/sample-light.txt`, `sample-dark.txt` (copies réelles, sans jeton ni identifiant)

**Interfaces:**
- Produces: le chemin du fichier de réglages d'iiSU, son format, et le nom des champs de Style, Glass et Motion. Les tâches 2 et 4 s'en servent.

- [ ] **Step 1: Écrire `tools/iisu_probe.sh`**

Le script copie dans `/sdcard/Download/iisu_probe/` : la liste récursive de `/data/data/com.iisulauncher` (`ls -laR`, sans les dossiers `cache` et `code_cache`), puis tout fichier de moins de 1 Mo dans `shared_prefs/`, `files/` et `databases/`. Il rend le dossier lisible (`chmod -R a+r`). Aucune écriture ailleurs.

- [ ] **Step 2: Pousser et faire exécuter**

Run: `adb push tools/iisu_probe.sh /sdcard/Download/` puis Thomas lance le script par Settings › Thor › Run script as root (ou Claude le fait, une action à la fois).
Expected: `/sdcard/Download/iisu_probe/listing.txt` existe et n'est pas vide.

- [ ] **Step 3: Repérer les champs**

Récupérer le dossier sur le PC (hors dépôt). Chercher `theme`, `dark`, `accent`, `font`, `glass`, `blur`, `opacity`, `motion`, `anim`.

- [ ] **Step 4: Confirmer par différence**

Thomas change l'accent, puis le thème, dans iiSU › Settings › Appearance › Style. Relancer le script après chaque changement et comparer.
Expected: un champ identifié par réglage, avec sa valeur avant et après.

- [ ] **Step 5: Écrire `iisu-settings-format.md` et les deux fichiers d'exemple**

Le document donne : chemin du fichier, format, tableau champ → signification → valeurs observées. Retirer des exemples tout identifiant de compte et toute clé.

- [ ] **Step 6: Point d'arrêt**

Rapporter à Thomas ce qui est lisible. Si rien ne l'est, la tâche 4 se réduit à ses étapes de repli et Thomas décide de la suite.

- [ ] **Step 7: Commit**

```bash
git add tools/iisu_probe.sh docs/superpowers/specs/iisu-settings-format.md app/src/test/resources/iisu
git commit -m "Probe iiSU's appearance settings and record their format"
```

### Task 1: Compilation de référence et première installation

Prouve que l'outillage marche avant toute modification.

**Files:** aucun fichier modifié.

- [ ] **Step 1: Compiler le fork tel quel**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, APK dans `app/build/outputs/apk/debug/`. Les binaires natifs sont fournis dans `app/src/main/assets/fx/` ; si Gradle réclame le NDK 27.2.12479018 (le PC a le 28.2), s'arrêter et le signaler.

- [ ] **Step 2: Sauvegarder les réglages de Wayfinder**

Thomas : Wayfinder › More › Help & status › Back up now. Noter où va le fichier. D'après le CHANGELOG, la restauration ramène les contrôles de jeu ; les combos et les autres réglages sont à refaire à la main. Thomas prend des captures de ses pages de réglages avant de continuer.

- [ ] **Step 3: Remplacer l'appli**

Run: `adb uninstall app.wayfinder` puis `adb install app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`. La signature change, d'où la désinstallation, une seule fois.

- [ ] **Step 4: Remettre en service**

Thomas suit le tour de Wayfinder (accessibilité, clavier, arrière-plan), restaure la sauvegarde. Vérifier que `enabled_accessibility_services` contient encore le service d'AYN `com.odin.gameassistant`.
Expected: le bouton AYN ouvre le panneau rapide, identique à avant.

### Task 2: Thème iiSU par défaut et surfaces

**Files:**
- Create: `app/src/main/java/app/wayfinder/ui/IisuLook.kt`
- Create: `app/src/main/res/font/cal_sans.ttf` (Cal Sans, licence OFL ; copier `OFL.txt` dans `docs/licenses/`)
- Modify: `app/src/main/java/app/wayfinder/ui/GlassTheme.kt` (`Radii`, `glassColorsFor`, `GlassTokens.resolve`, `ThorGlassTheme`, `GlassType`)
- Modify: `app/src/main/java/app/wayfinder/ui/GlassComponents.kt` (`GlassScreen`, `glassSurface`)
- Modify: `app/src/main/java/app/wayfinder/Hub.kt` (lignes du thème, vers 614 et 1319)
- Modify: `app/src/main/java/app/wayfinder/AppSettings.kt` (lecture de `theme_mode`, vers 330)

**Interfaces:**
- Produces:

```kotlin
/** iiSU's appearance; a null field means "not read", and the default applies. */
data class IisuLook(
    val dark: Boolean? = null,
    val accent: Int? = null,        // ARGB
    val glassOpacity: Float? = null, // 0..1
    val glassTint: Int? = null,      // ARGB
    val motionScale: Float? = null,  // 1 = iiSU's default speed
    val font: String? = null,
)
fun glassColorsFor(dark: Boolean, look: IisuLook): GlassColors
```

`glassColorsFor(dark, black, custom)` est supprimée ; ses deux appelants (`GlassTokens.resolve`, `ThorGlassTheme`) passent `IisuLook()` jusqu'à la tâche 4. Les champs de la tâche 0 peuvent ajouter ou retirer des propriétés d'`IisuLook` : s'en tenir à ce qui a été observé.

- [ ] **Step 1: `IisuLook.kt`** — la classe ci-dessus, rien d'autre.

- [ ] **Step 2: `glassColorsFor(dark, look)`**

Deux palettes construites sur les valeurs par défaut des Global Constraints. Verre : `panelFill` = couleur de carte à l'opacité `look.glassOpacity ?: 0.72f` en clair, `0.60f` en sombre ; `blobs` vide et `blobAlpha = 0f`. Accent : `look.accent ?: 0xFF6C7BFF` (bleu-violet relevé sur iiSU), `accent2` = le violet `0xFF8A7BFF`. Les autres rôles (`danger`, `warn`, `onAccent`, `focusRing`, `optionWell`, `positive`, `surfaceModal`, `wash`) gardent leur calcul par `TokenArgb`.

- [ ] **Step 3: `Radii`** — `Card = 24.dp`, `Action = 20.dp`, `Tile = 20.dp`, `Panel = 26.dp`, `Modal = 26.dp`, `Large = 28.dp`. Les autres inchangés.

- [ ] **Step 4: Police** — `GlassType` : Cal Sans pour `headlineMedium`, `titleLarge`, `titleMedium`, `labelLarge`. `bodyLarge` et plus petit restent en police système.

- [ ] **Step 5: `GlassScreen`** — ne dessine plus l'aurore ni son horloge d'animation. Avec `LocalRealGlass`, la teinte de lisibilité actuelle est gardée ; sans, un aplat `g.base`. Retirer le code devenu mort dans ce fichier.

- [ ] **Step 6: `glassSurface`** — ajouter une ombre douce sous la forme (décalage 2 dp, flou 12 dp, noir à 10 % en clair, 30 % en sombre). Le liseré et le remplissage restent, avec les valeurs du thème.

- [ ] **Step 7: Réglages de thème** — `AppSettings` lit toujours `ThemeMode.SYSTEM`, quelle que soit la valeur stockée, et `customColours()` rend `null`. Dans `Hub.kt`, retirer les lignes « Theme », « Black » et « Your own colours » de la page ; leur code de stockage reste.

- [ ] **Step 8: Compiler et installer**

Run: `./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `BUILD SUCCESSFUL`, `Success`.

- [ ] **Step 9: Vérifier sur le Thor**

Captures du panneau rapide, du clavier et de la page d'accueil de Wayfinder, en clair puis en sombre (`adb shell cmd uimode night yes|no`, remis à sa valeur d'origine ensuite). Comparer à la capture d'iiSU. Thomas valide à l'œil ou donne ses corrections.

- [ ] **Step 10: Commit**

```bash
git add app/src/main docs/licenses
git commit -m "iiSU default look: palettes, radii, Cal Sans, no aurora, soft shadow"
```

### Task 3: Tailles du panneau rapide pour l'écran du bas

**Files:**
- Modify: `app/src/main/java/app/wayfinder/QuickPanelWindow.kt` (vers 254-275 et 680-690)
- Modify: `app/src/main/java/app/wayfinder/PanelShortcuts.kt` (`ShortcutGridEditor`)
- Modify: `app/src/main/java/app/wayfinder/ui/GlassControls.kt` (épaisseur du curseur)

**Interfaces:**
- Consumes: `Radii` de la tâche 2.

- [ ] **Step 1: Mesurer** — capture du panneau actuel sur l'écran du bas (1240 × 1080 px, environ 537 × 468 dp). Noter la largeur utile d'une colonne.

- [ ] **Step 2: Grille** — 4 colonnes ; `cellHeight` égal à la largeur d'une colonne, pour des tuiles carrées (remplace le `64.dp` fixe).

- [ ] **Step 3: Espacements** — `Arrangement.spacedBy(7.dp)` devient `10.dp` ; marge extérieure `12.dp` horizontale conservée.

- [ ] **Step 4: Curseurs** — épaisseur de la piste portée à 10 dp en mode compact (lire la valeur actuelle dans `GlassControls.kt` ; si elle est déjà de 10 dp ou plus, ne rien changer).

- [ ] **Step 5: Vérifier** — compiler, installer, capture. Tout le contenu actuel du panneau reste atteignable à la manette et au doigt, sans carte coupée. Thomas valide les tailles.

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git commit -m "Quick panel: square tiles and roomier spacing on the bottom screen"
```

### Task 4: Lecture d'iiSU branchée sur le thème

**Files:**
- Modify: `app/src/main/java/app/wayfinder/ui/IisuLook.kt`
- Modify: `app/src/main/java/app/wayfinder/ui/GlassTheme.kt` (`GlassTokens`, `ThorGlassTheme`)
- Modify: `app/src/main/java/app/wayfinder/QuickPanelWindow.kt` (rafraîchissement à l'ouverture)
- Modify: `app/build.gradle.kts` (`testImplementation("junit:junit:4.13.2")`)
- Test: `app/src/test/java/app/wayfinder/ui/IisuLookTest.kt`

**Interfaces:**
- Consumes: le format décrit par `iisu-settings-format.md` et les exemples de la tâche 0 ; `PServiceBridge.exec(cmd: String): String?`.
- Produces:

```kotlin
object IisuLookParser { fun parse(text: String): IisuLook }   // pure, never throws
object IisuLookSource {
    @Volatile var current: IisuLook        // last good read; IisuLook() until then
    fun refreshAsync()                     // background thread; reads only if the file's mtime changed
}
fun resolveDark(look: IisuLook, systemNight: Boolean): Boolean = look.dark ?: systemNight
```

- [ ] **Step 1: Écrire les tests**

```kotlin
class IisuLookTest {
    private fun sample(name: String) = javaClass.getResource("/iisu/$name")!!.readText()

    @Test fun parse_sampleLight_readsThemeAndAccent() {
        val look = IisuLookParser.parse(sample("sample-light.txt"))
        assertEquals(false, look.dark)
        assertNotNull(look.accent)   // replace with the exact ARGB recorded in iisu-settings-format.md
    }
    @Test fun parse_sampleDark_readsDark() =
        assertEquals(true, IisuLookParser.parse(sample("sample-dark.txt")).dark)

    @Test fun parse_emptyOrBroken_givesDefaults() {
        for (t in listOf("", "{", "\u0000\u0001", "<map><string name=\"x\">", "theme=")) assertEquals(IisuLook(), IisuLookParser.parse(t))
    }
    @Test fun resolveDark_withoutLook_followsSystem() {
        assertEquals(true, resolveDark(IisuLook(), systemNight = true))
        assertEquals(false, resolveDark(IisuLook(), systemNight = false))
        assertEquals(true, resolveDark(IisuLook(dark = true), systemNight = false))
    }
    @Test fun accent_extremes_keepReadableInk() {
        for (argb in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFF00.toInt())) {
            val ink = TokenArgb.onAccent(argb)
            assertTrue(contrast(ink, argb) >= 4.5)   // WCAG ratio, helper in the test file
        }
    }
}
```

- [ ] **Step 2: Vérifier qu'ils échouent**

Run: `./gradlew :app:testDebugUnitTest --tests "app.wayfinder.ui.IisuLookTest"`
Expected: échec de compilation, `IisuLookParser` introuvable.

- [ ] **Step 3: `IisuLookParser.parse`** — selon le format relevé. Chaque champ est lu séparément dans un `runCatching` : un champ illisible donne `null` pour ce champ seul. Les valeurs hors bornes (opacité hors de 0..1, échelle d'animation hors de 0.25..4) donnent `null`.

- [ ] **Step 4: Vérifier que les tests passent**

Run: la même commande.
Expected: 5 tests, 0 échec. Si `accent_extremes_keepReadableInk` échoue, corriger `TokenArgb.onAccent`, pas le test.

- [ ] **Step 5: `IisuLookSource`** — `refreshAsync` lance un thread ; lit la date du fichier par `PServiceBridge.exec("stat -c %Y <chemin>")`, puis le fichier par `cat` seulement si la date a changé ; écrit `current` uniquement après une analyse réussie. Si `PServiceBridge.isAvailable()` est faux, ne fait rien. Le chemin vient de `iisu-settings-format.md`.

- [ ] **Step 6: Brancher** — `GlassTokens.resolve` et `ThorGlassTheme` utilisent `IisuLookSource.current` et `resolveDark`. `QuickPanelWindow` appelle `refreshAsync()` à l'ouverture, sans l'attendre : le panneau s'ouvre avec la dernière valeur connue, la nouvelle s'applique à l'ouverture suivante. Appel aussi au démarrage du service. `motionScale` : si l'ouverture du panneau a une seule durée d'animation, la multiplier par `look.motionScale ?: 1f` ; s'il y en a plusieurs dispersées, ne rien faire et le signaler à Thomas (la conception dit « si c'est simple »). Vérifier dans le code qu'aucun `exec` n'est fait sur le fil principal.

- [ ] **Step 7: Vérifier sur le Thor**

Compiler, installer. Changer l'accent dans iiSU, ouvrir le panneau deux fois : la deuxième ouverture a le nouvel accent. Basculer le thème d'iiSU, même contrôle. Couper le service (test : renommer provisoirement le chemin dans une version de test) : le panneau s'ouvre avec les valeurs par défaut.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src
git commit -m "Follow iiSU's Style, Glass and Motion settings, with safe defaults"
```
