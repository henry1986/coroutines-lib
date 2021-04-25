package org.daiv.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobMapTest {
    companion object {
        private val logger = KotlinLogging.logger("org.daiv.coroutines.JobMapTest")
    }

    private class Counter {
        val scope = DefaultScopeContextable()
        val list = mutableSetOf<Int>()
        val jobMap = JobMap("test", scope)
        suspend fun launch(i: Int, delay: Long, block: suspend () -> Unit = {}) = jobMap.nextJob(scope.launch("job i: $i") {
            logger.trace { "before delay $i" }
            delay(delay)
            logger.trace { "delay done: $i" }
            block()
            list.add(i)
        })

        suspend fun launchWithDelay(i: Int, delay: Long, block: suspend () -> Unit = {}) {
            val job = scope.launch("DelayJob i: $i") {
                logger.trace { "before delayJob $i" }
                delay(delay)
                logger.trace { "delayJob done: $i" }
                block()
                list.add(i)
            }
            delay(1000)
            jobMap.nextJob(job)
        }

        suspend fun join() = jobMap.join()
        fun assert(set: Set<Int>) = assertEquals(set, list)
    }

    @Test
    fun test() = runTest {
        val counter = Counter()
        counter.launch(1, 500)
        counter.launch(2, 600)
        counter.launch(3, 300)
        counter.join()
        counter.assert(setOf(1, 2, 3))
    }

    @Test
    fun testIntern() = runTest {
        val counter = Counter()
        counter.launch(1, 500) {
            counter.launchWithDelay(5, 500)
        }
        counter.launch(2, 350) {
            counter.launchWithDelay(6, 500)
        }
        counter.launch(3, 196) {
            counter.launchWithDelay(8, 500)
        }
        counter.join()
        counter.assert(setOf(1, 2, 3, 5, 6, 8))
    }

    @Test
    fun testCalcWrite() = runTest {
        val write = CalculationCollectionWrite("test", CalculationCollection<Int, Int, Any>("test"))
        val called = CalculationSuspendableMapTest.Called()
        write.insert(5, 6, { "Hello $it" }) {
            logger.trace { "here: $it" }
            called.callDone()
        }
        write.join()
        assertTrue(called.called)
    }

    @Test
    fun testNextJob() = runTest {
        val scope = DefaultScopeContextable()
        val called = CalculationSuspendableMapTest.Called()
        val jobMap = JobMap("", scope)
        jobMap.nextJob(scope.launch("testLaunch") {
            val l = scope.launch("") {
                delay(100)
                called.callDone()
            }
            jobMap.nextJob(l)
        })
        jobMap.join()
        assertTrue(called.called)
    }


//    @Test
//    fun testChild()= runTest{
//        val scope = DefaultScopeContextable()
//        val job = scope.launch("test"){
//            delay(10)
//            launch {
//                delay(5000)
//                logger.trace { "hello"}
//            }
//        }
//        delay(1000)
//        logger.trace { "job: $job"}
//        job.join()
//        logger.trace { "job: $job"}
//    }
}
