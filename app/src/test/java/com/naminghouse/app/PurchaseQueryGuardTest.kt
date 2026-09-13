package com.naminghouse.app

import org.junit.Assert.*
import org.junit.Test

class PurchaseQueryGuardTest {
    @Test fun oldQueryCannotRevokeJustCompletedPurchase() {
        val guard = PurchaseQueryGuard()
        val beforePayment = guard.begin()
        guard.purchaseUpdated()
        assertFalse(guard.isCurrent(beforePayment))
        assertTrue(guard.isCurrent(guard.begin()))
    }
    @Test fun latestQueryWinsWhenResponsesArriveOutOfOrder() {
        val guard = PurchaseQueryGuard()
        val first = guard.begin()
        val latest = guard.begin()
        assertTrue(guard.isCurrent(latest))
        assertFalse(guard.isCurrent(first))
    }
}
