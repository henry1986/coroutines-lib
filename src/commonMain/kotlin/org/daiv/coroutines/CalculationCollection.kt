package org.daiv.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging

class CalculationCollection<K, CK, V : Any>(val name: String, val scopeContextable: ScopeContextable = DefaultScopeContextable()) {
    private val actor = ActorableInterface("$name -> calculationCollection", Channel.RENDEZVOUS, scopeContextable)
    private val map = mutableMapOf<K, CalculationSuspendableMap<out CK, V>>()

    companion object {
        private val logger = KotlinLogging.logger("org.daiv.coroutines.CalculationCollection")
    }

    private inner class Insert<T : CK>(
        val o: T,
        val k: K,
        val valueCreation: suspend (T) -> V,
        val afterRes: suspend (V) -> Unit
    ) : ActorAnswerable<Job> {
        override suspend fun run(): Job {
            val x = tryDirectGet(k) ?: run {
                val calc = CalculationSuspendableMap(name, scopeContextable, valueCreation)
                logger.trace { "add $k to map" }
                map[k] = calc
                logger.trace { "added $k to map" }
                calc
            }
            x as CalculationSuspendableMap<CK, V>
            return x.launch(o, afterRes)
        }

        override fun toString() = "insert $o for $k"
    }

    private fun tryDirectGet(k: K): CalculationSuspendableMap<*, V>? {
        return map[k]
    }

    suspend fun <T : CK> insert(
        o: T,
        key: K,
        valueCreation: suspend (T) -> V,
        afterRes: suspend (V) -> Unit = {}
    ): Job {
        val calc = tryDirectGet(key)
        return if (calc == null) {
            actor.receiveAnswer(Insert(o, key, valueCreation, afterRes))
        } else {
            calc as CalculationSuspendableMap<T, V>
            calc.launch(o, afterRes)
        }
    }

    tailrec suspend fun join() {
        val filter = map.filter { !it.value.isEmpty() }
        if (filter.isNotEmpty()) {
            logger.trace { "to join, we have at least to wait for ${filter.map { it.key }}" }
            filter.forEach {
                logger.trace { "join $it" }
                it.value.join()
            }
            join()
        }
    }

    fun toMap() = map.toMap()

    fun all() = map.values.toList()
}
