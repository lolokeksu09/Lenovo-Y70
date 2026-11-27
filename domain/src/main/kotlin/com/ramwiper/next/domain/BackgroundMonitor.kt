package com.ramwiper.next.domain

/** Scheduling is a platform adapter. This contract does not promise exact execution time. */
interface BackgroundMonitor { fun sync(enabled: Boolean) }
