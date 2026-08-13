---
title: "Plugin Config"
sidebar_position: 4
---

The plugin-wide settings live in `config.yml` in the EcoCrafting data folder (`/plugins/EcoCrafting/config.yml`). It controls the recipe GUIs, sounds, and other plugin-wide behavior. Edit it, then run `/ecocrafting reload` to apply changes.

## Default config.yml

```yaml
debug: false

# Force local storage even when eco uses a database; disables cross-server sync
use-local-storage: false

# Exclude rotated/mirrored duplicates of shaped crafting recipes
deduplicate-symmetrical: false

# When true, items with no recipe are still shown in categories
show-items-without-recipes: false

# When true, recipe lookup matches full item (name/lore/meta), not just base material
strict-item-matching: true

# ItemFlags applied to every display item in EcoCrafting GUIs
# Valid values: HIDE_ATTRIBUTES, HIDE_ENCHANTS, HIDE_UNBREAKABLE, HIDE_DESTROYS,
#               HIDE_PLACED_ON, HIDE_POTION_EFFECTS, HIDE_DYE, HIDE_ARMOR_TRIM
item-flags: []

fuel-slot: # Item shown in the fuel slot of furnace/smoker/blast furnace GUIs (display only)
  item: charcoal name:"&7Fuel"
  lore: []

# Workstation markers shown on the right side of the recipe GUI.
# Pattern marker keys:
#   i = ingredient slot   o = output slot   u = fuel slot (display)
#   C = crafting table    F = furnace        B = blast furnace
#   S = smoker            P = campfire       M = smithing table
#   T = stonecutter       W = brewing stand  G = grindstone
#   A = anvil             V = villager
workstation-markers:
  crafting_table:
    active: crafting_table name:"&aCrafting Table" glint
    inactive: crafting_table name:"&7Crafting Table"
    not-applicable: gray_dye name:"&8%workstation%"
    lore:
      active: []
      inactive:
        - "&bClick to view %workstation% recipe"
      not-applicable:
        - "&7No %workstation% recipe available"
  # ... (all workstation types follow the same structure)

# How to identify the player who triggered a block-based recipe effect
# placed: the player who placed/first opened the block
# nearest: the nearest online player within owner-nearest-radius blocks
owner-mode: placed
owner-nearest-radius: 32

# When true, effect recipes in brewing stands fire once per matched base slot
brewing-stand:
  effect-recipes-per-slot: true

# Scan all loaded villagers on reload to remove PDC keys for disabled recipes
villager-scan-on-reload: true

# Title of the trade GUI opened by /ecocrafting open-trade
trade-gui:
  title: "&8Trades"

#*/ Category Browser GUI /*#
category-browser-gui:
  custom-slots: []
  title: "&8EcoCrafting | Page &6%page%&8/&6%max_page%"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "100000001"
      - "100000001"
      - "100000001"
      - "111111111"
  buttons:
    next-page:
      item:
        active: orange_stained_glass_pane name:"&aNext page"
        inactive: black_stained_glass_pane name:""
      row: 5
      column: 6
    prev-page:
      item:
        active: orange_stained_glass_pane name:"&aPrevious page"
        inactive: black_stained_glass_pane name:""
      row: 5
      column: 4
    close:
      item: barrier name:"&cClose"
      lore: []
      row: 5
      column: 5

#*/ EcoShop Integration /*#
shop-integration:
  enabled: true # Enable EcoShop buy-missing-materials feature
  show-prices: true # Show shop prices in QuickCraft lore for missing materials
  auto-buy-missing-materials: false
  require-shift-click: true

#*/ Recipe GUI (Crafting Table) /*#
craft-gui:
  title: "&8Crafting Recipe"
  quick-craft-enabled: true
  buy-materials-enabled: true
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "1111111CF"
      - "1iii111BS"
      - "1iii1o1PW"
      - "1iii111MT"
      - "1111111GA"
      - "11111111V"
  buttons:
    recipe-parts-lore:
      - ""
      - "&bLeft Click&f to see the recipe"
      - ""
    back:
      row: 6
      column: 5
      item: barrier name:"&cBack"
      lore: []
    quick-craft:
      row: 6
      column: 1
      item: crafting_table name:"&aQuick Craft"
      lore:
        - ""
        - "&7Materials:"
        - "%materials%"
        - ""
        - "&bLeft Click&f to craft"
        - ""
    prev-variant:
      row: 6
      column: 4
      item:
        active: arrow name:"&aPrevious Method"
        inactive: black_stained_glass_pane name:""
      lore: []
    next-variant:
      row: 6
      column: 6
      item:
        active: arrow name:"&aNext Method"
        inactive: black_stained_glass_pane name:""
      lore: []
    crafter-indicator: # Shows whether recipe is available in the Crafter block
      enabled: false
      row: 5
      column: 7
      item:
        active: crafter name:"&aAvailable in Crafter"
        inactive: gray_dye name:"&7Not available in Crafter"
    shapeless-indicator: # Shows whether recipe is shaped or shapeless
      enabled: false
      row: 5
      column: 7
      item:
        active: slime_ball name:"&aShapeless Recipe"
        inactive: gray_dye name:"&7Shaped Recipe"
  custom-slots: []

# furnace-gui, blast-furnace-gui, smoker-gui, campfire-gui,
# smithing-gui, stonecutter-gui, brewing-gui, grindstone-gui,
# anvil-gui, villager-gui follow the same structure.

#*/ Sound Configuration /*#
sounds:
  next-page:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  prev-page:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  workstation-switch:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  back:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  close:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  slot-click:
    sound: ui.button_click
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  quick-craft-success:
    sound: entity.player.levelup
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  quick-craft-fail:
    sound: entity.villager.no
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  purchase-success:
    sound: entity.experience_orb.pickup
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
  purchase-fail:
    sound: entity.villager.no
    pitch: 1.0
    volume: 1.0
    enabled: true
    category: MASTER
```

<hr/>

## Where to go next

- **Build a recipe:** [How to Make a Recipe](how-to-make-a-recipe) covers the per-recipe config files.
- **Build a category:** [How to Make a Category](how-to-make-a-category) covers the per-category config files.
- **Commands:** [Commands and Permissions](commands-and-permissions) for the reload and admin commands.
