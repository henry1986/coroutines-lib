package org.daiv.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import mu.KLogger
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface ActorAnswerable<R> {
    suspend fun run(): R
}

interface ActorRunnable {
    suspend fun run()
}

data class DefaultScopeContextable(
    override val scope: CoroutineScope = GlobalScope,
    override val context: CoroutineContext = EmptyCoroutineContext
) : ScopeContextable

interface ScopeContextable {
    val scope: CoroutineScope
    val context: CoroutineContext

    fun launch(name: String, block: suspend CoroutineScope.() -> Unit) = scope.launch(context + CoroutineName("$name"), block = block)
}


class ActorableInterface(
    name: String,
    val logger:KLogger = Companion.logger,
    val channelCapacity: Int = Channel.RENDEZVOUS,
    scopeContextable: ScopeContextable = DefaultScopeContextable()
) : ScopeContextable by scopeContextable {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.coroutines.ActorableInterface")
    }

    internal val channel: Channel<ActorableEventHandler> = Channel(channelCapacity)

    interface ActorableEventHandler {
        suspend fun handle()
    }

    private class RunEvent(
        val actorRunnable: ActorRunnable
    ) : ActorableEventHandler {
        override suspend fun handle() {
            actorRunnable.run()
        }

        override fun toString() = actorRunnable.toString()
    }

    data class RunOnAnswerableEvent<X>(
        val actorAnswerable: ActorAnswerable<X>,
        val answerChannel: Channel<X>
    ) : ActorableEventHandler {
        override suspend fun handle() {
            answerChannel.send(actorAnswerable.run())
        }

        override fun toString() = actorAnswerable.toString()
    }

    val job = scopeContextable.launch("$name -> ChannelReceiveCoroutine") {
        while (true) {
            val e = channel.receive()
            logger.trace { "handle: $e" }
            e.handle()
            logger.trace { "handled: $e" }
        }
    }

    private inner class HasEventsWaiting() : ActorAnswerable<Boolean> {
        override suspend fun run(): Boolean {
            return !channel.isEmpty
        }
    }

    /**
     * executes checks and tests, if the receiving channel is emty.
     */
    private inner class CheckDone(val check: suspend () -> Unit) : ActorAnswerable<Boolean> {
        override suspend fun run(): Boolean {
            check()
            val empty = channel.isEmpty
            logger.trace { "isEmpty: $empty" }
            return empty
        }

        override fun toString() = "CheckDone"
    }

    /**
     * if the receiving channel is not empty after executing check, the function blocks the coroutine
     */
    suspend fun waitOnDone(check: suspend () -> Unit) {
        while (!receiveAnswer(CheckDone(check))) {
        }
    }

    /**
     * handles the event [t], if the channel is not full, suspends otherwise
     */
    suspend fun <X> receiveAnswer(t: ActorAnswerable<X>): X {
        val answerChannel = Channel<X>()
        logger.trace { "try to send $t" }
        channel.send(RunOnAnswerableEvent(t, answerChannel))
        logger.trace { "sent $t" }
        return answerChannel.receive()
    }

    /**
     * handles the event [t], if the channel is not full, suspends otherwise
     */
    suspend fun suspendRunEvent(t: ActorRunnable) {
        logger.trace { "try to send $t" }
        channel.send(RunEvent(t))
        logger.trace { "sent $t" }
    }

    /**
     * returns true, if event was handled, false if it was ignored
     */
    fun offerRunEvent(t: ActorRunnable): Boolean {
        return channel.offer(RunEvent(t))
    }

    /**
     * starts a coroutine to send the event [t],
     */
    fun runEvent(t: ActorRunnable) {
        scope.launch(context + CoroutineName("$t")) {
            logger.trace { "sendEvent: $t" }
            channel.send(RunEvent(t))
            logger.trace { "sent event: $t" }
        }
    }

    override fun toString(): String {
        return job.toString()
    }
}
