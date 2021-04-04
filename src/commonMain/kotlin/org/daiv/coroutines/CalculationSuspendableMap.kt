package org.daiv.coroutines

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
    fun getValue(channel: Channel<V>): V? {
        return when (calculationState) {
            CalculationState.CALCULATING -> {
                this.listener.add(channel)
                null
            }
            CalculationState.DONE -> v ?: throw RuntimeException("if calculationState is done, then v should never be null")
        }
    }
}


class CalculationSuspendableMap<K : Any, V : Any> {
    val contextable = DefaultScopeContextable()
    val actorableInterface = ActorableInterface(contextable)

    enum class CalculationState {
        CALCULATING, DONE
    }

    private val map = mutableMapOf<K, ValueData<V>>()


    private inner class SetValue(val key: K, val v: V) : ActorRunnable {
        override suspend fun run() {
            map[key]?.listener?.forEach { it.send(v) }
            map[key] = ValueData(CalculationState.DONE, v, null, mutableListOf())
        }
    }

    internal inner class GetValue(val key: K, val channel: Channel<V>, val block: suspend () -> V) :
        ActorAnswerable<V?> {

        fun startCalculation(): V? {
            val job = contextable.scope.launch(contextable.context + CoroutineName("Calculate $key")) {
                val ret = block()
                actorableInterface.runEvent(SetValue(key, ret))
            }
            map[key] = ValueData(CalculationState.CALCULATING, null, job, mutableListOf(channel))
            return null
        }

        override suspend fun run(): V? {
            return map[key].let {
                if(it == null){
                    startCalculation()
                } else {
                    it.getValue(channel)
                }
            }
        }
    }

    fun tryDirectGet(key: K) = map[key]?.v

    suspend fun getValue(key: K, block: suspend () -> V): V {
        return tryDirectGet(key) ?: run {
            val channel = Channel<V>()
            actorableInterface.receiveAnswer(GetValue(key, channel, block)) ?: run {
                channel.receive()
            }
        }
    }

//    fun setInsertionResult(key: ClassKey, insertionResult: InsertionResult<*>) {
//        map[key] = InsertionData(InsertionState.DONE, insertionResult)
//    }

    fun all() = map.values.map { it.v }
}
