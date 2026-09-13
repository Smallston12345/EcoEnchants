package com.willfp.ecoenchants.target

import com.willfp.eco.core.cache.EcoCache
import com.willfp.eco.core.fast.fast
import com.willfp.ecoenchants.enchant.EcoEnchant
import com.willfp.ecoenchants.enchant.EcoEnchantLevel
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.TypedHolderProvider
import com.willfp.libreforge.TypedProvidedHolder
import com.willfp.libreforge.slot.ItemHolderFinder
import com.willfp.libreforge.slot.SlotItemProvidedHolder
import com.willfp.libreforge.slot.SlotType
import com.willfp.libreforge.slot.impl.NumericSlotType
import com.willfp.libreforge.slot.impl.SlotTypeAny
import com.willfp.libreforge.toDispatcher
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.UUID

object EnchantFinder : ItemHolderFinder<EcoEnchantLevel>() {
    private val levelCache = EcoCache.builder<UUID, List<ProvidedLevel>>()
        .expireAfterWrite(Duration.ofSeconds(1))
        .build()

    private val activeProvider = object : TypedHolderProvider<EcoEnchantLevel> {
        override fun provide(dispatcher: Dispatcher<*>): Collection<TypedProvidedHolder<EcoEnchantLevel>> {
            return toHolderProvider().provide(dispatcher).filter { provided ->
                val slot = (provided as? SlotItemProvidedHolder<*>)?.slotType
                    ?: return@filter true

                if (slot !is NumericSlotType) {
                    return@filter true
                }

                // `any` is needed by item-management enchantments such as Soulbound
                // and Repairing, but ordinary effects must not activate from items
                // sitting unused in the player's inventory. Explicit numeric or
                // combined numeric targets remain supported as an opt-in.
                provided.holder.enchant.targets.any { target ->
                    target.slot !== SlotTypeAny && target.slot.isOrContains(slot)
                }
            }
        }
    }

    override fun find(item: ItemStack): List<EcoEnchantLevel> {
        val enchantMap = item.fast().enchants
        val enchants = mutableListOf<EcoEnchantLevel>()

        for ((enchant, level) in enchantMap) {
            if (enchant !is EcoEnchant) {
                continue
            }

            enchants += enchant.getLevel(level)
        }

        return enchants
    }

    override fun isValidInSlot(holder: EcoEnchantLevel, slot: SlotType): Boolean {
        return holder.enchant.targets.any { it.slot.isOrContains(slot) }
    }

    fun toActiveHolderProvider(): TypedHolderProvider<EcoEnchantLevel> {
        return activeProvider
    }

    internal fun LivingEntity.clearEnchantmentCache() = levelCache.invalidate(this.uniqueId)

    private val LivingEntity.cachedLevels: List<ProvidedLevel>
        get() = levelCache.get(this.uniqueId) {
            toHolderProvider().provide(this.toDispatcher())
                .mapNotNull {
                    val level = it.holder
                    val item = it.provider as? ItemStack ?: return@mapNotNull null

                    ProvidedLevel(level, item, it)
                }
        }

    fun LivingEntity.hasEnchantActive(enchant: EcoEnchant): Boolean {
        val levels = this.cachedLevels

        if (levels.none { it.level.enchant == enchant }) {
            return false
        }

        val dispatcher = this.toDispatcher()

        return levels.any {
            it.level.enchant == enchant &&
                it.level.conditions.areMet(dispatcher, it.holder)
        }
    }

    fun LivingEntity.getItemsWithEnchantActive(enchant: EcoEnchant): Map<ItemStack, Int> {
        val levels = this.cachedLevels

        if (levels.none { it.level.enchant == enchant }) {
            return emptyMap()
        }

        val dispatcher = this.toDispatcher()

        return buildMap {
            for (provided in levels) {
                if (provided.level.enchant != enchant) {
                    continue
                }

                if (!provided.level.conditions.areMet(dispatcher, provided.holder)) {
                    continue
                }

                put(provided.item, provided.level.level)
            }
        }
    }

    private data class ProvidedLevel(
        val level: EcoEnchantLevel,
        val item: ItemStack,
        val holder: ProvidedHolder
    )
}
