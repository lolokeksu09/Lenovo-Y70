package com.ramwiper.next.domain

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class RunCleanupUseCaseTest {
    private class Runner : CleanupRunner {
        var received: Set<String>? = null
        override suspend fun run(engine: Engine, profile: Profile, packages: Set<String>, stop: AtomicBoolean,
                                 limits: NativeLimits, progress: (Progress) -> Unit): CleanupResult {
            received = packages
            return CleanupResult(1, engine.name, profile.name, 1, 1, 0, 0, 0, false, "", Outcome.SKIPPED)
        }
    }

    @Test fun revalidatesSelectionAtExecution() = runBlocking {
        val runner = Runner()
        val catalog = object : AppRepository {
            override fun read() = listOf(AppTarget("A", "test.app1"), AppTarget("B", "test.app2"))
        }
        val result = RunCleanupUseCase(runner, catalog)(
            CleanupRequest(UserSettings(protected = setOf("test.app2")), setOf("test.app1", "test.app2", "removed.app")),
            AtomicBoolean(false)) {}
        assertEquals(setOf("test.app1"), runner.received)
        assertTrue(result.note.contains("пропущены"))
    }
    @Test fun nativeDoesNotDependOnPackageManager() = runBlocking {
        val runner = Runner()
        val catalog = object : AppRepository { override fun read(): List<AppTarget> = error("unexpected lookup") }
        val result = RunCleanupUseCase(runner, catalog)(
            CleanupRequest(UserSettings(engine = Engine.NATIVE), setOf("test.app")), AtomicBoolean(false)) {}
        assertEquals(emptySet<String>(), runner.received)
        assertEquals("", result.note)
    }
    @Test fun catalogFailureNeverSendsUnverifiedTargets() = runBlocking {
        val runner = Runner()
        val catalog = object : AppRepository { override fun read(): List<AppTarget> = error("unavailable") }
        val result = RunCleanupUseCase(runner, catalog)(
            CleanupRequest(UserSettings(engine = Engine.HYBRID), setOf("test.app")), AtomicBoolean(false)) {}
        assertEquals(emptySet<String>(), runner.received)
        assertTrue(result.note.contains("не выполняются"))
    }
    @Test fun comparisonReadsBeforeAndAfterWithoutSendingNativeKillTargets() = runBlocking {
        val events = mutableListOf<String>()
        val catalog = object : AppRepository { override fun read() = listOf(AppTarget("A", "test.app")) }
        val access = object : PrivilegedAccess {
            override val status = MutableStateFlow(AccessStatus(AccessState.CONNECTED))
            override fun ready() = true
            override fun request() {}
            override fun close() {}
            override fun kill(pkg: String, userId: Int): Int = error("native must not kill")
            override fun memoryInfo(pkg: String): String {
                val value = if ("cleanup" in events) 1024 else 2048
                events += "sample"
                return "** MEMINFO in pid 123 [$pkg] **\nTOTAL PSS: $value"
            }
        }
        val runner = object : CleanupRunner {
            override suspend fun run(engine: Engine, profile: Profile, packages: Set<String>, stop: AtomicBoolean,
                limits: NativeLimits, progress: (Progress) -> Unit): CleanupResult {
                assertTrue(packages.isEmpty())
                events += "cleanup"
                return CleanupResult(1,engine.name,profile.name,10,20,16,0,0,false,"",Outcome.COMPLETED)
            }
        }
        val result = RunCleanupUseCase(runner,catalog,AppMemorySampler(catalog,access))(
            CleanupRequest(UserSettings(engine=Engine.NATIVE,compareSelected=true),setOf("test.app")), AtomicBoolean(false)) {}
        assertEquals(listOf("sample","cleanup","sample"), events)
        assertEquals(-1024L, requireNotNull(result.comparisons.single().pssDeltaKiB))
    }
}
