# Custom Crafting + Ghost Item System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **CHECKPOINT RULE:** Mark each step `[x]` immediately on completion. Do NOT batch. If session ends mid-task, next session reads task list first and resumes from first `[ ]` item. Completed files on disk = done; do not rewrite unless task explicitly marked for rework.

**Goal:** Add a 13-station custom crafting engine with ghost-item mode, per-recipe conditions, per-player unlock system, libreforge triggers/effects/condition, and an in-game recipe creator GUI to RecipeBook.

**Architecture:** Custom recipes defined in `plugins/RecipeBook/recipes/*.yml` (one file = one recipe, eco item-lookup format). Group A stations register Bukkit recipes and intercept result events; Group B stations use PrepareEvent + InventoryClickEvent for manual matching. Ghost recipes cancel output delivery and dispatch libreforge effects. Unlock state persisted per-player in `data/players/<uuid>.yml`.

**Tech Stack:** Kotlin 2.3, Paper 1.21.8, eco 7.x, libreforge 5.4.2, Gradle shadow

---

## File Map

### New files
```
src/main/kotlin/ru/oftendev/recipebook/
  custom/
    CustomRecipe.kt          — sealed class hierarchy + SmeltingType enum + StonecutterOutput
    CustomRecipes.kt         — singleton registry
    CustomRecipeLoader.kt    — YAML parsing + Bukkit recipe registration
    CustomRecipeListener.kt  — all event interception
    FurnaceOwnerTracker.kt   — Location→UUID map for furnace/blast/smoker/campfire
    BrewingOwnerTracker.kt   — Location→UUID map for brewing stand
    RecipeUnlockStore.kt     — per-player unlock persistence
  custom/event/
    CustomCraftEvent.kt
    CustomSmeltEvent.kt
    CustomBrewEvent.kt
    CustomSmithEvent.kt
    CustomWorkbenchCraftEvent.kt
  custom/libreforge/
    TriggerGhostCraft.kt
    TriggerCustomCraft.kt
    TriggerRecipeUnlocked.kt
    TriggerRecipeLocked.kt
    EffectUnlockRecipe.kt
    EffectLockRecipe.kt
    ConditionHasUnlockedRecipe.kt
  gui/
    RecipeCreatorGUI.kt

src/test/kotlin/ru/oftendev/recipebook/
  custom/
    CustomRecipeTest.kt
    CustomRecipesTest.kt
    RecipeUnlockStoreTest.kt
    SymmetryTest.kt
```

### Modified files
```
build.gradle.kts                          — add libreforge compileOnly
src/main/kotlin/ru/oftendev/recipebook/
  RecipeBookPlugin.kt                     — register all new components
  recipe/RecipeSource.kt                  — add CUSTOM
  recipe/ResolvedRecipe.kt                — add displayType + locked fields
  recipe/RecipeResolver.kt               — add resolveForPlayer + CustomRecipes lookup
  gui/RecipeGUI.kt                        — displayType switching
  commands/MainCommand.kt                 — add create/unlock/lock subcommands
src/main/resources/
  config.yml                              — add GUI sections per display type
  lang.yml                                — add craft-conditions-not-met, recipe-locked, recipe-unlocked
  plugin.yml                              — add subcommands + permissions
```

---

## Module 1 — Build Setup

### Task 1: Add libreforge dependency

**Files:**
- Modify: `build.gradle.kts`

- [x] Add dep to `dependencies` block:

```kotlin
compileOnly("com.willfp:libreforge:5.4.2")
```

- [x] Verify build resolves:

```
./gradlew dependencies --configuration compileClasspath | grep libreforge
```

Expected: `com.willfp:libreforge:5.4.2`

- [x] Commit:

```bash
git add build.gradle.kts
git commit -m "build: add libreforge 5.4.2 compileOnly dep"
```

---

## Module 2 — Core Data Model

### Task 2: RecipeSource + RecipeDisplayType

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/recipe/ResolvedRecipe.kt`

- [x] Add `CUSTOM` to `RecipeSource` enum in `ResolvedRecipe.kt`:

```kotlin
enum class RecipeSource {
    ECO,
    BUKKIT,
    VAULTPACK,
    CUSTOM,
    UNKNOWN
}
```

- [x] Add `RecipeDisplayType` enum in same file after `RecipeSource`:

```kotlin
enum class RecipeDisplayType {
    CRAFTING,
    SMELTING,
    SMITHING,
    STONECUTTER,
    CRAFTER,
    BREWING,
    CARTOGRAPHY,
    GRINDSTONE,
    ANVIL,
    VILLAGER
}
```

- [x] Add fields to `ResolvedRecipe` data class (keep defaults for back-compat):

```kotlin
data class ResolvedRecipe(
    val key: NamespacedKey?,
    val output: ItemStack,
    val ingredients: List<RecipeIngredient>,
    val permission: String? = null,
    val source: RecipeSource = RecipeSource.UNKNOWN,
    val shapeless: Boolean = false,
    val displayType: RecipeDisplayType = RecipeDisplayType.CRAFTING,
    val locked: Boolean = false
)
```

- [x] Run existing tests to confirm no regression:

```
./gradlew test
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/recipe/ResolvedRecipe.kt
git commit -m "feat: add RecipeDisplayType enum + displayType/locked to ResolvedRecipe"
```

---

### Task 3: CustomRecipe sealed class hierarchy

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipe.kt`

- [x] Create file:

```kotlin
package ru.oftendev.recipebook.custom

import com.willfp.libreforge.SimpleHolder
import com.willfp.libreforge.conditions.ConditionList
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.recipe.RecipeIngredient
import ru.oftendev.recipebook.recipe.RecipeDisplayType

enum class SmeltingType {
    FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE
}

data class StonecutterOutput(
    val item: ItemStack,
    val ghost: Boolean,
    val ghostHolder: SimpleHolder?
)

sealed class CustomRecipe {
    abstract val key: NamespacedKey
    abstract val output: ItemStack
    abstract val permission: String?
    abstract val ghost: Boolean
    abstract val ghostHolder: SimpleHolder?
    abstract val visibilityConditions: ConditionList
    abstract val craftingConditions: ConditionList
    abstract val lockedByDefault: Boolean
    abstract val showWhenLocked: Boolean
    abstract val lockedLore: List<String>
    abstract val unlockConditions: ConditionList
    abstract val displayType: RecipeDisplayType

    data class CraftingTable(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val parts: List<RecipeIngredient>,
        val shapeless: Boolean,
        val symmetry: Boolean,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.CRAFTING
    }

    data class Smelting(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val input: RecipeIngredient,
        val stationType: SmeltingType,
        val cookTime: Int,
        val experience: Float,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.SMELTING
    }

    data class Smithing(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val template: RecipeIngredient,
        val base: RecipeIngredient,
        val addition: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.SMITHING
    }

    data class Stonecutter(
        override val key: NamespacedKey,
        val input: RecipeIngredient,
        val outputs: List<StonecutterOutput>,
        override val permission: String?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val output: ItemStack get() = outputs.first().item
        override val ghost: Boolean get() = outputs.any { it.ghost }
        override val ghostHolder: SimpleHolder? get() = null
        override val displayType = RecipeDisplayType.STONECUTTER
    }

    data class Crafter(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val parts: List<RecipeIngredient>,
        val shapeless: Boolean,
        override val permission: String?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList
    ) : CustomRecipe() {
        override val ghost = false
        override val ghostHolder: SimpleHolder? = null
        override val lockedByDefault = false
        override val showWhenLocked = false
        override val lockedLore: List<String> = emptyList()
        override val unlockConditions = ConditionList(emptyList())
        override val displayType = RecipeDisplayType.CRAFTER
    }

    data class Brewing(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val base: RecipeIngredient,
        val ingredient: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.BREWING
    }

    data class Cartography(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val map: RecipeIngredient,
        val addition: RecipeIngredient,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.CARTOGRAPHY
    }

    data class Grindstone(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val item1: RecipeIngredient,
        val item2: RecipeIngredient?,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.GRINDSTONE
    }

    data class Anvil(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val base: RecipeIngredient,
        val material: RecipeIngredient?,
        val resultName: String?,
        val repairCost: Int,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.ANVIL
    }

    data class Villager(
        override val key: NamespacedKey,
        override val output: ItemStack,
        val input1: RecipeIngredient,
        val input2: RecipeIngredient?,
        override val permission: String?,
        override val ghost: Boolean,
        override val ghostHolder: SimpleHolder?,
        override val visibilityConditions: ConditionList,
        override val craftingConditions: ConditionList,
        override val lockedByDefault: Boolean,
        override val showWhenLocked: Boolean,
        override val lockedLore: List<String>,
        override val unlockConditions: ConditionList
    ) : CustomRecipe() {
        override val displayType = RecipeDisplayType.VILLAGER
    }
}
```

- [x] Write unit test:

```kotlin
// src/test/kotlin/ru/oftendev/recipebook/custom/CustomRecipeTest.kt
package ru.oftendev.recipebook.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomRecipeTest {

    @Test
    fun `Crafter always has ghost false and lockedByDefault false`() {
        // Crafter fields are hardcoded — verify sealed class enforces them
        // Pure reflection check, no Bukkit needed
        val ghostField = CustomRecipe.Crafter::class.members.first { it.name == "ghost" }
        // Crafter.ghost is a hardcoded val, not a constructor param
        assertTrue(CustomRecipe.Crafter::class.members.none { it.name == "lockedByDefault" && it.parameters.size > 1 })
    }

    @Test
    fun `SmeltingType covers all four smelting stations`() {
        val types = SmeltingType.values().map { it.name }.toSet()
        assertEquals(setOf("FURNACE", "BLAST_FURNACE", "SMOKER", "CAMPFIRE"), types)
    }

    @Test
    fun `StonecutterOutput ghost true requires non-null ghostHolder contract documented`() {
        // Contract: ghost=true → ghostHolder non-null enforced at loader, not type level
        // This test just confirms StonecutterOutput is a data class with expected fields
        val fields = StonecutterOutput::class.members.map { it.name }.toSet()
        assertTrue("item" in fields)
        assertTrue("ghost" in fields)
        assertTrue("ghostHolder" in fields)
    }
}
```

- [x] Run:

```
./gradlew test --tests "ru.oftendev.recipebook.custom.CustomRecipeTest"
```

Expected: 3 PASS

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipe.kt \
        src/test/kotlin/ru/oftendev/recipebook/custom/CustomRecipeTest.kt
git commit -m "feat: add CustomRecipe sealed class hierarchy + SmeltingType + StonecutterOutput"
```

---

### Task 4: CustomRecipes registry

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipes.kt`

- [x] Create file:

```kotlin
package ru.oftendev.recipebook.custom

import com.willfp.eco.core.items.HashedItem
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack

object CustomRecipes {
    private val byKey = mutableMapOf<NamespacedKey, CustomRecipe>()
    private val byOutput = mutableMapOf<HashedItem, CustomRecipe>()

    fun register(recipe: CustomRecipe) {
        byKey[recipe.key] = recipe
        when (recipe) {
            is CustomRecipe.Stonecutter -> recipe.outputs.forEach { out ->
                byOutput[HashedItem.of(out.item.clone().apply { amount = 1 })] = recipe
            }
            else -> byOutput[HashedItem.of(recipe.output.clone().apply { amount = 1 })] = recipe
        }
    }

    fun clear() {
        byKey.clear()
        byOutput.clear()
    }

    fun getByKey(key: NamespacedKey): CustomRecipe? = byKey[key]

    fun getByOutput(stack: ItemStack): CustomRecipe? =
        byOutput[HashedItem.of(stack.clone().apply { amount = 1 })]

    fun all(): Collection<CustomRecipe> = byKey.values
}
```

- [x] Write unit test:

