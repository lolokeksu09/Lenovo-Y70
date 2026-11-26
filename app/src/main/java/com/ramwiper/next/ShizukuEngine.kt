package com.ramwiper.next

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

class ShizukuEngine(context: Context) : AutoCloseable {
    val status = MutableStateFlow("Shizuku недоступен")
    @Volatile private var service: IMemoryShell? = null
    private var bound = false
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, MemoryShellService::class.java.name))
        .daemon(false).processNameSuffix("memory_shell").version(200).debuggable(false)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IMemoryShell.Stub.asInterface(binder); status.value = "Shizuku подключён"
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; bound = false; status.value = "Shizuku отключён" }
    }
    private val received = Shizuku.OnBinderReceivedListener { connectIfAllowed() }
    private val dead = Shizuku.OnBinderDeadListener { service = null; bound = false; status.value = "Shizuku отключён" }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == 200) { if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed() else status.value = "Доступ отклонён" }
    }
    init {
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        Shizuku.addBinderReceivedListenerSticky(received)
    }
    fun ready(): Boolean = service?.asBinder()?.isBinderAlive == true
    fun request() {
        runCatching {
            if (!Shizuku.pingBinder()) { status.value = "Запусти Shizuku"; return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) connectIfAllowed()
            else Shizuku.requestPermission(200)
        }.onFailure { status.value = "Ошибка Shizuku: ${it.message}" }
    }
    private fun connectIfAllowed() {
        runCatching {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { status.value = "Требуется разрешение Shizuku"; return }
            if (!bound) { bound = true; status.value = "Подключение…"; Shizuku.bindUserService(args, connection) }
        }.onFailure { bound = false; status.value = "Ошибка подключения: ${it.message}" }
    }
    fun kill(pkg: String, userId: Int): Int = service?.killBackground(pkg, userId) ?: -5
    fun memoryInfo(pkg: String): String = service?.memoryInfo(pkg) ?: "Shizuku не подключён"
    override fun close() {
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null; bound = false
    }
}
