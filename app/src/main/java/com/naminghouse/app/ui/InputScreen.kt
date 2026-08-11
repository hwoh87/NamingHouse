package com.naminghouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.naminghouse.app.AppMode
import com.naminghouse.app.NamingViewModel
import com.naminghouse.engine.oheng.BaleumSchool
import com.samramanshang.manseryeok.orrery.model.Gender

@Composable
fun InputScreen(vm: NamingViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("작명하우스", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "사주와 성명학으로 좋은 이름을 짓습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (vm.loadError != null) {
            Text(vm.loadError!!, color = MaterialTheme.colorScheme.error)
        }
        if (vm.hanjaDb == null && vm.loadError == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                Text("한자 데이터 로딩 중…", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ── 모드 선택
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = vm.mode == m,
                    onClick = { vm.mode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = AppMode.entries.size),
                ) { Text(m.label) }
            }
        }

        // ── 성씨
        SectionCard("성씨") {
            OutlinedTextField(
                value = vm.surname,
                onValueChange = vm::onSurnameChanged,
                label = { Text("성 (한글, 1~2자)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.surname.forEachIndexed { i, ch ->
                    HanjaSlotButton(
                        syllable = ch,
                        selected = vm.surnameHanja.value.getOrNull(i),
                        candidates = vm.surnameCandidates(i),
                        onSelect = { picked ->
                            vm.surnameHanja.value =
                                vm.surnameHanja.value.toMutableList().also { it[i] = picked }
                        },
                    )
                }
            }
        }

        // ── 이름 입력 — 감명은 한자까지 직접 고르고, 한자 추천은 한글만 받는다
        if (vm.mode == AppMode.EVALUATE || vm.mode == AppMode.HANJA) {
            SectionCard("이름") {
                OutlinedTextField(
                    value = vm.givenName,
                    onValueChange = vm::onGivenNameChanged,
                    label = { Text("이름 (한글, 1~3자)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.mode == AppMode.EVALUATE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.givenName.forEachIndexed { i, ch ->
                            HanjaSlotButton(
                                syllable = ch,
                                selected = vm.givenHanja.value.getOrNull(i),
                                candidates = vm.givenNameCandidates(i),
                                onSelect = { picked ->
                                    vm.givenHanja.value =
                                        vm.givenHanja.value.toMutableList().also { it[i] = picked }
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        "이름에 붙일 한자 조합을 점수순으로 찾아 드립니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 성별 (추천 모드)
        if (vm.mode == AppMode.RECOMMEND) {
            SectionCard("아기 정보") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = vm.gender == Gender.M,
                        onClick = { vm.gender = Gender.M },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("남아") }
                    SegmentedButton(
                        selected = vm.gender == Gender.F,
                        onClick = { vm.gender = Gender.F },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("여아") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = vm.popularOnly, onCheckedChange = { vm.popularOnly = it })
                    Spacer(Modifier.width(8.dp))
                    Text("최근 인기 이름 위주로 추천", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── 출생 정보
        SectionCard(if (vm.mode == AppMode.RECOMMEND) "출생 정보 (사주 반영)" else "출생 정보") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.preBirth, onCheckedChange = { vm.preBirth = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    when (vm.mode) {
                        AppMode.RECOMMEND -> "출생 전 작명 (사주 없이 성명학만)"
                        AppMode.HANJA -> "사주 없이 성명학만으로 고르기"
                        AppMode.EVALUATE -> "사주 없이 감명"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!vm.preBirth) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.year, onValueChange = { vm.year = it.filter(Char::isDigit).take(4) },
                        label = { Text("년") }, singleLine = true, modifier = Modifier.weight(1.2f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = vm.month, onValueChange = { vm.month = it.filter(Char::isDigit).take(2) },
                        label = { Text("월") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = vm.day, onValueChange = { vm.day = it.filter(Char::isDigit).take(2) },
                        label = { Text("일") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !vm.isLunar,
                        onClick = { vm.isLunar = false; vm.isLeapMonth = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("양력") }
                    SegmentedButton(
                        selected = vm.isLunar,
                        onClick = { vm.isLunar = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("음력") }
                }
                if (vm.isLunar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.isLeapMonth, onCheckedChange = { vm.isLeapMonth = it })
                        Text("윤달", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = vm.hour, onValueChange = { vm.hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("시 (0~23)") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !vm.unknownTime,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = vm.minute, onValueChange = { vm.minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("분") }, singleLine = true, modifier = Modifier.weight(1f),
                        enabled = !vm.unknownTime,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.unknownTime, onCheckedChange = { vm.unknownTime = it })
                    Text("태어난 시간 모름", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── 설정
        SectionCard("성명학 설정") {
            Text("발음오행 학파", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BaleumSchool.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = vm.school == s,
                        onClick = { vm.school = s },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = BaleumSchool.entries.size),
                    ) { Text(s.label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Text(
                "운해본: ㅇㅎ=토(작명 실무 다수설) · 해례본: ㅇㅎ=수(훈민정음 원전)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        vm.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                when (vm.mode) {
                    AppMode.RECOMMEND -> vm.runRecommend()
                    AppMode.HANJA -> vm.runHanjaRecommend()
                    AppMode.EVALUATE -> vm.runEvaluate()
                }
            },
            enabled = !vm.busy && vm.hanjaDb != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    when (vm.mode) {
                        AppMode.RECOMMEND -> "이름 추천 받기"
                        AppMode.HANJA -> "한자 조합 찾기"
                        AppMode.EVALUATE -> "이름 감명 보기"
                    }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