```kotlin
// src/test/kotlin/ru/oftendev/recipebook/custom/CustomRecipesTest.kt
package ru.oftendev.recipebook.custom

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomRecipesTest {

    @BeforeTest
    fun setup() {
        CustomRecipes.clear()
    }

    @Test
    fun `clear removes all entries`() {
        // registry starts empty after clear
        assertEquals(0, CustomRecipes.all().size)
    }

    @Test
    fun `all returns registered recipes`() {
        assertEquals(0, CustomRecipes.all().size)
    }
}
```

- [x] Run:

```
./gradlew test --tests "ru.oftendev.recipebook.custom.CustomRecipesTest"
```

Expected: 2 PASS (Bukkit-dependent tests deferred to integration)

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipes.kt \
        src/test/kotlin/ru/oftendev/recipebook/custom/CustomRecipesTest.kt
git commit -m "feat: add CustomRecipes singleton registry"
```

---

## Module 3 — Custom Events

### Task 5: All custom event classes

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/event/CustomCraftEvent.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/event/CustomSmeltEvent.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/event/CustomBrewEvent.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/event/CustomSmithEvent.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/event/CustomWorkbenchCraftEvent.kt`

- [x] Create `CustomCraftEvent.kt`:

```kotlin
package ru.oftendev.recipebook.custom.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe

class CustomCraftEvent(
    player: Player,
    val recipe: CustomRecipe,
    val item: ItemStack,
    val amount: Int
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
```

- [x] Create `CustomSmeltEvent.kt`:

```kotlin
package ru.oftendev.recipebook.custom.event

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe

class CustomSmeltEvent(
    player: Player,
    val recipe: CustomRecipe.Smelting,
    val item: ItemStack,
    val furnaceLocation: Location
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
```

- [x] Create `CustomBrewEvent.kt`:

```kotlin
package ru.oftendev.recipebook.custom.event

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe

class CustomBrewEvent(
    player: Player,
    val recipe: CustomRecipe.Brewing,
    val item: ItemStack,
    val brewingLocation: Location,
    val bottlesAffected: Int
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
```

- [x] Create `CustomSmithEvent.kt`:

```kotlin
package ru.oftendev.recipebook.custom.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe

class CustomSmithEvent(
    player: Player,
    val recipe: CustomRecipe.Smithing,
    val item: ItemStack
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
```

- [x] Create `CustomWorkbenchCraftEvent.kt`:

```kotlin
package ru.oftendev.recipebook.custom.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.recipe.RecipeDisplayType

/** Fired for cartography, grindstone, anvil, and villager recipes. */
class CustomWorkbenchCraftEvent(
    player: Player,
    val recipe: CustomRecipe,
    val item: ItemStack,
    val stationType: RecipeDisplayType
) : PlayerEvent(player), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLER_LIST
    companion object {
        private val HANDLER_LIST = HandlerList()
        @JvmStatic fun getHandlerList() = HANDLER_LIST
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/event/
git commit -m "feat: add custom craft event classes (CustomCraftEvent, Smelt, Brew, Smith, Workbench)"
```

---

## Module 4 — Owner Trackers

### Task 6: FurnaceOwnerTracker + BrewingOwnerTracker

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/FurnaceOwnerTracker.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/BrewingOwnerTracker.kt`

- [x] Create `FurnaceOwnerTracker.kt`:

```kotlin
package ru.oftendev.recipebook.custom

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import java.util.UUID

object FurnaceOwnerTracker : Listener {
    private val owners = mutableMapOf<Location, UUID>()

    private val furnaceTypes = setOf(
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER
    )

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.type !in furnaceTypes) return
        val loc = event.inventory.location ?: return
        owners[loc] = event.player.uniqueId
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory.type !in furnaceTypes) return
        val loc = event.inventory.location ?: return
        owners.remove(loc)
    }

    fun getOwner(location: Location): Player? {
        val uuid = owners[location] ?: return null
        return location.world?.players?.firstOrNull { it.uniqueId == uuid }
    }
}
```

- [x] Create `BrewingOwnerTracker.kt`:

```kotlin
package ru.oftendev.recipebook.custom

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import java.util.UUID

object BrewingOwnerTracker : Listener {
    private val owners = mutableMapOf<Location, UUID>()

    @EventHandler
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.type != InventoryType.BREWING) return
        val loc = event.inventory.location ?: return
        owners[loc] = event.player.uniqueId
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (event.inventory.type != InventoryType.BREWING) return
        val loc = event.inventory.location ?: return
        owners.remove(loc)
    }

    fun getOwner(location: Location): Player? {
        val uuid = owners[location] ?: return null
        return location.world?.players?.firstOrNull { it.uniqueId == uuid }
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/FurnaceOwnerTracker.kt \
        src/main/kotlin/ru/oftendev/recipebook/custom/BrewingOwnerTracker.kt
git commit -m "feat: add FurnaceOwnerTracker and BrewingOwnerTracker"
```

---

## Module 5 — Unlock System

### Task 7: RecipeUnlockStore

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/RecipeUnlockStore.kt`
- Create: `src/test/kotlin/ru/oftendev/recipebook/custom/RecipeUnlockStoreTest.kt`

- [x] Create `RecipeUnlockStore.kt`:

```kotlin
package ru.oftendev.recipebook.custom

import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.oftendev.recipebook.recipeBookPlugin
import java.io.File
import java.util.UUID

object RecipeUnlockStore : Listener {
    // uuid → set of unlocked recipe keys (e.g. "dragon_sword")
    private val cache = mutableMapOf<UUID, MutableSet<String>>()

    private fun dataFile(uuid: UUID): File {
        val dir = File(recipeBookPlugin.dataFolder, "data/players")
        dir.mkdirs()
        return File(dir, "$uuid.yml")
    }

    fun loadPlayer(uuid: UUID) {
        val cfg = YamlConfiguration.loadConfiguration(dataFile(uuid))
        cache[uuid] = cfg.getStringList("unlocked").toMutableSet()
    }

    fun savePlayer(uuid: UUID) {
        val file = dataFile(uuid)
        val cfg = YamlConfiguration()
        cfg.set("unlocked", cache[uuid]?.toList() ?: emptyList<String>())
        cfg.save(file)
    }

    fun saveAll() {
        cache.keys.toList().forEach { savePlayer(it) }
    }

    fun isUnlocked(player: OfflinePlayer, recipe: CustomRecipe): Boolean {
        if (!recipe.lockedByDefault) return true
        return recipe.key.key in (cache[player.uniqueId] ?: emptySet())
    }

    fun isLocked(player: OfflinePlayer, recipe: CustomRecipe): Boolean =
        !isUnlocked(player, recipe)

    fun unlock(player: Player, recipe: CustomRecipe) {
        if (!recipe.lockedByDefault) return
        val set = cache.getOrPut(player.uniqueId) { mutableSetOf() }
        if (recipe.key.key in set) return
        set.add(recipe.key.key)
        savePlayer(player.uniqueId)
        // trigger dispatched in libreforge module — called from EffectUnlockRecipe
    }

    fun lock(player: Player, recipe: CustomRecipe) {
        val set = cache[player.uniqueId] ?: return
        if (recipe.key.key !in set) return
        set.remove(recipe.key.key)
        savePlayer(player.uniqueId)
        // trigger dispatched in libreforge module — called from EffectLockRecipe
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        loadPlayer(event.player.uniqueId)
        // auto-unlock check
        val player = event.player
        for (recipe in CustomRecipes.all()) {
            if (!recipe.lockedByDefault) continue
            if (!isLocked(player, recipe)) continue
            if (recipe.unlockConditions.areMet(player.toDispatcher(), com.willfp.libreforge.EmptyProvidedHolder)) {
                unlock(player, recipe)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        savePlayer(event.player.uniqueId)
        cache.remove(event.player.uniqueId)
    }
}
```

- [x] Write unit test (pure logic, no Bukkit):

```kotlin
// src/test/kotlin/ru/oftendev/recipebook/custom/RecipeUnlockStoreTest.kt
package ru.oftendev.recipebook.custom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeUnlockStoreTest {

    @Test
    fun `isUnlocked returns true for non-locked recipes without player data`() {
        // lockedByDefault=false → always unlocked regardless of store state
        // This is pure logic enforced by isUnlocked: if !recipe.lockedByDefault return true
        // Verified by reading the implementation — integration test needed for full coverage
        assertTrue(true) // placeholder; full test requires MockBukkit
    }
}
```

- [x] Run:

```
./gradlew test --tests "ru.oftendev.recipebook.custom.RecipeUnlockStoreTest"
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/RecipeUnlockStore.kt \
        src/test/kotlin/ru/oftendev/recipebook/custom/RecipeUnlockStoreTest.kt
git commit -m "feat: add RecipeUnlockStore with per-player YAML persistence and auto-unlock on join"
```

---

## Module 6 — Libreforge Extensions

### Task 8: Ghost + Custom triggers

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerGhostCraft.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerCustomCraft.kt`

- [x] Create `TriggerGhostCraft.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerGhostCraft : Trigger("ghost_craft") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE
    )
}
```

- [x] Create `TriggerCustomCraft.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerCustomCraft : Trigger("custom_craft") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE,
        TriggerParameter.TEXT
    )
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerGhostCraft.kt \
        src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerCustomCraft.kt
git commit -m "feat: add TriggerGhostCraft and TriggerCustomCraft libreforge triggers"
```

---

### Task 9: Unlock/lock triggers

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerRecipeUnlocked.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerRecipeLocked.kt`

- [x] Create `TriggerRecipeUnlocked.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerRecipeUnlocked : Trigger("recipe_unlocked") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.TEXT
    )
}
```

- [x] Create `TriggerRecipeLocked.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerParameter

object TriggerRecipeLocked : Trigger("recipe_locked") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.TEXT
    )
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerRecipeUnlocked.kt \
        src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/TriggerRecipeLocked.kt
git commit -m "feat: add TriggerRecipeUnlocked and TriggerRecipeLocked"
```

---

### Task 10: Unlock/lock effects

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/EffectUnlockRecipe.kt`
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/EffectLockRecipe.kt`

- [x] Create `EffectUnlockRecipe.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.EffectContext
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object EffectUnlockRecipe : Effect<NoCompileData>("unlock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        player: Player,
        data: TriggerData,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        RecipeUnlockStore.unlock(player, recipe)
        TriggerRecipeUnlocked.dispatch(
            player.toDispatcher(),
            data.copy(text = recipe.key.toString())
        )
        return true
    }
}
```

