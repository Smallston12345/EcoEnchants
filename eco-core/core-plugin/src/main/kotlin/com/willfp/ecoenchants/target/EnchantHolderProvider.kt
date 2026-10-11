package com.willfp.ecoenchants.target

import com.willfp.ecoenchants.enchant.EcoEnchantLevel
import com.willfp.ecoenchants.plugin
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.slot.SlotItemProvidedHolder
import com.willfp.libreforge.slot.impl.NumericSlotType
import com.willfp.libreforge.slot.impl.SlotTypeAny

/**
 * Provides enchantment holders, except in worlds EcoEnchants is disabled in.
 *
 * Uses the plain generic holder provider API (libreforge 2026.40 has no ScopedHolderProvider).
 */
internal object EnchantHolderProvider {
    private val finder = EnchantFinder.toHolderProvider()

    /**
     * `any` is needed by item-management enchantments such as Soulbound and
     * Repairing, but ordinary effects must not activate from items sitting unused
     * in the player's inventory. Explicit numeric targets remain an opt-in.
     */
    private fun Collection<ProvidedHolder>.onlyActiveSlots(): Collection<ProvidedHolder> =
        filter { provided ->
            val slot = (provided as? SlotItemProvidedHolder<*>)?.slotType
                ?: return@filter true

            if (slot !is NumericSlotType) {
                return@filter true
            }

            val level = provided.holder as? EcoEnchantLevel ?: return@filter true

            level.enchant.targets.any { target ->
                target.slot !== SlotTypeAny && target.slot.isOrContains(slot)
            }
        }

    fun provide(dispatcher: Dispatcher<*>): Collection<ProvidedHolder> {
        if (plugin.isDisabledFor(dispatcher)) {
            return emptyList()
        }

        return finder.provide(dispatcher).onlyActiveSlots()
    }
}
