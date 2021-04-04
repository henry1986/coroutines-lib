package org.daiv.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
}


class ActorableInterface(scopeContextable: ScopeContextable = DefaultScopeContextable()) : ScopeContextable by scopeContextable {
    interface ActorableEventHandler {
        suspend fun handle()
    }

    private class RunEvent(
        val actorRunnable: ActorRunnable
    ) : ActorableEventHandler {
        override suspend fun handle() {
            actorRunnable.run()
        }
    }

    data class RunOnAnswerableEvent<X>(
        val actorAnswerable: ActorAnswerable<X>,
        val answerChannel: Channel<X>
    ) : ActorableEventHandler {
        override suspend fun handle() {
            answerChannel.send(actorAnswerable.run())
        }
    }

    private val channel: Channel<ActorableEventHandler> = Channel()
    val job = scope.launch(context) {
        while (true) {
            val e = channel.receive()
            e.handle()
        }
    }

    suspend fun <X> receiveAnswer(t: ActorAnswerable<X>): X {
        val answerChannel = Channel<X>()
        scope.launch(context) {
            channel.send(RunOnAnswerableEvent(t, answerChannel))
        }
        return answerChannel.receive()
    }

    fun runEvent(t: ActorRunnable) {
        scope.launch(context) {
            channel.send(RunEvent(t))
        }
    }
}
