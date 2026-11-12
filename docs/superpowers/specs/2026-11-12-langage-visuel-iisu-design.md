# Wayfinder au langage visuel d'iiSU — conception (morceau 1)

Date : 2026-11-12. Fork privé `ligne12/thor-wayfinder`, pour un seul Thor Max (Android 13). Rien n'est publié.

## But

Le Thor doit donner l'impression d'une seule console dont iiSU est la référence visuelle.
Ce document couvre le morceau 1 : Wayfinder prend l'apparence d'iiSU et la suit quand elle change.

Morceaux suivants, chacun avec son propre document :

2. barre de volume ;
3. centre de notifications (glisser du haut vers le bas) ;
4. multitâche (glisser du bas vers le haut).

## Décisions prises

- Toutes les fonctions de Wayfinder sont conservées. Le contenu et l'organisation du panneau rapide (bouton AYN) ne changent pas.
- Le rendu est modifié à la source, dans `app/src/main/java/app/wayfinder/ui/`. Les écrans ne sont pas réécrits.
- Thèmes clair et sombre.
- iiSU est la source de l'apparence : ses réglages Style, Glass et, si c'est simple, Motion se reflètent dans Wayfinder.
- Les tailles du panneau rapide sont réadaptées à l'écran du bas.

## Hors périmètre

- Les réglages iiSU Home Layout, Media & Background, Tooltips et Dual Screen.
- Toute réécriture d'écran (remappage, manettes externes, tutoriel, clavier). Ils héritent du thème.
- Les morceaux 2 à 4.

## Architecture

Trois unités, toutes dans `ui/` sauf la lecture.

### 1. Lecture d'iiSU (`IisuLook.kt`, nouveau)

- Rôle : lire les réglages d'apparence d'iiSU et les rendre sous la forme d'une valeur `IisuLook` (thème clair ou sombre, accent, police, paramètres du verre, durée des animations).
- Moyen : `PServiceBridge.exec`, qui lit les données privées de `com.iisulauncher` avec les droits administrateur. Lecture seule, jamais d'écriture.
- Moment : au démarrage du service, puis à chaque ouverture d'un panneau ou d'un écran, si le fichier source a changé (date de modification).
- Repli : tout champ absent ou illisible prend la valeur par défaut ci-dessous. Si le service AYN est indisponible, tout est par défaut, et le clair/sombre suit le mode d'Android (`ThemeMode.SYSTEM`, déjà présent).
- L'analyse du fichier est une fonction pure (texte en entrée, `IisuLook` en sortie), testée sans appareil.

### 2. Thème (`GlassTheme.kt`, modifié)

- `glassColorsFor` construit les couleurs à partir d'un `IisuLook`.
- Valeurs par défaut, relevées sur iiSU en clair et proposées pour le sombre :

| Rôle | Clair | Sombre |
|---|---|---|
| Fond de repli | F2F2F2 | 1C1C20 |
| Carte, panneau | FFFFFF | 2B2B2B |
| Tuile, creux | E4E4E6 | 3A3A40 |
| Texte principal | 2B2B2B | F2F2F2 |

- Accent : le bleu-violet d'iiSU, lu dans Style. Anneau de focus à la manette en dégradé d'accent.
- Arrondis (`Radii`) : cartes 24, tuiles 20, les autres rôles ajustés en proportion.
- Police : Cal Sans (licence OFL, embarquée dans `res/font`) pour titres et libellés. La police système reste pour les petits textes. Si Style indique une autre police que l'appli n'embarque pas, Cal Sans est gardée.
- Retirés des réglages : le choix du thème, « Your own colours » et le noir pur. Leur code de stockage reste en place, inutilisé, pour limiter les conflits avec l'amont.

### 3. Surfaces (`GlassComponents.kt`, modifié)

- `glassSurface` garde son moteur de verre. Opacité, teinte, liseré et ombre viennent du thème, donc d'`IisuLook`.
- `GlassScreen` ne dessine plus l'aurore colorée animée. Derrière le verre, on voit le jeu ou l'accueil, avec le flou système déjà géré par `LocalRealGlass`. Sans flou disponible, le fond de repli du tableau est utilisé.

### Tailles du panneau rapide (`QuickPanelWindow.kt`, `PanelShortcuts.kt`)

Écran du bas : 1240 × 1080 px, soit environ 537 × 468 dp.

- Grille de raccourcis : 4 colonnes, tuiles carrées.
- Espacement entre cartes : de 7 à 10 dp.
- Curseurs de luminosité et de volume plus épais.

Les valeurs finales sont réglées sur captures du Thor et validées à l'œil par Thomas.

## Ordre de réalisation

1. **Essai de lecture.** Une version de test liste et lit les données d'iiSU par `PServiceBridge`. Résultat attendu : le fichier, son format et les champs de Style, Glass et Motion. Si rien n'est lisible, l'unité 1 se réduit aux valeurs par défaut et au mode d'Android, et Thomas en est informé avant la suite.
2. Thème par défaut et surfaces (unités 2 et 3), sans lecture.
3. Tailles du panneau rapide.
4. Lecture d'iiSU branchée sur le thème.

Chaque étape donne une appli installable.

## Vérification

- Les tests existants passent, y compris `SourceRulesTest` (pas de couleur ni d'arrondi écrit en dur dans les pages).
- Test unitaire de l'analyse : un fichier d'exemple donne les bonnes valeurs ; un fichier vide ou abîmé donne les valeurs par défaut sans erreur.
- Sur le Thor : captures du panneau rapide, du clavier et d'un écran de réglage, en clair et en sombre, comparées à iiSU.
- Changer l'accent dans iiSU, rouvrir le panneau : l'accent a changé.

## Installation

- La signature du fork diffère de celle de l'original : la première installation demande de désinstaller Wayfinder. Les réglages sont sauvegardés avant, par l'export de Wayfinder s'il existe, sinon par copie de ses données via le service AYN.
- Les installations suivantes se font par-dessus.

## Risques

- **iiSU est une alpha.** Le format de ses réglages peut changer à chaque version et rompre la lecture. Le repli évite la casse ; la réparation reste à faire à la main.
- **Le service AYN dépend de SELinux en mode permissif**, le réglage d'usine. S'il devient indisponible, l'apparence reste aux valeurs par défaut.
- **Licence** : Wayfinder est sous PolyForm Strict 1.0.0, qui n'autorise pas la modification. Usage privé uniquement, aucune publication du fork modifié ni des APK.
- **Divergence avec l'amont** : les modifications restent groupées dans `ui/` et deux fichiers du panneau, pour que la fusion des versions de l'auteur reste simple.