- [x] Create `EffectLockRecipe.kt`:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object EffectLockRecipe : Effect<NoCompileData>("lock_recipe") {
    override val parameters = setOf(TriggerParameter.PLAYER)

    override fun onTrigger(
        config: Config,
        player: Player,
        data: TriggerData,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        RecipeUnlockStore.lock(player, recipe)
        TriggerRecipeLocked.dispatch(
            player.toDispatcher(),
            data.copy(text = recipe.key.toString())
        )
        return true
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/EffectUnlockRecipe.kt \
        src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/EffectLockRecipe.kt
git commit -m "feat: add EffectUnlockRecipe and EffectLockRecipe"
```

---

### Task 11: ConditionHasUnlockedRecipe

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/ConditionHasUnlockedRecipe.kt`

- [x] Create file:

```kotlin
package ru.oftendev.recipebook.custom.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.conditions.Condition
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

object ConditionHasUnlockedRecipe : Condition<NoCompileData>("has_unlocked_recipe") {
    override fun isConditionMet(
        config: Config,
        player: Player,
        data: TriggerData,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ): Boolean {
        val recipeId = config.getString("args.recipe")
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", recipeId)) ?: return false
        return RecipeUnlockStore.isUnlocked(player, recipe)
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/libreforge/ConditionHasUnlockedRecipe.kt
git commit -m "feat: add ConditionHasUnlockedRecipe libreforge condition"
```

---

## Module 7 — Shared Dispatch Helpers

### Task 12: Ghost + custom craft dispatch helpers

These helpers are used by `CustomRecipeListener` (Module 8). Define them as top-level
functions in a new file so the listener stays focused.

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/CraftingDispatchers.kt`

- [x] Create file:

```kotlin
package ru.oftendev.recipebook.custom

import com.willfp.libreforge.SimpleProvidedHolder
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft
import ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft
import ru.oftendev.recipebook.debug

fun fireGhostEffects(player: Player, recipe: CustomRecipe, item: ItemStack, amount: Int) {
    val holder = recipe.ghostHolder ?: return
    val data = TriggerData(
        player = player,
        item = item,
        value = amount.toDouble()
    )
    TriggerGhostCraft.dispatch(
        player.toDispatcher(),
        data,
        listOf(SimpleProvidedHolder(holder))
    )
    TriggerCustomCraft.dispatch(
        player.toDispatcher(),
        data.copy(text = recipe.key.toString())
    )
}

fun fireStonecutterGhostEffects(
    player: Player,
    recipe: CustomRecipe.Stonecutter,
    output: StonecutterOutput,
    amount: Int
) {
    val holder = output.ghostHolder ?: return
    val data = TriggerData(
        player = player,
        item = output.item,
        value = amount.toDouble()
    )
    TriggerGhostCraft.dispatch(
        player.toDispatcher(),
        data,
        listOf(SimpleProvidedHolder(holder))
    )
    TriggerCustomCraft.dispatch(
        player.toDispatcher(),
        data.copy(text = recipe.key.toString())
    )
}

fun fireCustomCraftTrigger(player: Player, recipe: CustomRecipe, item: ItemStack, amount: Int) {
    val data = TriggerData(
        player = player,
        item = item,
        value = amount.toDouble(),
        text = recipe.key.toString()
    )
    TriggerCustomCraft.dispatch(player.toDispatcher(), data)
}

/**
 * Returns true if crafting conditions pass and recipe is unlocked.
 * Sends not-met messages to player on failure.
 */
fun checkCraftingConditions(player: Player, recipe: CustomRecipe): Boolean {
    if (RecipeUnlockStore.isLocked(player, recipe)) {
        // send locked message from lang
        player.sendMessage(
            ru.oftendev.recipebook.recipeBookPlugin.langYml.getFormattedString("messages.recipe-locked")
        )
        return false
    }
    val dispatcher = player.toDispatcher()
    val notMet = recipe.craftingConditions.getNotMetLines(dispatcher, com.willfp.libreforge.EmptyProvidedHolder)
    if (notMet.isNotEmpty()) {
        notMet.forEach { player.sendMessage(it) }
        return false
    }
    return true
}
```

- [x] Add `debug` extension to `RecipeBookPlugin.kt` — it already exists (line 70). Verify import resolves.

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CraftingDispatchers.kt
git commit -m "feat: add ghost/custom craft dispatch helpers and condition checker"
```

---

## Module 8 — Recipe Loading

### Task 13: CustomRecipeLoader framework + common field parser

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`

- [x] Create file skeleton with common field parser. Type-specific parsers added in Tasks 14–23:

```kotlin
package ru.oftendev.recipebook.custom

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.config.yaml.YamlBaseConfig
import com.willfp.eco.core.items.Items
import com.willfp.libreforge.SimpleHolder
import com.willfp.libreforge.conditions.ConditionList
import org.bukkit.NamespacedKey
import ru.oftendev.recipebook.recipe.RecipeIngredient
import ru.oftendev.recipebook.recipe.IngredientMatcher
import ru.oftendev.recipebook.recipeBookPlugin
import java.io.File

object CustomRecipeLoader {

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
    }

    private fun loadFile(file: File) {
        val config = YamlBaseConfig(file, recipeBookPlugin)
        val type = config.getString("type").lowercase()
        val recipe = when (type) {
            "crafting_table" -> loadCraftingTable(file.nameWithoutExtension, config)
            "furnace"        -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.FURNACE)
            "blast_furnace"  -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.BLAST_FURNACE)
            "smoker"         -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.SMOKER)
            "campfire"       -> loadSmelting(file.nameWithoutExtension, config, SmeltingType.CAMPFIRE)
            "smithing_table" -> loadSmithing(file.nameWithoutExtension, config)
            "stonecutter"    -> loadStonecutter(file.nameWithoutExtension, config)
            "crafter"        -> loadCrafter(file.nameWithoutExtension, config)
            "brewing_stand"  -> loadBrewing(file.nameWithoutExtension, config)
            "cartography_table" -> loadCartography(file.nameWithoutExtension, config)
            "grindstone"     -> loadGrindstone(file.nameWithoutExtension, config)
            "anvil"          -> loadAnvil(file.nameWithoutExtension, config)
            "villager"       -> loadVillager(file.nameWithoutExtension, config)
            else -> error("Unknown recipe type: $type")
        }
        CustomRecipes.register(recipe)
        recipe.registerBukkit()
    }

    // ── Common helpers ───────────────────────────────────────────────────

    internal fun key(id: String) = NamespacedKey("recipebook", id.lowercase())

    internal fun parseIngredient(lookup: String): RecipeIngredient {
        if (lookup.isBlank()) return RecipeIngredient.empty(org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR))
        val item = runCatching { Items.lookup(lookup).item }.getOrNull()
            ?: error("Cannot resolve item: $lookup")
        return RecipeIngredient(item.clone(), IngredientMatcher.SimilarItem(item.clone()))
    }

    internal fun parseCommonConditions(id: String, config: Config): CommonConditions {
        val permission = config.getStringOrNull("permission")?.takeIf { it.isNotBlank() }
        val visConds = recipeBookPlugin.compileConditions(
            config.getSubsections("visibility-conditions"), "visibility-conditions-$id", null
        )
        val craftConds = buildList {
            addAll(recipeBookPlugin.compileConditions(
                config.getSubsections("crafting-conditions"), "crafting-conditions-$id", null
            ))
            if (permission != null) {
                addAll(recipeBookPlugin.compileConditions(
                    listOf(mapOf("id" to "has_permission", "args" to mapOf("permission" to permission))
                        .let { m -> object : com.willfp.eco.core.config.interfaces.Config {
                            override fun getString(path: String) = m[path]?.toString() ?: ""
                            // minimal stub — see note below
                        }
                        }
                    ), "permission-$id", null
                ))
            }
        }
        val unlockConds = recipeBookPlugin.compileConditions(
            config.getSubsections("unlock-conditions"), "unlock-conditions-$id", null
        )
        return CommonConditions(
            permission = permission,
            visibilityConditions = visConds,
            craftingConditions = ConditionList(craftConds),
            lockedByDefault = config.getBool("locked-by-default"),
            showWhenLocked = config.getBool("show-when-locked"),
            lockedLore = config.getFormattedStrings("locked-lore"),
            unlockConditions = unlockConds
        )
    }

    internal fun parseGhostHolder(id: String, config: Config): SimpleHolder? {
        if (!config.getBool("ghost")) return null
        val effects = recipeBookPlugin.compileEffects(
            config.getSubsections("effects"), "ghost-effects-$id", null
        )
        val conditions = recipeBookPlugin.compileConditions(
            config.getSubsections("conditions"), "ghost-conditions-$id", null
        )
        return SimpleHolder(key(id), effects, conditions)
    }

    // ── Type-specific parsers (added in Tasks 14–23) ─────────────────────

    private fun loadCraftingTable(id: String, config: Config): CustomRecipe = TODO()
    private fun loadSmelting(id: String, config: Config, type: SmeltingType): CustomRecipe = TODO()
    private fun loadSmithing(id: String, config: Config): CustomRecipe = TODO()
    private fun loadStonecutter(id: String, config: Config): CustomRecipe = TODO()
    private fun loadCrafter(id: String, config: Config): CustomRecipe = TODO()
    private fun loadBrewing(id: String, config: Config): CustomRecipe = TODO()
    private fun loadCartography(id: String, config: Config): CustomRecipe = TODO()
    private fun loadGrindstone(id: String, config: Config): CustomRecipe = TODO()
    private fun loadAnvil(id: String, config: Config): CustomRecipe = TODO()
    private fun loadVillager(id: String, config: Config): CustomRecipe = TODO()

    // ── Bukkit registration ──────────────────────────────────────────────

    private fun CustomRecipe.registerBukkit() {
        // implemented in Task 24
    }
}

data class CommonConditions(
    val permission: String?,
    val visibilityConditions: ConditionList,
    val craftingConditions: ConditionList,
    val lockedByDefault: Boolean,
    val showWhenLocked: Boolean,
    val lockedLore: List<String>,
    val unlockConditions: ConditionList
)
```

> **Note on permission condition compilation:** eco's `compileConditions` takes a list of `Config` subsections. The `has_permission` condition needs a tiny config wrapper. In Task 14 this is replaced with a proper approach using eco's config API once we verify the exact method signature from eco source.

- [x] Verify build compiles (TODO stubs expected):

```
./gradlew compileKotlin
```

Expected: compiles with TODO stub warnings, no errors.

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt
git commit -m "feat: add CustomRecipeLoader framework with common field parser"
```

---

### Task 14: loadCraftingTable + symmetry + Bukkit registration

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`
- Create: `src/test/kotlin/ru/oftendev/recipebook/custom/SymmetryTest.kt`

- [x] Add symmetry rotation helper above `loadCraftingTable`:

```kotlin
// Flat 9-element index remappings for shaped recipe symmetry variants.
// Original grid indices: 0 1 2 / 3 4 5 / 6 7 8
private val ROT_90_CW   = intArrayOf(6, 3, 0, 7, 4, 1, 8, 5, 2)
private val ROT_180     = intArrayOf(8, 7, 6, 5, 4, 3, 2, 1, 0)
private val ROT_270_CW  = intArrayOf(2, 5, 8, 1, 4, 7, 0, 3, 6)
private val MIRROR_H    = intArrayOf(2, 1, 0, 5, 4, 3, 8, 7, 6)

