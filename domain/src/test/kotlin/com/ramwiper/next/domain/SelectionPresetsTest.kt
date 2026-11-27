package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class SelectionPresetsTest {
    private val targets = (1..15).map { AppTarget("App $it", "test.app$it") }
    @Test fun saveAndApplyRevalidateProtectionAndInstallation() {
        val saved = SelectionPresets.add(emptyList(), " Игры ", targets.map { it.pkg }.toSet(), setOf("test.app2"), targets).single()
        assertEquals("Игры", saved.name)
        assertEquals(12, saved.packages.size)
        assertFalse("test.app2" in saved.packages)
        val current = SelectionPresets.apply(saved,setOf("test.app3"),targets.filterNot { it.pkg=="test.app1" })
        assertFalse("test.app1" in current); assertFalse("test.app3" in current)
        assertEquals(10,current.size)
    }
    @Test fun duplicateEmptyAndOverLimitRejected() {
        val selected = setOf("test.app1")
        val existing = SelectionPresets.add(emptyList(),"Work",selected,emptySet(),targets)
        assertThrows(IllegalArgumentException::class.java) { SelectionPresets.add(existing,"work",selected,emptySet(),targets) }
        assertThrows(IllegalArgumentException::class.java) { SelectionPresets.add(existing,"Empty",selected,selected,targets) }
        val full = (1..8).map { SelectionPreset("Set $it", selected) }
        assertThrows(IllegalArgumentException::class.java) { SelectionPresets.add(full,"Extra",selected,emptySet(),targets) }
        assertTrue(SelectionPresets.remove(existing,"Work").isEmpty())
    }
}
