package com.ramwiper.next

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// Instantiated by Shizuku, not exported as an Android component. No arbitrary shell API.
class MemoryShellService : IMemoryShell.Stub() {
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private fun valid(name: String) = name.length <= 255 && packagePattern.matches(name) &&
        name !in setOf("com.ramwiper.next", "com.ramwiper.app", "moe.shizuku.privileged.api", "android", "com.android.systemui", "com.android.settings")
    override fun destroy() { exitProcess(0) }
    override fun killBackground(packageName: String, userId: Int): Int {
        if (!valid(packageName) || userId !in 0..999) return -2
        return runCatching {
            val p = ProcessBuilder("/system/bin/am", "kill", "--user", userId.toString(), packageName)
                .redirectOutput(File("/dev/null")).redirectError(File("/dev/null")).start()
            try { if (p.waitFor(5, TimeUnit.SECONDS)) p.exitValue() else -3 } finally { p.destroyForcibly() }
        }.getOrDefault(-4)
    }
    override fun memoryInfo(packageName: String): String {
        if (!valid(packageName)) return "Недопустимый пакет"
        return runCatching {
            val p = ProcessBuilder("/system/bin/dumpsys", "-t", "4", "meminfo", packageName).redirectErrorStream(true).start()
            try {
                // Limit returned Binder payload; timeout is imposed by dumpsys itself.
                val bytes = ByteArray(48 * 1024)
                var used = 0
                while (used < bytes.size) {
                    val n = p.inputStream.read(bytes, used, bytes.size - used)
                    if (n <= 0) break
                    used += n
                }
                String(bytes, 0, used, Charsets.UTF_8)
            } finally { p.destroyForcibly() }
        }.getOrElse { "Ошибка диагностики: ${it.message}" }
    }
}
