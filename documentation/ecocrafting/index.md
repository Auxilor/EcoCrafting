---
title: "EcoCrafting"
---

## What is EcoCrafting?

EcoCrafting is a browseable in-game recipe book for Minecraft servers. Players can look up crafting recipes for
any item across all vanilla workstations — crafting table, furnace, blast furnace, smoker, campfire, smithing table,
stonecutter, brewing stand, grindstone, anvil, and villager trades.

## Features

- **Recipe browser GUI** — navigate recipes by category or look up any item directly
- **All workstation types** — supports all vanilla crafting stations with a workstation switcher in the recipe view
- **Custom recipes** — define your own recipes for any workstation using YAML config files
- **Quick Craft** — craft items directly from the recipe GUI if you have the required materials
- **Recipe locking** — hide recipes until unlocked per-player via command or unlock conditions
- **In-game recipe creator** — create custom recipes without editing files using the admin GUI
- **EcoShop integration** — buy missing materials and craft in one click
- **libreforge conditions** — restrict recipe visibility and crafting with conditions

## How does it work?

Recipes are stored as YAML files under `recipes/<workstation>/`. Categories group items together and appear in the
category browser GUI. Categories are configured under `categories/`.

Players open the recipe book with `/ecocrafting` (alias `/rbook`).
