package androidx.compose.runtime.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentityHashCodeTest {

    @Test
    fun smokeTest() {
        val a = DefaultImpl()
        val b = DefaultImpl()

        assertEquals(a, a)
        assertNotEquals(a, b)
        assertNotEquals(b, a)

        val set = mutableSetOf<DefaultImpl>()
        set.add(a)
        set.add(a)
        set.add(b)

        assertEquals(set.size, 2)
    }

}

private class DefaultImpl {
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return identityHashCode(this)
    }
}