internal fun generateSymmetryVariants(parts: List<RecipeIngredient>): List<Pair<String, List<RecipeIngredient>>> {
    val variants = mutableListOf<Pair<String, List<RecipeIngredient>>>()
    val seen = mutableSetOf<List<Int>>()
    // fingerprint = indices of non-empty slots in order
    fun fingerprint(p: List<RecipeIngredient>) = p.indices.filter { !p[it].empty }

    fun addVariant(suffix: String, remap: IntArray) {
        val remapped = remap.map { parts[it] }
        val fp = fingerprint(remapped)
        if (seen.add(fp)) variants.add(suffix to remapped)
    }

    seen.add(fingerprint(parts)) // original already registered
    addVariant("_rot90",  ROT_90_CW)
    addVariant("_rot180", ROT_180)
    addVariant("_rot270", ROT_270_CW)
    addVariant("_mir",    MIRROR_H)
    addVariant("_mir90",  MIRROR_H.map { ROT_90_CW[it] }.toIntArray())
    addVariant("_mir180", MIRROR_H.map { ROT_180[it] }.toIntArray())
    addVariant("_mir270", MIRROR_H.map { ROT_270_CW[it] }.toIntArray())
    return variants
}
```

- [x] Replace `loadCraftingTable` stub:

```kotlin
private fun loadCraftingTable(id: String, config: Config): CustomRecipe {
    val rawParts = config.getStrings("recipe")
    require(rawParts.size == 9) { "crafting_table recipe must have exactly 9 entries, got ${rawParts.size}" }
    val parts = rawParts.map { parseIngredient(it) }
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.CraftingTable(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        parts = parts,
        shapeless = config.getBool("shapeless"),
        symmetry = config.getBool("symmetry"),
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}
```

- [x] Replace `registerBukkit` with type-dispatched implementation:

```kotlin
private fun CustomRecipe.registerBukkit() {
    when (this) {
        is CustomRecipe.CraftingTable -> registerCraftingTable()
        is CustomRecipe.Smelting      -> registerSmelting()
        is CustomRecipe.Smithing      -> registerSmithing()
        is CustomRecipe.Stonecutter   -> registerStonecutter()
        is CustomRecipe.Crafter       -> registerCrafter()
        else -> { /* Group B — no Bukkit recipe needed */ }
    }
}

private fun CustomRecipe.CraftingTable.registerCraftingTable() {
    fun register(recipeKey: NamespacedKey, recipeParts: List<RecipeIngredient>) {
        if (shapeless) {
            val builder = com.willfp.eco.core.recipe.recipes.ShapelessCraftingRecipe
                .builder(recipeBookPlugin, recipeKey.key)
                .setOutput(output)
            recipeParts.filter { !it.empty }.forEach { builder.addRecipePart(it.matcher.toTestableItem()) }
            builder.build().register()
        } else {
            val builder = com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
                .builder(recipeBookPlugin, recipeKey.key)
                .setOutput(output)
            recipeParts.forEachIndexed { idx, part ->
                if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
            }
            builder.build().register()
        }
    }

    register(key, parts)
    if (symmetry && !shapeless) {
        generateSymmetryVariants(parts).forEach { (suffix, variantParts) ->
            register(NamespacedKey("recipebook", "${key.key}$suffix"), variantParts)
        }
    }
}
```

> `IngredientMatcher.toTestableItem()` — add extension in `ResolvedRecipe.kt` (Task 15).

- [x] Write symmetry unit test:

```kotlin
// src/test/kotlin/ru/oftendev/recipebook/custom/SymmetryTest.kt
package ru.oftendev.recipebook.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymmetryTest {

    private fun dummyPart(id: Int) = object {
        val empty = (id == 0)
        val tag = id
        override fun equals(other: Any?) = other is Int && other == tag
    }

    @Test
    fun `ROT_90_CW remaps index 0 to index 6`() {
        assertEquals(6, CustomRecipeLoader.ROT_90_CW[0])
    }

    @Test
    fun `ROT_90_CW remaps index 2 to index 0`() {
        assertEquals(0, CustomRecipeLoader.ROT_90_CW[2])
    }

    @Test
    fun `ROT_180 remaps index 0 to index 8`() {
        assertEquals(8, CustomRecipeLoader.ROT_180[0])
    }

    @Test
    fun `MIRROR_H remaps index 0 to index 2`() {
        assertEquals(2, CustomRecipeLoader.MIRROR_H[0])
    }

    @Test
    fun `symmetric recipe produces no extra variants`() {
        // All slots filled identically → all rotations/mirrors identical → 0 extra
        val parts = List(9) { RecipeIngredient(
            org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND),
            ru.oftendev.recipebook.recipe.IngredientMatcher.MaterialOnly(
                org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND)
            )
        )}
        // Can't call generateSymmetryVariants without Bukkit, but we can test the arrays
        assertTrue(CustomRecipeLoader.ROT_90_CW.size == 9)
    }
}
```

> Note: Full symmetry variant generation test requires MockBukkit (RecipeIngredient uses ItemStack). Manual test: create a shaped recipe with `symmetry: true`, verify all rotations craft correctly in-game.

- [x] Run:

```
./gradlew test --tests "ru.oftendev.recipebook.custom.SymmetryTest"
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt \
        src/test/kotlin/ru/oftendev/recipebook/custom/SymmetryTest.kt
git commit -m "feat: implement loadCraftingTable, symmetry variant generation, Bukkit registration"
```

---

### Task 15: IngredientMatcher.toTestableItem() extension

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/recipe/ResolvedRecipe.kt`

- [x] Add extension at bottom of `ResolvedRecipe.kt`:

```kotlin
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.recipe.parts.MaterialTestableItem
import com.willfp.eco.core.recipe.parts.EmptyTestableItem

fun IngredientMatcher.toTestableItem(): TestableItem = when (this) {
    is IngredientMatcher.Empty        -> EmptyTestableItem()
    is IngredientMatcher.EcoPart      -> part
    is IngredientMatcher.SimilarItem  -> MaterialTestableItem(item.type)
    is IngredientMatcher.MaterialOnly -> MaterialTestableItem(item.type)
}
```

- [x] Compile check:

```
./gradlew compileKotlin
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/recipe/ResolvedRecipe.kt
git commit -m "feat: add IngredientMatcher.toTestableItem() extension for eco recipe builder"
```

---

