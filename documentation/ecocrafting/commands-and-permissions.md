---
title: "Commands and Permissions"
sidebar_position: 3
---

Every EcoCrafting command and the permission node it requires.

## Commands

| Command                                    | Description                                 | Permission                        |
|--------------------------------------------|---------------------------------------------|-----------------------------------|
| `/ecocrafting`                             | Opens the category browser GUI              | `ecocrafting.command.ecocrafting` |
| `/ecocrafting reload`                      | Reloads the plugin                          | `ecocrafting.command.reload`      |
| `/ecocrafting lookup <item>`               | Look up the recipe for any item             | `ecocrafting.command.lookup`      |
| `/ecocrafting open <category> [player]`    | Open a specific category GUI                | `ecocrafting.command.open`        |
| `/ecocrafting open-trade <ids> [player]`   | Open a villager trade GUI with no villager  | `ecocrafting.command.opentrade`   |
| `/ecocrafting debug`                       | Debug the item held in main hand            | `ecocrafting.command.debug`       |
| `/ecocrafting validate`                    | Validate all categories and report warnings | `ecocrafting.command.validate`    |
| `/ecocrafting list`                        | List all loaded categories                  | `ecocrafting.command.list`        |
| `/ecocrafting create`                      | Open the in-game recipe creator GUI         | `ecocrafting.admin.create`        |
| `/ecocrafting confirm`                     | Confirm save in the recipe creator          | `ecocrafting.admin.create`        |
| `/ecocrafting cancel`                      | Cancel save in the recipe creator           | `ecocrafting.admin.create`        |
| `/ecocrafting unlock <player> <recipe-id>` | Unlock a locked recipe for a player         | `ecocrafting.admin`               |
| `/ecocrafting lock <player> <recipe-id>`   | Lock a recipe for a player                  | `ecocrafting.admin`               |

**Alias:** `/ecocrafting` can also be run as `/rbook`.

## Additional Permissions

| Permission                | Description                                                                                    |
|---------------------------|------------------------------------------------------------------------------------------------|
| `ecocrafting.open.others` | Required to open a category GUI or trade GUI for another player (the `<player>` argument)      |

<hr/>

## Where to go next

- **Build a recipe:** [How to Make a Recipe](how-to-make-a-recipe) to create recipes to unlock and lock.
- **Plugin config:** [Plugin Config](plugin-config) for the GUI and sound settings.
