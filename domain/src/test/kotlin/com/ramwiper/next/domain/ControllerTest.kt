package com.ramwiper.next.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ControllerTest {
    private val healthy = Snapshot(8L * 1024 * MIB, 4L * 1024 * MIB, 128 * MIB, false)
    private class Source(var sample: Snapshot) : MemorySource {
        var reads = 0
        override fun read(): Snapshot { reads++; return sample }
    }
    private class Shell(var online: Boolean = true, var code: Int = 0) : BackgroundControl {
        val calls = mutableListOf<String>()
        override fun ready() = online
        override fun kill(pkg: String, userId: Int): Int { calls += pkg; return code }
    }
    private class Session(var successful: Boolean = true) : AllocationSession {
        var blocks = 0
        var closed = false
        override fun add(bytes: Int): Boolean { if (!successful) return false; blocks++; return true }
        override fun close() { closed = true }
    }
    @Test fun skippedIsNotSuccess() = runBlocking {
        val source = Source(healthy.copy(low = true))
        val r = MemoryController(source, Shell(), 0, { error("must not open") }, {}).run(Engine.NATIVE, Profile.SOFT, emptySet(), AtomicBoolean(false)) {}
        assertEquals(Outcome.SKIPPED, r.outcome); assertEquals(0L,r.allocated); assertNull(r.settled)
    }
    @Test fun unavailableShizukuSkipsWithoutFallbackInExplicitMode() = runBlocking {
        val shell = Shell(false)
        val r = MemoryController(Source(healthy),shell,0,{ error("unexpected native") },{}).run(Engine.SHIZUKU, Profile.SOFT,setOf("test.app"),AtomicBoolean(false)) {}
        assertEquals(Outcome.SKIPPED,r.outcome); assertTrue(shell.calls.isEmpty())
    }
    @Test fun rejectedShellCommandIsFailure() = runBlocking {
        val r = MemoryController(Source(healthy),Shell(code = 1),0,{ error("unexpected native") },{}).run(Engine.SHIZUKU,Profile.SOFT,setOf("test.app"),AtomicBoolean(false)) {}
        assertEquals(Outcome.FAILED,r.outcome); assertEquals(1,r.attempted); assertEquals(0,r.accepted)
    }
    @Test fun cancelClosesSessionAndDoesNotSettle() = runBlocking {
        val stop = AtomicBoolean(false)
        val session = Session()
        val r = MemoryController(Source(healthy),Shell(),0,{ session },{}).run(Engine.NATIVE,Profile.SOFT,emptySet(),stop) { if (it.allocated > 0) stop.set(true) }
        assertTrue(session.closed); assertEquals(1,session.blocks); assertEquals(Outcome.CANCELLED,r.outcome); assertNull(r.settled)
    }
    @Test fun allocationFailureClosesSession() = runBlocking {
        val session = Session(false)
        val r = MemoryController(Source(healthy),Shell(),0,{ session },{}).run(Engine.NATIVE,Profile.SOFT,emptySet(),AtomicBoolean(false)) {}
        assertTrue(session.closed); assertEquals(Outcome.FAILED,r.outcome)
    }
    @Test fun pressureStopsHoldingAndReleasesMemory() = runBlocking {
        val source = Source(healthy)
        val session = Session()
        val r = MemoryController(source,Shell(),0,{ session },{ source.sample = healthy.copy(low = true) }).run(Engine.NATIVE,Profile.SOFT,emptySet(),AtomicBoolean(false)) {}
        assertTrue(session.closed); assertEquals(1,session.blocks); assertEquals(Outcome.PARTIAL,r.outcome)
    }
    @Test fun hybridUsesNativeWhenShellUnavailableAndMeasuresSettledValue() = runBlocking {
        val source = Source(healthy)
        val session = Session()
        var elapsed = 0L
        val r = MemoryController(source,Shell(false),0,{ session },{ elapsed += it },{ elapsed }).run(Engine.HYBRID,Profile.SOFT,setOf("test.app"),AtomicBoolean(false)) {}
        assertTrue(session.closed); assertEquals(Outcome.COMPLETED,r.outcome); assertEquals(128 * MIB,r.allocated)
        assertEquals(healthy.available,requireNotNull(r.settled)); assertTrue((r.durationMs ?: 0) >= 3000)
    }
    @Test fun successfulShellWithLargeGainDoesNotOpenRust() = runBlocking {
        val source = Source(healthy)
        val shell = Shell()
        val r = MemoryController(source,shell,0,{ error("unnecessary Rust") },{ source.sample = healthy.copy(available = healthy.available + 256 * MIB) }).run(Engine.HYBRID,Profile.SOFT,setOf("test.app"),AtomicBoolean(false)) {}
        assertEquals(Outcome.COMPLETED,r.outcome); assertEquals(0L,r.allocated); assertEquals(1,r.accepted); assertEquals(256*MIB, requireNotNull(r.settledDelta))
    }
    @Test fun protectionBeforeFirstAllocationIsSkipped() = runBlocking {
        val source = Source(healthy)
        val session = Session()
        val r = MemoryController(source,Shell(),0,{ source.sample = healthy.copy(low = true); session },{}).run(Engine.NATIVE,Profile.SOFT,emptySet(),AtomicBoolean(false)) {}
        assertTrue(session.closed); assertEquals(0,session.blocks); assertEquals(Outcome.SKIPPED,r.outcome)
    }
    @Test fun customLimitsReachAllocatorAndHold() = runBlocking {
        val session = Session()
        var elapsed = 0L
        val r = MemoryController(Source(healthy), Shell(), 0, { session }, { elapsed += it }, { elapsed })
            .run(Engine.NATIVE, Profile.STRONG, emptySet(), AtomicBoolean(false), NativeLimits(64, 0)) {}
        assertEquals(64 * MIB, r.allocated)
        assertEquals(4, session.blocks)
        assertEquals(3440L, elapsed)
        assertTrue(session.closed)
    }
    @Test fun adaptiveModeSkipsHighAvailableRamWithoutOpeningSession() = runBlocking {
        val r = MemoryController(Source(healthy),Shell(),0,{ error("unnecessary native") },{})
            .run(Engine.NATIVE,Profile.STRONG,emptySet(),AtomicBoolean(false),NativeLimits(adaptive=true)) {}
        assertEquals(Outcome.SKIPPED,r.outcome); assertEquals(0L,requireNotNull(r.plannedNativeBytes))
        assertNull(r.effectiveNativeProfile)
    }
    @Test fun adaptiveModeUsesActualLowerProfile() = runBlocking {
        val session = Session()
        val r = MemoryController(Source(healthy.copy(available=2L*1024*MIB)),Shell(),0,{session},{})
            .run(Engine.NATIVE,Profile.STRONG,emptySet(),AtomicBoolean(false),NativeLimits(adaptive=true)) {}
        assertEquals("STRONG",r.profile); assertEquals("SOFT",r.effectiveNativeProfile)
        assertEquals(128*MIB,r.allocated); assertTrue(session.closed)
    }
    @Test fun hybridReplansAfterShellStage() = runBlocking {
        val source = Source(healthy.copy(available=3200*MIB))
        val shell = Shell()
        val r = MemoryController(source,shell,0,{error("native should be skipped")},{
            source.sample = source.sample.copy(available=3300*MIB)
        }).run(Engine.HYBRID,Profile.STRONG,setOf("test.app"),AtomicBoolean(false),NativeLimits(adaptive=true)) {}
        assertEquals(1,r.accepted)
        assertEquals(0L,r.allocated)
        assertNull(r.effectiveNativeProfile)
        assertEquals(0L,requireNotNull(r.plannedNativeBytes))
    }
    @Test fun extendedCheckRunsAfterReleaseAndPreservesThreeSecondResult() = runBlocking {
        val source = Source(healthy)
        val session = Session()
        var elapsed = 0L
        val r = MemoryController(source,Shell(),0,{session},{ elapsed += it; if(elapsed >= 10000) source.sample = healthy.copy(available=healthy.available+64*MIB) },{elapsed})
            .run(Engine.NATIVE,Profile.SOFT,emptySet(),AtomicBoolean(false),NativeLimits(64,0,extendedCheck=true)) {}
        assertTrue(session.closed); assertEquals(healthy.available,requireNotNull(r.settled))
        assertEquals(64*MIB,requireNotNull(r.extendedDelta)); assertEquals(10440L,elapsed)
    }
    @Test fun stoppingOptionalCheckDoesNotEraseCompletedCleanup() = runBlocking {
        val stop=AtomicBoolean(false)
        val session=Session()
        val r=MemoryController(Source(healthy),Shell(),0,{session},{})
            .run(Engine.NATIVE,Profile.SOFT,emptySet(),stop,NativeLimits(64,0,extendedCheck=true)) {
                if(it.stage.startsWith("Дополнительный")) { assertTrue(session.closed); stop.set(true) }
            }
        assertEquals(Outcome.COMPLETED,r.outcome); assertFalse(r.cancelled); assertNull(r.extendedAvailable)
        assertNotNull(r.settled)
    }
    @Test fun invalidReadingsAreRejected() {
        assertFalse(Policy.safe(healthy.copy(total = 0))); assertFalse(Policy.safe(healthy.copy(available = healthy.total + 1)))
        assertFalse(Policy.safe(healthy.copy(psiSome = Double.NaN))); assertFalse(Policy.safe(healthy.copy(psiSome = -1.0)))
    }
}
