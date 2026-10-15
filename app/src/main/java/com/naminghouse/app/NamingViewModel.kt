package com.naminghouse.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.NameEvaluator
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.gen.GeneratorOptions
import com.naminghouse.engine.gen.NameCandidate
import com.naminghouse.engine.gen.NameGenerator
import com.naminghouse.engine.gen.NamePool
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.gen.NameStats
import com.naminghouse.engine.gen.NameStatsMetadata
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.saju.SajuNamingService
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.City
import com.samramanshang.manseryeok.orrery.model.Gender
import com.samramanshang.manseryeok.orrery.repository.CityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

enum class AppMode(val label: String) {
    RECOMMEND("이름 추천"),
    HANJA("한자 추천"),
    EVALUATE("이름 감명"),
}

class NamingViewModel(app: Application) : AndroidViewModel(app) {

    // ── 데이터 로딩
    var hanjaDb by mutableStateOf<HanjaDb?>(null)
        private set
    var namePool by mutableStateOf<NamePool?>(null)
        private set
    var nameStats by mutableStateOf(NameStats.EMPTY)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var statsLoaded by mutableStateOf(false)
        private set
    var statsError by mutableStateOf<String?>(null)
        private set
    private var hanjaDataVersion = "unknown"

    // ── 화면 상태
    var mode by mutableStateOf(AppMode.RECOMMEND)
    var busy by mutableStateOf(false)
        private set

    /** 계산이 끝나 이동해야 할 목적지. 입력 화면이 소비하고 비운다. */
    var pendingRoute by mutableStateOf<String?>(null)

    /** 상세 화면이 보고 있는 이름 — 라우트로 직렬화하기엔 무거워 여기 든다. */
    var selected by mutableStateOf<Pair<NameEvaluation, NameStat?>?>(null)

    var selectedSaju by mutableStateOf<SajuSummary?>(null)
        private set
    var selectedBirthLine by mutableStateOf<String?>(null)
        private set
    var selectedEvaluationRecord by mutableStateOf<EvaluationRecord?>(null)
        private set
    private var resultEvaluationRecord: EvaluationRecord? = null
    private var resultBirthLine: String? = null
    private var calculationJob: Job? = null
    private var calculationVersion = 0L

    var comparison by mutableStateOf<FavoriteComparison?>(null)
        private set
    var comparisonBusy by mutableStateOf(false)
        private set
    var comparisonError by mutableStateOf<String?>(null)
        private set
    private var comparisonJob: Job? = null
    private var comparisonVersion = 0L

    /** 저장된 입력이 있는가 — 홈의 '이어서 이름 짓기' 노출 기준. */
    var hasSavedInput by mutableStateOf(false)
        private set
    var inputsLoaded by mutableStateOf(false)
        private set
    var lastSavedInput by mutableStateOf<SavedInput?>(null)
        private set
    var inputRestoreNotice by mutableStateOf<String?>(null)
        private set
    private val recentNamesStore = RecentNamesStore(app)
    var recentNames by mutableStateOf<List<String>>(emptyList())
        private set
    var recentNamesLoaded by mutableStateOf(false)
        private set
    var explorationError by mutableStateOf<String?>(null)
        private set

    // ── 설정
    private val settingsStore = SettingsStore(app)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    // ── 프리미엄 (감명서 PDF·낙관 각인·표구)
    val premium = PremiumManager(app, viewModelScope)
    var isPremium by mutableStateOf(false)
        private set

    /** 광고를 지워야 하는가 — 광고 제거 단품 또는 프리미엄 소유. 배너가 이걸 본다. */
    var isAdFree by mutableStateOf(false)
        private set
    var jokjaStyle by mutableStateOf(JokjaStyle.BAEKJI)
        private set

    // ── 감명서 해제 (프리미엄이 아닌 사람의 개별 공개)
    private val unlockStore = UnlockStore(app)

    /** 개별로 열어 둔 이름들 — 무료 1회와 광고 해제가 같이 들어 있다. */
    var unlockedNames by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 무료 1회를 이미 썼는가 — 안 썼으면 잠긴 카드에 '무료로 열기'가 뜬다. */
    var freeUnlockUsed by mutableStateOf(false)
        private set

