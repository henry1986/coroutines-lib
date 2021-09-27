package org.daiv.coroutines

import mu.KotlinLogging

class StartHandler {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val actor = ActorableInterface("BenderCP")
    private val waiter = mutableListOf<AnswerMeterSerial>()
    private var isInit: Boolean = false

    private inner class AnswerMeterSerial(private val action: suspend () -> Unit) : ActorRunnable {
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
        actor.runEvent(AnswerMeterSerial(action))
    }
}