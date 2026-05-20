package ru.oftendev.recipebook.custom.packet

import com.willfp.eco.core.packet.PacketEvent
import com.willfp.eco.core.packet.PacketListener
import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryType
import ru.oftendev.recipebook.custom.CustomRecipe
import ru.oftendev.recipebook.custom.CustomRecipes
import ru.oftendev.recipebook.recipeBookPlugin

object GrindstonePacketListener : PacketListener {

    override fun onReceive(event: PacketEvent) {
        val packet = event.packet.handle
        if (!packet.javaClass.name.endsWith("ServerboundContainerClickPacket")) return

        val slotNum = runCatching {
            packet.javaClass.getDeclaredField("slotNum").apply { isAccessible = true }.getInt(packet)
        }.getOrElse {
            packet.javaClass.methods.firstOrNull { it.name == "getSlotNum" }?.invoke(packet) as? Int
        } ?: return

        if (slotNum != 0 && slotNum != 1) return

        val player = event.player
        if (player.openInventory.topInventory.type != InventoryType.GRINDSTONE) return

        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type.isAir) return

        val matches = CustomRecipes.all()
            .filterIsInstance<CustomRecipe.Grindstone>()
            .any { recipe ->
                when (slotNum) {
                    0 -> recipe.item1.matches(cursor)
                    1 -> recipe.item2?.matches(cursor) == true
                    else -> false
                }
            }
        if (!matches) return

        event.isCancelled = true

        Bukkit.getScheduler().runTask(recipeBookPlugin, Runnable {
            val topInv = player.openInventory.topInventory
            if (topInv.type != InventoryType.GRINDSTONE) return@Runnable

            val current = topInv.getItem(slotNum)
            if (current != null && !current.type.isAir) return@Runnable

            val toPlace = cursor.clone().apply { amount = 1 }
            topInv.setItem(slotNum, toPlace)
            if (cursor.amount <= 1) player.setItemOnCursor(null)
            else cursor.amount--
            player.updateInventory()
        })
    }
}