    /**
     * 감명서 해제의 신원 — 이름 + 한자.
     *
     * 즐겨찾기와 같은 기준이다(점수는 사주에 따라 달라져 신원이 될 수 없다).
     */
    fun nameKey(eval: NameEvaluation): String {
        val hanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() }
        return "${eval.surname}${eval.givenName}|$hanja"
    }

    /** 이 이름의 감명서를 전부 보여 줘도 되는가. */
    fun isUnlocked(eval: NameEvaluation): Boolean =
        isPremium || nameKey(eval) in unlockedNames

    /** 무료 1회를 이 이름에 쓴다. 이미 썼으면 아무 일도 하지 않는다. */
    fun claimFreeUnlock(eval: NameEvaluation) {
        if (freeUnlockUsed || isUnlocked(eval)) return
        viewModelScope.launch { unlockStore.unlock(nameKey(eval), consumeFree = true) }
    }

    /** 보상형 광고를 끝까지 본 대가로 이 이름을 연다. */
    fun grantAdUnlock(eval: NameEvaluation) {
        viewModelScope.launch { unlockStore.unlock(nameKey(eval), consumeFree = false) }
    }

    fun onJokjaStyleChanged(style: JokjaStyle) {
        jokjaStyle = style
        viewModelScope.launch { settingsStore.saveJokjaStyle(style) }
    }

    fun onThemeModeChanged(m: ThemeMode) {
        themeMode = m
        viewModelScope.launch { settingsStore.saveThemeMode(m) }
    }

    fun onSchoolChanged(s: BaleumSchool) {
        school = s
        viewModelScope.launch { settingsStore.saveSchool(s) }
    }

    // ── 공통 입력: 성씨
    var surname by mutableStateOf("김")
    var surnameHanja = mutableStateOf<List<HanjaEntry?>>(listOf(null))

    // ── 추천 모드 입력
    var gender by mutableStateOf(Gender.M)
    var preBirth by mutableStateOf(false) // 출생 전 작명: 사주 없이
    var year by mutableStateOf("2026")
    var month by mutableStateOf("8")
    var day by mutableStateOf("11")
    var hour by mutableStateOf("12")
    var minute by mutableStateOf("0")
    var isLunar by mutableStateOf(false)
    var isLeapMonth by mutableStateOf(false)
    var unknownTime by mutableStateOf(false)
    /** 출생 지역 — 경도로 진태양시를 보정한다(국내 도시만, 해외 출생은 시간대 정보가 없어 미지원). */
    var city by mutableStateOf<City>(CityRepository.SEOUL)
    var school by mutableStateOf(BaleumSchool.UNHAE)
    var excludedSyllables by mutableStateOf("")
    var diverseHanja by mutableStateOf(true)
    var popularOnly by mutableStateOf(false) // tier 1 이름만
    var singleName by mutableStateOf(false) // 외자(한 글자) 이름만 추천
    var dolimja by mutableStateOf("") // 돌림자 (빈 문자열 = 사용 안 함)
    var dolimjaLast by mutableStateOf(false) // 돌림자 위치: false=첫 글자, true=끝 글자

    fun onDolimjaChanged(value: String) {
        dolimja = acceptHangul(value, maxSyllables = 1)
    }

    // ── 감명 모드 입력
    var givenName by mutableStateOf("")
    var givenHanja = mutableStateOf<List<HanjaEntry?>>(emptyList())

    // ── 결과
    var resultMode by mutableStateOf(AppMode.RECOMMEND)
        private set
    var saju by mutableStateOf<SajuSummary?>(null)
        private set
    var candidates by mutableStateOf<List<NameCandidate>>(emptyList())
        private set
    var hanjaCombos by mutableStateOf<List<NameEvaluation>>(emptyList())
        private set
    var evaluation by mutableStateOf<NameEvaluation?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    // ── 입력 영속화
    private val inputStore = InputStore(app)
    /** 저장돼 있던 성씨 한자 — DB 로딩이 끝나야 실제 항목으로 되살릴 수 있다 */
    private var savedSurnameHanja: String? = null
    private var savedGivenHanja: String? = null

    // ── 즐겨찾기
    private val favoritesStore = FavoritesStore(app)
    var favorites by mutableStateOf<List<FavoriteName>>(emptyList())
        private set
    var favoritesLoaded by mutableStateOf(false)
        private set
    var favoriteError by mutableStateOf<String?>(null)
        private set
    var recentlyRemoved by mutableStateOf<FavoriteName?>(null)
        private set

    /** 이 이름이 담겨 있는가 — 이름+한자로 판별(점수는 사주에 따라 달라진다) */
    fun isFavorite(eval: NameEvaluation): Boolean {
        val hanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() }
        return favorites.any {
            it.surname == eval.surname && it.givenName == eval.givenName && it.hanja == hanja
        }
    }

    private fun changeFavorites(action: suspend () -> Unit) {
        favoriteError = null
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                favoriteError = "이름을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요"
            }
        }
    }

    fun toggleFavorite(eval: NameEvaluation) {
        val item = FavoriteName(
            surname = eval.surname,
            givenName = eval.givenName,
            hanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() },
            score = eval.score,
            grade = eval.grade,
            meaning = meaningLine(eval),
            evaluationRecord = when {
                selected?.first === eval -> selectedEvaluationRecord
                candidates.any { it.evaluation === eval } || hanjaCombos.any { it === eval } -> resultEvaluationRecord
                else -> null
            },
        )
        changeFavorites { favoritesStore.toggle(item) }
    }

    fun updateFavoriteNote(item: FavoriteName, note: String) {
        changeFavorites { favoritesStore.updateNote(item, note.take(160)) }
    }

    suspend fun importFavorites(items: List<FavoriteName>): FavoriteImportResult = favoritesStore.importNames(items)

    fun setFavoriteFinalist(item: FavoriteName, value: Boolean) {
        changeFavorites { favoritesStore.setFinalist(item, value) }
    }

    fun removeFavorite(item: FavoriteName) {
        changeFavorites { favoritesStore.remove(item)?.let { recentlyRemoved = it } }
    }

    fun restoreFavorite(item: FavoriteName) {
        changeFavorites {
            favoritesStore.restore(item)
            dismissRemovedFavorite(item)
        }
    }

    fun dismissRemovedFavorite(item: FavoriteName) {
        if (recentlyRemoved == item) recentlyRemoved = null
    }

    /** The result list keeps its own context when a saved name is opened elsewhere. */
    fun selectResult(eval: NameEvaluation, stat: NameStat?) {
        cancelCalculation()
        selected = eval to stat
        selectedSaju = saju
        selectedBirthLine = resultBirthLine
        selectedEvaluationRecord = resultEvaluationRecord
    }

    fun openFavorite(item: FavoriteName): Boolean {
        cancelCalculation()
        val db = hanjaDb ?: run {
            errorMessage = loadError ?: "한자 자료를 불러오는 중입니다. 잠시 후 다시 시도해 주세요"
            return false
        }
        val condition = captureConditionOrError() ?: return false
        launchCalculation(calculate = {
            val analyzed = condition.birth?.let(SajuNamingService::analyze)
            analyzed to evaluateFavorite(item, db, analyzed, condition.school)
        }) { (analyzed, eval) ->
            selected = eval to nameStats[item.givenName]
            selectedSaju = analyzed
            selectedBirthLine = condition.birthLine
            selectedEvaluationRecord = condition.record
            pendingRoute = Routes.DETAIL
        }
        return true
    }

    fun compareFavorites(items: List<FavoriteName>) {
        cancelCalculation()
        clearComparison()
        errorMessage = null
        val db = hanjaDb ?: run {
            comparisonError = loadError ?: "한자 자료를 불러오는 중입니다. 잠시 후 다시 시도해 주세요"
            return
        }
        val snapshot = items.toList()
        val condition = try {
            captureCondition()
        } catch (error: IllegalArgumentException) {
            comparisonError = error.message
            return
        }
        val version = ++comparisonVersion
        comparisonBusy = true
        comparisonJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    compareFavoriteNames(snapshot, db, condition.birth, condition.school,
                        condition.label, condition.birthLine)
                }
                if (version == comparisonVersion) comparison = result.copy(evaluationRecord = condition.record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == comparisonVersion) {
                    comparisonError = error.message ?: "비교 결과를 준비하지 못했습니다. 다시 시도해 주세요"
                }
            } finally {
                if (version == comparisonVersion) comparisonBusy = false
            }
        }
    }

    fun clearComparison() {
        comparisonVersion++
        comparisonJob?.cancel()
        comparisonJob = null
        comparison = null
        comparisonError = null
        comparisonBusy = false
    }

    fun openComparedFavorite(entry: ComparedFavorite) {
        val result = comparison ?: return
        if (entry !in result.entries) return
        // A previously requested favorite must not replace the name the user just chose.
        calculationVersion++
        calculationJob?.cancel()
        busy = false
        errorMessage = null
        selected = entry.evaluation to nameStats[entry.favorite.givenName]
        selectedSaju = result.saju
        selectedBirthLine = result.birthLine
        selectedEvaluationRecord = result.evaluationRecord
        pendingRoute = Routes.DETAIL
    }

    init {
        premium.start()
        viewModelScope.launch {
            recentNamesStore.flow.catch { error ->
                if (error is CancellationException) throw error
                explorationError = "최근 본 이름을 불러오지 못했어요. 이름 검색은 계속 사용할 수 있어요."
                recentNamesLoaded = true
            }.collect {
                recentNames = it
                recentNamesLoaded = true
            }
        }
        viewModelScope.launch {
            premium.isPremium.collect { isPremium = it }
        }
        viewModelScope.launch {
            premium.isAdFree.collect { isAdFree = it }
        }
        viewModelScope.launch {
            favoritesStore.flow.catch { error ->
                if (error is CancellationException) throw error
                favoriteError = "담아둔 이름을 불러오지 못했습니다. 앱을 다시 열어 주세요"
                favoritesLoaded = true
            }.collect {
                favorites = it
                favoritesLoaded = true
            }
        }
        viewModelScope.launch {
            unlockStore.flow.collect {
                unlockedNames = it.keys
                freeUnlockUsed = it.freeUsed
            }
        }
        // 저장된 입력 복원. 성씨 한자만은 DB가 있어야 하므로 문자열로 들고 있다가
        // 아래 로딩 완료 쪽과 늦게 끝나는 쪽이 되살린다(둘 다 메인 스레드라 경합 없음).
        viewModelScope.launch {
            try {
                inputStore.load()?.let { s ->
                    hasSavedInput = true
                    lastSavedInput = s
                    applySavedInput(s)
                }
                // 학파는 설정으로 옮겨졌다 — 설정 값이 있으면 그쪽이 우선한다.
                val settings = settingsStore.load()
                themeMode = settings.themeMode
                jokjaStyle = settings.jokjaStyle
                settings.school?.let { school = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorMessage = "저장된 입력을 불러오지 못했습니다. 출생 정보를 다시 확인해 주세요"
            } finally {
                inputsLoaded = true
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assets = getApplication<Application>().assets
                val hanjaBytes = assets.open("hanja.tsv").use { it.readBytes() }
                val db = HanjaDb.parse(hanjaBytes.toString(Charsets.UTF_8).lineSequence())
                val hanjaVersion = MessageDigest.getInstance("SHA-256").digest(hanjaBytes)
                    .joinToString("") { "%02x".format(it) }
                val pool = assets.open("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
                // Optional statistics must not block naming, but their screen must stop loading on failure.
                val statsResult = runCatching {
                    val metadata = assets.open("name-stats-metadata.properties").bufferedReader().useLines {
                        NameStatsMetadata.parse(it)
                    }
                    NameStats.parse(assets.open("name-stats.tsv").use { it.readBytes() }, metadata)
                }
                withContext(Dispatchers.Main) {
                    hanjaDb = db
                    hanjaDataVersion = hanjaVersion
                    namePool = pool
                    nameStats = statsResult.getOrDefault(NameStats.EMPTY)
                    statsLoaded = true
                    statsError = if (statsResult.isFailure) "통계 자료를 불러오지 못했어요. 이름 추천과 한자 찾기는 계속 사용할 수 있어요." else null
                    // DB 로딩 전에는 후보를 못 봐서 기본 성씨가 비어 있다 — 로딩 직후 채운다
                    if (surnameHanja.value.all { it == null }) {
                        restoreSurnameHanja()
                    }
                    restoreGivenHanja()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadError = "데이터 로딩 실패: ${e.message}"
                    statsLoaded = true
                    statsError = "이름 자료를 불러오지 못했어요. 앱을 다시 열어 주세요."
                }
            }
        }
    }

    /** 저장된 한자가 그 음의 후보에 있으면 그걸로, 없으면 유일 후보 자동 선택으로. */
    private fun restoreSurnameHanja() {
        val saved = savedSurnameHanja
        surnameHanja.value = List(surnameSyllables.length) { i ->
            val char = saved?.getOrNull(i)
            if (char == null || char == '_') soleSurnameHanja(i)
            else surnameCandidates(i).firstOrNull { it.char == char }.also {
                if (it == null) inputRestoreNotice = "예전에 선택한 한자를 현재 자료에서 찾지 못했어요. 빈 한자 칸을 다시 확인해 주세요."
            }
        }
    }

    private fun restoreGivenHanja() {
        if (hanjaDb == null) return
        val saved = savedGivenHanja
        givenHanja.value = List(givenNameSyllables.length) { i ->
            val char = saved?.getOrNull(i)
            if (char == null || char == '_') null
            else givenNameCandidates(i).firstOrNull { it.char == char }.also {
                if (it == null) inputRestoreNotice = "예전에 선택한 한자를 현재 자료에서 찾지 못했어요. 빈 한자 칸을 다시 확인해 주세요."
            }
        }
        savedGivenHanja = null
    }

    private fun applySavedInput(s: SavedInput) {
        inputRestoreNotice = null
        surname = acceptHangul(s.surname, 2)
        savedSurnameHanja = s.surnameHanja
        gender = runCatching { Gender.valueOf(s.gender) }.getOrDefault(Gender.M)
        mode = runCatching { AppMode.valueOf(s.mode) }.getOrDefault(AppMode.RECOMMEND)
        givenName = acceptHangul(s.givenName, 3)
        savedGivenHanja = s.givenHanja
        preBirth = s.preBirth
        popularOnly = s.popularOnly
        excludedSyllables = s.excludedSyllables
        diverseHanja = s.diverseHanja
        year = s.year; month = s.month; day = s.day
        hour = s.hour; minute = s.minute
        isLunar = s.isLunar; isLeapMonth = s.isLeapMonth; unknownTime = s.unknownTime
        val savedCity = CityRepository.KOREAN_CITIES.firstOrNull {
            it.name == s.cityName && (it.region ?: "") == s.cityRegion
        }
        city = savedCity ?: CityRepository.SEOUL
        if (savedCity == null && s.cityName.isNotEmpty()) inputRestoreNotice = "저장했던 출생 지역을 찾지 못해 서울로 표시했어요. 계산 전에 지역을 확인해 주세요."
        school = runCatching { BaleumSchool.valueOf(s.school) }.getOrDefault(BaleumSchool.UNHAE)
        singleName = s.singleName; dolimja = s.dolimja; dolimjaLast = s.dolimjaLast
        surnameHanja.value = List(surnameSyllables.length) { null }
        givenHanja.value = List(givenNameSyllables.length) { null }
        if (hanjaDb != null) {
            restoreSurnameHanja()
            restoreGivenHanja()
        }
    }

    /** The home action deliberately restores the last completed job, including its name and school. */
    fun resumeLastInput() {
        if (!inputsLoaded) return
        cancelCalculation()
        clearComparison()
        lastSavedInput?.let(::applySavedInput) ?: run { mode = AppMode.RECOMMEND }
    }

    fun startHanjaFor(name: String): Boolean {
        if (!inputsLoaded || hanjaDb == null) {
            explorationError = loadError ?: "이름 자료를 준비하고 있어요. 잠시 후 다시 선택해 주세요."
            return false
        }
        if (name.length !in 1..3 || !name.all(::isHangulSyllable)) {
            explorationError = "완성된 한글 이름을 1~3글자로 입력해 주세요."
            return false
        }
        cancelCalculation()
        clearComparison()
        mode = AppMode.HANJA
        onGivenNameChanged(name)
        givenHanja.value = List(name.length) { null }
        errorMessage = null
        inputRestoreNotice = null
        explorationError = null
        viewModelScope.launch {
            try {
                recentNamesStore.rememberName(name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                explorationError = "최근 본 이름을 저장하지 못했어요. 이름 검색은 계속 사용할 수 있어요."
            }
        }
        return true
    }

    fun clearRecentNames() {
        explorationError = null
        viewModelScope.launch {
            try {
                recentNamesStore.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                explorationError = "최근 본 이름을 지우지 못했어요. 다시 시도해 주세요."
            }
        }
    }

    private fun inputSnapshot() = SavedInput(
        surname = surnameSyllables,
        surnameHanja = surnameHanja.value.joinToString("") { it?.char?.toString() ?: "_" },
        gender = gender.name,
        preBirth = preBirth,
        popularOnly = popularOnly,
        excludedSyllables = excludedSyllables,
        diverseHanja = diverseHanja,
        year = year, month = month, day = day,
        hour = hour, minute = minute,
        isLunar = isLunar, isLeapMonth = isLeapMonth, unknownTime = unknownTime,
        cityName = city.name, cityRegion = city.region ?: "",
        school = school.name,
        singleName = singleName,
        dolimja = dolimja,
        dolimjaLast = dolimjaLast,
        mode = mode.name,
        givenName = givenNameSyllables,
        givenHanja = givenHanja.value.joinToString("") { it?.char?.toString() ?: "_" },
        savedAtEpochMillis = System.currentTimeMillis(),
    )

    /** Save the input that actually produced this result, only after successful calculation. */
    private fun persistInputs(snapshot: SavedInput) {
        viewModelScope.launch {
            try {
                inputStore.save(snapshot)
                hasSavedInput = true
                lastSavedInput = snapshot
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorMessage = "계산은 완료했지만 입력을 저장하지 못했습니다"
            }
        }
    }

    private data class EvaluationCondition(
        val birth: BirthInput?,
        val school: BaleumSchool,
        val birthLine: String?,
        val label: String,
        val record: EvaluationRecord,
    )

    private fun captureCondition(): EvaluationCondition {
        require(inputsLoaded) { "저장된 입력을 불러오는 중입니다. 잠시 후 다시 시도해 주세요" }
        val birth = if (preBirth) null else requireNotNull(birthInput()) {
            "출생 정보를 확인해 주세요. 실제 날짜·윤달과 시간을 입력하거나 사주 없이 보기를 선택해 주세요"
        }
        val birthLine = birth?.let {
            val calendar = when {
                !it.isLunar -> "양력"
                it.isLeapMonth -> "음력(윤달)"
                else -> "음력"
            }
            val time = if (it.unknownTime) "시간 모름" else "%d시 %02d분".format(it.hour, it.minute)
            val genderLabel = if (it.gender == Gender.M) "남아" else "여아"
            "$genderLabel · $calendar ${it.year}년 ${it.month}월 ${it.day}일 $time · ${city.namingLabel}"
        }
        val record = EvaluationRecord(school, System.currentTimeMillis(), EvaluationVersions.RULES,
            hanjaDataVersion, birth, if (birth != null) city.namingLabel else "")
        return EvaluationCondition(birth, school, birthLine,
            "${birthLine ?: "사주 없이 · 성명학 기준"} · ${school.label}", record)
    }

    private fun captureConditionOrError(): EvaluationCondition? = try {
        captureCondition()
    } catch (error: IllegalArgumentException) {
        errorMessage = error.message
        null
    }

    private fun cancelCalculation() {
        calculationVersion++
        calculationJob?.cancel()
        calculationJob = null
        busy = false
        pendingRoute = null
    }

    /** Capture on main, calculate off main, publish only the newest completed request. */
    private fun <T> launchCalculation(calculate: () -> T, publish: (T) -> Unit) {
        calculationJob?.cancel()
        val version = ++calculationVersion
        errorMessage = null
        pendingRoute = null
        busy = true
        calculationJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { calculate() }
                if (version == calculationVersion) publish(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == calculationVersion) errorMessage = "계산 오류: ${error.message ?: "다시 시도해 주세요"}"
            } finally {
                if (version == calculationVersion) busy = false
            }
        }
    }

    /**
     * 입력란에는 조합 중인 낱자가 섞여 있을 수 있으므로, 실제 계산·한자 슬롯에는
     * **완성된 음절만** 쓴다.
     */
    val surnameSyllables: String get() = surname.filter(::isHangulSyllable)
    val givenNameSyllables: String get() = givenName.filter(::isHangulSyllable)

    fun surnameCandidates(index: Int): List<HanjaEntry> {
        val db = hanjaDb ?: return emptyList()
        val ch = surnameSyllables.getOrNull(index) ?: return emptyList()
        return db.candidatesFor(ch.toString())
    }

    fun givenNameCandidates(index: Int): List<HanjaEntry> {
        val db = hanjaDb ?: return emptyList()
        val ch = givenNameSyllables.getOrNull(index) ?: return emptyList()
        return db.candidatesFor(ch.toString())
    }

    private fun birthInput(): BirthInput? {
        val y = year.toIntOrNull() ?: return null
        val mo = month.toIntOrNull() ?: return null
        val d = day.toIntOrNull() ?: return null
        val h = if (unknownTime) 12 else hour.toIntOrNull() ?: return null
        val mi = if (unknownTime) 0 else minute.toIntOrNull() ?: return null
        if (!isValidBirthDate(y, mo, d, isLunar, isLunar && isLeapMonth)) return null
        if (h !in 0..23 || mi !in 0..59) return null
        return BirthInput(
            year = y, month = mo, day = d, hour = h, minute = mi,
            gender = gender,
            unknownTime = unknownTime,
            isLunar = isLunar,
            isLeapMonth = isLunar && isLeapMonth,
            latitude = city.lat,
            longitude = city.lon,
            // 지역을 받는 이유가 이 보정이다 — 표준시(동경 135°) 대신 출생지 태양시로 계산.
            useTrueSolarTime = true,
        )
    }

    private fun pickedSurnameHanja(): List<HanjaEntry>? {
        val picked = surnameHanja.value.filterNotNull()
        if (picked.size != surnameSyllables.length || picked.isEmpty()) return null
        return picked
    }

    fun runRecommend() {
        cancelCalculation()
        val db = hanjaDb ?: return
        val pool = namePool ?: return
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        val condition = captureConditionOrError() ?: return
        val snapshot = inputSnapshot().copy(mode = AppMode.RECOMMEND.name)
        val requestGender = gender
        val stats = nameStats
        val options = GeneratorOptions(
            school = condition.school,
            maxTier = if (popularOnly) 1 else 3,
            singleSyllable = singleName,
            fixedSyllable = if (singleName) null else dolimja.filter(::isHangulSyllable).firstOrNull(),
            fixedLast = dolimjaLast,
            excludedSyllables = excludedSyllables.filter(::isHangulSyllable).toSet(),
            diverseHanja = diverseHanja,
        )
        launchCalculation(calculate = {
            val analyzed = condition.birth?.let(SajuNamingService::analyze)
            analyzed to NameGenerator(db, pool, stats).generate(snapshot.surname, sHanja, requestGender, analyzed, options)
        }) { (analyzed, list) ->
            saju = analyzed
            resultBirthLine = condition.birthLine
            resultEvaluationRecord = condition.record
            resultMode = AppMode.RECOMMEND
            candidates = list
            hanjaCombos = emptyList()
            evaluation = null
            if (list.isEmpty()) {
                errorMessage = "조건에 맞는 이름을 찾지 못했습니다 — 제외할 글자·돌림자·외자·인기 이름 조건을 조정해 보세요"
            } else {
                persistInputs(snapshot)
                pendingRoute = Routes.RESULT
            }
        }
    }

    fun runHanjaRecommend() {
        cancelCalculation()
        val db = hanjaDb ?: return
        val pool = namePool ?: return
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        val requestName = givenNameSyllables
        if (requestName.isEmpty()) {
            errorMessage = "한자를 붙일 이름을 입력해 주세요"
            return
        }
        val condition = captureConditionOrError() ?: return
        val snapshot = inputSnapshot().copy(mode = AppMode.HANJA.name)
        val stats = nameStats
        launchCalculation(calculate = {
            val analyzed = condition.birth?.let(SajuNamingService::analyze)
            analyzed to NameGenerator(db, pool, stats).hanjaCombosFor(
                surname = snapshot.surname, surnameHanja = sHanja, givenName = requestName,
                saju = analyzed, options = GeneratorOptions(school = condition.school, requireAllGoodSuri = false),
            )
        }) { (analyzed, combos) ->
            saju = analyzed
            resultBirthLine = condition.birthLine
            resultEvaluationRecord = condition.record
            resultMode = AppMode.HANJA
            hanjaCombos = combos
            candidates = emptyList()
            evaluation = null
            if (combos.isEmpty()) {
                errorMessage = "'$requestName'에 추천할 한자 조합이 없습니다. 이름 감명에서 한자를 직접 선택할 수 있어요"
            } else {
                persistInputs(snapshot)
                pendingRoute = Routes.RESULT
            }
        }
    }

    fun runEvaluate() {
        cancelCalculation()
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        val gHanja = givenHanja.value.filterNotNull()
        val requestName = givenNameSyllables
        if (requestName.isEmpty() || gHanja.size != requestName.length) {
            errorMessage = "이름과 이름 한자를 모두 선택해 주세요"
            return
        }
        val condition = captureConditionOrError() ?: return
        val snapshot = inputSnapshot().copy(mode = AppMode.EVALUATE.name)
        launchCalculation(calculate = {
            val analyzed = condition.birth?.let(SajuNamingService::analyze)
            analyzed to NameEvaluator.evaluate(snapshot.surname, requestName, sHanja, gHanja, analyzed, condition.school)
        }) { (analyzed, eval) ->
            // The existing recommendation list and its context can still be reached with Back.
            evaluation = eval
            selected = eval to nameStats[eval.givenName]
            selectedSaju = analyzed
            selectedBirthLine = condition.birthLine
            selectedEvaluationRecord = condition.record
            persistInputs(snapshot)
            pendingRoute = Routes.DETAIL
        }
    }

    fun onSurnameChanged(value: String) {
        val before = surnameSyllables
        surname = acceptHangul(value, maxSyllables = 2)
        // 조합 중에는 한자 슬롯을 건드리지 않는다 — 두 글자 성씨에서 둘째 글자를 치는 동안
        // 첫 글자에 골라 둔 한자가 날아가면 안 된다.
        if (surnameSyllables != before) {
            surnameHanja.value = List(surnameSyllables.length) { i -> soleSurnameHanja(i) }
        }
    }

    /**
     * 그 음으로 읽는 인명용 한자가 딱 하나뿐이면 미리 골라 둔다(김→金, 최→崔).
     * 후보가 여럿이면 고르지 않는다 — 성씨는 잘못 찍으면 결과가 통째로 틀어진다.
     */
    private fun soleSurnameHanja(index: Int): HanjaEntry? =
        surnameCandidates(index).singleOrNull()

    fun onGivenNameChanged(value: String) {
        val before = givenNameSyllables
        givenName = acceptHangul(value, maxSyllables = 3)
        if (givenNameSyllables != before) {
            givenHanja.value = List(givenNameSyllables.length) { null }
        }
    }

    override fun onCleared() {
        premium.dispose()
    }
}
