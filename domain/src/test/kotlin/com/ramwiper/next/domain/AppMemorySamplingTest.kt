package com.ramwiper.next.domain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AppMemorySamplingTest {
    private val apps = object : AppRepository {
        override fun read() = (1..25).map { AppTarget("App $it", "test.app$it") }
    }
    private class Access : PrivilegedAccess {
        override val status = MutableStateFlow(AccessStatus(AccessState.CONNECTED))
        var online = true
        val calls = mutableListOf<String>()
        override fun ready() = online
        override fun request() {}
        override fun close() {}
        override fun kill(pkg: String,userId: Int): Int = error("read-only")
        override fun memoryInfo(pkg: String): String {
            calls += pkg
            return if (pkg == "test.app1") "No process found" else
                "** MEMINFO in pid 123 [$pkg] **\nTOTAL PSS: 2048"
        }
    }
    @Test fun boundedReadOnlyScanKeepsUnknownDistinct() = runBlocking {
        val access = Access()
        val result = AppMemorySampler(apps, access, { 10L }).measure(
            listOf("not.installed") + (1..25).map { "test.app$it" }, AtomicBoolean(false))
        assertEquals(20, result.samples.size)
        assertEquals(20, access.calls.size)
        assertNull(result.samples.first().pssKiB)
        assertEquals("test.app1", AppMemoryRanking.order(result.samples).last().pkg)
        assertFalse(result.cancelled)
    }
    @Test fun stopAndDisconnectDoNotReadMorePackages() = runBlocking {
        val access = Access()
        val stop = AtomicBoolean(false)
        val result = AppMemorySampler(apps, access).measure(listOf("test.app2","test.app3"),stop) { done, _ ->
            if (done == 1) stop.set(true)
        }
        assertEquals(1, access.calls.size); assertTrue(result.cancelled)
        access.online = false
        assertTrue(AppMemorySampler(apps,access).measure(listOf("test.app2"),AtomicBoolean(false)).cancelled)
    }
    @Test fun missingAfterIsNotZero() {
        val before = AppMemorySample("test.app2",2048,null,null,1,1)
        assertNull(AppMemoryComparison(before.pkg,before,null).pssDeltaKiB)
        assertEquals(-1024L, requireNotNull(AppMemoryComparison(before.pkg,before,before.copy(pssKiB=1024)).pssDeltaKiB))
    }
}
