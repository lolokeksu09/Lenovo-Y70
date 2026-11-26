package com.ramwiper.next

import com.ramwiper.next.domain.AllocationSession

/** A single Rust-owned session; stale tokens cannot release a newer operation. */
object NativeWiper {
    init { System.loadLibrary("ramwiper_next") }
    private external fun begin(): Long
    private external fun append(session: Long, bytes: Int): Boolean
    private external fun finish(session: Long): Boolean

    class Session : AllocationSession {
        private var token = begin().also { check(it != 0L) { "Другая нативная операция активна" } }
        override fun add(bytes: Int): Boolean = token != 0L && append(token, bytes)
        override fun close() {
            if (token == 0L) return
            check(finish(token)) { "Не удалось освободить нативную сессию" }
            token = 0
        }
    }
}
