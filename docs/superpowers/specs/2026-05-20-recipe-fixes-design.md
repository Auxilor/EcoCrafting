# Recipe Fixes & Owner Tracking Redesign

**Date:** 2026-05-20  
**Branch:** feat/custom-crafting

---

## Overview

Four independent subsystems, each self-contained with clear interfaces.

1. Grindstone + Brewing packet bypass (client-side slot restrictions)
2. Smithing + Stonecutter ghost bug fix
3. Campfire fix + Brewing ghost-per-slot
4. Block PDC owner tracking redesign + Villager chance persistence

---

## Subsystem 1: Grindstone + Brewing Packet Bypass

### Problem

**Grindstone:** Client-side prediction ignores server's `PrepareGrindstoneEvent` result for
non-enchanted inputs. Client renders result slot as empty; player cannot take a result even
though the server has one set.

**Brewing Stand:** `BrewingStandMenu` slot 3 (`mayPlace`) rejects non-vanilla-potion
ingredients on both client and server sides. Custom ingredients cannot be placed in the
ingredient slot at all.

### Approach: eco PacketListener (no external deps)

Eco's built-in `PacketListener` interface provides `onSend(PacketEvent)` and
`onReceive(PacketEvent)`. Handle is raw NMS — cast to the required packet class.
Register via `eventManager.registerPacketListener(listener)` from `RecipeBookPlugin`.

### Grindstone Design

**New file:** `custom/packet/GrindstonePacketListener.kt`

Implements `PacketListener`. Registered in `RecipeBookPlugin.handleEnable()`.

**`onSend` — force result visibility:**
- Cast handle to `ClientboundContainerSetSlotPacket`
- Check if `containerId` matches the player's currently-open grindstone container
- If slot == 2 (result) and item is non-null: already handled by `PrepareGrindstoneEvent` —
  pass through untouched
- If `PrepareGrindstoneEvent` sets a result but the client doesn't show it, send an additional
  `ClientboundContainerSetSlotPacket` for slot 2 immediately after the `PrepareGrindstoneEvent`
  fires via a Bukkit scheduler `runTask`. This forces a client-side update independent of the
  client's prediction.

**`onPrepareGrindstone` update:**
After setting `event.result`, schedule `runTask { player.updateInventory() }` to force the
client slot packet to be re-sent. `updateInventory()` re-sends all container slots, which
overrides the client prediction.

> **Key insight:** `Player.updateInventory()` triggers `ClientboundContainerSetContentPacket`
> for all slots, which the client applies regardless of local prediction. This is the cleanest
> approach — no NMS packet construction needed.

### Brewing Stand Design

**New file:** `custom/packet/BrewingPacketListener.kt`

Implements `PacketListener`. Intercepts `ServerboundContainerClickPacket` on `onReceive`.

Logic:
1. Cast handle to `ServerboundContainerClickPacket`
2. Check if player's open inventory is `InventoryType.BREWING`
3. Check if `slotNum == 3` (ingredient slot) and `clickType` is PICKUP or QUICK_MOVE
4. Extract the item being placed (from `carriedItem` or player cursor)
5. If item matches any `CustomRecipe.Brewing` ingredient:
   - Cancel the packet (`event.isCancelled = true`)
   - `Bukkit.getScheduler().runTask` → call `brewer.ingredient = item` directly (bypasses
     `mayPlace` slot restriction)
   - The brewing stand then processes normally; existing `BrewEvent` handler fires when done

**Edge case:** if packet is cancelled, client may show a "ghost" item. Fix: after setting the
slot via API, call `player.updateInventory()` on the next tick to sync client.

**Ingredient slot validation note:** `Inventory.setItem(slot, item)` bypasses Paper's slot
filter. This is intentional — we are taking ownership of validation.

---

## Subsystem 2: Smithing + Stonecutter Ghost Bug Fix

### Root Cause

`handleSmithing` and `handleStonecutter` call `CustomRecipes.getByKey(recipeKey)`. If the
lookup returns null (key mismatch between registered key and `event.recipe.key` at runtime),
the method returns early without cancelling `CraftItemEvent`. Result: vanilla gives the item,
no custom effects fire.

