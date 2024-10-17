package org.daiv.coroutines

import kotlinx.coroutines.Job
import mu.KotlinLogging

class StartManager<T:Any>(t:T) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val actor = DataActor(t)
    private val waiter = mutableListOf<AnswerOnStart>()
    private var isInit: Boolean = false

    suspend fun waitOnActor() = actor.actor.waitOnDone {  }

    private inner class AnswerOnStart(private val action: suspend (T) -> Unit) : ActorRunnable {
        suspend fun send(t:T) {
            action(t)
        }

        override suspend fun run() {
            if (isInit) {
                send(actor.plainData())
            } else {
                waiter.add(this)
            }
        }
    }


    fun initialized(): Job {
        return actor.change {
            waiter.forEach { it.send(this) }
            waiter.clear()
            isInit = true
            this
        }

    }

    fun runOnStart(action: suspend (T) -> Unit): Job {
        return actor.actor.runEvent(AnswerOnStart(action))
    }

    fun runOnNotStart(action: suspend (T) -> T): Job {
        return actor.change {
            if(!isInit){
                action(this)
            } else {
                this
            }
        }
    }
}
