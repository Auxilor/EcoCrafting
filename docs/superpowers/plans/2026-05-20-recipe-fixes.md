# Recipe Fixes & Owner Tracking Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix ghost-recipe bugs across smithing/stonecutter/campfire/brewing, bypass client-side slot restrictions on grindstone/brewing via eco's PacketListener, replace three in-memory owner trackers with a single PDC-backed BlockOwnerTracker, and persist villager trade chance rolls to entity PDC.

**Architecture:** BlockOwnerTracker replaces FurnaceOwnerTracker/BrewingOwnerTracker/CrafterOwnerTracker using block PDC (survives restarts, set once on place or first open). BrewingPacketListener intercepts `ServerboundContainerClickPacket` to place non-vanilla ingredients in brewing stands, then schedules a custom brew timer. GrindstonePacketListener uses `player.updateInventory()` after PrepareGrindstoneEvent to override client-side prediction. Villager chance rolls are stored as PDC bytes on the entity.

**Tech Stack:** Kotlin, Paper 1.21.x, eco 7.6.0 (PacketListener API, configYml), libreforge, NMS `ServerboundContainerClickPacket`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `custom/BlockOwnerTracker.kt` | **Create** | PDC-backed owner for all block types; replaces 3 trackers |
| `custom/FurnaceOwnerTracker.kt` | **Delete** | Replaced by BlockOwnerTracker |
| `custom/BrewingOwnerTracker.kt` | **Delete** | Replaced by BlockOwnerTracker |
| `custom/CrafterOwnerTracker.kt` | **Delete** | Replaced by BlockOwnerTracker |
| `custom/packet/BrewingPacketListener.kt` | **Create** | Intercept ServerboundContainerClickPacket for brewing slot 3; custom brew timer |
| `custom/CustomRecipeListener.kt` | **Modify** | Tracker migration, campfire fix, brewing ghost-per-slot, smithing/stonecutter debug+fallback, grindstone updateInventory, crafter debug cleanup |
| `RecipeBookPlugin.kt` | **Modify** | Register BlockOwnerTracker + BrewingPacketListener; remove old tracker registrations |
| `resources/config.yml` | **Modify** | Add owner-mode, owner-nearest-radius, brewing-stand.ghost-per-slot, villager-scan-on-reload |
| `custom/CustomRecipeLoader.kt` | **Modify** | Add villager PDC scan on reload |

---

## Task 1: Config additions

**Files:**
- Modify: `eco-core/core-plugin/src/main/resources/config.yml`

- [ ] **Step 1: Add four new config keys after `debug: false`**

Open `eco-core/core-plugin/src/main/resources/config.yml`. Insert after line 4 (`debug: false`):

```yaml
# Owner tracking for block-based recipes (furnace, brewing, crafter, campfire)
# placed: dispatch effects on the player who placed (or first opened) the block
# nearest: dispatch effects on the nearest online player within owner-nearest-radius blocks
owner-mode: placed
owner-nearest-radius: 32

# Brewing stand ghost recipe behaviour
brewing-stand:
  # When true, ghost effects fire once per matched base slot (not once total)
  ghost-per-slot: true

# Scan all loaded villagers on plugin reload to remove PDC keys for disabled recipes
villager-scan-on-reload: true
```

- [ ] **Step 2: Commit**

```bash
git add eco-core/core-plugin/src/main/resources/config.yml
git commit -m "config: add owner-mode, brewing ghost-per-slot, villager-scan-on-reload"
```

---

## Task 2: BlockOwnerTracker

**Files:**
- Create: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/BlockOwnerTracker.kt`

Replaces FurnaceOwnerTracker, BrewingOwnerTracker, CrafterOwnerTracker. Stores owner UUID in block tile-entity PDC. Set once on place or first open. Never overwritten by subsequent opens.

- [ ] **Step 1: Create BlockOwnerTracker.kt**

```kotlin
package ru.oftendev.recipebook.custom

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.persistence.PersistentDataType
import ru.oftendev.recipebook.recipeBookPlugin
import java.util.UUID

object BlockOwnerTracker : Listener {

    private val PDC_KEY = NamespacedKey("recipebook", "owner")

    private val TRACKED_MATERIALS = setOf(
        Material.FURNACE,
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.BREWING_STAND,
        Material.CRAFTER,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE
    )

    private val TRACKED_INVENTORY_TYPES = setOf(
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.BREWING,
        InventoryType.CRAFTER
    )

