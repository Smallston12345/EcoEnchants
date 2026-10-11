package com.willfp.ecoenchants.target

import com.willfp.ecoenchants.plugin
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.HolderChange
import com.willfp.libreforge.ProvideContext
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.ScopedHolderProvider
import com.willfp.libreforge.slot.SlotItemProvidedHolder
import com.willfp.libreforge.slot.impl.NumericSlotType
import com.willfp.libreforge.slot.impl.SlotTypeAny
import com.willfp.ecoenchants.enchant.EcoEnchantLevel

/**
 * Provides enchantment holders, except in worlds EcoEnchants is disabled in.
 *
 * Registered in place of a generic lambda so that libreforge stores its answers and asks it with
 * signal scopes, letting the finder re-check only the items that changed.
 */
internal object EnchantHolderProvider : ScopedHolderProvider {
    private val finder = EnchantFinder.toHolderProvider()

    override val id: String
        get() = finder.id

    override val invalidatedBy: Set<HolderChange>
        get() = finder.invalidatedBy

    override fun maxAge(dispatcher: Dispatcher<*>): Int? =
        finder.maxAge(dispatcher)

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

    override fun provide(dispatcher: Dispatcher<*>): Collection<ProvidedHolder> {
        if (plugin.isDisabledFor(dispatcher)) {
            return emptyList()
        }

        return finder.provide(dispatcher).onlyActiveSlots()
    }

    override fun provide(context: ProvideContext): Collection<ProvidedHolder> {
        if (plugin.isDisabledFor(context.dispatcher)) {
            return emptyList()
        }

        return ((finder as? ScopedHolderProvider)?.provide(context)
            ?: finder.provide(context.dispatcher)).onlyActiveSlots()
    }
}
