package org.daiv.coroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test

class FlowTest {
    @Test
    fun test() = runTest {
        val list: List<Int> = listOf(
            5, 9, 10
        )
        val f = flow<Int> {
            list.forEach {
                delay(10)
                emit(it)
            }
        }.toList()
        println("f: $f")
        println("x: ${(1..5).map { it.toString() }}")
    }
}