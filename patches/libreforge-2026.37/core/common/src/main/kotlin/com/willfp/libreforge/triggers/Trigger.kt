package com.willfp.libreforge.triggers

import com.willfp.eco.core.registry.KRegistrable
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.ProvidedEffectBlock
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.counters.bind.BoundCounters
import com.willfp.libreforge.counters.bind.BoundCounters.bindings
import com.willfp.libreforge.generatePlaceholders
import com.willfp.libreforge.getProvidedActiveEffects
import com.willfp.libreforge.plugin
import com.willfp.libreforge.providedActiveEffectsFor
import com.willfp.libreforge.triggers.event.TriggerDispatchEvent
import com.willfp.libreforge.triggers.impl.TriggerAltClick
import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Listener

abstract class Trigger(
    override val id: String
) : Listener, KRegistrable {
    /**
     * The TriggerData parameters that are sent.
     */
    abstract val parameters: Set<TriggerParameter>

    /**
     * Human-readable description of when this trigger fires, shown on the wiki.
     */
    open val description: String = ""

    /**
     * Category tags for grouping and filtering on the wiki, e.g. "combat", "movement".
     */
    open val categories: Set<String> = emptySet()

    /**
     * Additional notes, warnings, or constraints shown on the wiki.
     */
    open val additionalInfo: List<String> = emptyList()

    /**
     * Per-parameter descriptions giving context specific to this trigger.
     * Describes what each TriggerParameter value represents when this trigger fires,
     * e.g. what VICTIM or VALUE actually contains for this specific event.
     */
    open val parameterDescriptions: Map<TriggerParameter, String> = emptyMap()

    /**
     * Whether this trigger is enabled.
     */
    open var isEnabled: Boolean = false
        protected set

    /**
     * If the listener is registered.
     */
    private var isListenerRegistered = false

    /**
     * The cached hashcode.
     */
    private val hashCode by lazy {
        id.hashCode()
    }

    /**
     * Enable the trigger.
     */
    fun enable() {
        isEnabled = true

        if (!this.isListenerRegistered) {
            this.isListenerRegistered = true
            plugin.runWhenEnabled {
                plugin.eventManager.unregisterListener(this)
                plugin.eventManager.registerListener(this)
                postRegister()
            }
        }
    }


    /**
     * Dispatch the trigger.
     */
    fun dispatch(
        dispatcher: Dispatcher<*>,
        data: TriggerData,
        forceHolders: Collection<ProvidedHolder>? = null,
        allowDuplicates: Boolean = false
    ) {
        val effectsArePreFiltered = forceHolders == null
        val effects = forceHolders?.getProvidedActiveEffects(dispatcher)
            ?: dispatcher.providedActiveEffectsFor(this)

        dispatchOnEffects(dispatcher, data, effects, allowDuplicates, effectsArePreFiltered)
    }

    /**
     * Dispatch the trigger on a collection of [ProvidedEffectBlock]s.
     */
    private fun dispatchOnEffects(
        dispatcher: Dispatcher<*>,
        data: TriggerData,
        effects: List<ProvidedEffectBlock>,
        allowDuplicates: Boolean = false,
        effectsArePreFiltered: Boolean = false
    ) {
        // Do this first to filter disabled triggers
        val dispatch = plugin.dispatchedTriggerFactory.create(dispatcher, this, data, allowDuplicates) ?: return

        // Filter out effects that can't be triggered by this trigger
        val triggerableEffects = if (effectsArePreFiltered) {
            effects
        } else {
            buildList {
                for (block in effects) {
                    if (block.effect.canBeTriggeredBy(this@Trigger)) {
                        add(block)
                    }
                }
            }
        }

        // Prevent dispatching useless triggers
        if (triggerableEffects.isEmpty() && !BoundCounters.anyCanBeTriggeredBy(this)) {
            return
        }

        // Only dispatch placeholders after we know we're going to dispatch
        dispatch.generatePlaceholders()

        val dispatchEvent = TriggerDispatchEvent(dispatcher, dispatch)
        Bukkit.getPluginManager().callEvent(dispatchEvent)

        if (dispatchEvent.isCancelled) {
            return
        }

        // Only calculate placeholders once per holder
        // Preserve the previous last-equal-holder behaviour without regenerating the same
        // holder dispatch and placeholders for every effect block it owns.
        val uniqueHolders = LinkedHashMap<ProvidedHolder, ProvidedHolder>()
        for ((_, holder) in triggerableEffects) {
            uniqueHolders[holder] = holder
        }

        val holderDispatches = HashMap<ProvidedHolder, DispatchedTrigger>(uniqueHolders.size)

        for ((key, holder) in uniqueHolders) {
            val withHolder = dispatch.data.copy().apply {
                this.holder = holder
            }

            val dispatchWithHolder = DispatchedTrigger(dispatcher, this, withHolder).inheritPlaceholders(dispatch)

            dispatchWithHolder.addPlaceholders(holder.generatePlaceholders(dispatcher))

            holderDispatches[key] = dispatchWithHolder
        }

        // Finally, trigger effects
        val cancellableEvent = data.event as? Cancellable
        val allowCancelled = this is TriggerAltClick

        for ((block, holder) in triggerableEffects) {
            // Fixes cancel_event not working
            if (cancellableEvent?.isCancelled == true) {
                // alt_click triggers are special and should be allowed to trigger even if the event is cancelled
                // Otherwise, clicking the air will not trigger the alt_click trigger
                if (!allowCancelled) {
                    return
                }
            }

            val dispatchWithHolder = holderDispatches[holder] ?: continue

            // This collection has already been filtered for this trigger.
            block.trigger(dispatchWithHolder)
        }

        // Probably a better way to work with counters, but this works for now.
        for (counter in BoundCounters.values()) {
            counter.bindings.forEach { it.accept(dispatch) }
        }
    }

    open fun postRegister() {
        // Override when needed.
    }

    override fun getID() = id

    override fun equals(other: Any?): Boolean {
        return other is Trigger && other.id == this.id
    }

    override fun hashCode(): Int {
        return hashCode
    }
}
