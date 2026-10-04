# Shareware HUD (Fabric, Minecraft 26.3)

Brings back the HUD from the 2019 April Fools' version **Java Edition 3D Shareware v1.34**
("MineCraft 3D: Memory Block Edition").

```
+--------+--------+-------+------+-----------+
|  100%  |   45%  | (you) | beef | [1][2][3] |
|        |        |       |      | [4][5][6] |
| HEALTH | ARMOR  |       | [OH] | [7][8][9] |
+--------+--------+-------+------+-----------+
```

- **3x3 hotbar** — slot 1 top-left through slot 9 bottom-right; keys 1–9 and scrolling work as normal.
- **Player portrait** — your live player model (skin, armour, held item, hurt flash), head glancing left and right at random.
- **Compact stats** — health and armour as percentages. Absorption turns the health number gold.
- **Hunger** — raw beef on a bone; the beef drains as you get hungry. Creative shows only the bone and empty stat boxes, like the original.
- Offhand slot under the beef, air bubbles above the hotbar block.
- XP level and progress shown compactly in the panel (or the vanilla bar, or hidden like the original). The horse jump bar is drawn at the top of the screen.
- Held-item name, action-bar messages and mount health are moved up above the panel.

## Requirements
Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3, Java 25. Client-side only.

## Building
JDK 25 required.

```
./gradlew build
```
The jar is in `build/libs/shareware-hud-1.1.0.jar`. Put it in `.minecraft/mods` alongside Fabric API.

No local JDK? Push this folder to a GitHub repo — the included workflow builds the jar and
uploads it as an artifact on the Actions tab.

To test in a dev client: `./gradlew runClient`.

## Settings
With Mod Menu installed: **Mods → Shareware HUD → Configure**. Changes apply straight away and
save when you press Done. Without Mod Menu, edit `config/sharewarehud.json` and restart.

| Setting | Key | Default | What it does |
|---|---|---|---|
| HUD size | `hudScale` | `1.0` | Panel size relative to your GUI scale. `1.0` = same as GUI scale; go down to `0.25` to shrink it. |
| HUD opacity | `hudOpacity` | `1.0` | Fades the numbers, labels, icons, XP and selection box. Items and the portrait can't be faded by the game's GUI renderer, so they stay solid (and hide at 0%). |
| Background opacity | `backgroundOpacity` | `1.0` | Fades the grey frame, section boxes and slot backgrounds. 0% leaves just the contents floating. |
| Experience | `experienceStyle` | `PANEL` | `PANEL`: green level number and a mini bar in the beef column. `VANILLA`: normal XP bar above the panel (also brings back the locator bar). `HIDDEN`: no XP, like the original. |
| Portrait zoom | `portraitScale` | `42` | Size of the player in the portrait box. |
| Portrait framing | `portraitYOffset` | `0.55` | Raise to show more head, lower to show more body. |
| Portrait looks around | `portraitLooksAround` | `true` | Random head glances. |
| Item counts & durability | `showItemDecorations` | `true` | Stack counts and durability bars on hotbar items. |

## How it works
No mixins. It uses Fabric API's `HudElementRegistry` to replace the vanilla `HOTBAR` element with
the panel, blank out the vanilla health/armour/food/air bars, and translate the elements that sit
above the hotbar.
