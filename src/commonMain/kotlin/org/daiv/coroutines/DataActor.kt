package org.daiv.coroutines

import kotlinx.coroutines.Job

data class DataActor<T : Any>(private var t: T) {
    val actor = ActorableInterface("")

    fun new(func: T.() -> T): Job {
        return actor.runEvent {
            t = t.func()
        }
    }

    suspend fun suspendNew(func: T.() -> T) {
        actor.suspendRunEvent {
            t = t.func()
        }
    }

    suspend fun getData(): T {
        return actor.receiveAnswer {
            t
        }
    }
}
