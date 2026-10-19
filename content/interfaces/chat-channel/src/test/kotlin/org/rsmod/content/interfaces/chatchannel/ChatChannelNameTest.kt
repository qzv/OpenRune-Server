package org.rsmod.content.interfaces.chatchannel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatChannelNameTest {
    @Test
    fun `single letter is capitalised`() {
        assertEquals("A", ChatChannelName.format("a"))
    }

    @Test
    fun `dashes underscores and spaces become separators`() {
        assertEquals("L O L F", ChatChannelName.format("L-o_l f"))
    }

    @Test
    fun `invalid characters become separators`() {
        assertEquals("Lm  Ao", ChatChannelName.format("Lm\$\$ao"))
    }

    @Test
    fun `leading separators are dropped`() {
        assertEquals("Testing   L", ChatChannelName.format("  Testing__ L"))
    }

    @Test
    fun `names are cut to twelve characters and trimmed`() {
        assertEquals("This Is A", ChatChannelName.format("This is a     test"))
    }

    @Test
    fun `blank names are invalid`() {
        assertNull(ChatChannelName.format(" "))
        assertNull(ChatChannelName.format("_"))
    }
}
