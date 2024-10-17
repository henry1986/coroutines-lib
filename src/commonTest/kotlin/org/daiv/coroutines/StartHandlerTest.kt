package org.daiv.coroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartHandlerTest {
    @Test
    fun test() = runTest {
        val s = StartHandler()
        val channel = Channel<Boolean>()
        var firstAction = false
        var action2 = false
        var action3 = false
            s.runAction { firstAction = true }
        s.runAction { action2 = true }
        GlobalScope.launch {
            channel.send(firstAction)
            channel.send(action2)
            delay(1000)
            s.initialized()
            s.runAction {
                channel.send(action3)
                channel.send(firstAction)
                channel.send(action2)
                s.runAction { action3 = true }
                s.runAction {
                    channel.send(action3)
                }
            }
        }
        assertFalse(channel.receive())
        assertFalse(channel.receive())
        assertFalse(channel.receive())
        assertTrue(channel.receive())
        assertTrue(channel.receive())
        assertTrue(channel.receive())
    }
}
