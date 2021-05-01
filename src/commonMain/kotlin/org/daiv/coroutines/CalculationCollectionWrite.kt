package org.daiv.coroutines

import kotlinx.coroutines.CoroutineScope
import mu.KotlinLogging

class CalculationCollectionWrite<K, CK, V : Any>(
    name: String,
    val cache: CalculationCollection<K, CK, V>,
    val contextable: ScopeContextable = cache.scopeContextable
) {
    private val logger = KotlinLogging.logger("org.daiv.coroutines.CalculationCollectionWrite - $name")
    private val jobMap = JoinSet("$name calculationCollection -> write", contextable)

    fun <T : CK> insert(
        o: T,
        key: K,
        valueCreation: suspend (T) -> V,
        afterRes: suspend (V) -> Unit = {}
    ) {
        jobMap.offerNextJob(contextable.launch("insert: $o, key $key") {
            val job = cache.insert(o, key, valueCreation, afterRes)
            logger.trace { "addJob: $job" }
            jobMap.nextJob(job)
        })
    }

    fun launch(name: String, block: suspend CoroutineScope.() -> Unit) {
        jobMap.offerNextJob(contextable.launch(name, block))
    }

    suspend fun join() = jobMap.join()
}
