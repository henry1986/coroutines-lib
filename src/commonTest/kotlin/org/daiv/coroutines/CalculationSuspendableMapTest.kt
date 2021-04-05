package org.daiv.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

expect fun runTest(block: suspend () -> Unit)

class CalculationSuspendableMapTest {
    private fun map() = CalculationSuspendableMap<Int, Int> {
        delay(500L)
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

    class Called(var called: Boolean = false)
    class CalledJob(job: Job, val called: Called) : Job by job

    private fun CalculationSuspendableMap<Int, Int>.testLaunch(
        i1: Int,
        i2: Int,
        launchBlock: CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Job
    ): CalledJob {
        val called = Called()
        return CalledJob(launchBlock(i1) {
            called.called = true
            assertEquals(i2, it)
        }, called)
    }

    private fun CalculationSuspendableMap<Int, Int>.testLaunch5(launchBlock: CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Job) =
        testLaunch(5, 11, launchBlock)

    private fun CalculationSuspendableMap<Int, Int>.testLaunch6(launchBlock: CalculationSuspendableMap<Int, Int>.(Int, suspend (Int) -> Unit) -> Job) =
        testLaunch(6, 12, launchBlock)

    @Test
    fun test() = runTest {
        val map = map()
        fun launch5() = map.testLaunch5 { k, afterRecvResult -> launch(k, afterRecvResult) }
        fun launch6() = map.testLaunch6 { k, afterRecvResult -> launch(k, afterRecvResult) }

        val allJobs = listOf(launch5(), launch6(), launch5(), launch5())
        allJobs.joinAll()
        // test if calculation from K to V was called
        assertEquals(1, calcCounter)
        assertEquals(1, calcCounter2)
        // test if function after calculation was called
        assertTrue(allJobs.all { it.called.called })
    }


    @Test
    fun testCallOnNotExistence() = runTest {
        val map = map()
        fun launch5() = map.testLaunch5 { k, afterRecvResult -> launchOnNotExistence(k, afterRecvResult) }
        fun launch6() = map.testLaunch6 { k, afterRecvResult -> launchOnNotExistence(k, afterRecvResult) }

        val allJobs = listOf(launch5(), launch6(), launch5(), launch5())
        allJobs.joinAll()
        // test if function after calculation was called or rather was not called, if calculation was already done or already in process
        assertTrue(allJobs.take(2).all { it.called.called })
        assertTrue(allJobs.drop(2).all { !it.called.called })
        // test if calculation from K to V was called
        assertEquals(1, calcCounter)
        assertEquals(1, calcCounter2)
    }
}