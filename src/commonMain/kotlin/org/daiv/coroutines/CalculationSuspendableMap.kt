package org.daiv.coroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.launch
import org.daiv.coroutines.CalculationSuspendableMap.CalculationState


/**
 * stores the state of the calculation. If [calculationState] is [CalculationState.DONE], then [v] is set.
 */
internal data class ValueData<V>(
    val calculationState: CalculationState,
    val v: V?,
    val job: Job?,
    val listener: MutableList<Channel<V>>
) {
    fun getValue(channel: Channel<V>?): V? {
        return when (calculationState) {
            CalculationState.CALCULATING -> {
                channel?.let { this.listener.add(it) }
                null
            }
            CalculationState.DONE -> v ?: throw RuntimeException("if calculationState is done, then v should never be null")
        }
    }
}


class CalculationSuspendableMap<K : Any, V : Any>(val valueCreation: suspend (K) -> V) {
    private val contextable = DefaultScopeContextable()
    private val actorableInterface = ActorableInterface(contextable)

    enum class CalculationState {
        CALCULATING, DONE
    }

    private val map = mutableMapOf<K, ValueData<V>>()


    private inner class SetValue(val key: K, val v: V) : ActorRunnable {
        override suspend fun run() {
            val listener = map[key]?.listener
            map[key] = ValueData(CalculationState.DONE, v, null, mutableListOf())
            listener?.forEach { it.send(v) }
        }
    }


    interface Calculatable<K : Any, V : Any> {
        val key: K
        val channel: Channel<V>

        fun startCalculation(): V?
    }

    inner class StartCalculatable(
        override val key: K,
        override val channel: Channel<V>,
    ) : Calculatable<K, V> {

        private fun valueData(job: Job) =
            ValueData(CalculationState.CALCULATING, null, job, mutableListOf(channel))

        override fun startCalculation(): V? {
            val job = contextable.scope.launch(contextable.context + CoroutineName("Calculate $key")) {
                val ret = valueCreation(key)
                actorableInterface.runEvent(SetValue(key, ret))
            }
            map[key] = valueData(job)
            return null
        }
    }

    internal inner class GetValue(startCalculatable: StartCalculatable) :
        ActorAnswerable<V?>, Calculatable<K, V> by startCalculatable {

        override suspend fun run(): V? {
            return map[key].let {
                if (it == null) {
                    startCalculation()
                } else {
                    it.getValue(channel)
                }
            }
        }
    }

    internal inner class ExecuteOnNotExistence(startCalculatable: StartCalculatable) :
        ActorRunnable, Calculatable<K, V> by startCalculatable {

        override suspend fun run() {
            val res = map[key]
            when (res) {
                null -> startCalculation()
                else -> {
                    channel.close()
                }
            }
        }
    }


    fun tryDirectGet(key: K) = map[key]?.v

    suspend fun getValue(key: K): V {
        return tryDirectGet(key) ?: run {
            val channel = Channel<V>()
            actorableInterface.receiveAnswer(GetValue(StartCalculatable(key, channel))) ?: run {
                channel.receive()
            }
        }
    }

    fun launch(key: K, afterRes: suspend (V) -> Unit): Job {
        return contextable.scope.launch(contextable.context) { afterRes(getValue(key)) }
    }

    fun launchOnNotExistence(key: K, afterRes: suspend (V) -> Unit): Job {
        val job = contextable.scope.launch(contextable.context + CoroutineName("After Calculation $key")) {
            tryDirectGet(key) ?: run {
                val channel = Channel<V>()
                actorableInterface.runEvent(ExecuteOnNotExistence(StartCalculatable(key, channel)))
                channel.receiveOrNull()?.let { afterRes(it) }
            }
        }
        return job
    }

//    fun setInsertionResult(key: ClassKey, insertionResult: InsertionResult<*>) {
//        map[key] = InsertionData(InsertionState.DONE, insertionResult)
//    }

    fun all() = map.values.map { it.v }
}
