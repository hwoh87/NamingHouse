package com.naminghouse.app

import org.junit.Assert.*
import org.junit.Test

class BillingConnectionQueueTest {
    @Test fun backgroundOnlyFailureDropsCallbacksAndStaysSilent() {
        val queue = BillingConnectionQueue()
        var calls = 0
        queue.add(false) { calls++ }
        queue.add(false) { calls++ }
        assertFalse(queue.fail())
        queue.succeed()
        assertEquals(0, calls)
    }

    @Test fun userRequestJoiningAnAutomaticConnectionStillGetsFailureFeedback() {
        val queue = BillingConnectionQueue()
        val called = mutableListOf<String>()
        queue.add(false) { called += "automatic refresh" }
        queue.add(true) { called += "restore" }
        assertTrue(queue.fail())
        assertTrue(called.isEmpty())

        // A later background retry must neither replay failed work nor inherit its alert flag.
        queue.add(false) { called += "next refresh" }
        assertFalse(queue.fail())
        queue.succeed()
        assertTrue(called.isEmpty())
    }

    @Test fun successfulConnectionRunsEveryWaitingRequestOnceInOrder() {
        val queue = BillingConnectionQueue()
        val called = mutableListOf<String>()
        queue.add(false) { called += "automatic refresh" }
        queue.add(true) { called += "purchase" }
        queue.add(true) { called += "restore" }
        queue.succeed()
        queue.succeed()
        assertEquals(listOf("automatic refresh", "purchase", "restore"), called)
        assertFalse(queue.fail())
    }

    @Test fun workEnqueuedFromASuccessCallbackBelongsToTheNextAttempt() {
        val queue = BillingConnectionQueue()
        val called = mutableListOf<String>()
        queue.add(false) {
            called += "first"
            queue.add(true) { called += "later" }
        }
        queue.succeed()
        assertEquals(listOf("first"), called)
        queue.succeed()
        assertEquals(listOf("first", "later"), called)
        assertFalse(queue.fail())
    }
}
