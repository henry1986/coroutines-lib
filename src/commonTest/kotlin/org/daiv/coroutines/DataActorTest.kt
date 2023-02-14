package org.daiv.coroutines

import kotlin.test.Test
import kotlin.test.assertEquals

class DataActorTest {
    data class StateX(val x:Int, val s:String)
    @Test
    fun test() = runTest{
        val myDataActor = DataActor(StateX(9, "Hello"))
        myDataActor.change { copy(5) }.join()
        assertEquals(myDataActor.getData(), StateX(5, "Hello"))
        myDataActor.change { copy(s = "World") }.join()
        assertEquals( StateX(5, "World"), myDataActor.getData())
    }
}