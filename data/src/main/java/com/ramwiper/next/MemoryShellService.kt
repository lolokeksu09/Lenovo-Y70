package com.ramwiper.next

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import java.io.ByteArrayOutputStream
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
            val capture = FutureTask {
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var truncated = false
                p.inputStream.use { input ->
                    while (true) {
                        val n = input.read(chunk)
                        if (n <= 0) break
                        val take = minOf(n, 48 * 1024 - out.size())
                        if (take > 0) out.write(chunk, 0, take)
                        if (take < n) truncated = true
                    }
                }
                (if (truncated) "Вывод усечён; числовой отчёт недоступен.\n" else "") + out.toString("UTF-8")
            }
            Thread(capture, "ram-meminfo-reader").apply { isDaemon = true; start() }
            try {
                if (!p.waitFor(5, TimeUnit.SECONDS)) "Ошибка диагностики: превышен лимит времени"
                else if (p.exitValue() != 0) "Ошибка диагностики: dumpsys завершился с кодом ${p.exitValue()}"
                else capture.get(500, TimeUnit.MILLISECONDS)
            } finally {
                p.destroyForcibly()
                capture.cancel(true)
            }
        }.getOrElse { "Ошибка диагностики: ${it.message}" }
    }
}
