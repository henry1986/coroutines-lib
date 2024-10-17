package org.daiv.coroutines

import mu.KotlinLogging

@Deprecated("use StartManager instead", replaceWith = ReplaceWith("StartManager"))
class StartHandler {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val actor = ActorableInterface("StartHandler")
    private val waiter = mutableListOf<AnswerOnStart>()
    private var isInit: Boolean = false

    private inner class AnswerOnStart(private val action: suspend () -> Unit) : ActorRunnable {
        suspend fun send() {
            action()
        }

        override suspend fun run() {
            if (isInit) {
                send()
            } else {
                waiter.add(this)
            }
        }
    }

    private inner class Initialized : ActorRunnable {
        override suspend fun run() {
            waiter.forEach { it.send() }
            waiter.clear()
            isInit = true
        }
    }

    fun initialized() {
        actor.runEvent(Initialized())
    }

    fun runAction(action: suspend () -> Unit) {
        actor.runEvent(AnswerOnStart(action))
    }
}
