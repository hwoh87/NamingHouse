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
import com.naminghouse.engine.gen.NameStats
import com.naminghouse.engine.hanja.HanjaDb
import com.naminghouse.engine.hanja.HanjaEntry
import com.naminghouse.engine.oheng.BaleumSchool
import com.naminghouse.engine.saju.SajuNamingService
import com.naminghouse.engine.saju.SajuSummary
import com.samramanshang.manseryeok.orrery.model.BirthInput
import com.samramanshang.manseryeok.orrery.model.Gender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppMode(val label: String) {
    RECOMMEND("이름 추천"),
    HANJA("한자 추천"),
    EVALUATE("이름 감명"),
}

enum class AppScreen { INPUT, RESULT }

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
    var screen by mutableStateOf(AppScreen.INPUT)
    var mode by mutableStateOf(AppMode.RECOMMEND)
    var busy by mutableStateOf(false)
        private set

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
    var school by mutableStateOf(BaleumSchool.UNHAE)
    var popularOnly by mutableStateOf(false) // tier 1 이름만

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

    // ── 즐겨찾기
    private val favoritesStore = FavoritesStore(app)
    var favorites by mutableStateOf<List<FavoriteName>>(emptyList())
        private set
    var showFavorites by mutableStateOf(false)

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

    init {
        viewModelScope.launch {
            favoritesStore.flow.collect { favorites = it }
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
                        surnameHanja.value = List(surnameSyllables.length) { i -> soleSurnameHanja(i) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { loadError = "데이터 로딩 실패: ${e.message}" }
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
        if (y !in 1900..2050 || mo !in 1..12 || d !in 1..31) return null
        if (h !in 0..23 || mi !in 0..59) return null
        return BirthInput(
            year = y, month = mo, day = d, hour = h, minute = mi,
            gender = gender,
            unknownTime = unknownTime,
            isLunar = isLunar,
            isLeapMonth = isLunar && isLeapMonth,
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
        busy = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val sajuResult = input?.let { SajuNamingService.analyze(it) }
                val generator = NameGenerator(db, pool, nameStats)
                val options = GeneratorOptions(
                    school = school,
                    maxTier = if (popularOnly) 1 else 3,
                )
                val list = generator.generate(surnameSyllables, sHanja, gender, sajuResult, options)
                withContext(Dispatchers.Main) {
                    saju = sajuResult
                    candidates = list
                    hanjaCombos = emptyList()
                    evaluation = null
                    screen = AppScreen.RESULT
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
                    if (combos.isNotEmpty()) screen = AppScreen.RESULT
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
                    screen = AppScreen.RESULT
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

    fun backToInput() {
        screen = AppScreen.INPUT
    }
}
