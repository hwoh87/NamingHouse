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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // ── 화면 상태
    var mode by mutableStateOf(AppMode.RECOMMEND)
    var busy by mutableStateOf(false)
        private set

    /** 계산이 끝나 이동해야 할 목적지. 입력 화면이 소비하고 비운다. */
    var pendingRoute by mutableStateOf<String?>(null)

    /** 상세 화면이 보고 있는 이름 — 라우트로 직렬화하기엔 무거워 여기 든다. */
    var selected by mutableStateOf<Pair<NameEvaluation, NameStat?>?>(null)

    /** 저장된 입력이 있는가 — 홈의 '이어서 이름 짓기' 노출 기준. */
    var hasSavedInput by mutableStateOf(false)
        private set

    // ── 설정
    private val settingsStore = SettingsStore(app)
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

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

    // ── 즐겨찾기
    private val favoritesStore = FavoritesStore(app)
    var favorites by mutableStateOf<List<FavoriteName>>(emptyList())
        private set

    /** 이 이름이 담겨 있는가 — 이름+한자로 판별(점수는 사주에 따라 달라진다) */
    fun isFavorite(eval: NameEvaluation): Boolean {
        val hanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() }
        return favorites.any {
            it.surname == eval.surname && it.givenName == eval.givenName && it.hanja == hanja
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
        )
        viewModelScope.launch { favoritesStore.toggle(item) }
    }

    fun removeFavorite(item: FavoriteName) {
        viewModelScope.launch { favoritesStore.remove(item) }
    }

    /**
     * 담아둔 이름을 다시 평가해 상세로 보낸다.
     *
     * 즐겨찾기에는 요약(점수·등급·뜻)만 저장돼 있어 수리·오행 풀이는 그때그때 새로 계산한다.
     * 사주는 **지금 저장된 생년월일시** 기준이라, 담아둔 뒤 입력을 바꿨으면 점수가 달라질 수 있다.
     *
     * @return 계산을 시작했으면 true, 데이터가 아직 준비되지 않았으면 false
     */
    fun openFavorite(item: FavoriteName): Boolean {
        val db = hanjaDb ?: return false
        val entries = item.hanja.map { db.byChar[it] ?: return false }
        if (entries.size != item.surname.length + item.givenName.length) return false
        val sHanja = entries.take(item.surname.length)
        val gHanja = entries.drop(item.surname.length)
        if (sHanja.isEmpty() || gHanja.isEmpty()) return false
        // 생년월일시가 비어 있거나 어긋나면 사주 없이 수리·오행만 보여 준다.
        val input = if (preBirth) null else birthInput()

        errorMessage = null
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sajuResult = input?.let { SajuNamingService.analyze(it) }
                val eval = NameEvaluator.evaluate(
                    item.surname, item.givenName, sHanja, gHanja, sajuResult, school
                )
                withContext(Dispatchers.Main) {
                    saju = sajuResult
                    selected = eval to nameStats[item.givenName]
                    pendingRoute = Routes.DETAIL
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "계산 오류: ${e.message}"
                    busy = false
                }
            }
        }
        return true
    }

    init {
        viewModelScope.launch {
            favoritesStore.flow.collect { favorites = it }
        }
        // 저장된 입력 복원. 성씨 한자만은 DB가 있어야 하므로 문자열로 들고 있다가
        // 아래 로딩 완료 쪽과 늦게 끝나는 쪽이 되살린다(둘 다 메인 스레드라 경합 없음).
        viewModelScope.launch {
            inputStore.load()?.let { s ->
                hasSavedInput = true
                surname = s.surname
                savedSurnameHanja = s.surnameHanja
                gender = runCatching { Gender.valueOf(s.gender) }.getOrDefault(Gender.M)
                preBirth = s.preBirth
                popularOnly = s.popularOnly
                if (s.year.isNotBlank()) { year = s.year; month = s.month; day = s.day }
                if (s.hour.isNotBlank()) { hour = s.hour; minute = s.minute }
                isLunar = s.isLunar
                isLeapMonth = s.isLeapMonth
                unknownTime = s.unknownTime
                city = CityRepository.KOREAN_CITIES.firstOrNull {
                    it.name == s.cityName && (it.region ?: "") == s.cityRegion
                } ?: CityRepository.SEOUL
                school = runCatching { BaleumSchool.valueOf(s.school) }
                    .getOrDefault(BaleumSchool.UNHAE)
                singleName = s.singleName
                dolimja = s.dolimja
                dolimjaLast = s.dolimjaLast
                if (hanjaDb != null) restoreSurnameHanja()
            }
            // 학파는 설정으로 옮겨졌다 — 설정 값이 있으면 그쪽이 우선한다.
            val settings = settingsStore.load()
            themeMode = settings.themeMode
            settings.school?.let { school = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assets = getApplication<Application>().assets
                val db = assets.open("hanja.tsv").bufferedReader().useLines { HanjaDb.parse(it) }
                val pool = assets.open("names.tsv").bufferedReader().useLines { NamePool.parse(it) }
                // 통계는 없어도 앱이 동작해야 하므로 실패해도 무시한다
                val stats = runCatching {
                    assets.open("name-stats.tsv").bufferedReader().useLines { NameStats.parse(it) }
                }.getOrDefault(NameStats.EMPTY)
                withContext(Dispatchers.Main) {
                    hanjaDb = db
                    namePool = pool
                    nameStats = stats
                    // DB 로딩 전에는 후보를 못 봐서 기본 성씨가 비어 있다 — 로딩 직후 채운다
                    if (surnameHanja.value.all { it == null }) {
                        restoreSurnameHanja()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loadError = "데이터 로딩 실패: ${e.message}" }
            }
        }
    }

    /** 저장된 한자가 그 음의 후보에 있으면 그걸로, 없으면 유일 후보 자동 선택으로. */
    private fun restoreSurnameHanja() {
        val saved = savedSurnameHanja
        surnameHanja.value = List(surnameSyllables.length) { i ->
            saved?.getOrNull(i)?.let { ch -> surnameCandidates(i).firstOrNull { it.char == ch } }
                ?: soleSurnameHanja(i)
        }
    }

    /** 제출에 성공한 입력을 저장한다 — 다음 실행 때 그대로 복원된다. */
    private fun persistInputs() {
        val snapshot = SavedInput(
            surname = surnameSyllables,
            surnameHanja = surnameHanja.value.joinToString("") { it?.char?.toString() ?: "" },
            gender = gender.name,
            preBirth = preBirth,
            popularOnly = popularOnly,
            year = year, month = month, day = day,
            hour = hour, minute = minute,
            isLunar = isLunar, isLeapMonth = isLeapMonth, unknownTime = unknownTime,
            cityName = city.name, cityRegion = city.region ?: "",
            school = school.name,
            singleName = singleName,
            dolimja = dolimja,
            dolimjaLast = dolimjaLast,
        )
        hasSavedInput = true
        viewModelScope.launch { inputStore.save(snapshot) }
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
        if (y !in 1900..2050 || mo !in 1..12 || d !in 1..31) return null
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
        val db = hanjaDb ?: return
        val pool = namePool ?: return
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        val input = if (preBirth) null else birthInput()
            ?: run { errorMessage = "생년월일시를 확인해 주세요"; return }

        errorMessage = null
        persistInputs()
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sajuResult = input?.let { SajuNamingService.analyze(it) }
                val generator = NameGenerator(db, pool, nameStats)
                val options = GeneratorOptions(
                    school = school,
                    maxTier = if (popularOnly) 1 else 3,
                    singleSyllable = singleName,
                    fixedSyllable = if (singleName) null else dolimja.filter(::isHangulSyllable).firstOrNull(),
                    fixedLast = dolimjaLast,
                )
                val list = generator.generate(surnameSyllables, sHanja, gender, sajuResult, options)
                withContext(Dispatchers.Main) {
                    saju = sajuResult
                    candidates = list
                    hanjaCombos = emptyList()
                    evaluation = null
                    // 외자·돌림자·인기 필터가 겹치면 빈 결과가 나올 수 있다 —
                    // 빈 화면으로 넘기지 말고 입력 화면에서 조건을 풀게 안내한다.
                    errorMessage = if (list.isEmpty()) {
                        "조건에 맞는 이름을 찾지 못했습니다 — 돌림자·외자·인기 이름 조건을 조정해 보세요"
                    } else null
                    if (list.isNotEmpty()) {
                        pendingRoute = "result"
                    }
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "계산 오류: ${e.message}"
                    busy = false
                }
            }
        }
    }

    /** 한글 이름은 정해져 있고 한자 조합만 추천받는 모드. */
    fun runHanjaRecommend() {
        val db = hanjaDb ?: return
        val pool = namePool ?: return
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        if (givenNameSyllables.isEmpty()) {
            errorMessage = "한자를 붙일 이름을 입력해 주세요"
            return
        }
        val input = if (preBirth) null else birthInput()
            ?: run { errorMessage = "생년월일시를 확인해 주세요"; return }

        errorMessage = null
        persistInputs()
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sajuResult = input?.let { SajuNamingService.analyze(it) }
                val combos = NameGenerator(db, pool, nameStats).hanjaCombosFor(
                    surname = surnameSyllables,
                    surnameHanja = sHanja,
                    givenName = givenNameSyllables,
                    saju = sajuResult,
                    options = GeneratorOptions(school = school, requireAllGoodSuri = false),
                )
                withContext(Dispatchers.Main) {
                    saju = sajuResult
                    hanjaCombos = combos
                    candidates = emptyList()
                    evaluation = null
                    errorMessage = if (combos.isEmpty()) {
                        "'$givenNameSyllables' 에 쓸 수 있는 인명용 한자를 찾지 못했습니다"
                    } else null
                    if (combos.isNotEmpty()) {
                        pendingRoute = "result"
                    }
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "계산 오류: ${e.message}"
                    busy = false
                }
            }
        }
    }

    fun runEvaluate() {
        val sHanja = pickedSurnameHanja()
            ?: run { errorMessage = "성씨 한자를 선택해 주세요"; return }
        val gHanja = givenHanja.value.filterNotNull()
        if (givenNameSyllables.isEmpty() || gHanja.size != givenNameSyllables.length) {
            errorMessage = "이름과 이름 한자를 모두 선택해 주세요"
            return
        }
        val input = if (preBirth) null else birthInput()
            ?: run { errorMessage = "생년월일시를 확인해 주세요"; return }

        errorMessage = null
        persistInputs()
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sajuResult = input?.let { SajuNamingService.analyze(it) }
                val eval = NameEvaluator.evaluate(
                    surnameSyllables, givenNameSyllables, sHanja, gHanja, sajuResult, school
                )
                withContext(Dispatchers.Main) {
                    saju = sajuResult
                    evaluation = eval
                    candidates = emptyList()
                    hanjaCombos = emptyList()
                    // 감명은 이름이 하나라 목록을 거치지 않고 바로 상세로 간다
                    selected = eval to nameStats[eval.givenName]
                    pendingRoute = "detail"
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "계산 오류: ${e.message}"
                    busy = false
                }
            }
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
}
