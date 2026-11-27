package com.ramwiper.next.data

import com.ramwiper.next.domain.*

import com.ramwiper.next.IMemoryShell
import com.ramwiper.next.MemoryShellService
import kotlinx.coroutines.flow.asStateFlow
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku

class ShizukuEngine(context: Context) : PrivilegedAccess {
    private val mutableStatus = MutableStateFlow(AccessStatus())
    override val status = mutableStatus.asStateFlow()
    @Volatile private var closed = false
    private fun update(state: AccessState, detail: String? = null) { if (!closed) mutableStatus.value = AccessStatus(state, detail) }
    @Volatile private var service: IMemoryShell? = null
    @Volatile private var bound = false
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, MemoryShellService::class.java.name))
        .daemon(false).processNameSuffix("memory_shell").version(202).debuggable(false)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            service = IMemoryShell.Stub.asInterface(binder); update(AccessState.CONNECTED)
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; bound = false; update(AccessState.DISCONNECTED) }
    }
    private val received = Shizuku.OnBinderReceivedListener { connectIfAllowed() }
    private val dead = Shizuku.OnBinderDeadListener { service = null; bound = false; update(AccessState.DISCONNECTED) }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == 200) { if (result == PackageManager.PERMISSION_GRANTED) connectIfAllowed() else update(AccessState.DENIED) }
    }
    init {
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        Shizuku.addBinderReceivedListenerSticky(received)
    }
    override fun ready(): Boolean = !closed && service?.asBinder()?.isBinderAlive == true
    override fun request() {
        if (closed) return
        runCatching {
            if (!Shizuku.pingBinder()) { update(AccessState.UNAVAILABLE); return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) connectIfAllowed()
            else Shizuku.requestPermission(200)
        }.onFailure { update(AccessState.ERROR, it.message) }
    }
    private fun connectIfAllowed() {
        if (closed) return
        runCatching {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { update(AccessState.NEED_PERMISSION); return }
            if (!bound) { bound = true; update(AccessState.CONNECTING); Shizuku.bindUserService(args, connection) }
        }.onFailure { bound = false; update(AccessState.ERROR, it.message) }
    }
    override fun kill(pkg: String, userId: Int): Int = service?.killBackground(pkg, userId) ?: -5
    override fun memoryInfo(pkg: String): String = service?.memoryInfo(pkg) ?: "Shizuku не подключён"
    override fun close() {
        if (closed) return
        closed = true
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null; bound = false
    }
}
