---
title: "Commands and Permissions"
sidebar_position: 3
---

Every EcoCrafting command and the permission node it requires.

## Commands

| Command                                                   | Description                                 | Permission                        |
|-----------------------------------------------------------|---------------------------------------------|-----------------------------------|
| `/ecocrafting`                                            | Opens the category browser GUI              | `ecocrafting.command.ecocrafting` |
| `/ecocrafting reload`                                     | Reloads the plugin                          | `ecocrafting.command.reload`      |
| `/ecocrafting lookup <item>`                              | Look up the recipe for any item             | `ecocrafting.command.lookup`      |
| `/ecocrafting open <category> [player]`                   | Open a specific category GUI                | `ecocrafting.command.open`        |
| `/ecocrafting open-trade <ids> [player]`                  | Open a villager trade GUI with no villager  | `ecocrafting.command.opentrade`   |
| `/ecocrafting debug`                                      | Debug the item held in main hand            | `ecocrafting.command.debug`       |
| `/ecocrafting validate`                                   | Validate all categories and report warnings | `ecocrafting.command.validate`    |
| `/ecocrafting list`                                       | List all loaded categories                  | `ecocrafting.command.list`        |
| `/ecocrafting create`                                     | Open the in-game recipe creator GUI         | `ecocrafting.admin.create`        |
| `/ecocrafting edit <recipe-id>`                           | Edit a recipe in the recipe creator GUI     | `ecocrafting.admin.edit`          |
| `/ecocrafting confirm`                                    | Confirm save in the recipe creator          | `ecocrafting.admin.create`        |
| `/ecocrafting cancel`                                     | Cancel save in the recipe creator           | `ecocrafting.admin.create`        |
| `/ecocrafting unlock <player> <recipe-id>`                | Unlock a locked recipe for a player         | `ecocrafting.admin`               |
| `/ecocrafting lock <player> <recipe-id>`                  | Lock a recipe for a player                  | `ecocrafting.admin`               |
| `/ecocrafting resetlimits <player\|global\|all> [recipe]` | Reset craft limits                          | `ecocrafting.admin`               |

**Alias:** `/ecocrafting` can also be run as `/rbook`.

## Stonecutter recipe IDs

A stonecutter recipe has one ID for the whole recipe and one ID for each output.

- **Whole recipe:** the file name, e.g. `stone_cuts`. `/ecocrafting unlock` and `/ecocrafting lock` act on every output at once, and `ecocrafting.recipe.stone_cuts` lets a player craft every output.
- **One output:** the file name, an underscore, then the output's `id`, e.g. `stone_cuts_stairs`. An output without an `id` uses its position in the list, counting from 0, e.g. `stone_cuts_0`. Use it to unlock or lock that output alone, for its own node `ecocrafting.recipe.stone_cuts_stairs`, and for `/ecocrafting resetlimits` and the craft limit placeholders (e.g. `%ecocrafting_limit_stone_cuts_stairs_remaining%`), which only accept output IDs.

An output's own unlock or lock wins over the whole recipe's. The `unlock_recipe` and `lock_recipe` effects and the `has_unlocked_recipe` condition take the same IDs.

## Additional Permissions

| Permission                 | Description                                                                               |
|----------------------------|-------------------------------------------------------------------------------------------|
| `ecocrafting.open.others`  | Required to open a category GUI or trade GUI for another player (the `<player>` argument) |
| `ecocrafting.limit.bypass` | Ignore craft limits. Crafts made with this are not counted                                |

<hr/>

## Where to go next

- **Build a recipe:** [How to Make a Recipe](how-to-make-a-recipe) to create recipes to unlock and lock.
- **Plugin config:** [Plugin Config](plugin-config) for the GUI and sound settings.
