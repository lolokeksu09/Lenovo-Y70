package com.ramwiper.next.domain

import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

interface MemorySource { fun read(): Snapshot }
interface BackgroundControl {
    fun ready(): Boolean
    fun kill(pkg: String, userId: Int): Int
}
interface AllocationSession : AutoCloseable { fun add(bytes: Int): Boolean }
interface CleanupRunner {
    suspend fun run(engine: Engine, profile: Profile, packages: Set<String>, stop: AtomicBoolean,
                    limits: NativeLimits = NativeLimits(), progress: (Progress) -> Unit): CleanupResult
}

data class AppTarget(val name: String, val pkg: String)
data class UserSettings(val engine: Engine = Engine.HYBRID, val profile: Profile = Profile.BALANCED,
                        val protected: Set<String> = emptySet(),
                        val automation: AutomationSettings = AutomationSettings(),
                        val nativeLimits: NativeLimits = NativeLimits(),
                        val compareSelected: Boolean = false)
interface AppRepository { fun read(): List<AppTarget> }
interface SettingsRepository {
    fun load(): UserSettings
    fun save(settings: UserSettings)
}
interface HistoryRepository {
    fun load(): List<CleanupResult>
    fun save(history: List<CleanupResult>)
}
enum class AccessState { UNAVAILABLE, NEED_PERMISSION, CONNECTING, CONNECTED, DISCONNECTED, DENIED, ERROR }
data class AccessStatus(val state: AccessState = AccessState.UNAVAILABLE, val detail: String? = null)
interface PrivilegedAccess : BackgroundControl, AutoCloseable {
    val status: StateFlow<AccessStatus>
    fun request()
    fun memoryInfo(pkg: String): String
}
