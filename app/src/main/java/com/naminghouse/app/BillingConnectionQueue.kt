package com.naminghouse.app

/** Connection requests may mix background refreshes with a purchase or restore initiated by the user. */
internal class BillingConnectionQueue {
    private data class Request(val reportFailure: Boolean, val onReady: () -> Unit)
    private val pending = mutableListOf<Request>()

    fun add(reportFailure: Boolean, onReady: () -> Unit) {
        pending.add(Request(reportFailure, onReady))
    }

    fun succeed() {
        // Clear before callbacks: a callback can enqueue work for a later connection attempt.
        drain().forEach { it.onReady() }
    }

    /** Drop this failed attempt's callbacks and report only if a user request was waiting. */
    fun fail(): Boolean = drain().any { it.reportFailure }

    private fun drain(): List<Request> = pending.toList().also { pending.clear() }
}
