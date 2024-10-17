package org.daiv.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals

class StartManagerTest {

    @Test
    fun testOnStarted() = runTest {
        val s = StartManager(Unit)
        var onNotStart = 0
        var onStart = 0
        var onStart2 = 0
        s.runOnStart { onStart = 1 }.join()
        s.runOnNotStart { onNotStart = 1 }.join()
        s.initialized().join()
        s.runOnNotStart { onNotStart = 2 }.join()
        s.runOnStart { onStart2 = 1 }.join()
        s.waitOnActor()
        assertEquals(1, onNotStart, "onNotStart")
        assertEquals(1, onStart, "onStart")
        assertEquals(1, onStart2, "onStart2")
    }

    @Test
    fun testOnStartedChangeState() = runTest {
        val s = StartManager(1)
        var onNotStart = 0
        var onStart = 0
        var onStart2 = 0
        s.runOnStart { onStart = 1 }.join()
        s.runOnNotStart {
            onNotStart = 1
            5
        }.join()
        s.initialized().join()
        s.runOnStart {
            if (it == 5)
                onStart2 = 1
            else
                onStart2 = 3
        }.join()
        s.runOnNotStart {
            onNotStart = 2
            6
        }.join()
        s.waitOnActor()
        assertEquals(1, onNotStart, "onNotStart")
        assertEquals(1, onStart, "onStart")
        assertEquals(1, onStart2, "onStart2")
    }
}