### Fix

**In `handleSmithing`:**
- Add `recipeBookPlugin.debug("[Ghost] smithing key=${recipeKey}")` at entry
- Add fallback: if `getByKey(recipeKey)` is null, try `getByOutput(event.recipe.result)`
  cast to `CustomRecipe.Smithing`
- If still null: return (expected for non-custom recipes)

**In `handleStonecutter`:**
- Add `recipeBookPlugin.debug("[Ghost] stonecutter key=${recipeKey}")` at entry
- `parseStonecutterKey` already extracts base ID + index; add debug log of both
- If `CustomRecipes.getByKey(baseKey)` is null, log a warning: `"[RecipeBook] stonecutter
  event fired for key $recipeKey but no matching recipe found"` — this surfaces misconfiguration
- No fallback needed for stonecutter (output-based lookup is ambiguous for multi-output recipes)

### Ghost Result Delivery Verification

After cancelling `CraftItemEvent` for smithing/stonecutter:
- Smithing: after consuming slots, call `player.updateInventory()` to sync client
- Stonecutter: same

This prevents ghost items in the client UI when the event is cancelled.

---

## Subsystem 3: Campfire Fix + Brewing Ghost Per Slot

### Campfire "Does Nothing" Fix

**Root cause:** `FurnaceOwnerTracker` tracks `InventoryOpenEvent` for
`FURNACE`/`BLAST_FURNACE`/`SMOKER`. Campfire has no openable inventory — players right-click
to add items but no `InventoryOpenEvent` fires. So `FurnaceOwnerTracker.getOwner(loc)` always
returns null for campfire locations. The `BlockCookEvent` handler then hits the early-return
null branch and fires no effects.

**Fix:** Campfire owner is tracked via the new `BlockOwnerTracker` in Subsystem 4
(PDC on place or first interaction). The `onCampfire` handler uses `BlockOwnerTracker.getOwner`
instead of `FurnaceOwnerTracker.getOwner`.

If campfire has no PDC owner and config mode is `nearest`: find nearest online player within
16 blocks. If no player nearby: cancel the cook event if recipe is ghost (prevent vanilla item
delivery), skip effects.

### Brewing Ghost Per Slot

**Current:** `matchedSlots.forEach { brewer.setItem(it, null) }` then single
`fireGhostEffects(...)` call.

**New:** Loop over `matchedSlots`, fire `fireGhostEffects` once per slot:

```kotlin
matchedSlots.forEach { slot ->
    brewer.setItem(slot, null)
    val ce = CustomBrewEvent(player, recipe, item.clone(), loc, 1)
    Bukkit.getPluginManager().callEvent(ce)
    if (!ce.isCancelled) fireGhostEffects(player, recipe, item.clone(), 1)
}
```

**Config:** `brewing-stand.ghost-per-slot: true` (default `true`). When `false`, reverts to
original single-fire behavior. Add to `config.yml`.

---

## Subsystem 4: Block PDC Owner Tracking + Villager Persistence

### Block Owner Tracker Redesign

**Replace** `FurnaceOwnerTracker`, `BrewingOwnerTracker`, `CrafterOwnerTracker` with a single
`BlockOwnerTracker` object.

**New file:** `custom/BlockOwnerTracker.kt`

```
object BlockOwnerTracker : Listener {
    private val PDC_KEY = NamespacedKey("recipebook", "owner")
    
    // Set owner in block PDC
    fun setOwner(block: Block, player: Player)
    
    // Get owner per config mode (placed | nearest)
    fun getOwner(location: Location): Player?
    
    // Events:
    @EventHandler BlockPlaceEvent -> setOwner if supported block type
    @EventHandler InventoryOpenEvent -> setOwner if no existing PDC owner (first-open fallback)
    @EventHandler BlockBreakEvent -> clear PDC (via block state)
}
```

**Supported block types:** `FURNACE`, `BLAST_FURNACE`, `SMOKER`, `BREWING_STAND`, `CRAFTER`,
`CAMPFIRE`, `SOUL_CAMPFIRE`

