package org.daiv.coroutines

import kotlinx.coroutines.Job

data class DataActor<T : Any>(
    private var t: T,
    private val actor: ActorableInterface = ActorableInterface(t.toString())
) {
    fun change(func: T.() -> T): Job {
        return actor.runEvent {
            t = t.func()
        }
    }

    suspend fun suspendChange(func: T.() -> T) {
        actor.suspendRunEvent {
            t = t.func()
        }
    }

    fun plainData() = t

    suspend fun getData(): T {
        return actor.receiveAnswer {
            t
        }
    }
}
