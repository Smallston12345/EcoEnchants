package com.willfp.ecoenchants.enchant.impl.hardcoded

import com.willfp.eco.util.DurabilityUtils
import com.willfp.ecoenchants.enchant.impl.HardcodedEcoEnchant
import com.willfp.ecoenchants.target.EnchantFinder.getItemsWithEnchantActive
import com.willfp.libreforge.slot.impl.SlotTypeArmor
import com.willfp.libreforge.slot.impl.SlotTypeHands
import org.bukkit.Bukkit

object EnchantmentRepairing : HardcodedEcoEnchant(
    "repairing"
) {
    override fun onRegister() {
        val frequency = config.getInt("frequency").toLong()

        plugin.scheduler.runTimer(frequency, frequency) {
            handleRepairing()
        }
    }

    private fun handleRepairing() {
        val notWhileHolding = config.getBool("not-while-holding")

        for (player in Bukkit.getOnlinePlayers()) {
            val items = player.getItemsWithEnchantActive(this)

            if (items.isEmpty()) {
                continue
            }

            val repairPerLevel = config.getIntFromExpression("repair-per-level", player)
            val heldItems = if (notWhileHolding) SlotTypeHands.getItems(player) else null
            val equippedItems = if (notWhileHolding) SlotTypeArmor.getItems(player) else null

            for ((item, level) in items) {
                if (heldItems?.contains(item) == true || equippedItems?.contains(item) == true) {
                    continue
                }

                DurabilityUtils.repairItem(item, level * repairPerLevel)
            }
        }
    }
}
