package org.daiv.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlin.test.*

class DataActorTest {
    data class StateX(val x: Int, val s: String)

    @Test
    fun test() = runTest {
        val myDataActor = DataActor(StateX(9, "Hello"))
        myDataActor.change { copy(x = 5) }.join()
        assertEquals(myDataActor.getData(), StateX(5, "Hello"))
        myDataActor.change { copy(s = "World") }.join()
        assertEquals(StateX(5, "World"), myDataActor.getData())
    }

    @Test
    fun testDelay() = runTest {
        val timeGetter = object : TimeGetter {
            override fun currentTime(): Long {
                return 500L
            }
        }
        assertEquals(20L, timeGetter.getDelayTime(520L, "testTimeTriggerable"))
        assertEquals(null, timeGetter.getDelayTime(499L, "testTimeTriggerable"))
    }

    data class StateTime(val _nextTime: Long? = null, val t: Int = 0) :
        TimeTriggerable<StateTime> {
        override fun getNextTime(): Long? {
            return _nextTime
        }

        override fun runTimeEvent(time: Long): StateTime {
            return copy(t = t + 1, _nextTime = null)
        }

        override fun isToCall(): Boolean {
            return _nextTime != null
        }
    }

    private fun check1(timeHandler: TimeHandler<StateTime>, t: StateTime) {
        assertTrue(timeHandler.hasJob())
        assertEquals(StateTime(520, 0), t)
    }

    @Test
    fun testClose() = runTest {
        val myDataActor = DataActor(StateX(9, "Hello"))
        myDataActor.suspendChange { copy(5) }
        myDataActor.stop()
        try {
            myDataActor.suspendChange { copy(6) }
            myDataActor.suspendChange { copy(7) }
            fail("exception should be thrown")
        } catch (t: CancellationException) {
            assertEquals(myDataActor.plainData(), StateX(5, "Hello"))
            return@runTest
        }
    }


    @Test
    fun testTimeActor() = runTest {
        val suspendChannel = Channel<Any>()
        val timeGetter = object : TimeGetter {
            override fun currentTime(): Long {
                return 500L
            }
        }
        val timeHandler: TimeHandler<StateTime> = TimeHandler(timeGetter) { suspendChannel.receive() }
        val d = DataActor(StateTime(), timeHandler)
        val c = Channel<StateTime>()
        assertFalse(timeHandler.hasJob())
        d.suspendChange { copy(_nextTime = 520L) }
        d.suspendOnData {
            c.send(this@suspendOnData)
        }
        check1(timeHandler, c.receive())
        suspendChannel.send(Any())
        delay(10)
        d.suspendOnData {
            c.send(this)
        }
        assertFalse(timeHandler.hasJob())
        assertEquals(StateTime(null, 1), c.receive())
    }

    @Test
    fun testTimeAbort() = runTest {
        val suspendChannel = Channel<Any>()
        val timeGetter = object : TimeGetter {
            override fun currentTime(): Long {
                return 500L
            }
        }
        val timeHandler: TimeHandler<StateTime> = TimeHandler(timeGetter) { suspendChannel.receive() }
        val d = DataActor(StateTime(), timeHandler)
        val c = Channel<StateTime>()
        assertFalse(timeHandler.hasJob())
        d.suspendChange { copy(_nextTime = 520L) }
        d.suspendOnData {
            c.send(this@suspendOnData)
        }
        check1(timeHandler, c.receive())
        // abort counter
        d.suspendChange { copy(_nextTime = null) }
        d.suspendOnData {
            c.send(this)
        }
        assertFalse(timeHandler.hasJob())
        assertEquals(StateTime(null, 0), c.receive())
    }
}
