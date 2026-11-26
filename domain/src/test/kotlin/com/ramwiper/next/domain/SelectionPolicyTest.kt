package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class SelectionPolicyTest {
    private val apps = (1..15).map { AppTarget("App $it", "test.app$it") }
    @Test fun protectsExcludedAndUninstalledTargets() {
        val selected = setOf("test.app1", "test.app2", "missing.app")
        assertEquals(setOf("test.app1"), SelectionPolicy.eligible(selected, setOf("test.app2"), apps))
        assertTrue(SelectionPolicy.toggle(emptySet(), "test.app2", true, setOf("test.app2"), apps).isEmpty())
    }
    @Test fun limitsAndAllowsRemoval() {
        val full = apps.take(SelectionPolicy.LIMIT).map { it.pkg }.toSet()
        assertEquals(full, SelectionPolicy.toggle(full, "test.app13", true, emptySet(), apps))
        assertEquals(full - "test.app1", SelectionPolicy.toggle(full, "test.app1", false, emptySet(), apps))
        assertEquals(SelectionPolicy.LIMIT, SelectionPolicy.eligible(apps.map { it.pkg }.toSet(), emptySet(), apps).size)
    }
}
