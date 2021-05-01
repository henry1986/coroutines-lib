package org.daiv.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import org.daiv.util.DefaultRegisterer
import org.daiv.util.Registerer

interface Joinable {
    suspend fun join()
}

class JoinFassade(val job: Job) : Joinable {
    override suspend fun join() = job.join()
    override fun toString() = job.toString()
}

suspend fun Collection<Joinable>.joinAll() = forEach { it.join() }

class JoinSet(val name: String, private val scopeContextable: ScopeContextable) {

    val logger = KotlinLogging.logger("org.daiv.coroutines.JobMap - $name")

    private val actor = ActorableInterface(name, Channel.UNLIMITED, scopeContextable)
    private val map = mutableSetOf<Joinable>()

    private inner class AddJob(val job: Joinable) : ActorRunnable {
        override suspend fun run() {
            logger.trace { "adding job: $job" }
            map.add(job)
            logger.trace { "added job: $job" }
        }

        override fun toString() = "AddJob: $job"
    }

    private inner class JoinTask(private val registerer: DefaultRegisterer<Channel<Unit>> = DefaultRegisterer()) : ActorRunnable,
                                                                                                                   Registerer<Channel<Unit>> by registerer {

        override suspend fun run() {
            val m = map.toSet()
            map.clear()
            if (m.isEmpty()) {
                this@JoinSet.joinTask = null
                registerer.suspendEvent { send(Unit) }
                registerer.unregisterAll()
                return
            }
            scopeContextable.launch("$name - start waiting") {
                m.joinAll()
                actor.runEvent(this@JoinTask)
            }
        }
    }

    private var joinTask: JoinTask? = null

    private inner class StartJoinTask(val channel: Channel<Unit>) : ActorRunnable {
        override suspend fun run() {
            joinTask?.let {
                it.register(channel)
            } ?: run {
                if(map.isNotEmpty()) {
                    val newJoinTask = JoinTask()
                    this@JoinSet.joinTask = newJoinTask
                    newJoinTask.register(channel)
                    actor.runEvent(newJoinTask)
                }else{
                    channel.send(Unit)
                }
            }
        }
    }

    private inner class IsEmpty : ActorAnswerable<Boolean> {
        override suspend fun run() = joinTask == null && map.isEmpty()
    }

    suspend fun isEmpty() = actor.receiveAnswer(IsEmpty())

    suspend fun nextJob(job: Joinable) = actor.suspendRunEvent(AddJob(job))
    suspend fun nextJob(job: Job) = nextJob(JoinFassade(job))

    fun offerNextJob(job: Job) = offerNextJob(JoinFassade(job))

    fun offerNextJob(job: Joinable) {
        val event = AddJob(job)
        val offerSuccess = actor.offerRunEvent(event)
        if (!offerSuccess) {
            throw RuntimeException("could not offer $event, which should not happen, because channel should be Channel.UNLIMITED, but seems to be ${actor.channelCapacity} - ${actor.channel}")
        }
    }

    suspend fun join() {
        val c = Channel<Unit>()
        actor.runEvent(StartJoinTask(c))
        c.receive()
    }
}

