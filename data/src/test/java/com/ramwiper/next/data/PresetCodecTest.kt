package com.ramwiper.next.data
import com.ramwiper.next.domain.*
import org.junit.Assert.*
import org.junit.Test
class PresetCodecTest {
    @Test fun roundTripAndLegacyDefault() {
        val rows=listOf(SelectionPreset("Работа",setOf("test.app1","test.app2")))
        assertEquals(rows,PresetCodec.decode(PresetCodec.encode(rows)))
        assertTrue(PresetCodec.decode("[]").isEmpty())
    }
    @Test fun invalidAndDuplicateRowsDoNotCreateSelections() {
        val raw="""[{},{"name":"Set","packages":["test.app"]},{"name":"set","packages":["other.app"]},{"name":"","packages":["other.app"]}]"""
        assertEquals(listOf(SelectionPreset("Set",setOf("test.app"))),PresetCodec.decode(raw))
    }
}
