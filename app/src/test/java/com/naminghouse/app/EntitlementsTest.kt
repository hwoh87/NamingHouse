package com.naminghouse.app

import org.junit.Assert.*
import org.junit.Test

class EntitlementsTest {
    @Test fun successfulEmptyQueryRevokesRefundedPurchase() {
        assertEquals(Entitlements(false, false), Entitlements(true, true).reconcile(true, emptySet()))
    }
    @Test fun failedQueryPreservesOfflineCache() {
        assertEquals(Entitlements(true, false), Entitlements(true, false).reconcile(false, emptySet()))
    }
    @Test fun premiumIncludesAdFreeButAdFreeDoesNotGrantPremium() {
        val premium = Entitlements(false, false).reconcile(true, setOf("premium_certificate"))
        assertTrue(premium.premium); assertTrue(premium.adFree)
        val adFree = premium.reconcile(true, setOf("remove_ads"))
        assertFalse(adFree.premium); assertTrue(adFree.adFree)
    }
    @Test fun unrelatedProductsNeverGrantAccess() {
        assertEquals(Entitlements(false, false), Entitlements(true, true).reconcile(true, setOf("other")))
    }
}
