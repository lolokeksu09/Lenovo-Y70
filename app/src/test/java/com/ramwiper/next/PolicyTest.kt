package com.ramwiper.next
import org.junit.Test
import org.junit.Assert.*
class PolicyTest {
    private val normal = Snapshot(16L*1024*MIB, 6L*1024*MIB, 256*MIB, false)
    @Test fun cap() { assertEquals(512*MIB, Policy.budget(normal, Profile.BALANCED)); assertEquals(128*MIB, Policy.budget(normal, Profile.SOFT)) }
    @Test fun safety() { for (s in listOf(normal.copy(low=true),normal.copy(thermal=3),normal.copy(psiSome=10.0),normal.copy(available=512*MIB))) { assertEquals(0L,Policy.budget(s,Profile.STRONG)); assertEquals(0,Policy.next(s,64*MIB)) } }
    @Test fun noExcess() { assertEquals(0,Policy.next(normal, MIB)); assertFalse(Policy.shouldRunNative(normal,normal.copy(available=normal.available+128*MIB))) }
}
