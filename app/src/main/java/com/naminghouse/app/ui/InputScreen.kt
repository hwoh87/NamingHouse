package com.naminghouse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.app.ui.theme.InkShape
import com.samramanshang.manseryeok.orrery.model.Gender
import com.samramanshang.manseryeok.orrery.util.TimezoneUtils
import kotlin.math.abs

/**
 * 입력 화면.
 *
 * 모드는 홈에서 정해져 들어온다 — 여기서는 그 모드에 필요한 칸만 보인다.
 * 폼이 길어서 제출 버튼을 스크롤 맨 아래에 두면 매번 끝까지 내려야 하므로,
 * 제목·제출은 고정하고 가운데만 스크롤한다.
 */
@Composable
fun InputScreen(vm: NamingViewModel, nav: NavHostController) {
    // 계산이 끝나면 뷰모델이 목적지를 적어 둔다 — 여기서 소비하고 이동한다.
    LaunchedEffect(vm.pendingRoute) {
        vm.pendingRoute?.let { route ->
            vm.pendingRoute = null
            nav.navigate(route)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                vm.mode.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            vm.loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.hanjaDb == null && vm.loadError == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "한자 데이터를 읽는 중입니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SurnameCard(vm)
            if (vm.mode == AppMode.EVALUATE || vm.mode == AppMode.HANJA) GivenNameCard(vm)
            if (vm.mode == AppMode.RECOMMEND) BabyCard(vm)
            BirthCard(vm)

            Spacer(Modifier.height(4.dp))
        }

        SubmitBar(vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurnameCard(vm: NamingViewModel) {
    InkCard {
        SectionTitle("성씨")
        InkField(
            value = vm.surname,
            onValueChange = vm::onSurnameChanged,
            label = "성 (한글, 1~2자)",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.surnameSyllables.forEachIndexed { i, ch ->
                HanjaSlotButton(
                    syllable = ch,
                    selected = vm.surnameHanja.value.getOrNull(i),
                    candidates = vm.surnameCandidates(i),
                    onSelect = { picked -> vm.surnameHanja.value = pick(vm.surnameHanja.value, vm.surnameSyllables.length, i, picked) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GivenNameCard(vm: NamingViewModel) {
    InkCard {
        SectionTitle("이름")
        InkField(
            value = vm.givenName,
            onValueChange = vm::onGivenNameChanged,
            label = "이름 (한글, 1~3자)",
        )
        if (vm.mode == AppMode.EVALUATE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.givenNameSyllables.forEachIndexed { i, ch ->
                    HanjaSlotButton(
                        syllable = ch,
                        selected = vm.givenHanja.value.getOrNull(i),
                        candidates = vm.givenNameCandidates(i),
                        onSelect = { picked -> vm.givenHanja.value = pick(vm.givenHanja.value, vm.givenNameSyllables.length, i, picked) },
                    )
                }
            }
        } else {
            Hint("이름에 붙일 한자 조합을 점수순으로 찾아 드립니다.")
        }
    }
}

@Composable
private fun BabyCard(vm: NamingViewModel) {
    InkCard {
        SectionTitle("아기 정보")
        InkSegmented(
            options = listOf(Gender.M, Gender.F),
            selected = vm.gender,
            label = { if (it == Gender.M) "남아" else "여아" },
            onSelect = { vm.gender = it },
        )
        SwitchRow(
            checked = vm.popularOnly,
            onChange = { vm.popularOnly = it },
            text = "최근 인기 이름 위주로 추천",
            hint = "대법원 출생신고 상위권에 든 이름만 후보로 씁니다",
        )
        SwitchRow(
            checked = vm.singleName,
            onChange = { vm.singleName = it },
            text = "외자 이름",
            hint = "준·율·설처럼 한 글자 이름만 추천합니다",
        )
        // 외자에는 돌림자가 무의미하다 — 켜면 접는다
        AnimatedVisibility(!vm.singleName) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkField(
                        value = vm.dolimja,
                        onValueChange = vm::onDolimjaChanged,
                        label = "돌림자 (선택)",
                        modifier = Modifier.weight(1f),
                    )
                    Column(Modifier.weight(1.4f)) {
                        InkSegmented(
                            options = listOf(false, true),
                            selected = vm.dolimjaLast,
                            label = { if (it) "끝 글자" else "첫 글자" },
                            onSelect = { vm.dolimjaLast = it },
                        )
                    }
                }
                if (vm.dolimja.isNotEmpty()) {
                    Hint("'${vm.dolimja}' 자가 ${if (vm.dolimjaLast) "끝" else "첫"} 글자에 든 이름만 추천합니다")
                }
            }
        }
    }
}

@Composable
private fun BirthCard(vm: NamingViewModel) {
    InkCard {
        SectionTitle(if (vm.mode == AppMode.RECOMMEND) "출생 정보" else "출생 정보 (사주)")

        SwitchRow(
            checked = vm.preBirth,
            onChange = { vm.preBirth = it },
            text = when (vm.mode) {
                AppMode.RECOMMEND -> "출생 전 작명"
                AppMode.HANJA -> "사주 없이 고르기"
                AppMode.EVALUATE -> "사주 없이 감명"
            },
            hint = "사주를 빼고 성명학만으로 봅니다",
        )

        if (!vm.preBirth) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkField(
                    value = vm.year,
                    onValueChange = { vm.year = it.filter(Char::isDigit).take(4) },
                    label = "년", numeric = true, modifier = Modifier.weight(1.3f),
                )
                InkField(
                    value = vm.month,
                    onValueChange = { vm.month = it.filter(Char::isDigit).take(2) },
                    label = "월", numeric = true, modifier = Modifier.weight(1f),
                )
                InkField(
                    value = vm.day,
                    onValueChange = { vm.day = it.filter(Char::isDigit).take(2) },
                    label = "일", numeric = true, modifier = Modifier.weight(1f),
                )
            }

            InkSegmented(
                options = listOf(false, true),
                selected = vm.isLunar,
                label = { if (it) "음력" else "양력" },
                onSelect = { lunar -> vm.isLunar = lunar; if (!lunar) vm.isLeapMonth = false },
            )
            if (vm.isLunar) {
                CheckRow(vm.isLeapMonth, { vm.isLeapMonth = it }, "윤달")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkField(
                    value = vm.hour,
                    onValueChange = { vm.hour = it.filter(Char::isDigit).take(2) },
                    label = "시 (0~23)", numeric = true, enabled = !vm.unknownTime,
                    modifier = Modifier.weight(1f),
                )
                InkField(
                    value = vm.minute,
                    onValueChange = { vm.minute = it.filter(Char::isDigit).take(2) },
                    label = "분", numeric = true, enabled = !vm.unknownTime,
                    modifier = Modifier.weight(1f),
                )
            }
            CheckRow(vm.unknownTime, { vm.unknownTime = it }, "태어난 시간 모름")

            RegionRow(vm)

            // 숫자 칸 다섯 개를 눈으로 검산하기 어렵다 — 읽을 수 있는 한 줄로 되돌려 준다.
            val summary = birthSummary(vm)
            Text(
                summary ?: "생년월일시를 다시 확인해 주세요",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (summary != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 출생 지역 — 표준시(동경 135°) 대신 출생지 태양시로 사주를 계산하기 위한 입력.
 * 서울 기준 약 -32분이라 시(時) 경계 부근 출생은 시주가 달라질 수 있다.
 */
@Composable
private fun RegionRow(vm: NamingViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("출생 지역", style = MaterialTheme.typography.bodyMedium)
            Hint(regionHint(vm))
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { open = true }, shape = InkShape.medium) {
            Text(vm.city.name, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (open) {
        CityPickerDialog(
            selected = vm.city,
            onSelect = { vm.city = it; open = false },
            onDismiss = { open = false },
        )
    }
}

private fun regionHint(vm: NamingViewModel): String {
    val base = "출생지 기준 진태양시로 사주를 봅니다"
    val y = vm.year.toIntOrNull() ?: return base
    val mo = vm.month.toIntOrNull() ?: return base
    val d = vm.day.toIntOrNull() ?: return base
    // 2월 31일 같은 없는 날짜는 델타 계산이 못 받는다 — 힌트는 기본 문구로 물러난다.
    val delta = runCatching { TimezoneUtils.trueSolarDeltaMinutes(y, mo, d, vm.city.lon) }
        .getOrNull() ?: return base
    val sign = if (delta >= 0) "+" else "−"
    return "$base · 보정 $sign${abs(delta)}분"
}

@Composable
private fun SubmitBar(vm: NamingViewModel) {
    Column(Modifier.fillMaxWidth()) {
        InkStroke(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurface,
            alpha = 0.16f,
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            vm.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = {
                    when (vm.mode) {
                        AppMode.RECOMMEND -> vm.runRecommend()
                        AppMode.HANJA -> vm.runHanjaRecommend()
                        AppMode.EVALUATE -> vm.runEvaluate()
                    }
                },
                enabled = !vm.busy && vm.hanjaDb != null,
                shape = InkShape.medium,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        when (vm.mode) {
                            AppMode.RECOMMEND -> "이름 추천 받기"
                            AppMode.HANJA -> "한자 조합 찾기"
                            AppMode.EVALUATE -> "이름 감명 보기"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── 작은 부품들 ──────────────────────────────────────────────────────────────

@Composable
private fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    numeric: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = InkShape.medium,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        modifier = modifier,
    )
}

/** 스위치 한 줄 — 줄 전체가 눌린다. 스위치만 겨냥해 누르게 두면 놓치기 쉽다. */
@Composable
private fun SwitchRow(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    text: String,
    hint: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InkShape.medium)
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InkShape.medium)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 슬롯 [index] 에 한자를 끼운 새 목록.
 *
 * 항상 음절 수 [size] 만큼의 자리를 만들어 돌려준다 — 슬롯 버튼은 음절을 보고 그려지므로
 * 저장된 목록이 그보다 짧으면 그대로 넣다가 인덱스를 넘긴다.
 */
private fun <T> pick(current: List<T?>, size: Int, index: Int, value: T): List<T?> =
    List(size) { i -> if (i == index) value else current.getOrNull(i) }

/**
 * 입력한 날짜·시간을 읽을 수 있는 한 줄로 되돌린다.
 * 계산에 쓰이는 조건과 같은 범위로 검사하므로, 여기서 빨간 줄이 뜨면 제출도 실패한다.
 */
private fun birthSummary(vm: NamingViewModel): String? {
    val y = vm.year.toIntOrNull() ?: return null
    val mo = vm.month.toIntOrNull() ?: return null
    val d = vm.day.toIntOrNull() ?: return null
    if (y !in 1900..2050 || mo !in 1..12 || d !in 1..31) return null

    val calendar = when {
        vm.isLunar && vm.isLeapMonth -> "음력 윤달"
        vm.isLunar -> "음력"
        else -> "양력"
    }
    val time = if (vm.unknownTime) {
        "시간 모름"
    } else {
        val h = vm.hour.toIntOrNull() ?: return null
        val mi = vm.minute.toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59) return null
        "${h}시 ${mi.toString().padStart(2, '0')}분"
    }
    return "$calendar ${y}년 ${mo}월 ${d}일 · $time"
}
