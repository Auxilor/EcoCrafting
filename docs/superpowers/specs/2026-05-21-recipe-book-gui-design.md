# Recipe Book GUI — Custom Recipe Integration Design

**Date:** 2026-05-21
**Branch:** feat/custom-crafting
**Status:** Approved

---

## Overview

Wire custom recipes (defined in `recipes/` yml files) into the recipe book GUI so players can browse them by category, view workstation-specific layouts, and paginate through alternative crafting methods for the same output item.

---

## Flow

```
Open recipe book
  → click category
    → see output items (includes custom recipe outputs)
      → click item
        → RecipeGUI opens (workstation layout)
          → prev/next buttons to cycle alternative workstations
```

---

## Section 1: Category Registration

### Recipe config change

Recipe ymls gain one optional field:

```yaml
category: combat   # must match an existing RecipeCategory id
```

### Data model

`RecipeBookMeta` gains `categoryId: String?`.

`CustomRecipeLoader.parseMeta()` reads `category` from config (blank/absent → `null`).

### Runtime injection

`RecipeCategory` gains:

```kotlin
private val runtimeItems = mutableListOf<ItemStack>()

fun registerCustomRecipe(item: ItemStack) {
    runtimeItems += item
}
```

`getMemberItemsRecipes()` merges `items` (config-declared) + `runtimeItems` (injected at load).

`runtimeItems` starts empty on every new `RecipeCategory` instance. Since eco recreates category instances from config on each reload, no explicit clear is needed — `afterReload()` always injects into fresh instances.

### Load-time wiring

`CustomRecipeLoader.afterReload()` iterates `CustomRecipes.allKeys()`, resolves the output `ItemStack` for each key via `WorkstationRecipes`, reads `getMeta(key).categoryId`, and calls `RecipeCategories.getById(categoryId)?.registerCustomRecipe(output)`.

Unknown/null category IDs are silently skipped (logged at debug level).

---

## Section 2: RecipeGUI Pagination

### ResolvedRecipe ordering

`RecipeResolver` gains:

```kotlin
fun resolveAll(itemStack: ItemStack): List<ResolvedRecipe>
```

Same resolution chain as `resolve()` but collects all matches. Results sorted ascending by `displayType.name` (alphabetical). This determines the default-open recipe (index 0 = first alphabetically).

### RecipeGUI constructor

```kotlin
class RecipeGUI(
    val stack: ItemStack,
    val alternatives: List<ResolvedRecipe> = emptyList(),
    val altIndex: Int = 0
)
```

`open()` uses `alternatives.getOrNull(altIndex)` as the active recipe, falling back to `RecipeResolver.resolve(stack)` when alternatives is empty (backward-compatible call sites).

### Pagination buttons

Each workstation GUI section in `config.yml` gains optional `buttons.prev-variant` and `buttons.next-variant` subsections (same shape as existing prev/next-page buttons: `row`, `column`, `item.active`, `item.inactive`, `lore`, `click_sound`).

Buttons only render when `alternatives.size > 1`. Clicking calls:

```kotlin
RecipeGUI(stack, alternatives, altIndex ± 1).open(player, parent)
```

Index is clamped (no wrap-around).

### Entry point change

`ItemCategoryGUI.slot()` changes from:

```kotlin
RecipeGUI(item).open(player, menu)
```

to:

```kotlin
val alts = RecipeResolver.resolveAll(item)
RecipeGUI(item, alts, 0).open(player, menu)
```

---

## Section 3: Per-workstation Quick-Craft & Buy-Materials Flags

Each workstation GUI section in `config.yml` gains two boolean flags:

```yaml
craft-gui:
  quick-craft-enabled: true
  buy-materials-enabled: true
```

Default for both: `true` (backward-compatible — existing servers without these keys behave as before).

`RecipeGUI.open()` already holds `config` for the active workstation section. Rendering is gated:

```kotlin
config.getSubsectionOrNull("buttons.quick-craft")
    ?.takeIf { config.getBool("quick-craft-enabled", true) }
    ?.let { /* render */ }

config.getSubsectionOrNull("buttons.purchase-ingredients")
    ?.takeIf { config.getBool("buy-materials-enabled", true) }
    ?.let { /* render */ }
```

The two flags are independent. `quick-craft-enabled: false` with `buy-materials-enabled: true` shows only the purchase button — players can still buy missing materials without the one-click craft.

Global `ShopIntegration` settings (`auto-buy`, `show-prices`, `require-shift-click`) are unchanged — they are integration-level, not per-workstation.

---

## Section 4: Slot Differentiation via Pattern Markers

The existing pattern letter system is extended. Full marker table:

| Marker | Workstation | Slot meaning |
|--------|-------------|--------------|
| `i` | crafting / smelting / stonecutter / crafter | ingredient (increments l→r, t→b) |
| `o` | all | output |
| `t` | smithing | template |
| `b` | smithing | base |
| `a` | smithing | addition |
| `g` | brewing | ingredient (top slot) |
| `s` | brewing | base slot |
| `j` | grindstone / villager | second input |
| `m` | anvil | material |
| `f` | smelting | fuel (display only, no interaction) |

`RecipeGUI.open()` extends its pattern-char dispatch to handle all markers. Display items are pulled from `recipe.ingredients` (already packed into known indices by `RecipeResolver` — no `ResolvedRecipe` shape change needed).

Admins may freely reposition any marker within the pattern. Marker semantics are documented via comments in the default `config.yml`.

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| `category:` references unknown id | Skipped, debug log |
| `resolveAll()` returns empty list | Falls back to single `resolve()`, no pagination rendered |
| `altIndex` out of bounds | Clamped to valid range |
| Unknown pattern marker char | Ignored (existing behaviour) |

---

## What is NOT changing

- Category yml config shape — no new fields required
- `ResolvedRecipe` data class fields
- `ShopIntegration` global settings
- Any existing call site that constructs `RecipeGUI(item)` without alternatives continues to work
