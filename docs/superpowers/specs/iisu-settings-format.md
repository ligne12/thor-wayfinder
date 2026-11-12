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

| Nom | Type | Sens | Valeurs observées |
|---|---|---|---|
| `theme_mode` | chaîne | thème | `SYSTEM`, `DARK`, `LIGHT` |
| `focus_indicator_primary_color_argb` | entier | couleur d'accent (anneau de focus) | `0xFF74E3EE`, `0xFF699CA1` |
| `home_glass_opacity` | float | opacité du verre | absent, puis `0.45` |
| `home_glass_tint_color_argb` | entier | teinte du verre | absent, puis `0xFF4D7CFE` |
| `glass_outline_enabled` | booléen | liseré du verre | `false` |
| `launcher_font_choice` | chaîne | police | `cal_sans` |

## Champs relevés, non utilisés

- `single_screen_home_glass_mode` (`Blurred`), `background_skrim_opacity` (`0.25`), `custom_selection_gradient_enabled` (`true`).
- Motion : seulement deux booléens, `focus_ring_animations_enabled` et `page_swipe_animations_enabled`. Aucune durée : rien à reprendre pour la vitesse des panneaux.

## Conséquences pour le plan

- `theme_mode = SYSTEM` existe : iiSU sait suivre le mode sombre d'Android. Wayfinder suit `theme_mode`, et retombe sur le mode d'Android pour `SYSTEM` ou en cas de lecture impossible.
- `IisuLook` perd `motionScale` et gagne `glassOutline: Boolean?`.
- Les exemples de test sont `app/src/test/resources/iisu/sample-light.pb` et `sample-dark.pb` : des extraits réels réduits aux champs ci-dessus.
- « Run script as Root » d'AYN envoie le script ligne par ligne : pas de variable ni de bloc sur plusieurs lignes dans `tools/iisu_probe.sh`.
