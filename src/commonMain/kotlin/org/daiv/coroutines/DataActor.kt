package org.daiv.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging

interface TimeTriggerable<T : TimeTriggerable<T>> {
    fun getNextTime(): Long?
    fun runTimeEvent(time: Long): T
    fun isToCall(): Boolean = true
}

fun interface TimeGetter {
    fun currentTime(): Long
    fun getDelayTime(nextTime: Long, timerable: String): Long? {
        val current = currentTime()
        val delayTime = nextTime - current
        if (delayTime < 0) {
            logger.warn { "time was already done: nextTime $nextTime vs current $current, $timerable is not executed" }
            return null
        }
        return delayTime
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }
}

class DataHolder<T : Any>(private var t: T) {
    suspend fun doChange(func: suspend T.() -> T) {
        t = t.func()
    }

    fun name() = t.toString()

    fun plainData() = t
}

class TimeHandler<T : TimeTriggerable<T>>(
    private val timeGetter: TimeGetter,
    val delayFct: suspend (Long) -> Unit = { delay(it) }
) : AfterChange<T> {

    private var job: Job? = null

    private fun resetEvent() = job?.cancel()

    fun hasJob() = job?.isActive ?: false

    override suspend fun afterChange(scope: CoroutineScope, afterChangeCom: AfterChangeCom<T>) {
        resetEvent()
        afterChangeCom.getValue().getNextTime()?.let { nextTime ->
            job = scope.launch {
                delayFct(timeGetter.getDelayTime(nextTime, afterChangeCom.name()) ?: return@launch)
                afterChangeCom.callBack { it.runTimeEvent(nextTime) }
            }
        }
    }

    override fun isToCall(t: T): Boolean {
        return t.isToCall()
    }

    override fun noCall() {
        resetEvent()
    }
}

interface AfterChangeCom<T : Any> {
    fun name(): String
    fun callBack(func: suspend (T) -> T)
    fun getValue(): T
}

interface AfterChange<T : Any> {
    fun isToCall(t: T): Boolean
    fun noCall()
    suspend fun afterChange(scope: CoroutineScope, afterChangeCom: AfterChangeCom<T>)

    companion object {
        fun <T : Any> noChange() = object : AfterChange<T> {
            override suspend fun afterChange(scope: CoroutineScope, afterChangeCom: AfterChangeCom<T>) {

            }

            override fun isToCall(t: T): Boolean {
                return false
            }

            override fun noCall() {
            }
        }
    }
}

/**
 * A DataActor with
 *
 * leave [afterChange], if you just want a normal DataActor. If you want to use a [TimeTriggerable] for [t],
 * then set [afterChange] to a [TimeHandler]
 */
class DataActor<T : Any>(
    t: T,
    val afterChange: AfterChange<T> = AfterChange.noChange(),
    val onEveryChange: ((T) -> Unit)? = null,
    val actor: ActorableInterface = ActorableInterface(t.toString())
) : CActor<T>, Closeable by actor {
    private val dataHolder: DataHolder<T> = DataHolder(t)
    private val afterChangeCom = object : AfterChangeCom<T> {
        override fun name(): String {
            return dataHolder.name()
        }

        override fun callBack(t:suspend (T)-> T) {
            change(t)
        }

        override fun getValue(): T {
            return plainData()
        }
    }

    private suspend fun doChange(func: suspend T.() -> T) {
        dataHolder.doChange(func)
        if (afterChange.isToCall(dataHolder.plainData())) {
            afterChange.afterChange(actor.scope, afterChangeCom)
        } else {
            afterChange.noCall()
        }
        onEveryChange?.invoke(dataHolder.plainData())
    }

    override fun change(func: suspend T.() -> T): Job {
        return actor.runEvent {
            doChange(func)
        }
    }

    override suspend fun suspendChange(func: suspend T.() -> T) {
        actor.suspendRunEvent {
            doChange(func)
        }
    }

    fun onData(func: suspend T.() -> Unit) {
        actor.runEvent {
            dataHolder.plainData().func()
        }
    }

    suspend fun suspendOnData(func: suspend T.() -> Unit) {
        actor.suspendRunEvent {
            dataHolder.plainData().func()
        }
    }

    override suspend fun changeAndReceive(func: suspend T.() -> T): T {
        return actor.receiveAnswer {
            doChange(func)
            dataHolder.plainData()
        }
    }

    override fun plainData(): T = dataHolder.plainData()

    override suspend fun getData(): T {
        return actor.receiveAnswer {
            dataHolder.plainData()
        }
    }
}

interface CActor<T : Any> {
    fun change(func: suspend T.() -> T): Job
    suspend fun changeAndReceive(func: suspend T.() -> T): T
    suspend fun suspendChange(func: suspend T.() -> T)
    suspend fun getData(): T
    fun plainData(): T
}
