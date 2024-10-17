package org.daiv.coroutines

import kotlinx.coroutines.channels.Channel

class Waiter(private val channel: Channel<Unit> = Channel<Unit>(0)) {
    suspend fun doWait() {
        channel.receive()
    }

    suspend fun notifyAndWait() {
        channel.send(Unit)
    }

    fun tryNotify() {
        channel.trySend(Unit)
    }
}

/**
 * a class to simulate time for delayFunctions. Explicitly designed for test purpose.
 */
class TimeHolder(startTime: Long = 0L) {
    val waiter = Waiter()
    private val actor = DataActor<Long>(startTime)
    suspend fun notifyAndWait() {
        waiter.notifyAndWait()
    }

    /**
     * increase current time
     */
    fun increase(time: Long) {
        actor.change {
            waiter.tryNotify()
            this + time
        }
    }

    /**
     * get a delay function. Function suspends until [increase] is called. Then it checks, if enough time is past, and
     * finishes. Otherwise, it suspends again until next call of [increase]
     */
    fun getDelayFunction(): suspend (Long) -> Unit {
        return {
            val startTime = actor.getData()
            do {
                waiter.doWait()
                val time = actor.getData()
            } while (time < startTime + it)
        }
    }
}
