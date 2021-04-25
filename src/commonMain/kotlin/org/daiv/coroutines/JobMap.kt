package org.daiv.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import mu.KotlinLogging

class JobMap(name: String, private val scopeContextable: ScopeContextable) {

    val logger = KotlinLogging.logger("org.daiv.coroutines.JobMap - $name")

    private val actor = ActorableInterface(name, Channel.UNLIMITED, scopeContextable)
    private val map = mutableSetOf<Job>()

    private inner class AddJob(val job: Job) : ActorRunnable {
        override suspend fun run() {
            logger.trace { "adding job: $job" }
            map.add(job)
            logger.trace { "added job: $job" }
        }

        override fun toString() = "AddJob: $job"
    }

    suspend fun nextJob(job: Job) = actor.suspendRunEvent(AddJob(job))
    fun offerNextJob(job: Job) {
        val event = AddJob(job)
        val offerSuccess = actor.offerRunEvent(event)
        if (!offerSuccess) {
            throw RuntimeException("could not offer $event, which should not happen, because channel should be Channel.UNLIMITED, but seems to be ${actor.channelCapacity} - ${actor.channel}")
        }
    }

    suspend fun join() {
        actor.waitOnDone {
            logger.trace { "waitOnDone: $map" }
            map.joinAll()
            logger.trace { "ready -> waitOnDone: $map" }
        }
        logger.trace { "leave join" }
    }
}

class CalculationCollectionWrite<K, CK, V : Any>(
    name: String,
    val cache: CalculationCollection<K, CK, V>,
    val contextable: ScopeContextable = cache.scopeContextable
) {
    private val logger = KotlinLogging.logger("org.daiv.coroutines.CalculationCollectionWrite - $name")
    private val jobMap = JobMap("$name calculationCollection -> write", contextable)

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
