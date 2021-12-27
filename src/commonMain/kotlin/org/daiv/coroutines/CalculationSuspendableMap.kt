package org.daiv.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
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


class CalculationSuspendableMap<K, V : Any>(
    name: String,
    private val contextable: ScopeContextable = DefaultScopeContextable(),
    val valueCreation: suspend (K) -> V
) :Joinable{

    private val actorableInterface = ActorableInterface("$name -> suspendableMap", channelCapacity= Channel.RENDEZVOUS, scopeContextable = contextable)
    private val jobMap = JoinSet("CalculationSuspendableMap", contextable)

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

        override fun toString() = "setValue $key, $v"
    }


    interface Calculatable<K, V : Any> {
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

        override fun toString() = "startCalculation: $key"
    }

    internal inner class GetValue(private val startCalculatable: StartCalculatable) :
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

        override fun toString() = startCalculatable.toString()
    }

    internal inner class ExecuteOnNotExistence(private val startCalculatable: StartCalculatable) :
        ActorRunnable, Calculatable<K, V> by startCalculatable {

        override suspend fun run() {
            val res = map[key]
            when (res) {
                null -> {
                    startCalculation()
                }
                else -> {
                    channel.close()
                }
            }
        }

        override fun toString() = startCalculatable.toString()
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

    /**
     * launches a coroutine to get the value [V] for [K] and stores the result. [afterRes] is called, after the calculation
     * is done.
     * If there is already a result [V] for [K], there is no calculation, but [afterRes] is called nonetheless
     */
    fun launch(key: K, afterRes: suspend (V) -> Unit): Job {
        val job = contextable.launch("launch $key") { afterRes(getValue(key)) }
        jobMap.offerNextJob(job)
        return job
    }

    /**
     * launches a coroutine to get the value [V] for [key] and stores the result. [afterRes] is called, after the calculation
     * is done.
     * If there is already a result [V] for [K], there is no calculation, and [afterRes] is NOT called
     */
    fun launchOnNotExistence(key: K, afterRes: suspend (V) -> Unit): Job {
        val job = contextable.launch("launchOnNotExistence $key") {
            tryDirectGet(key) ?: run {
                println("run $key")
                val channel = Channel<V>()
                actorableInterface.runEvent(ExecuteOnNotExistence(StartCalculatable(key, channel)))
                channel.receiveCatching().getOrNull()?.let {
                    afterRes(it)
                }
            }
        }
        jobMap.offerNextJob(job)
        return job
    }

//    fun setInsertionResult(key: ClassKey, insertionResult: InsertionResult<*>) {
//        map[key] = InsertionData(InsertionState.DONE, insertionResult)
//    }

    override suspend fun join() {
        jobMap.join()
    }

    suspend fun isEmpty() = jobMap.isEmpty()

    suspend fun all(): List<V> {
        join()
        return map.values.map { it.v!! }
    }

    suspend fun map(): Map<K, V> {
        join()
        return map.map { it.key to it.value.v!! }.toMap()
    }
}