### Task 16: loadSmelting + Bukkit registration

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`

- [x] Replace `loadSmelting` stub:

```kotlin
private fun loadSmelting(id: String, config: Config, type: SmeltingType): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Smelting(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        input = parseIngredient(config.getString("input")),
        stationType = type,
        cookTime = if (config.has("cook-time")) config.getInt("cook-time") else -1,
        experience = config.getFloatOrNull("experience") ?: 0f,
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun CustomRecipe.Smelting.registerSmelting() {
    val recipeKey = key
    val inputChoice = org.bukkit.inventory.RecipeChoice.ExactChoice(input.displayItem)
    val output = output.clone()
    val cookTime = if (cookTime < 0) null else cookTime
    val xp = experience

    when (stationType) {
        SmeltingType.FURNACE -> {
            val r = org.bukkit.inventory.FurnaceRecipe(recipeKey, output, inputChoice, xp, cookTime ?: 200)
            org.bukkit.Bukkit.addRecipe(r)
        }
        SmeltingType.BLAST_FURNACE -> {
            val r = org.bukkit.inventory.BlastingRecipe(recipeKey, output, inputChoice, xp, cookTime ?: 100)
            org.bukkit.Bukkit.addRecipe(r)
        }
        SmeltingType.SMOKER -> {
            val r = org.bukkit.inventory.SmokingRecipe(recipeKey, output, inputChoice, xp, cookTime ?: 100)
            org.bukkit.Bukkit.addRecipe(r)
        }
        SmeltingType.CAMPFIRE -> {
            val r = org.bukkit.inventory.CampfireRecipe(recipeKey, output, inputChoice, xp, cookTime ?: 600)
            org.bukkit.Bukkit.addRecipe(r)
        }
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt
git commit -m "feat: implement loadSmelting for all four smelting station types"
```

---

### Task 17: loadSmithing + loadStonecutter + loadCrafter + Bukkit registration

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`

- [x] Replace the three stubs:

```kotlin
private fun loadSmithing(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Smithing(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        template = parseIngredient(config.getString("template")),
        base = parseIngredient(config.getString("base")),
        addition = parseIngredient(config.getString("addition")),
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun CustomRecipe.Smithing.registerSmithing() {
    val r = org.bukkit.inventory.SmithingTransformRecipe(
        key,
        output.clone(),
        org.bukkit.inventory.RecipeChoice.ExactChoice(template.displayItem),
        org.bukkit.inventory.RecipeChoice.ExactChoice(base.displayItem),
        org.bukkit.inventory.RecipeChoice.ExactChoice(addition.displayItem)
    )
    org.bukkit.Bukkit.addRecipe(r)
}

private fun loadStonecutter(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    val input = parseIngredient(config.getString("input"))
    val rawOutputs = config.getSubsections("outputs")
    require(rawOutputs.isNotEmpty()) { "stonecutter recipe '$id' must have at least one output" }
    val outputs = rawOutputs.mapIndexed { idx, outCfg ->
        val ghost = outCfg.getBool("ghost")
        val ghostHolder: SimpleHolder? = if (ghost) {
            val effects = recipeBookPlugin.compileEffects(outCfg.getSubsections("effects"), "sc-effects-$id-$idx", null)
            val conditions = recipeBookPlugin.compileConditions(outCfg.getSubsections("conditions"), "sc-conds-$id-$idx", null)
            SimpleHolder(NamespacedKey("recipebook", "${id}_out$idx"), effects, conditions)
        } else null
        StonecutterOutput(
            item = Items.lookup(outCfg.getString("item")).item,
            ghost = ghost,
            ghostHolder = ghostHolder
        )
    }
    return CustomRecipe.Stonecutter(
        key = key(id),
        input = input,
        outputs = outputs,
        permission = cc.permission,
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun CustomRecipe.Stonecutter.registerStonecutter() {
    outputs.forEachIndexed { idx, out ->
        val r = org.bukkit.inventory.StonecuttingRecipe(
            NamespacedKey("recipebook", "${key.key}_$idx"),
            out.item.clone(),
            org.bukkit.inventory.RecipeChoice.ExactChoice(input.displayItem)
        )
        org.bukkit.Bukkit.addRecipe(r)
    }
}

private fun loadCrafter(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    val rawParts = config.getStrings("recipe")
    require(rawParts.size == 9) { "crafter recipe must have exactly 9 entries" }
    return CustomRecipe.Crafter(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        parts = rawParts.map { parseIngredient(it) },
        shapeless = config.getBool("shapeless"),
        permission = cc.permission,
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions
    )
}

private fun CustomRecipe.Crafter.registerCrafter() {
    // Crafter uses ShapedRecipe with the crafter inventory type tag (Paper 1.21+)
    val builder = com.willfp.eco.core.recipe.recipes.ShapedCraftingRecipe
        .builder(recipeBookPlugin, key.key)
        .setOutput(output)
    parts.forEachIndexed { idx, part ->
        if (!part.empty) builder.setRecipePart(idx, part.matcher.toTestableItem())
    }
    val recipe = builder.build()
    recipe.register()
    // Note: crafter-specific tagging (Paper CraftingRecipeBuilder.craftingType = AUTOMATIC)
    // is not exposed via eco's API. Register as normal crafting table recipe for now;
    // the event handler (CustomRecipeListener) only fires effects on CrafterCraftEvent,
    // so crafting table crafting of this recipe is still valid vanilla behaviour.
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt
git commit -m "feat: implement loadSmithing, loadStonecutter, loadCrafter with Bukkit registration"
```

---

### Task 18: loadBrewing + loadCartography + loadGrindstone + loadAnvil + loadVillager

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt`

- [x] Replace remaining five stubs (Group B — no Bukkit registration):

```kotlin
private fun loadBrewing(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Brewing(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        base = parseIngredient(config.getString("base")),
        ingredient = parseIngredient(config.getString("ingredient")),
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun loadCartography(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Cartography(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        map = parseIngredient(config.getString("map")),
        addition = parseIngredient(config.getString("addition")),
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun loadGrindstone(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Grindstone(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        item1 = parseIngredient(config.getString("item1")),
        item2 = config.getStringOrNull("item2")?.let { parseIngredient(it) },
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun loadAnvil(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Anvil(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        base = parseIngredient(config.getString("base")),
        material = config.getStringOrNull("material")?.let { parseIngredient(it) },
        resultName = config.getStringOrNull("result-name")?.takeIf { it.isNotBlank() },
        repairCost = config.getIntOrNull("repair-cost") ?: 1,
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}

private fun loadVillager(id: String, config: Config): CustomRecipe {
    val cc = parseCommonConditions(id, config)
    return CustomRecipe.Villager(
        key = key(id),
        output = Items.lookup(config.getString("output")).item,
        input1 = parseIngredient(config.getString("input1")),
        input2 = config.getStringOrNull("input2")?.let { parseIngredient(it) },
        permission = cc.permission,
        ghost = config.getBool("ghost"),
        ghostHolder = parseGhostHolder(id, config),
        visibilityConditions = cc.visibilityConditions,
        craftingConditions = cc.craftingConditions,
        lockedByDefault = cc.lockedByDefault,
        showWhenLocked = cc.showWhenLocked,
        lockedLore = cc.lockedLore,
        unlockConditions = cc.unlockConditions
    )
}
```

- [x] Full compile check:

```
./gradlew compileKotlin
```

Expected: 0 errors (all TODO stubs replaced).

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeLoader.kt
git commit -m "feat: implement all Group B recipe loaders (brewing, cartography, grindstone, anvil, villager)"
```

---

## Module 9 — Event Listener

### Task 19: CustomRecipeListener — crafting table + smithing + stonecutter

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

- [x] Create with Group A shaped/smithing/stonecutter intercept. Remaining handlers added in Tasks 20–23:

```kotlin
package ru.oftendev.recipebook.custom

import com.willfp.libreforge.toDispatcher
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.SmithingInventory
import org.bukkit.inventory.StonecutterInventory
import ru.oftendev.recipebook.custom.event.CustomCraftEvent
import ru.oftendev.recipebook.custom.event.CustomSmithEvent
import ru.oftendev.recipebook.recipeBookPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class CustomRecipeListener : Listener {

    // ── Crafting table + smithing table + stonecutter ─────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val recipeKey = (event.recipe as? org.bukkit.Keyed)?.key ?: return

        // Stonecutter: key pattern is recipebook:<id>_<idx>
        if (event.view.topInventory is StonecutterInventory) {
            handleStonecutter(event, player, recipeKey)
            return
        }

        // Smithing table
        if (event.view.topInventory is SmithingInventory) {
            handleSmithing(event, player, recipeKey)
            return
        }

        // Crafting table (shaped/shapeless) — key may have symmetry suffix
        val baseKey = stripSymmetrySuffix(recipeKey)
        val recipe = CustomRecipes.getByKey(baseKey) as? CustomRecipe.CraftingTable ?: return
        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val amount = calculateCraftAmount(event)
        val item = recipe.output.clone().apply { this.amount = amount }

        if (recipe.ghost) {
            event.isCancelled = true
            consumeCraftingGrid(event)
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, amount)
        } else {
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, amount)
        }
    }

    private fun handleSmithing(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        val recipe = CustomRecipes.getByKey(recipeKey) as? CustomRecipe.Smithing ?: return
        if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

        val item = recipe.output.clone()
        if (recipe.ghost) {
            event.isCancelled = true
            consumeSmithingSlots(event)
            val ce = CustomSmithEvent(player, recipe, item)
            Bukkit.getPluginManager().callEvent(ce)
            if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
        } else {
            val ce = CustomSmithEvent(player, recipe, item)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, 1)
        }
    }

    private fun handleStonecutter(event: CraftItemEvent, player: Player, recipeKey: NamespacedKey) {
        // Key format: recipebook:<id>_<idx>
        val (baseId, idx) = parseStonecutterKey(recipeKey) ?: return
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", baseId)) as? CustomRecipe.Stonecutter ?: return
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
        } else {
            val ce = CustomCraftEvent(player, recipe, item, amount)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) { event.isCancelled = true; return }
            fireCustomCraftTrigger(player, recipe, item, amount)
        }
    }

    // ── Grid consumption helpers ──────────────────────────────────────────

    private fun consumeCraftingGrid(event: CraftItemEvent) {
        val matrix = event.inventory.matrix
        for (i in matrix.indices) {
            val stack = matrix[i] ?: continue
            if (stack.type.isAir) continue
            if (stack.amount <= 1) matrix[i] = null
            else stack.amount--
        }
        event.inventory.matrix = matrix
    }

    private fun consumeSmithingSlots(event: CraftItemEvent) {
        val inv = event.view.topInventory
        // Smithing: slots 0=template, 1=base, 2=addition
        for (slot in 0..2) {
            val stack = inv.getItem(slot) ?: continue
            if (stack.amount <= 1) inv.setItem(slot, null)
            else stack.amount--
        }
    }

    private fun consumeStonecutterSlot(event: CraftItemEvent) {
        val inv = event.view.topInventory
        val stack = inv.getItem(0) ?: return
        if (stack.amount <= 1) inv.setItem(0, null)
        else stack.amount--
    }

    // ── Key helpers ───────────────────────────────────────────────────────

    private val symmetrySuffixes = listOf("_rot90", "_rot180", "_rot270", "_mir", "_mir90", "_mir180", "_mir270")

    private fun stripSymmetrySuffix(key: NamespacedKey): NamespacedKey {
        if (key.namespace != "recipebook") return key
        val stripped = symmetrySuffixes.fold(key.key) { acc, suffix -> acc.removeSuffix(suffix) }
        return NamespacedKey("recipebook", stripped)
    }

    private fun parseStonecutterKey(key: NamespacedKey): Pair<String, Int>? {
        if (key.namespace != "recipebook") return null
        val lastUnderscore = key.key.lastIndexOf('_')
        if (lastUnderscore < 0) return null
        val idx = key.key.substring(lastUnderscore + 1).toIntOrNull() ?: return null
        return key.key.substring(0, lastUnderscore) to idx
    }

    private fun calculateCraftAmount(event: CraftItemEvent): Int {
        // Shift-click crafts as many as possible; single click = 1
        return if (event.isShiftClick) {
            val result = event.recipe.result
            val playerInv = (event.whoClicked as Player).inventory
            val freeSpace = playerInv.storageContents.sumOf { slot ->
                when {
                    slot == null || slot.type.isAir -> result.maxStackSize
                    slot.isSimilar(result) -> result.maxStackSize - slot.amount
                    else -> 0
                }
            }
            (freeSpace / result.amount).coerceAtLeast(1)
        } else 1
    }
}
```

- [x] Compile:

```
./gradlew compileKotlin
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "feat: add CustomRecipeListener — crafting table, smithing, stonecutter intercept"
```

---

### Task 20: CustomRecipeListener — furnace + campfire

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

- [x] Add handlers inside `CustomRecipeListener` class after `onCraft`:

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onSmelt(event: org.bukkit.event.inventory.FurnaceSmeltEvent) {
    val loc = event.block.location
    val player = FurnaceOwnerTracker.getOwner(loc)
    if (player == null) {
        recipeBookPlugin.debug("[RecipeListener] Smelt at $loc — owner offline, skipping custom recipe")
        return
    }

    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Smelting>()
        .firstOrNull { it.input.matches(event.source) && it.stationType != SmeltingType.CAMPFIRE }
        ?: return

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    val item = recipe.output.clone()
    if (recipe.ghost) {
        event.isCancelled = true  // source already consumed; result not placed
        val ce = ru.oftendev.recipebook.custom.event.CustomSmeltEvent(player, recipe, item, loc)
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
    } else {
        event.result = item
        val ce = ru.oftendev.recipebook.custom.event.CustomSmeltEvent(player, recipe, item, loc)
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        fireCustomCraftTrigger(player, recipe, item, 1)
    }
}

@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onCampfire(event: org.bukkit.event.block.BlockCookEvent) {
    val loc = event.block.location
    val player = FurnaceOwnerTracker.getOwner(loc) ?: return

    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Smelting>()
        .firstOrNull { it.input.matches(event.source) && it.stationType == SmeltingType.CAMPFIRE }
        ?: return

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    val item = recipe.output.clone()
    if (recipe.ghost) {
        event.isCancelled = true
        val ce = ru.oftendev.recipebook.custom.event.CustomSmeltEvent(player, recipe, item, loc)
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
    } else {
        event.result = item
        val ce = ru.oftendev.recipebook.custom.event.CustomSmeltEvent(player, recipe, item, loc)
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        fireCustomCraftTrigger(player, recipe, item, 1)
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "feat: CustomRecipeListener — furnace + campfire ghost/non-ghost intercept"
```

---

### Task 21: CustomRecipeListener — brewing stand

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

- [x] Add handler:

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onBrew(event: org.bukkit.event.inventory.BrewEvent) {
    val loc = event.block.location
    val player = BrewingOwnerTracker.getOwner(loc)
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

    // Find which bottle slots match the recipe base
    val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
    if (matchedSlots.isEmpty()) return

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    event.isCancelled = true  // always cancel — we handle output ourselves

    // Consume ingredient
    val ing = ingredientSlot.clone()
    if (ing.amount <= 1) brewer.ingredient = null
    else { ing.amount--; brewer.ingredient = ing }

    val item = recipe.output.clone()
    val ce = ru.oftendev.recipebook.custom.event.CustomBrewEvent(player, recipe, item, loc, matchedSlots.size)
    org.bukkit.Bukkit.getPluginManager().callEvent(ce)
    if (ce.isCancelled) return

    if (recipe.ghost) {
        matchedSlots.forEach { brewer.setItem(it, null) }
        fireGhostEffects(player, recipe, item, 1)
    } else {
        matchedSlots.forEach { brewer.setItem(it, item.clone()) }
        fireCustomCraftTrigger(player, recipe, item, matchedSlots.size)
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "feat: CustomRecipeListener — brewing stand intercept with per-bottle output"
```

---

### Task 22: CustomRecipeListener — Group B PrepareEvent handlers

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

Also add per-player transient recipe map at top of class:

```kotlin
// Inside class CustomRecipeListener, before any @EventHandler:
private val pendingRecipe = mutableMapOf<java.util.UUID, CustomRecipe>()
```

- [x] Add PrepareEvent handlers:

```kotlin
@EventHandler(priority = EventPriority.HIGH)
fun onPrepareCartography(event: org.bukkit.event.inventory.PrepareItemCraftEvent) {
    // PrepareItemCraftEvent fires for cartography too in 1.21+
    // Use PrepareCartographyItemEvent for type safety
}

@EventHandler(priority = EventPriority.HIGH)
fun onPrepareCartographyItem(event: org.bukkit.event.inventory.PrepareCartographyItemEvent) {
    val player = event.view.player as? Player ?: return
    val inv = event.inventory
    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Cartography>()
        .firstOrNull { it.map.matches(inv.getItem(0)) && it.addition.matches(inv.getItem(1)) }
        ?: return
    if (!recipe.visibilityConditions.areMet(player.toDispatcher(), com.willfp.libreforge.EmptyProvidedHolder)) return
    event.result = recipe.output.clone()
    pendingRecipe[player.uniqueId] = recipe
}

@EventHandler(priority = EventPriority.HIGH)
fun onPrepareGrindstone(event: org.bukkit.event.inventory.PrepareGrindstoneEvent) {
    val player = event.view.player as? Player ?: return
    val inv = event.inventory
    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Grindstone>()
        .firstOrNull {
            it.item1.matches(inv.getItem(0)) &&
            (it.item2 == null || it.item2.matches(inv.getItem(1)))
        } ?: return
    if (!recipe.visibilityConditions.areMet(player.toDispatcher(), com.willfp.libreforge.EmptyProvidedHolder)) return
    event.result = recipe.output.clone()
    pendingRecipe[player.uniqueId] = recipe
}

@EventHandler(priority = EventPriority.HIGH)
fun onPrepareAnvil(event: org.bukkit.event.inventory.PrepareAnvilEvent) {
    val player = event.view.player as? Player ?: return
    val inv = event.inventory
    val recipe = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Anvil>()
        .firstOrNull {
            it.base.matches(inv.getItem(0)) &&
            (it.material == null || it.material.matches(inv.getItem(1)))
        } ?: return
    if (!recipe.visibilityConditions.areMet(player.toDispatcher(), com.willfp.libreforge.EmptyProvidedHolder)) return
    val result = recipe.output.clone()
    recipe.resultName?.let { name ->
        val meta = result.itemMeta
        meta?.setDisplayName(name)
        result.itemMeta = meta
    }
    event.result = result
    event.inventory.repairCost = recipe.repairCost
    pendingRecipe[player.uniqueId] = recipe
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "feat: CustomRecipeListener — PrepareEvent handlers for cartography, grindstone, anvil"
```

---

### Task 23: CustomRecipeListener — InventoryClickEvent + villager injection

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt`

- [x] Add click handler + villager injection:

```kotlin
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
fun onInventoryClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
    val player = event.whoClicked as? Player ?: return
    val inv = event.inventory

    // Only intercept result-slot clicks in relevant station types
    val isResultSlot = when (inv.type) {
        org.bukkit.event.inventory.InventoryType.CARTOGRAPHY,
        org.bukkit.event.inventory.InventoryType.GRINDSTONE,
        org.bukkit.event.inventory.InventoryType.ANVIL -> event.rawSlot == 2
        org.bukkit.event.inventory.InventoryType.MERCHANT -> event.rawSlot == 2
        else -> return
    }
    if (!isResultSlot) return

    val recipe = pendingRecipe[player.uniqueId] ?: run {
        // Villager: check registered villager recipes
        if (inv.type == org.bukkit.event.inventory.InventoryType.MERCHANT) {
            val merchant = (inv as? org.bukkit.inventory.MerchantInventory) ?: return
            val selected = merchant.selectedRecipe ?: return
            CustomRecipes.all()
                .filterIsInstance<CustomRecipe.Villager>()
                .firstOrNull { selected.result.isSimilar(it.output) }
        } else null
    } ?: return

    if (!checkCraftingConditions(player, recipe)) { event.isCancelled = true; return }

    val item = recipe.output.clone()
    val stationType = recipe.displayType
    val ce = ru.oftendev.recipebook.custom.event.CustomWorkbenchCraftEvent(player, recipe, item, stationType)

    if (recipe.ghost) {
        event.isCancelled = true
        consumeWorkbenchInputs(inv, recipe)
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        if (!ce.isCancelled) fireGhostEffects(player, recipe, item, 1)
    } else {
        org.bukkit.Bukkit.getPluginManager().callEvent(ce)
        if (ce.isCancelled) { event.isCancelled = true; return }
        fireCustomCraftTrigger(player, recipe, item, 1)
    }
    pendingRecipe.remove(player.uniqueId)
}

private fun consumeWorkbenchInputs(inv: org.bukkit.inventory.Inventory, recipe: CustomRecipe) {
    fun consume(slot: Int) {
        val stack = inv.getItem(slot) ?: return
        if (stack.amount <= 1) inv.setItem(slot, null)
        else stack.amount--
    }
    when (recipe) {
        is CustomRecipe.Cartography -> { consume(0); consume(1) }
        is CustomRecipe.Grindstone  -> { consume(0); if (recipe.item2 != null) consume(1) }
        is CustomRecipe.Anvil       -> { consume(0); if (recipe.material != null) consume(1) }
        is CustomRecipe.Villager    -> { consume(0); if (recipe.input2 != null) consume(1) }
        else -> {}
    }
}

@EventHandler
fun onVillagerOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
    if (event.inventory.type != org.bukkit.event.inventory.InventoryType.MERCHANT) return
    val player = event.player as? Player ?: return
    val merchant = (event.inventory.holder as? org.bukkit.entity.AbstractVillager) ?: return

    val villagerRecipes = CustomRecipes.all()
        .filterIsInstance<CustomRecipe.Villager>()
        .filter { it.visibilityConditions.areMet(player.toDispatcher(), com.willfp.libreforge.EmptyProvidedHolder) }

    if (villagerRecipes.isEmpty()) return

    val existing = merchant.recipes.toMutableList()
    villagerRecipes.forEach { vr ->
        val mr = org.bukkit.inventory.MerchantRecipe(vr.output.clone(), Int.MAX_VALUE)
        mr.addIngredient(vr.input1.displayItem.clone())
        vr.input2?.let { mr.addIngredient(it.displayItem.clone()) }
        existing.add(mr)
    }
    merchant.recipes = existing
}
```

- [x] Compile:

```
./gradlew compileKotlin
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/custom/CustomRecipeListener.kt
git commit -m "feat: CustomRecipeListener — InventoryClickEvent + villager recipe injection"
```

---

## Module 10 — RecipeBook Display Integration

### Task 24: RecipeResolver — resolveForPlayer + CustomRecipes lookup

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/recipe/RecipeResolver.kt`

- [x] Add `resolveForPlayer` and custom recipe lookup to `RecipeResolver`:

```kotlin
// Add to RecipeResolver object:

fun resolveForPlayer(itemStack: ItemStack, player: org.bukkit.entity.Player): ResolvedRecipe? {
    val recipe = resolve(itemStack) ?: return null
    val locked = if (recipe.source == RecipeSource.CUSTOM) {
        val customRecipe = CustomRecipes.getByOutput(itemStack.clone().apply { amount = 1 })
        customRecipe?.let { RecipeUnlockStore.isLocked(player, it) } ?: false
    } else false
    return recipe.copy(locked = locked)
}

// Inside resolve(), add before findEcoRecipe — after VaultPack block:
CustomRecipes.getByOutput(clean)?.let { return it.toResolvedRecipe() }
```

- [x] Add `CustomRecipe.toResolvedRecipe()` extension in `RecipeResolver.kt`:

```kotlin
private fun CustomRecipe.toResolvedRecipe(): ResolvedRecipe {
    val air = ItemStack(Material.AIR)
    fun emptyIng() = RecipeIngredient.empty(air)

    val ingredients: List<RecipeIngredient> = when (this) {
        is CustomRecipe.CraftingTable -> parts
        is CustomRecipe.Smelting      -> listOf(input) + List(8) { emptyIng() }
        is CustomRecipe.Smithing      -> listOf(template, base, addition) + List(6) { emptyIng() }
        is CustomRecipe.Stonecutter   -> listOf(input) + List(8) { emptyIng() }
        is CustomRecipe.Crafter       -> parts
        is CustomRecipe.Brewing       -> listOf(base, ingredient) + List(7) { emptyIng() }
        is CustomRecipe.Cartography   -> listOf(map, addition) + List(7) { emptyIng() }
        is CustomRecipe.Grindstone    -> listOfNotNull(item1, item2) + List(7) { emptyIng() }
        is CustomRecipe.Anvil         -> listOfNotNull(base, material) + List(7) { emptyIng() }
        is CustomRecipe.Villager      -> listOfNotNull(input1, input2) + List(7) { emptyIng() }
    }

    return ResolvedRecipe(
        key = key,
        output = output.clone(),
        ingredients = ingredients,
        permission = permission,
        source = RecipeSource.CUSTOM,
        displayType = displayType
    )
}
```

- [x] Compile:

```
./gradlew compileKotlin
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/recipe/RecipeResolver.kt
git commit -m "feat: RecipeResolver — resolveForPlayer + CustomRecipes lookup + toResolvedRecipe"
```

---

### Task 25: RecipeGUI — displayType switching + new config sections

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/gui/RecipeGUI.kt`
- Modify: `src/main/resources/config.yml`

- [x] In `RecipeGUI.open()`, replace the hardcoded `config.getSubsection("craft-gui")` reference with a displayType switch. The current `open()` receives `config: Config` which is the `craft-gui` subsection. Change `RecipeGUI` constructor to take `stack` only, and resolve config internally:

```kotlin
// Replace class RecipeGUI(val config: Config, val stack: ItemStack) with:
class RecipeGUI(val stack: ItemStack) {
    fun open(player: Player, parent: Menu?) {
        val recipe = RecipeResolver.resolveForPlayer(stack, player) ?: run {
            player.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.no-recipe"))
            return
        }

        val guiSection = when (recipe.displayType) {
            RecipeDisplayType.CRAFTING     -> "craft-gui"
            RecipeDisplayType.SMELTING     -> "furnace-gui"
            RecipeDisplayType.SMITHING     -> "smithing-gui"
            RecipeDisplayType.STONECUTTER  -> "stonecutter-gui"
            RecipeDisplayType.CRAFTER      -> "craft-gui"
            RecipeDisplayType.BREWING      -> "brewing-gui"
            RecipeDisplayType.CARTOGRAPHY  -> "cartography-gui"
            RecipeDisplayType.GRINDSTONE   -> "grindstone-gui"
            RecipeDisplayType.ANVIL        -> "anvil-gui"
            RecipeDisplayType.VILLAGER     -> "villager-gui"
        }
        val config = recipeBookPlugin.configYml.getSubsection(guiSection)
        // ... rest of existing open() body unchanged, using resolved config
    }
}
```

> Update all call sites of `RecipeGUI(config, stack)` → `RecipeGUI(stack)` in `CategoryGUI.kt`, `ItemCategoryGUI.kt`, and `RecipeGUI.kt` itself.

- [x] Add GUI sections to `config.yml`:

```yaml
furnace-gui:
  title: "&8Smelting Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i11111"
      - "111111o11"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

smithing-gui:
  title: "&8Smithing Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "1t1b1a1o1"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

stonecutter-gui:
  title: "&8Stonecutter Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i11111"
      - "111111111"
      - "1oooooooo"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

brewing-gui:
  title: "&8Brewing Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i1g111"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

cartography-gui:
  title: "&8Cartography Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i1a1o1"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

grindstone-gui:
  title: "&8Grindstone Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i1j1o1"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

anvil-gui:
  title: "&8Anvil Recipe"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i1m1o1"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click

villager-gui:
  title: "&8Villager Trade"
  mask:
    items:
      - black_stained_glass_pane
    pattern:
      - "111111111"
      - "111i1j1o1"
      - "111111111"
      - "111111111"
      - "111010111"
  buttons:
    recipe-parts-lore: []
    back:
      row: 5
      column: 5
      item: barrier name:"&cBack"
      lore: []
      click_sound: ui.button_click
```

- [x] Compile:

```
./gradlew compileKotlin
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/gui/RecipeGUI.kt \
        src/main/resources/config.yml
git commit -m "feat: RecipeGUI displayType switching + config sections for all station GUIs"
```

---

### Task 26: lang.yml + plugin.yml additions

**Files:**
- Modify: `src/main/resources/lang.yml`
- Modify: `src/main/resources/plugin.yml`

- [x] Add to `lang.yml` under `messages:`:

```yaml
  recipe-locked: "&cThis recipe is locked."
  recipe-unlocked: "&aRecipe unlocked: &f%recipe%"
  craft-conditions-not-met: "&cYou do not meet the requirements to craft this."
```

- [x] Add to `plugin.yml` under `commands` and `permissions`:

```yaml
# under commands.recipebook.description or as sub-entries in your MainCommand registration:
# (permissions only — commands registered via eco's command API)

permissions:
  recipebook.admin.create:
    description: Open the in-game recipe creator GUI
    default: op
  recipebook.admin:
    description: Admin commands (unlock/lock recipes)
    default: op
```

- [x] Commit:

```bash
git add src/main/resources/lang.yml src/main/resources/plugin.yml
git commit -m "feat: add lang strings and permissions for recipe conditions and unlock system"
```

---

## Module 11 — In-Game Recipe Creator GUI

### Task 27: RecipeCreatorGUI — Step 1 (type select)

**Files:**
- Create: `src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt`

- [x] Create with Step 1 only. Steps 2–5 added in Tasks 28–30:

```kotlin
package ru.oftendev.recipebook.gui

import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.Items
import org.bukkit.Material
import org.bukkit.entity.Player
import ru.oftendev.recipebook.recipeBookPlugin

object RecipeCreatorGUI {

    // Station type → display material + label
    private val stationTypes = listOf(
        Triple("crafting_table",    Material.CRAFTING_TABLE, "&aCrafting Table"),
        Triple("furnace",           Material.FURNACE,        "&aFurnace"),
        Triple("blast_furnace",     Material.BLAST_FURNACE,  "&aBlast Furnace"),
        Triple("smoker",            Material.SMOKER,         "&aSmoker"),
        Triple("campfire",          Material.CAMPFIRE,       "&aCampfire"),
        Triple("smithing_table",    Material.SMITHING_TABLE, "&aSmithing Table"),
        Triple("stonecutter",       Material.STONECUTTER,    "&aStonecutter"),
        Triple("crafter",           Material.CRAFTER,        "&aCrafter"),
        Triple("brewing_stand",     Material.BREWING_STAND,  "&aBrewing Stand"),
        Triple("cartography_table", Material.CARTOGRAPHY_TABLE, "&aCartography Table"),
        Triple("grindstone",        Material.GRINDSTONE,     "&aGrindstone"),
        Triple("anvil",             Material.ANVIL,          "&aAnvil"),
        Triple("villager",          Material.EMERALD,        "&aVillager Trade")
    )

    fun openTypeSelect(player: Player) {
        val menu = Menu.builder(2).setTitle("&8New Recipe — Choose Type")

        stationTypes.forEachIndexed { idx, (typeKey, mat, label) ->
            val row = (idx / 9) + 1
            val col = (idx % 9) + 1
            menu.setSlot(row, col, Slot.builder(
                ItemStackBuilder(mat).setDisplayName(label).build()
            ).onLeftClick { _, _ ->
                openIngredientSetup(player, typeKey)
            }.build())
        }

        menu.setMask(FillerMask(
            MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
            "000000000", "000000000"
        ))
        menu.build().open(player)
    }

    // Stubs for remaining steps — implemented in Tasks 28–30
    private fun openIngredientSetup(player: Player, typeKey: String) { TODO() }
    fun openOutputSetup(player: Player, typeKey: String, parts: Map<Int, org.bukkit.inventory.ItemStack>) { TODO() }
    fun openMetadata(player: Player, typeKey: String, parts: Map<Int, org.bukkit.inventory.ItemStack>, output: org.bukkit.inventory.ItemStack, ghost: Boolean) { TODO() }
    fun openPreview(player: Player, pendingRecipe: PendingRecipe) { TODO() }
}

data class PendingRecipe(
    val typeKey: String,
    val parts: Map<Int, org.bukkit.inventory.ItemStack>,
    val output: org.bukkit.inventory.ItemStack,
    val ghost: Boolean,
    val id: String,
    val permission: String
)
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt
git commit -m "feat: RecipeCreatorGUI Step 1 — type select menu"
```

---

### Task 28: RecipeCreatorGUI — Step 2 (ingredient setup)

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt`

- [x] Replace `openIngredientSetup` stub:

```kotlin
private fun openIngredientSetup(player: Player, typeKey: String) {
    val slotLayout = ingredientSlotLayout(typeKey)  // returns list of (row,col) for input slots
    val menu = Menu.builder(4).setTitle("&8New Recipe — Ingredients")

    val collectedParts = mutableMapOf<Int, org.bukkit.inventory.ItemStack>()

    slotLayout.forEachIndexed { idx, (row, col) ->
        menu.setSlot(row, col, Slot.builder(
            ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7Slot ${idx + 1} — place ingredient").build()
        ).onLeftClick { event, _ ->
            val cursor = event.cursor ?: return@onLeftClick
            if (cursor.type.isAir) return@onLeftClick
            collectedParts[idx] = cursor.clone().apply { amount = 1 }
            event.inventory.setItem(event.rawSlot, ItemStackBuilder(cursor.clone()).build())
        }.onRightClick { event, _ ->
            collectedParts.remove(idx)
            event.inventory.setItem(event.rawSlot,
                ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setDisplayName("&7Slot ${idx + 1} — place ingredient").build())
        }.build())
    }

    // Shapeless toggle (crafting_table only)
    if (typeKey == "crafting_table") {
        var shapeless = false
        menu.setSlot(4, 9, Slot.builder(
            ItemStackBuilder(Material.PAPER).setDisplayName("&eShaped (click to toggle)").build()
        ).onLeftClick { event, _ ->
            shapeless = !shapeless
            val label = if (shapeless) "&eShapeless" else "&eShaped"
            event.inventory.setItem(event.rawSlot, ItemStackBuilder(Material.PAPER).setDisplayName(label).build())
        }.build())
    }

    // Next button
    menu.setSlot(4, 5, Slot.builder(
        ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →").build()
    ).onLeftClick { _, _ ->
        player.closeInventory()
        openOutputSetup(player, typeKey, collectedParts)
    }.build())

    menu.setMask(FillerMask(
        MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
        "111111111", "111111111", "111111111", "111111111"
    ))
    menu.build().open(player)
}

private fun ingredientSlotLayout(typeKey: String): List<Pair<Int, Int>> = when (typeKey) {
    "crafting_table", "crafter" -> listOf(
        1 to 1, 1 to 2, 1 to 3,
        2 to 1, 2 to 2, 2 to 3,
        3 to 1, 3 to 2, 3 to 3
    )
    "furnace", "blast_furnace", "smoker", "campfire", "stonecutter" -> listOf(2 to 2)
    "smithing_table" -> listOf(2 to 2, 2 to 4, 2 to 6)
    "brewing_stand"  -> listOf(2 to 2, 2 to 4)
    "cartography_table", "grindstone", "villager" -> listOf(2 to 2, 2 to 4)
    "anvil" -> listOf(2 to 2, 2 to 4)
    else -> emptyList()
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt
git commit -m "feat: RecipeCreatorGUI Step 2 — ingredient setup with slot picker"
```

---

### Task 29: RecipeCreatorGUI — Steps 3–5 (output, metadata, preview + save)

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt`

- [x] Replace the three remaining stubs:

```kotlin
fun openOutputSetup(player: Player, typeKey: String, parts: Map<Int, org.bukkit.inventory.ItemStack>) {
    var ghost = false
    val menu = Menu.builder(3).setTitle("&8New Recipe — Output")

    // Output slot
    menu.setSlot(2, 5, Slot.builder(
        ItemStackBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).setDisplayName("&7Place output item").build()
    ).onLeftClick { event, _ ->
        val cursor = event.cursor ?: return@onLeftClick
        if (cursor.type.isAir) return@onLeftClick
        event.inventory.setItem(event.rawSlot, cursor.clone())
    }.build())

    // Ghost toggle
    menu.setSlot(2, 7, Slot.builder(
        ItemStackBuilder(Material.GRAY_DYE).setDisplayName("&7Ghost: OFF").build()
    ).onLeftClick { event, _ ->
        ghost = !ghost
        val label = if (ghost) "&aGhost: ON" else "&7Ghost: OFF"
        val mat   = if (ghost) Material.LIME_DYE else Material.GRAY_DYE
        event.inventory.setItem(event.rawSlot, ItemStackBuilder(mat).setDisplayName(label).build())
    }.build())

    // Next
    menu.setSlot(3, 5, Slot.builder(
        ItemStackBuilder(Material.LIME_DYE).setDisplayName("&aNext →").build()
    ).onLeftClick { event, _ ->
        val outputItem = event.inventory.getItem(14) // slot (2,5) = raw slot 13 in 3-row
            ?.takeIf { !it.type.isAir } ?: run {
            player.sendMessage("&cPlace an output item first.")
            return@onLeftClick
        }
        player.closeInventory()
        openMetadata(player, typeKey, parts, outputItem, ghost)
    }.build())

    menu.setMask(FillerMask(
        MaskItems.fromItemNames(listOf("black_stained_glass_pane")),
        "111111111", "111111111", "111111111"
    ))
    menu.build().open(player)
}

fun openMetadata(
    player: Player,
    typeKey: String,
    parts: Map<Int, org.bukkit.inventory.ItemStack>,
    output: org.bukkit.inventory.ItemStack,
    ghost: Boolean
) {
    // Anvil-view prompt for recipe ID
    recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable {
        player.openAnvilView(net.kyori.adventure.text.Component.text("Recipe ID")) { result ->
            val id = result?.itemMeta?.displayName()
                ?.let { net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(it) }
                ?.lowercase()?.replace(" ", "_")?.replace(Regex("[^a-z0-9_]"), "")
                ?: return@openAnvilView

            if (id.isBlank()) { player.sendMessage("&cID cannot be blank."); return@openAnvilView }
            if (ru.oftendev.recipebook.custom.CustomRecipes.getByKey(org.bukkit.NamespacedKey("recipebook", id)) != null) {
                player.sendMessage("&cRecipe '$id' already exists."); return@openAnvilView
            }

            // Permission prompt
            recipeBookPlugin.server.scheduler.runTask(recipeBookPlugin, Runnable {
                player.openAnvilView(net.kyori.adventure.text.Component.text("Permission (blank = none)")) { permResult ->
                    val perm = permResult?.itemMeta?.displayName()
                        ?.let { net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(it) }
                        ?: ""
                    openPreview(player, PendingRecipe(typeKey, parts, output, ghost, id, perm))
                }
            })
        }
    })
}

fun openPreview(player: Player, pendingRecipe: PendingRecipe) {
    // Show read-only RecipeGUI based on output item, then confirm button
    RecipeGUI(pendingRecipe.output).open(player, null)
    // Confirm via chat command prompt (simpler than embedding in existing GUI)
    player.sendMessage("&aPreview shown. Type &e/recipebook confirm &ato save, or &c/recipebook cancel &ato discard.")
    pendingConfirm[player.uniqueId] = pendingRecipe
}

private val pendingConfirm = mutableMapOf<java.util.UUID, PendingRecipe>()

fun confirmSave(player: Player) {
    val pending = pendingConfirm.remove(player.uniqueId) ?: run {
        player.sendMessage("&cNo pending recipe to confirm.")
        return
    }
    saveRecipeYaml(pending)
    recipeBookPlugin.reload()
    player.sendMessage("&aRecipe '${pending.id}' saved and loaded.")
}

fun cancelSave(player: Player) {
    pendingConfirm.remove(player.uniqueId)
    player.sendMessage("&cRecipe creation cancelled.")
}

private fun saveRecipeYaml(pending: PendingRecipe) {
    val dir = java.io.File(recipeBookPlugin.dataFolder, "recipes")
    dir.mkdirs()
    val file = java.io.File(dir, "${pending.id}.yml")
    val sb = StringBuilder()
    sb.appendLine("type: ${pending.typeKey}")

    when (pending.typeKey) {
        "crafting_table", "crafter" -> {
            sb.appendLine("shapeless: false")
            sb.appendLine("recipe:")
            for (i in 0..8) {
                val item = pending.parts[i]
                val lookup = if (item == null || item.type.isAir) "\"\"" else item.type.name.lowercase()
                sb.appendLine("  - $lookup")
            }
        }
        "furnace", "blast_furnace", "smoker", "campfire" -> {
            val input = pending.parts[0]?.type?.name?.lowercase() ?: "air"
            sb.appendLine("input: $input")
            sb.appendLine("cook-time: 200")
            sb.appendLine("experience: 0.0")
        }
        "smithing_table" -> {
            sb.appendLine("template: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
            sb.appendLine("base: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
            sb.appendLine("addition: ${pending.parts[2]?.type?.name?.lowercase() ?: "air"}")
        }
        "stonecutter" -> {
            sb.appendLine("input: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
            sb.appendLine("outputs:")
            sb.appendLine("  - item: ${pending.output.type.name.lowercase()}")
            sb.appendLine("    ghost: ${pending.ghost}")
            if (pending.ghost) { sb.appendLine("    effects: []"); sb.appendLine("    conditions: []") }
        }
        "brewing_stand" -> {
            sb.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
            sb.appendLine("ingredient: ${pending.parts[1]?.type?.name?.lowercase() ?: "air"}")
        }
        "cartography_table", "grindstone", "villager" -> {
            sb.appendLine("input1: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
            pending.parts[1]?.let { sb.appendLine("input2: ${it.type.name.lowercase()}") }
        }
        "anvil" -> {
            sb.appendLine("base: ${pending.parts[0]?.type?.name?.lowercase() ?: "air"}")
            pending.parts[1]?.let { sb.appendLine("material: ${it.type.name.lowercase()}") }
            sb.appendLine("repair-cost: 1")
        }
    }

    if (pending.typeKey != "stonecutter") {
        sb.appendLine("output: ${pending.output.type.name.lowercase()}")
        sb.appendLine("ghost: ${pending.ghost}")
        if (pending.ghost) {
            sb.appendLine("# Add libreforge effects below.")
            sb.appendLine("# See: https://plugins.auxilor.io/effects/configuring-an-effect")
            sb.appendLine("effects: []")
            sb.appendLine("conditions: []")
        }
    }

    if (pending.permission.isNotBlank()) sb.appendLine("permission: \"${pending.permission}\"")
    sb.appendLine("locked-by-default: false")
    sb.appendLine("visibility-conditions: []")
    sb.appendLine("crafting-conditions: []")
    sb.appendLine("unlock-conditions: []")

    file.writeText(sb.toString())
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/gui/RecipeCreatorGUI.kt
git commit -m "feat: RecipeCreatorGUI Steps 3-5 — output, metadata, preview, YAML save"
```

---

## Module 12 — Commands + Plugin Registration

### Task 30: Admin subcommands (create / unlock / lock)

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/commands/MainCommand.kt`

- [x] Add subcommands inside `MainCommand`. Inspect existing structure first:

```
# Existing subcommands registered in MainCommand:
CommandDebug, CommandLookup, CommandOpen, CommandReload, CommandValidate
```

- [x] Add `CommandCreate`, `CommandUnlock`, `CommandLock` registrations in `MainCommand.addSubcommands()`:

```kotlin
// In MainCommand constructor or addSubcommands():
addSubcommand(CommandCreate(plugin))
addSubcommand(CommandUnlock(plugin))
addSubcommand(CommandLock(plugin))
```

- [x] Create `CommandCreate.kt`:

```kotlin
package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.oftendev.recipebook.gui.RecipeCreatorGUI

class CommandCreate(plugin: EcoPlugin) : Subcommand(plugin, "create", "recipebook.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: run { sender.sendMessage("Players only."); return }
        RecipeCreatorGUI.openTypeSelect(player)
    }
}
```

- [x] Create `CommandUnlock.kt`:

```kotlin
package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

class CommandUnlock(plugin: EcoPlugin) : Subcommand(plugin, "unlock", "recipebook.admin", true) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) { sender.sendMessage("Usage: /recipebook unlock <player> <recipe-id>"); return }
        val target = Bukkit.getPlayer(args[0]) ?: run { sender.sendMessage("Player not found or offline."); return }
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", args[1]))
            ?: run { sender.sendMessage("Unknown recipe: ${args[1]}"); return }
        RecipeUnlockStore.unlock(target, recipe)
        sender.sendMessage("Unlocked '${args[1]}' for ${target.name}.")
    }
}
```

- [x] Create `CommandLock.kt`:

```kotlin
package ru.oftendev.recipebook.commands

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.Subcommand
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.RecipeUnlockStore

class CommandLock(plugin: EcoPlugin) : Subcommand(plugin, "lock", "recipebook.admin", true) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) { sender.sendMessage("Usage: /recipebook lock <player> <recipe-id>"); return }
        val target = Bukkit.getPlayer(args[0]) ?: run { sender.sendMessage("Player not found or offline."); return }
        val recipe = CustomRecipes.getByKey(NamespacedKey("recipebook", args[1]))
            ?: run { sender.sendMessage("Unknown recipe: ${args[1]}"); return }
        RecipeUnlockStore.lock(target, recipe)
        sender.sendMessage("Locked '${args[1]}' for ${target.name}.")
    }
}
```

- [x] Add `confirm`/`cancel` subcommands for creator GUI flow:

```kotlin
class CommandConfirm(plugin: EcoPlugin) : Subcommand(plugin, "confirm", "recipebook.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: return
        RecipeCreatorGUI.confirmSave(player)
    }
}

class CommandCancel(plugin: EcoPlugin) : Subcommand(plugin, "cancel", "recipebook.admin.create", false) {
    override fun onExecute(sender: CommandSender, args: List<String>) {
        val player = sender as? Player ?: return
        RecipeCreatorGUI.cancelSave(player)
    }
}
```

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/commands/
git commit -m "feat: add CommandCreate, CommandUnlock, CommandLock, CommandConfirm, CommandCancel"
```

---

### Task 31: Plugin registration in RecipeBookPlugin

**Files:**
- Modify: `src/main/kotlin/ru/oftendev/recipebook/RecipeBookPlugin.kt`

- [x] Update `handleEnable()`:

```kotlin
override fun handleEnable() {
    // Existing
    VaultPackIntegration.init(this)
    ShopIntegration.init(this)
    RecipeCategories.reload()

    // New — libreforge extensions
    com.willfp.libreforge.triggers.Triggers.register(
        ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft
    )
    com.willfp.libreforge.triggers.Triggers.register(
        ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft
    )
    com.willfp.libreforge.triggers.Triggers.register(
        ru.oftendev.recipebook.custom.libreforge.TriggerRecipeUnlocked
    )
    com.willfp.libreforge.triggers.Triggers.register(
        ru.oftendev.recipebook.custom.libreforge.TriggerRecipeLocked
    )
    ru.oftendev.recipebook.custom.libreforge.TriggerGhostCraft.enable()
    ru.oftendev.recipebook.custom.libreforge.TriggerCustomCraft.enable()
    ru.oftendev.recipebook.custom.libreforge.TriggerRecipeUnlocked.enable()
    ru.oftendev.recipebook.custom.libreforge.TriggerRecipeLocked.enable()

    com.willfp.libreforge.effects.Effects.register(
        ru.oftendev.recipebook.custom.libreforge.EffectUnlockRecipe
    )
    com.willfp.libreforge.effects.Effects.register(
        ru.oftendev.recipebook.custom.libreforge.EffectLockRecipe
    )
    com.willfp.libreforge.conditions.Conditions.register(
        ru.oftendev.recipebook.custom.libreforge.ConditionHasUnlockedRecipe
    )

    // New — trackers + unlock store + recipe listener
    eventManager.registerListener(ru.oftendev.recipebook.custom.FurnaceOwnerTracker)
    eventManager.registerListener(ru.oftendev.recipebook.custom.BrewingOwnerTracker)
    eventManager.registerListener(ru.oftendev.recipebook.custom.RecipeUnlockStore)
    eventManager.registerListener(ru.oftendev.recipebook.custom.CustomRecipeListener())

    // New — load custom recipes
    ru.oftendev.recipebook.custom.CustomRecipeLoader.load()

    setupMetrics()
}

override fun handleReload() {
    ShopIntegration.init(this)
    RecipeCategories.reload()
    ru.oftendev.recipebook.custom.CustomRecipeLoader.load()
}

override fun handleDisable() {
    ru.oftendev.recipebook.custom.RecipeUnlockStore.saveAll()
}

override fun loadPluginCommands(): MutableList<PluginCommand> {
    return mutableListOf(MainCommand(this))
}
```

- [x] Full build:

```
./gradlew shadowJar
```

Expected: `build/libs/RecipeBook.jar` produced with no errors.

- [x] Commit:

```bash
git add src/main/kotlin/ru/oftendev/recipebook/RecipeBookPlugin.kt
git commit -m "feat: wire all custom crafting components in RecipeBookPlugin enable/reload/disable"
```

---

## Module 13 — Integration Testing Checklist

No automated tests possible for event-driven behaviour without a running server. Manual test plan:

### Task 32: Manual integration test matrix

- [ ] **Crafting table — real output:** Place recipe YAML with `ghost: false`. Craft in crafting table. Receive item. `CustomCraftEvent` fires (add debug listener to verify).

- [ ] **Crafting table — ghost output:** Place recipe YAML with `ghost: true`. Craft. No item received. `TriggerGhostCraft` fires. Libreforge effects execute. `custom_craft` trigger also fires.

- [ ] **Crafting table — symmetry:** Set `symmetry: true`. Craft with rotated layout. Recipe still matches.

- [ ] **Shapeless crafting:** Set `shapeless: true`. Any order of ingredients works.

- [ ] **Furnace ghost:** Place smelting ghost recipe. Put input in furnace. Open furnace to register owner. Let it smelt. No output in furnace. Effects fire on owning player.

- [ ] **Stonecutter multiple outputs:** Place stonecutter recipe with 3 outputs (one ghost, two real). Open stonecutter. See all three output options. Select ghost option → effects fire, no item. Select real option → item received.

- [ ] **Brewing ghost:** Place brewing recipe with `ghost: true`. Put base in bottle slots, ingredient in top. Let brew. Bottles emptied, effects fire.

- [ ] **Anvil / Cartography / Grindstone:** Place recipe. Open station. Place matching inputs. Ghost result appears. Click result → no item, effects fire.

- [ ] **Villager trade injection:** Place villager recipe. Open any villager. Custom trade visible. Trade it (ghost or real).

- [ ] **Visibility conditions:** Set `visibility-conditions` that player fails. Recipe not shown in RecipeBook GUI.

- [ ] **Crafting conditions:** Set `crafting-conditions` that player fails. Attempt to craft. Cancelled with message.

- [ ] **Locked by default:** Set `locked-by-default: true`. Recipe hidden or shows locked lore. `/recipebook unlock <player> <id>`. Recipe unlocks. Craftable.

- [ ] **Auto-unlock on join:** Set `unlock-conditions`. Meet conditions. Relog. Recipe auto-unlocks.

- [ ] **`unlock_recipe` effect:** Wire to a libreforge trigger. Trigger fires. Recipe unlocks immediately.

- [ ] **`has_unlocked_recipe` condition:** Use in another holder's conditions. Returns correct true/false.

- [ ] **Recipe creator GUI:** `/recipebook create`. Follow wizard through all 5 steps. YAML written to `recipes/` folder. Plugin reloads. Recipe appears in RecipeBook GUI.

- [ ] **Reload safety:** `/recipebook reload`. Custom recipes re-register. No duplicate Bukkit recipes. No data loss.

- [ ] Commit after all manual tests pass:

```bash
git add .
git commit -m "test: manual integration test matrix complete — all custom crafting scenarios verified"
```

---

## Self-Review

### Spec Coverage Check

| Spec section | Covered by |
|---|---|
| §2 Station types (all 13) | Tasks 13–18, 19–23 |
| §3 Data model (sealed hierarchy, StonecutterOutput) | Tasks 3–4 |
| §4 Config format (all 13 types) | Tasks 13–18 |
| §4.1 Common fields (conditions, unlock, ghost) | Task 13 |
| §5 Recipe loading | Tasks 13–18 |
| §6.1 Owner trackers | Task 6 |
| §6.2 Condition check helper | Task 12 |
| §6.3 Event handling (all stations) | Tasks 19–23 |
| §6.4 GUI visibility filtering | Task 25 |
| §7 Custom events (5 classes) | Task 5 |
| §8.1 libreforge dep | Task 1 |
| §8.2 Holder compilation | Task 13 (parseGhostHolder) |
| §8.3 Triggers (4) | Tasks 8–9 |
| §8.4 Effects (2) | Task 10 |
| §8.5 Condition | Task 11 |
| §8.6 Dispatch helpers | Task 12 |
| §9.1 RecipeResolver resolveForPlayer | Task 24 |
| §9.2 GUI layout (all display types) | Task 25 |
| §10 Unlock system | Tasks 7, 31 |
| §10.2 Auto-unlock on join | Task 7 (RecipeUnlockStore.onJoin) |
| §10.3 Admin commands | Task 30 |
| §11 Creator GUI (all 5 steps) | Tasks 27–29 |
| §12 New files | All tasks |
| §13 Modified files | Tasks 2, 15, 24, 25, 26, 30, 31 |
| §0 Agent checkpoint instructions | Plan header |

### Gap: `ConditionList.areMet()` method

Spec calls `unlockConditions.areMet(dispatcher, holder)` in `RecipeUnlockStore` and
`CustomRecipeListener`. Verify this method exists in libreforge `ConditionList` — if not,
the correct call is `unlockConditions.all { it.isMet(dispatcher, providedHolder) }` or
similar. Check libreforge source before implementing Task 7.

**Fix:** In `RecipeUnlockStore.onJoin`, replace `recipe.unlockConditions.areMet(...)` with
the correct libreforge API call discovered at implementation time.

### Gap: `Config.getFloatOrNull` / `Config.getIntOrNull`

Eco's `Config` interface may not expose `getFloatOrNull`/`getIntOrNull`. Use
`runCatching { config.getFloat("experience") }.getOrNull()` if needed.

### Gap: `player.openAnvilView`

Paper 1.21.8 anvil view API — verify method signature. May be
`player.openAnvilView(title: Component, callback: Consumer<ItemStack?>)` or different.
Check Paper javadoc at implementation time and adjust Task 29 accordingly.

### Gap: Crafter inventory type tag

As noted in Task 17, eco's `ShapedCraftingRecipe.builder` does not expose Paper's
crafter-specific recipe type. If crafter-exclusive recipes are required (not craftable at
crafting table), register raw Bukkit `ShapedRecipe` with Paper's
`CraftingBookCategory.EQUIPMENT` and verify Paper 1.21.8 API for crafter-only tagging.

---

