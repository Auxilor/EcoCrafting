---
title: How to make a Category
sidebar_position: 2
---

## Categories

Categories group items together in the recipe browser GUI. Each category appears as a clickable icon in the main category browser. There are two category types: `items` (a flat list of items) and `categories` (a nested group of sub-categories).

## How to add categories

Each category is its own YAML config file, placed in the `/categories/` folder. The ID of the category is the file name (without `.yml`). IDs must be lowercase letters, numbers, and underscores only.

## Example Items Category Config

```yaml
type: "items"

icon:
  item: diamond_sword name:"&cCombat"
  lore:
    - ""
    - "&7Weapons, armor, arrows, and combat utilities."
    - ""
    - "&bLeft Click &fto browse recipes"
    - ""

position:
  column: 6
  row: 3
  page: 1

items:
  - item: "#minecraft:swords"
    display-no-perm: false
  - item: "bow"
    display-no-perm: false
  - item: "shield"
    display-no-perm: false

gui:
  custom-slots: []
  title: "&8Combat Recipes | Page &6%page%"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "111111111"
  buttons:
    back:
      row: 6
      column: 5
      item: barrier name:"&cBack"
      lore: []
    next-page:
      item:
        active: orange_stained_glass_pane name:"&aNext page"
        inactive: black_stained_glass_pane name:""
      lore:
        active: []
        inactive: []
      row: 6
      column: 6
    prev-page:
      item:
        active: orange_stained_glass_pane name:"&aPrevious page"
        inactive: black_stained_glass_pane name:""
      lore:
        active: []
        inactive: []
      row: 6
      column: 4
    slot:
      lore:
        - ""
        - "&bLeft Click&f to see the recipe"
        - ""
```

## Understanding all the sections

### The Category Type

```yaml
type: "items"       # Flat list of items — most common type
# type: "categories" # A group of sub-categories (nested browser)
```

### The Icon Section

```yaml
icon:
  item: diamond_sword name:"&cCombat"  # Icon shown in the category browser GUI
  lore:
    - "&7Description shown to players"
```

Uses [eco item lookup format](https://plugins.auxilor.io/the-item-lookup-system).

### The Position Section

```yaml
position:
  column: 6  # Column in the category browser GUI (1-9)
  row: 3     # Row in the category browser GUI (1-5)
  page: 1    # Page in the category browser GUI
```

### The Items Section (type: items)

```yaml
items:
  - item: "diamond_sword"          # Exact item
    display-no-perm: false         # If true, show item in GUI even if player lacks recipe permission
  - item: "#minecraft:swords"      # Item tag — expands to all matching items
    display-no-perm: false
  - item: "ecoitems:my_item"       # Custom eco item
    display-no-perm: false
```

Items use [eco item lookup format](https://plugins.auxilor.io/the-item-lookup-system). Tags prefixed with `#` are expanded automatically.

### The GUI Section

```yaml
gui:
  title: "&8My Category | Page &6%page%"
  mask:
    items:
      - black_stained_glass_pane    # Material list (indexed 1, 2, 3...)
    pattern:
      - "111111111"                 # 9 chars per row, number = material index, i = item slot
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "1iiiiiii1"
      - "111111111"
  buttons:
    back:
      row: 6
      column: 5
      item: barrier name:"&cBack"
      lore: []
    next-page:
      item:
        active: orange_stained_glass_pane name:"&aNext page"
        inactive: black_stained_glass_pane name:""
      row: 6
      column: 6
    prev-page:
      item:
        active: orange_stained_glass_pane name:"&aPrevious page"
        inactive: black_stained_glass_pane name:""
      row: 6
      column: 4
    slot:
      lore:
        - ""
        - "&bLeft Click&f to see the recipe"
        - ""
  custom-slots: []
```

The pattern uses `i` for item slots (auto-filled left-to-right, top-to-bottom) and numbers for glass/decorative panes.

<hr/>

## Default configs

The default category configs can be found in `eco-core/core-plugin/src/main/resources/categories/`.
