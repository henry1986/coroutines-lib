package org.daiv.coroutines

import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals

class DataActorTest {
    data class StateX(val x:Int, val s:String)
    @Test
    fun test() = runTest{
        val myDataActor = DataActor(StateX(9, "Hello"))
        myDataActor.new { copy(5) }.join()
        assertEquals(myDataActor.getData(), StateX(5, "Hello"))
        myDataActor.new { copy(s = "World") }.join()
        assertEquals( StateX(5, "World"), myDataActor.getData())
    }
}