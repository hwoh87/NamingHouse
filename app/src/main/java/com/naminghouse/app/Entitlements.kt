package com.naminghouse.app

/** 조회 실패와 성공한 빈 구매 목록은 서로 다른 사건이다. */
internal data class Entitlements(val premium: Boolean, val adFreeProduct: Boolean) {
    val adFree: Boolean get() = premium || adFreeProduct
    fun reconcile(success: Boolean, purchasedProducts: Set<String>): Entitlements =
        if (!success) this else Entitlements(
            PremiumManager.PRODUCT_ID in purchasedProducts,
            PremiumManager.AD_FREE_PRODUCT_ID in purchasedProducts,
        )
}

/** 늦은 전체 조회가 새 구매 이벤트나 더 최근 조회를 덮어쓰지 못하게 한다. */
internal class PurchaseQueryGuard {
    private var revision = 0L
    private var request = 0L
    data class Ticket(val request: Long, val revision: Long)
    fun begin(): Ticket = Ticket(++request, revision)
    fun purchaseUpdated() { revision++ }
    fun isCurrent(ticket: Ticket): Boolean = ticket.request == request && ticket.revision == revision
}
