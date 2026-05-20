package ru.oftendev.recipebook.custom.packet

import com.willfp.eco.core.packet.PacketEvent
import com.willfp.eco.core.packet.PacketListener
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.scheduler.BukkitTask
import ru.oftendev.recipebook.custom.BlockOwnerTracker
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.custom.checkCraftingConditions
import ru.oftendev.recipebook.custom.fireCustomCraftTrigger
import ru.oftendev.recipebook.custom.fireGhostEffects
import ru.oftendev.recipebook.custom.event.CustomBrewEvent
import ru.oftendev.recipebook.recipeBookPlugin

object BrewingPacketListener : PacketListener {

    private val pendingBrews = mutableMapOf<Location, BukkitTask>()
    private val progressTasks = mutableMapOf<Location, BukkitTask>()

    override fun onReceive(event: PacketEvent) {
        val packet = event.packet.handle
        if (!packet.javaClass.name.endsWith("ServerboundContainerClickPacket")) return
        val slotNum = runCatching {
            packet.javaClass.getDeclaredField("slotNum").apply { isAccessible = true }.getInt(packet)
        }.getOrElse {
            packet.javaClass.methods.firstOrNull { it.name == "getSlotNum" }?.invoke(packet) as? Int
        } ?: return
        if (slotNum != 3) return

        val player = event.player
        if (player.openInventory.topInventory.type != InventoryType.BREWING) return

        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type.isAir) return

        val recipe = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Brewing>()
            .firstOrNull { it.ingredient.matches(cursor) } ?: return

        event.isCancelled = true

        Bukkit.getScheduler().runTask(recipeBookPlugin, Runnable {
            val topInv = player.openInventory.topInventory
            if (topInv.type != InventoryType.BREWING) return@Runnable

            val toPlace = cursor.clone().apply { amount = 1 }
            topInv.setItem(3, toPlace)
            if (cursor.amount <= 1) player.setItemOnCursor(null)
            else cursor.amount--
            player.updateInventory()

            val loc = topInv.location?.block?.location ?: return@Runnable
            scheduleBrew(loc, recipe, player)
        })
    }

    fun cancelBrew(location: Location) {
        pendingBrews.remove(location)?.cancel()
        progressTasks.remove(location)?.cancel()
    }

    private fun scheduleBrew(loc: Location, recipe: CustomRecipe.Brewing, animPlayer: Player? = null) {
        cancelBrew(loc)

        val brewTime = recipe.brewTime
        val player = animPlayer ?: BlockOwnerTracker.getOwner(loc)
        val containerId = if (player != null) getContainerId(player) else -1

        if (containerId >= 0 && player != null) {
            val totalSteps = (brewTime / 10).coerceAtLeast(1)
            var step = 0
            var progressTask: BukkitTask? = null
            progressTask = recipeBookPlugin.server.scheduler.runTaskTimer(recipeBookPlugin, Runnable {
                step++
                if (step > totalSteps || player.openInventory.topInventory.type != InventoryType.BREWING) {
                    progressTask?.cancel()
                    progressTasks.remove(loc)
                    return@Runnable
                }
                val normalized = (400 * (totalSteps - step) / totalSteps).coerceAtLeast(0)
                sendBrewDataPacket(player, containerId, normalized)
            }, 0L, 10L)
            progressTasks[loc] = progressTask!!
        }

        pendingBrews[loc] = Bukkit.getScheduler().runTaskLater(recipeBookPlugin, Runnable {
            pendingBrews.remove(loc)
            progressTasks.remove(loc)?.cancel()

            val state = loc.block.state as? org.bukkit.block.BrewingStand ?: return@Runnable
            val brewer = state.inventory
            val ingredient = brewer.ingredient ?: return@Runnable
            if (!recipe.ingredient.matches(ingredient)) return@Runnable

            val matchedSlots = (0..2).filter { recipe.base.matches(brewer.getItem(it)) }
            if (matchedSlots.isEmpty()) return@Runnable

            val owner = BlockOwnerTracker.getOwner(loc) ?: return@Runnable
            if (!checkCraftingConditions(owner, recipe)) return@Runnable

            val ing = ingredient.clone()
            if (ing.amount <= 1) brewer.ingredient = null
            else { ing.amount--; brewer.ingredient = ing }

            val item = recipe.output.clone()
            val ce = CustomBrewEvent(owner, recipe, item, loc, matchedSlots.size)
            Bukkit.getPluginManager().callEvent(ce)
            if (ce.isCancelled) return@Runnable

            val ghostPerSlot = recipeBookPlugin.configYml.getBool("brewing-stand.ghost-per-slot")
            if (recipe.ghost) {
                if (ghostPerSlot) {
                    matchedSlots.forEach { slot ->
                        brewer.setItem(slot, null)
                        val slotCe = CustomBrewEvent(owner, recipe, item.clone(), loc, 1)
                        Bukkit.getPluginManager().callEvent(slotCe)
                        if (!slotCe.isCancelled) fireGhostEffects(owner, recipe, item.clone(), 1)
                    }
                } else {
                    matchedSlots.forEach { brewer.setItem(it, null) }
                    fireGhostEffects(owner, recipe, item, 1)
                }
            } else {
                matchedSlots.forEach { brewer.setItem(it, item.clone()) }
                fireCustomCraftTrigger(owner, recipe, item, matchedSlots.size)
            }
        }, brewTime.toLong())
    }

    // ── NMS helpers ──────────────────────────────────────────────────────

    private fun getContainerId(player: Player): Int =
        runCatching {
            val nmsPlayer = player.javaClass.getMethod("getHandle").invoke(player)
            val menu = generateSequence(nmsPlayer.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .first { it.name == "containerMenu" }
                .apply { isAccessible = true }
                .get(nmsPlayer)
            generateSequence(menu.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .first { it.name == "containerId" }
                .apply { isAccessible = true }
                .getInt(menu)
        }.getOrDefault(-1)

    private fun sendBrewDataPacket(player: Player, containerId: Int, value: Int) {
        runCatching {
            val cls = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket"
            )
            val packet = cls
                .getDeclaredConstructor(Int::class.java, Int::class.java, Int::class.java)
                .newInstance(containerId, 0, value)
            val nmsPlayer = player.javaClass.getMethod("getHandle").invoke(player)
            val conn = generateSequence(nmsPlayer.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .first { it.name == "connection" }
                .apply { isAccessible = true }
                .get(nmsPlayer)
            conn.javaClass.methods
                .first { it.name == "send" && it.parameterCount == 1 }
                .invoke(conn, packet)
        }
    }
}
