package org.daiv.coroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
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
        }) {
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
        assertEquals(listOf("World"), x)
//        println("x: $x")
    }

    @Test
    fun avoidConcurrentModificationException() = runTest {
        val c = CalculationCollection<Int, String, Any>("test")
        c.insert("Hello5", 5, {
            delay(100)
            println("delay done")
            delay(100)
            c.insert("Hello6", 6, {
                delay(100)
                "World6"
            }) {

            }
            "World5"
        }) {

        }
        c.insert("Hello8", 8, {
            delay(100)
            println("delay done")
            "World8"
        }) {

        }
        println("start join")
        c.join()
        val l = c.all().flatMap { it.all() }
        assertEquals(setOf("World5", "World6", "World8"), l.toSet())
    }
}
