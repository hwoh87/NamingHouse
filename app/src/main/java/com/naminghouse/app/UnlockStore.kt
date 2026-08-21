package com.naminghouse.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unlockStore by preferencesDataStore(name = "unlocks")

private val UNLOCKED = stringSetPreferencesKey("unlocked")
private val FREE_USED = booleanPreferencesKey("freeUsed")

/** 이름 단위로 열린 감명서 — 무료 1회와 광고 해제가 같은 자루에 들어간다. */
data class UnlockState(
    val keys: Set<String> = emptySet(),
    /** 무료 1회를 이미 썼는가. 어느 이름에 썼는지는 [keys] 가 안다. */
    val freeUsed: Boolean = false,
)

/**
 * 프리미엄이 아닌 사람이 연 감명서를 기억한다.
 *
 * 프리미엄은 [PremiumManager] 가 따로 본다 — 여기 값은 **구매와 무관한 개별 해제**뿐이라
 * 기기에만 남는다(복원 대상이 아니다). 무료 1회는 어느 이름에 쓸지 사용자가 고르게 두는데,
 * 처음 연 이름에 자동으로 태우면 관심 없는 후보에 소진되기 때문이다.
 */
class UnlockStore(private val context: Context) {

    val flow: Flow<UnlockState> = context.unlockStore.data.map { p ->
        UnlockState(keys = p[UNLOCKED] ?: emptySet(), freeUsed = p[FREE_USED] == true)
    }

    /** @param consumeFree 무료 1회를 쓰는 해제면 true — 광고 해제는 false. */
    suspend fun unlock(key: String, consumeFree: Boolean) {
        context.unlockStore.edit { p ->
            p[UNLOCKED] = (p[UNLOCKED] ?: emptySet()) + key
            if (consumeFree) p[FREE_USED] = true
        }
    }
}