    fun setOwner(block: org.bukkit.block.Block, player: Player) {
        val state = block.state as? TileState ?: return
        state.persistentDataContainer.set(PDC_KEY, PersistentDataType.STRING, player.uniqueId.toString())
        state.update()
    }

    fun hasOwner(block: org.bukkit.block.Block): Boolean {
        val state = block.state as? TileState ?: return false
        return state.persistentDataContainer.has(PDC_KEY, PersistentDataType.STRING)
    }

    private fun getStoredUUID(block: org.bukkit.block.Block): UUID? {
        val state = block.state as? TileState ?: return null
        val raw = state.persistentDataContainer.get(PDC_KEY, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    /**
     * Returns the dispatching player for this block based on owner-mode config.
     * "placed"  → the stored PDC owner, if online.
     * "nearest" → closest online player within owner-nearest-radius blocks.
     */
    fun getOwner(location: Location): Player? {
        return when (recipeBookPlugin.configYml.getString("owner-mode")) {
            "nearest" -> {
                val radius = recipeBookPlugin.configYml.getInt("owner-nearest-radius")
                    .takeIf { it > 0 } ?: 32
                location.world?.players
                    ?.filter { it.location.distanceSquared(location) <= (radius * radius).toDouble() }
                    ?.minByOrNull { it.location.distanceSquared(location) }
            }
            else -> {
                val uuid = getStoredUUID(location.block) ?: return null
                location.world?.players?.firstOrNull { it.uniqueId == uuid }
            }
        }
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        if (event.block.type !in TRACKED_MATERIALS) return
        setOwner(event.block, event.player)
    }

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.type !in TRACKED_INVENTORY_TYPES) return
        val loc = event.inventory.location ?: return
        val block = loc.block
        if (hasOwner(block)) return
        setOwner(block, event.player as? Player ?: return)
    }

    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        if (event.block.type !in TRACKED_MATERIALS) return
        val state = event.block.state as? TileState ?: return
        state.persistentDataContainer.remove(PDC_KEY)
        state.update()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/BlockOwnerTracker.kt
git commit -m "feat: BlockOwnerTracker — PDC-backed owner for all block recipe types"
```

---

## Task 3: BrewingPacketListener

**Files:**
- Create: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/packet/BrewingPacketListener.kt`

Intercepts `ServerboundContainerClickPacket` when a player clicks on slot 3 (ingredient slot) of an open brewing stand. If the cursor item matches a custom brewing recipe, the vanilla packet is cancelled, the item is placed directly via `Inventory.setItem` (bypasses slot's `mayPlace` restriction), and a 400-tick delayed task executes the custom brew.

If vanilla `BrewEvent` fires for the same location before the timer (because ingredient also matches a vanilla potion recipe), the timer is cancelled to prevent double-firing.

- [ ] **Step 1: Create packet package directory and BrewingPacketListener.kt**

```kotlin
package ru.oftendev.recipebook.custom.packet

import com.willfp.eco.core.packet.PacketEvent
import com.willfp.eco.core.packet.PacketListener
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import org.bukkit.Location
import org.bukkit.event.inventory.InventoryType
import org.bukkit.scheduler.BukkitTask
import ru.oftendev.recipebook.custom.BlockOwnerTracker
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.CraftingDispatchers.checkCraftingConditions
import ru.oftendev.recipebook.custom.CraftingDispatchers.fireCustomCraftTrigger
import ru.oftendev.recipebook.custom.CraftingDispatchers.fireGhostEffects
import ru.oftendev.recipebook.custom.event.CustomBrewEvent
import org.bukkit.Bukkit
import ru.oftendev.recipebook.recipeBookPlugin

object BrewingPacketListener : PacketListener {

    // loc → pending custom-brew task (non-vanilla ingredient path)
    private val pendingBrews = mutableMapOf<Location, BukkitTask>()

    override fun onReceive(event: PacketEvent) {
        val packet = event.packet.handle as? ServerboundContainerClickPacket ?: return
        if (packet.slotNum != 3) return  // ingredient slot only

        val player = event.player
        if (player.openInventory.topInventory.type != InventoryType.BREWING) return

        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type.isAir) return

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Brewing>()
            .firstOrNull { it.ingredient.matches(cursor) } ?: return

        // Cancel vanilla click — we handle placement manually
        event.isCancelled = true

        Bukkit.getScheduler().runTask(recipeBookPlugin, Runnable {
            val topInv = player.openInventory.topInventory
            if (topInv.type != InventoryType.BREWING) return@Runnable

            // Place one unit of the ingredient
            val toPlace = cursor.clone().apply { amount = 1 }
            topInv.setItem(3, toPlace)
            if (cursor.amount <= 1) player.setItemOnCursor(null)
            else cursor.amount--
            player.updateInventory()

            // Find the block location for this brewing stand
            val loc = topInv.location ?: return@Runnable

            // Schedule custom brew (vanilla won't process non-potion ingredients)
            scheduleBrew(loc, recipe)
        })
    }

    /** Called from CustomRecipeListener.onBrew to cancel timer when vanilla fires first. */
    fun cancelBrew(location: Location) {
        pendingBrews.remove(location)?.cancel()
    }

    private fun scheduleBrew(loc: Location, recipe: CustomRecipe.Brewing) {
        pendingBrews[loc]?.cancel()
        pendingBrews[loc] = Bukkit.getScheduler().runTaskLater(recipeBookPlugin, Runnable {
            pendingBrews.remove(loc)

            val state = loc.block.state as? org.bukkit.block.BrewingStand ?: return@Runnable
            val brewer = state.inventory
            val ingredient = brewer.ingredient ?: return@Runnable
            if (!recipe.ingredient.matches(ingredient)) return@Runnable

            val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
            if (matchedSlots.isEmpty()) return@Runnable

            val player = BlockOwnerTracker.getOwner(loc) ?: return@Runnable
            if (!checkCraftingConditions(player, recipe)) return@Runnable

            // Consume ingredient
            val ing = ingredient.clone()
            if (ing.amount <= 1) brewer.ingredient = null
            else { ing.amount--; brewer.ingredient = ing }

            val item = recipe.output.clone()
            val ce = CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) return@Runnable

            val ghostPerSlot = recipeBookPlugin.configYml.getBool("brewing-stand.ghost-per-slot")
            if (recipe.ghost) {
                if (ghostPerSlot) {
                    matchedSlots.forEach { slot ->
                        brewer.setItem(slot, null)
                        fireGhostEffects(player, recipe, item.clone(), 1)
                    }
                } else {
                    matchedSlots.forEach { brewer.setItem(it, null) }
                    fireGhostEffects(player, recipe, item, 1)
                }
            } else {
                matchedSlots.forEach { brewer.setItem(it, item.clone()) }
                fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
            }
        }, 400L)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/packet/
git commit -m "feat: BrewingPacketListener — bypass ingredient slot restriction via ServerboundContainerClickPacket"
```

---

## Task 4: Migrate RecipeBookPlugin — register new, remove old

**Files:**
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/RecipeBookPlugin.kt`

- [ ] **Step 1: Update imports and registrations**

Replace the three old tracker imports and registrations with `BlockOwnerTracker` and `BrewingPacketListener`.

Full updated `handleEnable()`:

```kotlin
override fun handleEnable() {
    VaultPackIntegration.init(this)
    ShopIntegration.init(this)
    RecipeCategories.reload()

    Triggers.register(TriggerGhostCraft)
    Triggers.register(TriggerCustomCraft)
    Triggers.register(TriggerRecipeUnlocked)
    Triggers.register(TriggerRecipeLocked)

    Effects.register(EffectUnlockRecipe)
    Effects.register(EffectLockRecipe)
    Conditions.register(ConditionHasUnlockedRecipe)

    eventManager.registerListener(BlockOwnerTracker)
    eventManager.registerListener(RecipeUnlockStore)
    eventManager.registerListener(CustomRecipeListener())
    eventManager.registerPacketListener(BrewingPacketListener)

    CustomRecipeLoader.load()

    setupMetrics()
}
```

Update the import block — remove the three old trackers, add the new ones:

```kotlin
import ru.oftendev.recipebook.custom.BlockOwnerTracker
import ru.oftendev.recipebook.custom.packet.BrewingPacketListener
```

Remove these imports (no longer referenced):
```kotlin
import ru.oftendev.recipebook.custom.BrewingOwnerTracker
import ru.oftendev.recipebook.custom.CrafterOwnerTracker
import ru.oftendev.recipebook.custom.FurnaceOwnerTracker
```

- [ ] **Step 2: Delete the three old tracker files**

```bash
git rm eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/FurnaceOwnerTracker.kt
git rm eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/BrewingOwnerTracker.kt
git rm eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CrafterOwnerTracker.kt
```

- [ ] **Step 3: Build to verify compilation**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL (will fail until CustomRecipeListener is updated in Task 5 — if compile errors exist for the old tracker references there, proceed to Task 5 first then come back to verify).

- [ ] **Step 4: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/RecipeBookPlugin.kt
git commit -m "refactor: replace three owner trackers with BlockOwnerTracker; register BrewingPacketListener"
```

---

## Task 5: Update CustomRecipeListener — tracker migration + campfire fix + crafter cleanup

**Files:**
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

This task handles:
1. Replace all `FurnaceOwnerTracker`, `BrewingOwnerTracker`, `CrafterOwnerTracker` usages with `BlockOwnerTracker`
2. Remove `Bukkit.broadcastMessage` debug spam from `onCrafterCraft`
3. Fix campfire: `FurnaceOwnerTracker` never tracked campfire locations → `BlockOwnerTracker.getOwner` now covers it via `BlockPlaceEvent`

- [ ] **Step 1: Update imports at top of CustomRecipeListener.kt**

Remove:
```kotlin
import ru.oftendev.recipebook.custom.BrewingOwnerTracker
import ru.oftendev.recipebook.custom.CrafterOwnerTracker
import ru.oftendev.recipebook.custom.FurnaceOwnerTracker
```

Add:
```kotlin
import ru.oftendev.recipebook.custom.BlockOwnerTracker
```

- [ ] **Step 2: Replace tracker usages in onSmelt**

Change line:
```kotlin
val player = FurnaceOwnerTracker.getOwner(loc)
```
To:
```kotlin
val player = BlockOwnerTracker.getOwner(loc)
```

Also change the ghost-cancel block (same function) — the null check logic stays identical, just the tracker call changes.

- [ ] **Step 3: Replace tracker usage in onCampfire**

Change:
```kotlin
val player = FurnaceOwnerTracker.getOwner(loc)
```
To:
```kotlin
val player = BlockOwnerTracker.getOwner(loc)
```

- [ ] **Step 4: Replace tracker usage in onBrew**

Change:
```kotlin
val player = BrewingOwnerTracker.getOwner(loc)
```
To:
```kotlin
val player = BlockOwnerTracker.getOwner(loc)
```

- [ ] **Step 5: Clean up onCrafterCraft — replace tracker + remove all broadcastMessage calls**

Replace the entire `onCrafterCraft` function with the cleaned version (no `Bukkit.broadcastMessage` calls):

```kotlin
@EventHandler(priority = EventPriority.HIGHEST)
fun onCrafterCraft(event: org.bukkit.event.block.CrafterCraftEvent) {
    val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return
    val baseKey = if (recipeKey.namespace == "recipebook" && recipeKey.key.endsWith("_crafter"))
        NamespacedKey("recipebook", recipeKey.key.removeSuffix("_crafter"))
    else recipeKey

    val recipe = CustomRecipes.getByKey(baseKey) as? CustomRecipe.Crafter ?: return

    if (recipe.ghost) {
        event.isCancelled = true
        val crafterInv = (event.block.state as? org.bukkit.block.Crafter)?.inventory ?: return
        for (slot in 0 until 9) consume(crafterInv, slot)
        val player = BlockOwnerTracker.getOwner(event.block.location) ?: return
        fireGhostEffects(player, recipe, recipe.output.clone(), 1)
    }
    // non-ghost: vanilla handles item delivery, no action needed
}
```

- [ ] **Step 6: Build to verify no compile errors**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "fix: migrate all recipe listeners to BlockOwnerTracker; clean crafter debug logs"
```

---

## Task 6: Brewing ghost per-slot + cancel timer on BrewEvent

**Files:**
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

- [ ] **Step 1: Add BrewingPacketListener import**

Add at top of CustomRecipeListener.kt:
```kotlin
import ru.oftendev.recipebook.custom.packet.BrewingPacketListener
```

- [ ] **Step 2: Replace the entire `onBrew` function**

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onBrew(event: org.bukkit.event.inventory.BrewEvent) {
    val loc = event.block.location

    // Cancel any pending custom-brew timer (non-vanilla ingredient path) to prevent double-firing
    BrewingPacketListener.cancelBrew(loc)

    val player = BlockOwnerTracker.getOwner(loc)
    if (player == null) {
        recipeBookPlugin.debug("[RecipeListener] Brew at $loc — owner offline")
        return
    }

    val brewer = event.contents
    val ingredientSlot = brewer.ingredient ?: return

    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Brewing>()
        .firstOrNull { it.ingredient.matches(ingredientSlot) }
        ?: return

    val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
    if (matchedSlots.isEmpty()) return

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    event.isCancelled = true

    val ing = ingredientSlot.clone()
    if (ing.amount <= 1) brewer.ingredient = null
    else { ing.amount--; brewer.ingredient = ing }

    val item = recipe.output.clone()
    val ce = CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
    Bukkit.getPluginManager().callEvent(ce)
    if (ce.isCancelled) return

    val ghostPerSlot = recipeBookPlugin.configYml.getBool("brewing-stand.ghost-per-slot")
    if (recipe.ghost) {
        if (ghostPerSlot) {
            matchedSlots.forEach { slot ->
                brewer.setItem(slot, null)
                val slotCe = CustomBrewEvent(player, recipe, item.clone(), loc, 1)
                Bukkit.getPluginManager().callEvent(slotCe)
                if (!slotCe.isCancelled) fireGhostEffects(player, recipe, item.clone(), 1)
            }
        } else {
            matchedSlots.forEach { brewer.setItem(it, null) }
            fireGhostEffects(player, recipe, item, 1)
        }
    } else {
        matchedSlots.forEach { brewer.setItem(it, item.clone()) }
        fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "fix: brewing ghost fires once per matched slot; cancel custom-brew timer on BrewEvent"
```

---

## Task 7: Smithing + Stonecutter ghost bug fix + Grindstone updateInventory

**Files:**
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

### Smithing + Stonecutter

Root cause: `CustomRecipes.getByKey(recipeKey)` returns null on key mismatch → early return without `event.isCancelled = true` → vanilla gives item, effects never fire.

Fix: add debug logging + output-based fallback for smithing, plus `updateInventory()` call after ghost cancellation.

- [ ] **Step 1: Replace `handleSmithing`**

```kotlin
private fun handleSmithing(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
    val recipe = (CustomRecipes.getByKey(recipeKey)
        ?: event.recipe.result?.let { CustomRecipes.getByOutput(it) })
        as? CustomRecipe.Smithing
        ?: run {
            recipeBookPlugin.debug("[Smithing] no recipe for key=$recipeKey result=${event.recipe.result?.type}")
            return
        }

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    val item = recipe.output.clone()
    if (recipe.ghost) {
        event.isCancelled = true
        consumeSmithingSlots(event)
        val ce = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable { player.updateInventory() })
    } else {
        val ce = CustomSmithEvent(player, recipe, item)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) { event.isCancelled = true; return }
        fireCustomCraftTrigger(player, recipe, item, 1)
    }
}
```

- [ ] **Step 2: Replace `handleStonecutter`**

```kotlin
private fun handleStonecutter(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
    val parsed = parseStonecutterKey(recipeKey)
    if (parsed == null) {
        recipeBookPlugin.debug("[Stonecutter] could not parse key=$recipeKey")
        return
    }
    val (baseId, idx) = parsed
    val baseKey = NamespacedKey("recipebook", baseId)
    val recipe = CustomRecipes.getByKey(baseKey) as? CustomRecipe.Stonecutter
        ?: run {
            recipeBookPlugin.debug("[Stonecutter] no recipe for baseKey=$baseKey (from key=$recipeKey idx=$idx)")
            return
        }
    val out = recipe.outputs.getOrNull(idx) ?: return
    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    val amount = calculateCraftAmount(event)
    val item = out.item.clone().apply { this.amount = amount }

    if (out.ghost) {
        event.isCancelled = true
        consumeStonecutterSlot(event)
        val ce = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireStonecutterGhostEffects(player, recipe, out, amount)
        recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable { player.updateInventory() })
    } else {
        val ce = CustomCraftEvent(player, recipe, item, amount)
        Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) { event.isCancelled = true; return }
        fireCustomCraftTrigger(player, recipe, item, amount)
    }
}
```

### Grindstone

- [ ] **Step 3: Update `onPrepareGrindstone` to force client update**

Change:
```kotlin
event.result = recipe.output.clone()
pendingRecipe[player.uniqueId] = recipe
```
To:
```kotlin
event.result = recipe.output.clone()
pendingRecipe[player.uniqueId] = recipe
recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable { player.updateInventory() })
```

- [ ] **Step 4: Build**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "fix: smithing output-fallback + stonecutter debug logging for ghost key mismatches; grindstone updateInventory after PrepareEvent"
```

---

## Task 8: Villager chance persistence

**Files:**
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`
- Modify: `eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`

### onVillagerOpen changes

- [ ] **Step 1: Add import for PersistentDataType**

At top of CustomRecipeListener.kt add:
```kotlin
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
```

(`NamespacedKey` may already be imported — only add if missing)

- [ ] **Step 2: Replace `onVillagerOpen`**

```kotlin
@EventHandler
fun onVillagerOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
    if (event.inventory.type != org.bukkit.event.inventory.InventoryType.MERCHANT) return
    val player = event.player as? Player ?: return
    val merchant = (event.inventory.holder as? org.bukkit.entity.AbstractVillager) ?: return
    val isWanderingTrader = merchant is org.bukkit.entity.WanderingTrader

    // All custom villager recipe outputs (used to identify "our" merchant recipes)
    val allCustomOutputs = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Villager>()
        .map { it.output }

    val filteredRecipes = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Villager>()
        .filter { vr ->
            if (!vr.visibilityConditions.areMet(player.toDispatcher(), EmptyProvidedHolder)) return@filter false
            if (isWanderingTrader) {
                if (!vr.wanderingTrader) return@filter false
            } else {
                if (vr.wanderingTrader) return@filter false
                if (vr.profession != null || vr.minLevel > 0) {
                    val v = merchant as? org.bukkit.entity.Villager ?: return@filter false
                    if (vr.profession != null && v.profession != vr.profession) return@filter false
                    if (vr.minLevel > 0 && v.villagerLevel < vr.minLevel) return@filter false
                }
            }
            // Chance: roll once and persist result in entity PDC
            val pdcKey = NamespacedKey("recipebook", "vr_${vr.key.key}")
            val pdc = merchant.persistentDataContainer
            if (pdc.has(pdcKey, PersistentDataType.BYTE)) {
                pdc.get(pdcKey, PersistentDataType.BYTE) == 1.toByte()
            } else {
                val include = vr.chance >= 1.0 || Math.random() <= vr.chance
                pdc.set(pdcKey, PersistentDataType.BYTE, if (include) 1.toByte() else 0.toByte())
                include
            }
        }

    val validOutputs = filteredRecipes.map { it.output }

    // Rebuild merchant recipe list:
    // 1. Remove recipes that were added by us but are no longer valid (disabled or chance=false)
    // 2. Add valid recipes not yet in the list
    val existing = merchant.recipes.toMutableList()
    existing.removeIf { mr ->
        allCustomOutputs.any { it.isSimilar(mr.result) } &&
        validOutputs.none { it.isSimilar(mr.result) }
    }
    filteredRecipes.forEach { vr ->
        if (existing.none { it.result.isSimilar(vr.output) }) {
            val mr = org.bukkit.inventory.MerchantRecipe(vr.output.clone(), Int.MAX_VALUE)
            mr.addIngredient(vr.input1.displayItem.clone())
            vr.input2?.let { mr.addIngredient(it.displayItem.clone()) }
            existing.add(mr)
        }
    }
    merchant.recipes = existing
}
```

### CustomRecipeLoader scan

- [ ] **Step 3: Add `scanVillagers()` to CustomRecipeLoader.kt**

After the `load()` function body (before `loadFile`), add:

```kotlin
private fun scanVillagers() {
    val validKeyNames = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Villager>()
        .map { "vr_${it.key.key}" }
        .toSet()

    org.bukkit.Bukkit.getWorlds()
        .flatMap { it.entities }
        .filterIsInstance<org.bukkit.entity.AbstractVillager>()
        .forEach { villager ->
            val pdc = villager.persistentDataContainer
            pdc.keys
                .filter { it.namespace == "recipebook" && it.key.startsWith("vr_") }
                .filter { it.key !in validKeyNames }
                .forEach { pdc.remove(it) }
        }
}
```

At the end of `load()`, after `CustomRecipeLoader.load()` loop, add:

```kotlin
if (recipeBookPlugin.configYml.getBool("villager-scan-on-reload")) {
    scanVillagers()
}
```

The full `load()` becomes:
```kotlin
fun load() {
    CustomRecipes.clear()
    val recipesDir = File(recipeBookPlugin.dataFolder, "recipes")
    if (!recipesDir.exists()) recipesDir.mkdirs()
    recipesDir.walkTopDown()
        .filter { it.isFile && it.extension == "yml" && !it.nameWithoutExtension.startsWith("_") }
        .forEach { file ->
            runCatching { loadFile(file) }
                .onFailure { recipeBookPlugin.logger.warning("[RecipeBook] Failed to load recipe ${file.name}: ${it.message}") }
        }
    if (recipeBookPlugin.configYml.getBool("villager-scan-on-reload")) {
        scanVillagers()
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git add eco-core/core-plugin/src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt
git commit -m "feat: persist villager trade chance to entity PDC; scan villagers on reload to remove disabled recipes"
```

---

## Task 9: Final build + in-game verification

- [ ] **Step 1: Full build**

```bash
./gradlew shadowJar
```

Expected: BUILD SUCCESSFUL, `build/libs/RecipeBook.jar` produced.

- [ ] **Step 2: Deploy and test — BlockOwnerTracker**

Place a furnace, break it, re-place it. Confirm:
- Smelt with a custom smelting recipe → effects fire on the placer
- Second player opens the furnace → owner does NOT change (PDC already set)
- Break furnace → PDC cleared (verify via `/recipebook debug` or server log)
- Config `owner-mode: nearest` → effects fire on nearest online player instead

- [ ] **Step 3: Deploy and test — Campfire**

Place a custom campfire recipe. Place campfire, put ingredient on it. Wait for cook. Confirm effects fire. Previously this would do nothing.

- [ ] **Step 4: Deploy and test — Brewing ghost per-slot**

Set up 3 base items in brewing stand with a ghost custom recipe. Brew. Confirm effects fire 3 times (once per slot). Set `ghost-per-slot: false` in config, reload, confirm effects fire once.

- [ ] **Step 5: Deploy and test — Brewing non-vanilla ingredient**

Create a recipe with `ingredient: diamond` (not a valid potion ingredient). Place diamond in slot 3 of brewing stand. Confirm it can be placed (packet bypass works), confirm effects fire after 400 ticks (~20 seconds).

- [ ] **Step 6: Deploy and test — Smithing/Stonecutter ghost**

Set up a ghost smithing recipe. Craft at smithing table. Confirm item is NOT given, effects DO fire. Check debug log if issue persists (key mismatch message will appear).

Set up a ghost stonecutter recipe. Craft. Same confirmation.

- [ ] **Step 7: Deploy and test — Grindstone non-enchanted**

Set up a grindstone custom recipe with non-enchanted input. Place item in grindstone. Confirm result slot shows the custom output (updateInventory forces client sync).

- [ ] **Step 8: Deploy and test — Villager persistence**

Set up a villager recipe with `chance: 0.5`. Open the villager repeatedly:
- First open: recipe either appears or doesn't (50% chance)
- All subsequent opens: same result (PDC persists the roll)
- Reload plugin: recipe PDC scan runs, stale keys removed
- Disable the recipe in config, reload, open villager: recipe removed from trade list

- [ ] **Step 9: Commit any last fixes found during testing**

```bash
git add -p  # stage only relevant changes
git commit -m "fix: <describe what broke during in-game testing>"
```

---

## Self-Review Checklist

### Spec coverage

| Spec requirement | Task |
|-----------------|------|
| Grindstone client-side restriction bypass | Task 7 (updateInventory) |
| Smithing ghost gives item / no effects | Task 7 |
| Stonecutter ghost gives item / no effects | Task 7 |
| Campfire does nothing | Task 5 (BlockOwnerTracker covers campfire) |
| Brewing works only with valid potion ingredients | Task 3 (BrewingPacketListener) |
| Brewing ghost once per slot + config | Task 6 |
| Villager chance persists to entity | Task 8 |
| Villager disabled recipes removed on open | Task 8 |
| Villager scan on reload | Task 8 |
| Block PDC owner tracking | Task 2 |
| Set on place, or first open if no owner | Task 2 |
| Toggleable nearest-player vs placed | Task 1 (config) + Task 2 |
| Remove 3 old trackers | Task 4 |
| Crafter debug log cleanup | Task 5 |

All spec requirements covered. ✓
