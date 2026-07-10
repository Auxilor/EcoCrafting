---
title: "Commands and Permissions"
sidebar_position: 5
---

## Commands

| Command                                    | Description                                 | Permission                        |
|--------------------------------------------|---------------------------------------------|-----------------------------------|
| `/ecocrafting`                             | Opens the category browser GUI              | `EcoCrafting.command.ecocrafting` |
| `/ecocrafting reload`                      | Reloads the plugin                          | `EcoCrafting.command.reload`      |
| `/ecocrafting lookup <item>`               | Look up the recipe for any item             | `EcoCrafting.command.lookup`      |
| `/ecocrafting open <category> [player]`    | Open a specific category GUI                | `EcoCrafting.command.open`        |
| `/ecocrafting debug`                       | Debug the item held in main hand            | `EcoCrafting.command.debug`       |
| `/ecocrafting validate`                    | Validate all categories and report warnings | `EcoCrafting.command.validate`    |
| `/ecocrafting list`                        | List all loaded categories                  | `EcoCrafting.command.list`        |
| `/ecocrafting create`                      | Open the in-game recipe creator GUI         | `EcoCrafting.admin.create`        |
| `/ecocrafting confirm`                     | Confirm save in the recipe creator          | `EcoCrafting.admin.create`        |
| `/ecocrafting cancel`                      | Cancel save in the recipe creator           | `EcoCrafting.admin.create`        |
| `/ecocrafting unlock <player> <recipe-id>` | Unlock a locked recipe for a player         | `EcoCrafting.admin`               |
| `/ecocrafting lock <player> <recipe-id>`   | Lock a recipe for a player                  | `EcoCrafting.admin`               |

**Alias:** `/ecocrafting` can also be run as `/rbook`.

## Additional Permissions

| Permission                | Description                                                                                    |
|---------------------------|------------------------------------------------------------------------------------------------|
| `EcoCrafting.open.others` | Required to open a category GUI for another player via `/ecocrafting open <category> <player>` |
