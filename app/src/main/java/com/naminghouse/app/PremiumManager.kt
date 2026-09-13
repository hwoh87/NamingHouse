package com.naminghouse.app

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.premiumStore by preferencesDataStore(name = "premium")
private val OWNED = booleanPreferencesKey("owned")
private val AD_FREE = booleanPreferencesKey("ad_free")

/**
 * 인앱 상품 두 개(프리미엄 감명서 · 광고 제거)의 구매·복원·소유 상태.
 *
 * 서버가 없으므로 소유의 원본은 Play 구매 목록이고, DataStore 는 오프라인 캐시다
 * (비행기 모드에서도 산 사람은 계속 열려 있어야 한다). 영수증 검증 서버를 두지
 * 않는 대신 기기 밖 검증도 하지 않는다 — 기념품 성격의 상품이라 감수한다.
 */
class PremiumManager(
    private val app: Application,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    companion object {
        /** Play Console 에 등록한 상품 ID — 한 번 쓰면 재사용이 안 되니 바꾸지 말 것. */
        const val PRODUCT_ID = "premium_certificate"

        /** 광고 제거 단품. 프리미엄을 사면 이것 없이도 광고가 사라진다([isAdFree]). */
        const val AD_FREE_PRODUCT_ID = "remove_ads"

        private val ALL_PRODUCTS = listOf(PRODUCT_ID, AD_FREE_PRODUCT_ID)
    }

    /** 소유 여부 — 캐시로 먼저 채우고 Play 응답으로 갱신한다. */
    val isPremium = MutableStateFlow(false)

    /** 광고 제거 단품 소유 여부. 광고 노출 판정은 이게 아니라 [isAdFree] 로 한다. */
    val hasAdFreeProduct = MutableStateFlow(false)

    /**
     * 광고를 지워야 하는가 — 광고 제거를 샀거나 **프리미엄을 샀으면** 참.
     *
     * 9,900원짜리 상위 상품을 산 사람에게 광고를 계속 보이면 그게 더 큰 불만이다.
     * 상위가 하위를 포함하는 이 규칙이 두 상품을 파는 유일한 근거이기도 하다.
     */
    val isAdFree = MutableStateFlow(false)

    /** Play 가 준 현지 표시 가격("₩9,900"). 콘솔 등록 전이나 오프라인이면 null. */
    val priceText = MutableStateFlow<String?>(null)

    /** 광고 제거 단품의 현지 표시 가격("₩2,500"). */
    val adFreePriceText = MutableStateFlow<String?>(null)

    /** 일회성 안내문 — UI 가 토스트로 소비하고 null 로 되돌린다. */
    val message = MutableStateFlow<String?>(null)

    val entitlementLoaded = MutableStateFlow(false)
    val purchaseBusy = MutableStateFlow(false)
    private var cacheJob: Job? = null
    private val purchaseQueries = PurchaseQueryGuard()
    private val connectionWaiters = mutableListOf<() -> Unit>()

    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    private val client = BillingClient.newBuilder(app)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        cacheJob = scope.launch {
            val cached = app.premiumStore.data.first()
            isPremium.value = cached[OWNED] == true
            hasAdFreeProduct.value = cached[AD_FREE] == true
            syncAdFree()
            entitlementLoaded.value = true
            connect {
                queryProducts()
                refreshEntitlement()
            }
        }
    }

    /** 광고 제거는 단품 소유 또는 프리미엄 소유 — 두 소스가 바뀔 때마다 여기서 한 번에 맞춘다. */
    private fun syncAdFree() {
        isAdFree.value = hasAdFreeProduct.value || isPremium.value
    }

    fun dispose() {
        runCatching { client.endConnection() }
    }

    /** 연결돼 있으면 즉시, 아니면 연결 후 실행. 실패는 조용히 둔다 — 다음 시도가 다시 잇는다. */
    private fun connect(onReady: () -> Unit) {
        when (client.connectionState) {
            BillingClient.ConnectionState.CONNECTED -> {
                onReady()
                return
            }
            BillingClient.ConnectionState.CONNECTING -> {
                connectionWaiters.add(onReady)
                return
            }
            else -> Unit
        }
        connectionWaiters.add(onReady)
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val waiting = connectionWaiters.toList()
                connectionWaiters.clear()
                if (result.responseCode == BillingClient.BillingResponseCode.OK) waiting.forEach { it() }
                else {
                    purchaseBusy.value = false
                    message.value = "Google Play에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** 시작·onResume 때 Play 구매 목록으로 소유를 다시 맞춘다 — 대기 결제 완료도 여기서 잡힌다. */
    fun refreshEntitlement() {
        scope.launch {
            cacheJob?.join()
            connect {
                val ticket = purchaseQueries.begin()
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                ) { result, purchases ->
                    if (!purchaseQueries.isCurrent(ticket)) return@queryPurchasesAsync
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        reconcile(purchases)
                    }
                }
            }
        }
    }

    fun refreshProducts() = connect { queryProducts() }

    private fun queryProducts(after: (() -> Unit)? = null) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ALL_PRODUCTS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()
        client.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseBusy.value = false
                message.value = "상품 정보를 가져오지 못했습니다. 네트워크를 확인해 주세요."
                return@queryProductDetailsAsync
            }
            productDetailsById.clear()
            detailsResult.productDetailsList.forEach { productDetailsById[it.productId] = it }
            priceText.value = formattedPrice(PRODUCT_ID)
            adFreePriceText.value = formattedPrice(AD_FREE_PRODUCT_ID)
            after?.invoke()
        }
    }

    private fun formattedPrice(productId: String): String? =
        productDetailsById[productId]?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice

    /** 프리미엄 감명서 구매 */
    fun launchPurchase(activity: Activity) = launchPurchase(activity, PRODUCT_ID)

    /** 광고 제거 단품 구매 */
    fun launchAdFreePurchase(activity: Activity) = launchPurchase(activity, AD_FREE_PRODUCT_ID)

    private fun launchPurchase(activity: Activity, productId: String) {
        if (purchaseBusy.value) return
        if (isPremium.value || (productId == AD_FREE_PRODUCT_ID && isAdFree.value)) {
            message.value = "이미 이용 중인 상품입니다"
            return
        }
        purchaseBusy.value = true
        com.naminghouse.app.ads.AdFrequency(app).suppressFullScreen()
        connect {
            queryProducts {
                val details = productDetailsById[productId]
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                if (details == null || offer == null || activity.isFinishing || activity.isDestroyed) {
                    purchaseBusy.value = false
                    message.value = "현재 구매 가능한 상품이 없습니다. 잠시 후 다시 시도해 주세요."
                    return@queryProducts
                }
                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .apply { offer.offerToken?.let { setOfferToken(it) } }
                    .build()
                val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams)).build())
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    purchaseBusy.value = false
                    message.value = "결제 화면을 열지 못했습니다 — Google Play 상태를 확인해 주세요"
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        purchaseBusy.value = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchaseQueries.purchaseUpdated()
                purchases?.forEach(::handlePurchase)
                refreshEntitlement()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 어느 상품인지는 응답에 없다 — 구매 목록을 다시 읽어 맞춘다.
                message.value = "이미 구매한 상품입니다 — 구매 내역을 확인하고 있습니다"
                refreshEntitlement()
            }
            else -> message.value = "결제가 완료되지 않았습니다"
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val owned = ALL_PRODUCTS.filter { it in purchase.products }
        if (owned.isEmpty()) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    // 3일 내 확인하지 않으면 자동 환불된다. 실패해도 다음 refresh 가 재시도한다.
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    ) { }
                }
                owned.forEach(::own)
            }
            Purchase.PurchaseState.PENDING ->
                message.value = "결제가 진행 중입니다 — 완료되면 자동으로 열립니다"
            else -> Unit
        }
    }

    /** 성공한 전체 조회만 캐시를 대체한다. 실패/오프라인은 기존 권한을 유지한다. */
    private fun reconcile(purchases: List<Purchase>) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }.toSet()
        val next = Entitlements(isPremium.value, hasAdFreeProduct.value).reconcile(true, active)
        isPremium.value = next.premium
        hasAdFreeProduct.value = next.adFreeProduct
        syncAdFree()
        scope.launch {
            app.premiumStore.edit {
                it[OWNED] = PRODUCT_ID in active
                it[AD_FREE] = AD_FREE_PRODUCT_ID in active
            }
        }
        purchases.forEach(::handlePurchase)
    }

    /** 이미 알고 있던 소유면 조용히 넘어간다 — 실행할 때마다 토스트가 뜨면 안 된다. */
    private fun own(productId: String) {
        when (productId) {
            PRODUCT_ID -> {
                val silent = isPremium.value
                isPremium.value = true
                scope.launch { app.premiumStore.edit { it[OWNED] = true } }
                if (!silent) message.value = "프리미엄 감명서가 열렸습니다 — 고맙습니다"
            }
            AD_FREE_PRODUCT_ID -> {
                val silent = hasAdFreeProduct.value
                hasAdFreeProduct.value = true
                scope.launch { app.premiumStore.edit { it[AD_FREE] = true } }
                if (!silent) message.value = "광고를 껐습니다 — 고맙습니다"
            }
        }
        syncAdFree()
    }

    /** 설정의 '구매 복원' — 결과를 반드시 한 줄로 알린다(스토어 심사 요구이기도 하다). */
    fun restore() {
        scope.launch {
            cacheJob?.join()
            connect { doRestore() }
        }
    }

    private fun doRestore() {
        val ticket = purchaseQueries.begin()
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (!purchaseQueries.isCurrent(ticket)) {
                message.value = "구매 내역을 새로 확인 중입니다. 잠시 후 복원을 다시 눌러 주세요."
                return@queryPurchasesAsync
            }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                message.value = "구매 내역을 확인하지 못했습니다 — 잠시 후 다시 시도해 주세요"
                return@queryPurchasesAsync
            }
            reconcile(purchases)
            val owned = purchases.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    ALL_PRODUCTS.any { it in purchase.products }
            }
            if (owned) {
                message.value = "구매를 복원했습니다"
            } else {
                message.value = "복원할 구매 내역이 없습니다"
            }
        }
    }
}
