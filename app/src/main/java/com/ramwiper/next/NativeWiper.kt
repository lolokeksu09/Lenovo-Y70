package com.ramwiper.next

/** Opaque Rust handles, never native addresses. One controller owns one session. */
object NativeWiper {
    init { System.loadLibrary("ramwiper_next") }
    private external fun allocate(bytes: Int): Long
    private external fun release(handle: Long, bytes: Int): Boolean
    private external fun releaseAll()
    class Session : AutoCloseable {
        private val handles = ArrayList<Pair<Long, Int>>()
        fun add(bytes: Int): Boolean {
            val handle = allocate(bytes)
            if (handle == 0L) return false
            handles.add(handle to bytes)
            return true
        }
        override fun close() {
            try { for ((handle, size) in handles.asReversed()) release(handle, size) }
            finally { handles.clear(); releaseAll() }
        }
    }
}
