package org.daiv.coroutines

import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculationCollectionTest {

    @Test
    fun test() = runTest {
        val c = CalculationCollection<Int, String, Any>("test")
        val calledCalc = CalculationSuspendableMapTest.Called()
        val calledCalc2 = CalculationSuspendableMapTest.Called()
        val calledAfterCalc = CalculationSuspendableMapTest.Called()
        val calledAfterCalc2 = CalculationSuspendableMapTest.Called()
        c.insert("Hello", 5, { s ->
            delay(100)
            calledCalc.callDone()
            "World"
        }) {
            println("after 1")
            calledAfterCalc.callDone()
        }
        c.insert("Hello", 5, { s ->
            calledCalc2.callDone()
            "World"
        }){
            println("after 2")
            calledAfterCalc2.callDone()
        }
        println("join")
        c.join()
        println("joined")
        assertTrue(calledAfterCalc.called)
        assertTrue(calledCalc.called != calledCalc2.called)
        assertTrue(calledAfterCalc2.called)
        val x = c.all().flatMap { it.all() }
//        println("x: $x")
    }
}
