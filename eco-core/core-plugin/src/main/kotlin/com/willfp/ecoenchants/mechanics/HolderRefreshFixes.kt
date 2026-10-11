package com.willfp.ecoenchants.mechanics

import com.willfp.ecoenchants.plugin
import com.willfp.ecoenchants.target.EnchantFinder.clearEnchantmentCache
import com.willfp.libreforge.forceRefreshHolders
import com.willfp.libreforge.toDispatcher
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import java.util.UUID

/**
 * libreforge only re-scans a player's enchanted items on a handful of events
 * (hotbar scroll, single inventory clicks with a 500 ms anti-spam window,
 * pickup/drop, armor change). Items can leave the main hand through other
 * paths that fire none of those, so the enchantment keeps working from the
 * inventory / off hand until the next periodic poll:
 *
 * - F (swap hands)
 * - dragging items in an inventory
 * - fast clicks swallowed by `refresh.inventory-click.timeout`
 * - the held tool breaking
 *
 * This listener forces a refresh one tick after each of those, and once more
 * when an inventory is closed so any rearranging is always picked up.
 */
object HolderRefreshFixes : Listener {
    private val pending = mutableSetOf<UUID>()

    private fun refreshNextTick(player: Player) {
        // Coalesce several events in the same tick into one refresh.
        if (!pending.add(player.uniqueId)) {
            return
        }

        plugin.scheduler.run {
            pending.remove(player.uniqueId)

            if (!player.isOnline) {
                return@run
            }

            player.clearEnchantmentCache()
            player.toDispatcher().forceRefreshHolders()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        refreshNextTick(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        refreshNextTick(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        refreshNextTick(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemBreak(event: PlayerItemBreakEvent) {
        refreshNextTick(event.player)
    }
}
