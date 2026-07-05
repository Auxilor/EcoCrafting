# EcoCrafting

EcoCrafting is a Paper/eco recipe viewer and quick-craft helper for modern Minecraft servers.

## Target stack

- Java 21
- Paper / AdvancedSlimePaper 1.21.8+
- eco 7.x, tested against 7.6.0
- Optional: EcoShop 2.5.x

## Build

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew clean build
```

Install the shaded jar:

```text
build/libs/EcoCrafting.jar
```

## Commands

```text
/rbook
/rbook open <category> [player]
/rbook lookup <item>
/rbook debug
/rbook validate
/rbook list
/rbook reload
```

## Default categories

EcoCrafting ships with vanilla-only examples so it can boot on any server that has eco:

- `main.yml` - category hub
- `farming.yml` - food/crop recipes
- `mining.yml` - ore blocks/tools/workstations
- `combat.yml` - weapons/armor/ammo
- `utility.yml` - workstations/storage/redstone basics

## Adding EcoItems / EcoArmor / other eco items

Add the item ID to a category file under `plugins/EcoCrafting/categories/`:

```yaml
items:
  - item: "ecoitems:enchanted_wheat"
    display-no-perm: true
    no-perm-item:
      item: gray_dye
      name: "&c???"
      lore:
        - ""
        - "&7Unlock this recipe through progression."
```

Examples:

```yaml
  - item: "ecoitems:my_custom_sword"
    display-no-perm: false

  - item: "ecoarmor:mythic_helmet"
    display-no-perm: false
```

Use `/rbook validate` after edits to find missing category links, bad GUI masks, and unresolved recipes.

## Quick Craft

Quick Craft is inventory-safe:

- Builds a material consumption plan before removing anything.
- Matches custom items by matcher/similarity rather than blindly by material.
- Refuses to craft if the output will not fit.
- Clicking a recipe ingredient opens that ingredient's recipe when one exists.

## EcoShop integration

Config:

```yaml
shop-integration:
  enabled: true
  show-prices: true
  auto-buy-missing-materials: false
  require-shift-click: true
```

Recommended production setting is `auto-buy-missing-materials: false` until you have tested your shop pricing.

When enabled, shift-clicking Quick Craft can buy missing materials from EcoShop and then craft. The integration is isolated in `ShopIntegration.kt` so EcoShop API changes only require patching one file.

## Maintenance notes

EcoCrafting keeps fast-moving integrations isolated:

- `recipe/RecipeResolver.kt` owns Bukkit + eco recipe lookup.
- `craft/QuickCraftService.kt` owns inventory matching and mutation.
- `integration/ShopIntegration.kt` owns EcoShop integration boundaries.

When eco updates, start by checking `RecipeResolver.kt`, especially the fallback reflection used to list eco recipes.
