---
title: How to make a Recipe
sidebar_position: 1
---

## Recipes

Each recipe is a YAML file in `recipes/<workstation>/`. The file name (without `.yml`) is the recipe ID — lowercase letters, numbers, and underscores only. An `_example.yml` is provided in each folder.

| Folder | Workstation |
|---|---|
| `recipes/crafting_table/` | Crafting Table |
| `recipes/furnace/` | Furnace |
| `recipes/blast_furnace/` | Blast Furnace |
| `recipes/smoker/` | Smoker |
| `recipes/campfire/` | Campfire |
| `recipes/smithing_table/` | Smithing Table |
| `recipes/stonecutter/` | Stonecutter |
| `recipes/brewing_stand/` | Brewing Stand |
| `recipes/grindstone/` | Grindstone |
| `recipes/anvil/` | Anvil |
| `recipes/villager/` | Villager Trade |

---

## Common Config

These fields exist on **every** recipe type.

```yaml
enabled: true
# category: main # optional — category ID to assign this recipe to in the GUI

type: crafting_table # workstation type (see table above)

output: netherite_sword name:"&6Example Sword" # eco item lookup format
lore: [] # optional extra lore on the output item

give-result-item: true # false = no item given; fire libreforge effects instead
# effects:
#   - id: give_xp
#     args:
#       xp: 100
# conditions: []

permission: "" # permission node required to craft; blank = no restriction

locked-by-default: false # hide recipe until unlocked per-player
show-when-locked: false # if true, show recipe in GUI even when locked
locked-lore:
  - "&cUnlock this recipe to craft it."

visibility-conditions: [] # libreforge — hide from GUI if not met
crafting-conditions: [] # libreforge — block crafting if not met
unlock-conditions: [] # auto-unlock on join if met
```

Items use [eco item lookup format](https://plugins.auxilor.io/the-item-lookup-system). Conditions use [libreforge conditions](https://plugins.auxilor.io/effects/configuring-a-condition).

---

## Workstation Config

Each section below shows only the fields unique to that workstation. All common fields above apply too.

### Crafting Table

```yaml
type: crafting_table

# 3x3 grid, left-to-right top-to-bottom. Empty slots = "".
recipe:
  - diamond
  - diamond
  - diamond
  - diamond
  - netherite_ingot
  - diamond
  - diamond
  - diamond
  - diamond

shapeless: false # true = any slot order
symmetry: false # true = register all rotations/mirrors automatically
support-crafter: false # true = also fires in the vanilla Crafter block
```

### Furnace

```yaml
type: furnace

input: raw_iron # item placed in the smelting slot
cook-time: 200 # ticks (200 = 10 seconds, vanilla default)
experience: 0.7 # XP orbs dropped when result is taken
```

### Blast Furnace

```yaml
type: blast_furnace

input: raw_gold
cook-time: 100 # ticks (blast furnace is 2× faster; vanilla = 100)
experience: 1.0
```

### Smoker

```yaml
type: smoker

input: potato
cook-time: 100 # ticks (smoker is 2× faster for food)
experience: 0.35
```

### Campfire

```yaml
type: campfire

input: rabbit
cook-time: 600 # ticks (campfire is slow; vanilla = 600)
experience: 0.35
```

### Smithing Table

```yaml
type: smithing_table

template: netherite_upgrade_smithing_template
base: diamond_sword
addition: netherite_ingot
```

### Stonecutter

```yaml
type: stonecutter

input: stone

# Multiple outputs supported — each appears as a separate option in the UI.
outputs:
  - item: stone_slab
    lore: []
    give-result-item: true

  - item: stone_stairs
    lore:
      - "&7Crafted from stone"
    give-result-item: true

  # give-result-item: false on an output fires effects instead of giving the item
  - item: stone_brick_wall
    lore: []
    give-result-item: false
    effects:
      - id: give_xp
        args:
          xp: 25
    conditions: []
```

### Brewing Stand

```yaml
type: brewing_stand

base: glass_bottle # item in the bottle slots (can be an existing potion)
ingredient: nether_wart # item in the ingredient slot (top)
brew-time: 20 # ticks to brew (20 = 1 second)
```

### Grindstone

```yaml
type: grindstone

item1: enchanted_book # left slot (required)
item2: enchanted_book # right slot (optional — omit for single-item recipes)
```

### Anvil

```yaml
type: anvil

base: iron_sword # item in the left slot (required)
material: diamond # item in the right slot (optional — omit for rename-only)
result-name: "&bDiamond-Edged Sword" # optional — renames the result
repair-cost: 3 # levels shown and consumed
```

### Villager Trade

```yaml
type: villager

input1: emerald # first trade ingredient (required)
input2: book # second trade ingredient (optional)

profession: FARMER # optional — restrict to this profession
min-level: 1 # optional — minimum villager level (1–5; 0 = any)
chance: 1.0 # probability this trade appears on a villager (0.0–1.0)
wandering-trader: false # true = inject into WanderingTrader instead
villager-xp: 0 # XP awarded to the villager on trade completion
```
