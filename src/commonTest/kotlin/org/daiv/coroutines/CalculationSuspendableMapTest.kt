package org.daiv.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

expect fun runTest(block: suspend () -> Unit)

class CalculationSuspendableMapTest {
    private fun map() = CalculationSuspendableMap<Int, Int>("test") {
        println("start $it")
        delay(500L)
        println("delay done $it")
        when (it) {
            5 -> calcCounter++
            6 -> calcCounter2++
        }
        it + 6
    }

    var calcCounter = 0
    var calcCounter2 = 0

    @BeforeTest
    fun before() {
        calcCounter = 0
        calcCounter2 = 0
    }

    class Called(var called: Boolean = false, val name: String = "") {
        fun callDone() {
            called = true
        }

        override fun toString(): String {
            return "Called: $name -> $called"
        }
    }

    private suspend fun CalculationSuspendableMap<Int, Int>.testLaunch(
        i1: Int,
        i2: Int,
        launchBlock: suspend CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Unit
    ): Called {
        val called = Called(name = "$i1")
        launchBlock(i1) {
            called.called = true
            assertEquals(i2, it)
        }
        return called
    }

    private suspend fun CalculationSuspendableMap<Int, Int>.testLaunch5(launchBlock: suspend CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Unit) =
        testLaunch(5, 11, launchBlock)

    private suspend fun CalculationSuspendableMap<Int, Int>.testLaunch6(launchBlock: suspend CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Unit) =
        testLaunch(6, 12, launchBlock)

    @Test
    fun test() = runTest {
        val map = map()
        suspend fun launch5() = map.testLaunch5 { k, afterRecvResult -> launch(k, afterRecvResult) }
        suspend fun launch6() = map.testLaunch6 { k, afterRecvResult -> launch(k, afterRecvResult) }

        val allJobs = listOf(launch5(), launch6(), launch5(), launch5())
//        allJobs.joinAll()
        map.join()
        // test if calculation from K to V was called
        assertEquals(1, calcCounter)
        assertEquals(1, calcCounter2)
        // test if function after calculation was called
        assertTrue(allJobs.all { it.called })
    }


    @Test
    fun testCallOnNotExistence() = runTest {
        val map = map()
        suspend fun launch5() = map.testLaunch5 { k, afterRecvResult -> launchOnNotExistence(k, afterRecvResult) }
        suspend fun launch6() = map.testLaunch6 { k, afterRecvResult -> launchOnNotExistence(k, afterRecvResult) }

        val allJobs = listOf(launch5(), launch6(), launch5(), launch5())
        map.join()
        val fives = allJobs.filter { it.name == "5" }
        assertEquals(3, fives.size)
        assertEquals(1, fives.filter { it.called }.size)
        val sixes = allJobs.filter { it.name == "6" }
        assertEquals(1, sixes.size)
        assertEquals(1, sixes.filter { it.called }.size)
        // test if function after calculation was called or rather was not called, if calculation was already done or already in process
//        assertTrue(allJobs.take(2).all { it.called }, "first two jobs are not called, $allJobs")
//        assertTrue(allJobs.drop(2).all { !it.called }, "last jobs are (partly) called, $allJobs")
        // test if calculation from K to V was called
        assertEquals(1, calcCounter)
        assertEquals(1, calcCounter2)
    }

    @Test
    fun testJoin() = runTest {
        val map = map()
        val called = Called()
        map.launch(4) {
            delay(500)
            map.launch(6) {
                called.called = true
            }
        }
        map.join()
        assertTrue(called.called)
    }

}