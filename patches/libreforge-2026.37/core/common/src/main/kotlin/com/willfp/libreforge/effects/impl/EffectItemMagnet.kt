package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.scheduling.EcoTask
import com.willfp.eco.core.scheduling.TaskContext
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.plugin
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import java.util.function.Consumer

/*
 * Patched item_magnet.
 *
 * The official 2026.37 version adds `direction * pull_strength` to the item's
 * existing velocity every tick with no damping, no speed cap and no stop radius.
 * Items therefore accelerate, overshoot the player, swing back and forth and
 * finally get flung far away. It also pulls items the holder just threw with Q,
 * so a thrown item is yanked back before it can land and sprays around randomly.
 *
 * This patch:
 * - never attracts items thrown by a player (Q drops, inventory drops); only
 *   loose loot such as block/mob drops is pulled;
 * - waits until the item's pickup delay has ended;
 * - damps the existing velocity and caps the speed so items glide in smoothly;
 * - stops pulling once the item is inside the vanilla pickup range;
 * - uses a spherical radius instead of the bounding box.
 */
object EffectItemMagnet : Effect<EffectItemMagnet.ItemMagnetFilter>("item_magnet") {
    override val description =
        "Pulls nearby dropped items toward the holder while active, optionally restricted to specific item types."
    override val categories = setOf("inventory", "movement")

    override val arguments = arguments {
        require(
            "radius",
            "You must specify the radius!",
            description = "The radius within which dropped items will be pulled, in blocks. Supports expressions.",
            type = ArgType.EXPRESSION,
            example = "5 + %level% * 0.5"
        )
        optional(
            "items",
            description = "A whitelist of item types to attract. If empty, all items are attracted.",
            type = ArgType.ITEM_LIST,
            default = "[]"
        )
        optional(
            "exclude_items",
            description = "A blacklist of item types to never attract, checked after the whitelist.",
            type = ArgType.ITEM_LIST,
            default = "[]"
        )
        optional(
            "pull_strength",
            description = "How strongly items are pulled per tick. Supports expressions.",
            type = ArgType.EXPRESSION,
            default = "0.3",
            example = "0.1 + %level% * 0.02"
        )
    }

    /** Fraction of the previous velocity kept each tick. */
    private const val DAMPING = 0.6

    /** Maximum item speed in blocks per tick. */
    private const val MAX_SPEED = 0.6

    /** Stop pulling once the item is this close (squared distance); vanilla pickup takes over. */
    private const val STOP_DISTANCE_SQUARED = 0.8 * 0.8

    private val tasks = mutableMapOf<Identifiers, EcoTask>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: ItemMagnetFilter
    ) {
        val player = dispatcher.dispatcher as? Player
        val radius = config.getDoubleFromExpression("radius", player)
        val strength = if (config.has("pull_strength")) {
            config.getDoubleFromExpression("pull_strength", player)
        } else {
            0.3
        }

        if (!radius.isFinite() || radius <= 0 || !strength.isFinite() || strength <= 0) {
            return
        }

        val entity = dispatcher.dispatcher as? Entity
        val context: TaskContext = entity?.let { plugin.scheduler.on(it) }
            ?: dispatcher.location?.let { plugin.scheduler.at(it) }
            ?: plugin.scheduler.global()

        tasks.remove(identifiers)?.cancel()
        tasks[identifiers] = context.runTimer(Consumer {
            pull(dispatcher, radius, strength, compileData)
        }, 0L, 1L)
    }

    private fun pull(
        dispatcher: Dispatcher<*>,
        radius: Double,
        strength: Double,
        filter: ItemMagnetFilter
    ) {
        val location = dispatcher.location ?: return
        val world = location.world ?: return
        val target = location.toVector().add(org.bukkit.util.Vector(0.0, 0.5, 0.0))
        val radiusSquared = radius * radius

        for (entity in world.getNearbyEntities(location, radius, radius, radius)) {
            val item = entity as? Item ?: continue

            if (!item.isValid) continue

            // Never pull items a player threw (Q / inventory drops).
            if (item.thrower != null) continue

            // Let the vanilla pickup delay run out first.
            if (item.pickupDelay > 0) continue

            val stack = item.itemStack
            if (filter.whitelist.isNotEmpty() && filter.whitelist.none { it.matches(stack) }) continue
            if (filter.blacklist.any { it.matches(stack) }) continue

            val toTarget = target.clone().subtract(item.location.toVector())
            val distanceSquared = toTarget.lengthSquared()

            if (distanceSquared > radiusSquared) continue
            if (distanceSquared < STOP_DISTANCE_SQUARED) continue

            val velocity = item.velocity.multiply(DAMPING)
                .add(toTarget.normalize().multiply(strength))

            if (velocity.lengthSquared() > MAX_SPEED * MAX_SPEED) {
                velocity.normalize().multiply(MAX_SPEED)
            }

            if (!velocity.x.isFinite() || !velocity.y.isFinite() || !velocity.z.isFinite()) continue

            item.velocity = velocity
        }
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        tasks.remove(identifiers)?.cancel()
    }

    override fun makeCompileData(config: Config, context: ViolationContext): ItemMagnetFilter {
        return ItemMagnetFilter(
            config.getStrings("items").map { Items.lookup(it) },
            config.getStrings("exclude_items").map { Items.lookup(it) }
        )
    }

    class ItemMagnetFilter(
        val whitelist: List<TestableItem>,
        val blacklist: List<TestableItem>
    )
}
