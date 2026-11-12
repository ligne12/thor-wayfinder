# Réglages d'apparence d'iiSU — format relevé

Relevé le 2026-11-12 sur iiSU Alpha 0.0.7.4 (`com.iisulauncher`, versionName 0.1.6.1), par `tools/iisu_probe.sh`.

## Fichier

`/data/data/com.iisulauncher/files/datastore/launcher_settings.preferences_pb`

- Jetpack DataStore Preferences : un message protobuf, champ 1 répété = une entrée `{1: nom, 2: Value}`.
- `Value` : champ 1 booléen, 2 float (32 bits), 3 entier, 5 chaîne.
- 81 entrées, 17 Ko. Lisible seulement en root (`PServiceBridge.exec`).
- Une entrée absente signifie « valeur par défaut d'iiSU ».

Les identifiants (RetroAchievements, etc.) sont dans d'autres fichiers (`shared_prefs/*_secure.xml`) : jamais lus.

## Champs utilisés

Vérifiés un par un sur le Thor le 2026-11-12 : chaque option changée dans iiSU, puis relue par Wayfinder.

| Nom | Type | Option d'iiSU | Valeurs et pièges |
|---|---|---|---|
| `theme_mode` | chaîne | Style › Theme Style | `SYSTEM`, `LIGHT`, `DARK` |
| `launcher_font_choice` | chaîne | Style › Font | `cal_sans`, `console_sans`, `arctanium` |
| `focus_indicator_primary_color_argb` | entier | Style › Gradient colors › Primary | couleur |
| `focus_indicator_secondary_color_argb` | entier | Style › Gradient colors › Secondary | couleur ; absente = #C56EFF |
| `custom_selection_gradient_enabled` | booléen | Style › Custom selection gradient | `true` ; **coupé = clé supprimée** ; coupé : dégradé d'iiSU #71E0FF → #C56EFF |
| `home_glass_tint_color_argb` | entier | Glass › Custom glass color | activé : couleur opaque ; **coupé : même couleur, alpha 0** (ou clé absente) |
| `home_glass_opacity` | float | Glass › Opacity | 0,1 à 0,9 ; n'agit qu'avec la couleur de verre, et seulement sur les menus |
| `single_screen_home_glass_mode` | chaîne | Glass › Glass Blur | coupé : `Opaque` ; activé : `Blurred` ou clé supprimée |
| `focus_ring_animations_enabled` | booléen | Motion › Selection gradient animations | `true` / `false` |
| `glass_outline_enabled` | booléen | (accueil) | lu, sans effet : iiSU dessine le liseré partout |

Le dégradé de sélection passe par les teintes intermédiaires (turquoise → violet passe par le bleu, turquoise → saumon par le vert) ; le texte sélectionné d'iiSU en prend le milieu.

Mesures sur l'accueil : une case vide est un voile noir de 13 %, identique à 45 % et 90 % d'opacité ; la couleur de verre #4D7CFE teinte le fond d'environ 10 % (#F5F5F5 → #E4E8F2).

Polices : Console Sans (5 graisses) et Arctainium sont dans l'APK d'iiSU ; Wayfinder les lit depuis l'iiSU installé (`ui/IisuFonts.kt`), sans les copier.

## Champs relevés, non utilisés

- `background_skrim_opacity` (`0.25`).
- Glass › Advanced (verre des menus, surfaces d'affichage, panneaux) : pas encore relevé.
- Motion : `page_swipe_animations_enabled` et les autres réglages de Motion n'ont pas d'équivalent dans un panneau.

## Conséquences pour le plan

- `theme_mode = SYSTEM` existe : iiSU sait suivre le mode sombre d'Android. Wayfinder suit `theme_mode`, et retombe sur le mode d'Android pour `SYSTEM` ou en cas de lecture impossible.
- `IisuLook` perd `motionScale` et gagne `glassOutline: Boolean?`.
- Les exemples de test sont `app/src/test/resources/iisu/sample-light.pb` et `sample-dark.pb` : des extraits réels réduits aux champs ci-dessus.
- « Run script as Root » d'AYN envoie le script ligne par ligne : pas de variable ni de bloc sur plusieurs lignes dans `tools/iisu_probe.sh`.