**PDC storage:** `block.state` as `TileState` → `persistentDataContainer.set(PDC_KEY, PersistentDataType.STRING, uuid.toString())`
Read back with `UUID.fromString(pdc.get(PDC_KEY, PersistentDataType.STRING))`. Uses Paper's
built-in STRING type — no custom `PersistentDataType` needed.

**Owner resolution (`getOwner`):**
```
config "owner-mode: placed" (default):
  → read PDC UUID, find online player, return or null
  
config "owner-mode: nearest":
  → find nearest online player within configurable radius (default 32 blocks)
  → PDC UUID ignored
```

**Config additions to `config.yml`:**
```yaml
owner-mode: placed          # placed | nearest
owner-nearest-radius: 32    # only used when owner-mode: nearest
```

**Migration:** Existing in-memory state from old trackers is lost on reload (acceptable — these
are ephemeral sessions). Old tracker objects can be removed from `RecipeBookPlugin.handleEnable`.

### Villager Chance Persistence

**PDC key pattern:** `recipebook:vr_<recipe-id>` on the `AbstractVillager` entity.
**Value type:** `PersistentDataType.BOOLEAN`

**`onVillagerOpen` changes:**
```
for each VillagerRecipe (filtered by profession/level/type):
    key = "recipebook:vr_${vr.key.key}"
    if PDC has key:
        include = PDC.get(key) == true
    else:
        include = Math.random() <= vr.chance
        PDC.set(key, include)
    
    if include: add to merchant recipe list (if not already present)
```

**Recipe removal on disable:**
In `onVillagerOpen`, after building the new recipe list:
- Remove any merchant recipe whose result matches a `CustomRecipe.Villager` that is NO LONGER
  in `CustomRecipes.all()` (i.e., recipe was removed/disabled since last open)
- Also clear the corresponding PDC key

**Scan on reload:**
In `CustomRecipeLoader.load()`, after loading:
```kotlin
Bukkit.getWorlds().flatMap { it.entities }
    .filterIsInstance<AbstractVillager>()
    .forEach { villager ->
        // Remove PDC keys for recipes no longer loaded
        val loadedKeys = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Villager>()
            .map { "vr_${it.key.key}" }.toSet()
        villager.persistentDataContainer.keys
            .filter { it.namespace == "recipebook" && it.key.startsWith("vr_") }
            .filter { it.key !in loadedKeys }
            .forEach { villager.persistentDataContainer.remove(it) }
        // Recipe list rebuild happens on next villager open, not here.
        // Scan only clears stale PDC keys to prevent disabled recipes from reappearing.
    }
```

Config: `villager-scan-on-reload: true` (default `true`)

---

## Files Changed / Created

| File | Change |
|------|--------|
| `custom/BlockOwnerTracker.kt` | New — replaces 3 trackers |
| `custom/FurnaceOwnerTracker.kt` | Delete |
| `custom/BrewingOwnerTracker.kt` | Delete |
| `custom/CrafterOwnerTracker.kt` | Delete |
| `custom/packet/GrindstonePacketListener.kt` | New |
| `custom/packet/BrewingPacketListener.kt` | New |
| `custom/CustomRecipeListener.kt` | Fix ghost bugs, brewing-per-slot, campfire |
| `RecipeBookPlugin.kt` | Register new tracker + packet listeners, remove old trackers |
| `resources/config.yml` | Add `owner-mode`, `owner-nearest-radius`, `brewing-stand.ghost-per-slot`, `villager-scan-on-reload` |

---

## Dependencies

- eco `PacketListener` API (already in scope, no new deps)
- Paper `TileState.persistentDataContainer` (already used implicitly)
- `PersistentDataType` for UUID: custom 16-byte implementation (standard boilerplate, ~10 lines)

---

## Non-Goals

- No custom inventory UI for grindstone/brewing (packet approach is sufficient)
- No external packet library (ProtocolLib, PacketEvents)
- No database for villager persistence (entity PDC is sufficient)
- Crafter ghost recipes: no change (CrafterOwnerTracker already handles owner tracking correctly
  via open event; redesign only updates the backing mechanism to PDC)
