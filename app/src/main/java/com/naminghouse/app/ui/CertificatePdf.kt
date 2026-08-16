package com.naminghouse.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.naminghouse.app.R
import com.naminghouse.app.ui.theme.BrandLight
import com.naminghouse.app.ui.theme.LightInk
import com.naminghouse.engine.eval.AxisVerdict
import com.naminghouse.engine.eval.NameEvaluation
import com.naminghouse.engine.eval.meaningLine
import com.naminghouse.engine.eval.summarize
import com.naminghouse.engine.gen.NameStat
import com.naminghouse.engine.oheng.OhengRelation
import com.naminghouse.engine.saju.SajuSummary
import com.naminghouse.engine.suri.SuriMeaning
import com.samramanshang.manseryeok.orrery.model.Element

// ─────────────────────────────────────────────────────────────────────────────
// 프리미엄 감명서 PDF — 족자 표지 한 장 + 사주·성명학 풀이 본문.
//
// 본문은 비트맵이 아니라 번들 활자로 직접 그린다(벡터 글리프) — 인쇄에서
// 확대해도 뭉개지지 않고 파일도 작다. 표구가 어둡더라도 본문 종이는 항상
// 밝게 둔다: 증서의 본문 장은 읽는 종이지 감상하는 종이가 아니다.
// ─────────────────────────────────────────────────────────────────────────────

private const val PAGE_W = 595 // A4, 1/72 인치
private const val PAGE_H = 842
private const val MARGIN = 50f
private const val CONTENT_W = PAGE_W - MARGIN * 2
private const val CONTENT_BOTTOM = PAGE_H - 62f

// 인쇄용 색 — 값을 여기 다시 적지 않고 라이트 스킴·잉크 팔레트에서 가져온다.
// 예전에는 같은 16개 값을 손으로 베껴 뒀는데, 팔레트만 고치면 화면은 바뀌고
// PDF 는 그대로 남아 조용히 어긋나는 구조였다. 유료 산출물이라 더 위험했다.
// 증서는 종이에 인쇄되므로 다크 팔레트는 쓰지 않는다 — 항상 라이트다.
//
// 정확한 ARGB 정수는 CertificatePdfColorTest 가 못 박고 있다. 팔레트를 손보면
// 그 테스트가 먼저 깨져서, PDF 색이 함께 움직인다는 사실을 알려 준다.
internal val INK = BrandLight.onSurface.toArgb()
internal val MUTED = BrandLight.onSurfaceVariant.toArgb()
internal val RULE = BrandLight.outlineVariant.toArgb()
internal val GOLD = LightInk.gold.toArgb()
internal val SEAL = LightInk.seal.toArgb()
internal val ON_SEAL = LightInk.onSeal.toArgb()
internal val GIL = LightInk.gil.toArgb()
internal val BOTONG = LightInk.botong.toArgb()
internal val HYUNG = LightInk.hyung.toArgb()
internal val MALE = LightInk.male.toArgb()
internal val FEMALE = LightInk.female.toArgb()

/**
 * 본문 종이색만 팔레트에 대응하는 값이 없다. 화면의 한지(#F5EFE2)는 인쇄하면
 * 누렇게 뜨고, surfaceContainerLowest(#FEFBF4)는 종이에서 푸른 기가 돈다.
 */
internal val PAPER = 0xFFFDFAF2.toInt()

private fun elementColor(e: Element): Int = LightInk.of(e).toArgb()

private fun verdictInk(v: AxisVerdict): Int = when (v) {
    AxisVerdict.GIL -> GIL
    AxisVerdict.BOTONG -> BOTONG
    AxisVerdict.HYUNG -> HYUNG
}

/** 간지 한자 → 한글 독음 (丙午 → 병오) */
private val GANZI_HANGUL = mapOf(
    '甲' to "갑", '乙' to "을", '丙' to "병", '丁' to "정", '戊' to "무",
    '己' to "기", '庚' to "경", '辛' to "신", '壬' to "임", '癸' to "계",
    '子' to "자", '丑' to "축", '寅' to "인", '卯' to "묘", '辰' to "진", '巳' to "사",
    '午' to "오", '未' to "미", '申' to "신", '酉' to "유", '戌' to "술", '亥' to "해",
)

private fun ganziHangul(ganzi: String): String =
    ganzi.map { GANZI_HANGUL[it] ?: it.toString() }.joinToString("")

private fun isCjk(c: Char): Boolean =
    c.code in 0x3400..0x9FFF || c.code in 0xF900..0xFAFF

/** 번들 활자 — 한글(마루부리·프리텐다드)과 한자(명조)를 나눠 싣는다. */
private class CertFonts(private val appContext: Context) {
    private fun load(id: Int): Typeface? = runCatching {
        ResourcesCompat.getFont(appContext, id)
    }.getOrNull()

    val maruBold: Typeface = load(R.font.maruburi_bold) ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val body: Typeface = load(R.font.pretendard_regular) ?: Typeface.SANS_SERIF
    val bodyMedium: Typeface = load(R.font.pretendard_medium) ?: body
    val bodyBold: Typeface = load(R.font.pretendard_bold) ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val hanja: Typeface = load(R.font.hanja_serif) ?: Typeface.SERIF
}

/** 한 조각의 글 — 색·굵기를 따로 주고 싶은 단위. 한자는 자동으로 한자 명조로 그려진다. */
private class Run(val text: String, val color: Int = INK, val bold: Boolean = false)

/**
 * 페이지 커서를 들고 다니는 증서 작성기.
 * 블록을 그리기 전에 [ensure] 로 남은 높이를 확인하고 모자라면 장을 넘긴다.
 */
private class CertWriter(
    private val doc: PdfDocument,
    private val fonts: CertFonts,
    private val headerLine: String,
) {
    private var page: PdfDocument.Page = doc.startPage(
        PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
    )
    val canvas: Canvas get() = page.canvas
    var y = 0f
    private var pageNo = 1

    // init 의 decoratePage 가 쓰므로 반드시 init 보다 먼저 선언돼야 한다.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        decoratePage()
        y = 66f
    }

    private fun textPaint(size: Float, color: Int, tf: Typeface): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = tf
        }

    /** 종이색과 증서 테두리(겹줄) — 모든 장에 같은 틀을 두른다. */
    private fun decoratePage() {
        fill.color = PAPER
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), PAGE_H.toFloat(), fill)
        strokePaint.color = GOLD
        strokePaint.strokeWidth = 1.1f
        canvas.drawRect(22f, 22f, PAGE_W - 22f, PAGE_H - 22f, strokePaint)
        strokePaint.strokeWidth = 0.5f
        canvas.drawRect(27f, 27f, PAGE_W - 27f, PAGE_H - 27f, strokePaint)

        // 바닥글 — 가운데 상호, 오른쪽 장 번호
        val foot = textPaint(7f, MUTED, fonts.body)
        val label = "작명하우스 · 사주와 성명학 감명서"
        canvas.drawText(label, (PAGE_W - foot.measureText(label)) / 2f, PAGE_H - 34f, foot)
        val no = "$pageNo"
        canvas.drawText(no, PAGE_W - MARGIN - foot.measureText(no), PAGE_H - 34f, foot)

        if (pageNo > 1) {
            drawRuns(listOf(Run(headerLine, MUTED)), MARGIN, 44f, 7f)
            // 머리글 밑줄
            strokePaint.color = RULE
            strokePaint.strokeWidth = 0.6f
            canvas.drawLine(MARGIN, 50f, PAGE_W - MARGIN, 50f, strokePaint)
        }
    }

    fun newPage() {
        doc.finishPage(page)
        pageNo += 1
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        decoratePage()
        y = 66f
    }

    fun finish() {
        doc.finishPage(page)
    }

    fun ensure(height: Float) {
        if (y + height > CONTENT_BOTTOM) newPage()
    }

    fun space(h: Float) {
        y += h
    }

    // ── 글 그리기 ────────────────────────────────────────────────────────────

    /** 혼합 활자 한 줄 — 한자 글리프만 한자 명조로 바꿔 이어 그린다. 끝 x 를 돌려준다. */
    fun drawRuns(runs: List<Run>, x: Float, baseline: Float, size: Float): Float {
        var cx = x
        for (run in runs) {
            val base = textPaint(size, run.color, if (run.bold) fonts.bodyBold else fonts.body)
            val hanja = textPaint(size, run.color, fonts.hanja)
            var i = 0
            val t = run.text
            while (i < t.length) {
                val cjk = isCjk(t[i])
                var j = i + 1
                while (j < t.length && isCjk(t[j]) == cjk) j++
                val piece = t.substring(i, j)
                val p = if (cjk) hanja else base
                canvas.drawText(piece, cx, baseline, p)
                cx += p.measureText(piece)
                i = j
            }
        }
        return cx
    }

    fun measureRuns(runs: List<Run>, size: Float): Float {
        var w = 0f
        for (run in runs) {
            val base = textPaint(size, run.color, if (run.bold) fonts.bodyBold else fonts.body)
            val hanja = textPaint(size, run.color, fonts.hanja)
            var i = 0
            val t = run.text
            while (i < t.length) {
                val cjk = isCjk(t[i])
                var j = i + 1
                while (j < t.length && isCjk(t[j]) == cjk) j++
                w += (if (cjk) hanja else base).measureText(t.substring(i, j))
                i = j
            }
        }
        return w
    }

    /** 혼합 활자 한 줄을 커서 위치에 얹고 줄 높이만큼 내린다. */
    fun runsLine(runs: List<Run>, size: Float, indent: Float = 0f, spacingAfter: Float = 4f) {
        val lineH = size * 1.45f
        ensure(lineH + spacingAfter)
        drawRuns(runs, MARGIN + indent, y + size, size)
        y += lineH + spacingAfter
    }

    /** 접어 흐르는 문단 — 드문 한자는 시스템 글꼴 폴백에 맡긴다. */
    fun paragraph(
        text: String,
        size: Float = 9f,
        color: Int = INK,
        tf: Typeface = fonts.body,
        indent: Float = 0f,
        spacingAfter: Float = 6f,
    ) {
        if (text.isBlank()) return
        val paint = textPaint(size, color, tf)
        val width = (CONTENT_W - indent).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.32f)
            .build()
        ensure(layout.height + spacingAfter)
        canvas.save()
        canvas.translate(MARGIN + indent, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + spacingAfter
    }

    /** 마디 제목 — 왼쪽 금줄 표식과 마루부리 볼드. 제목만 남기고 장이 끝나지 않게 여유를 요구한다. */
    fun sectionTitle(title: String, minBlock: Float = 46f) {
        ensure(26f + minBlock)
        space(6f)
        fill.color = GOLD
        canvas.drawRoundRect(RectF(MARGIN, y + 2.5f, MARGIN + 2.6f, y + 13.5f), 1.3f, 1.3f, fill)
        val p = textPaint(12f, INK, fonts.maruBold)
        canvas.drawText(title, MARGIN + 9f, y + 12f, p)
        y += 22f
    }

    fun divider(spacingAfter: Float = 8f) {
        ensure(2f + spacingAfter)
        strokePaint.color = RULE
        strokePaint.strokeWidth = 0.7f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, strokePaint)
        y += 1f + spacingAfter
    }

    fun roundBox(x: Float, top: Float, w: Float, h: Float, strokeColor: Int = RULE) {
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(RectF(x, top, x + w, top + h), 6f, 6f, strokePaint)
    }

    fun fillRounded(rect: RectF, color: Int, radius: Float) {
        fill.color = color
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    fun textCentered(text: String, cx: Float, baseline: Float, size: Float, color: Int, tf: Typeface) {
        val p = textPaint(size, color, tf)
        canvas.drawText(text, cx - p.measureText(text) / 2f, baseline, p)
    }

    fun font(): CertFonts = fonts
}

/**
 * 감명서 PDF 본체 — 표지(족자·기본 정보) + 풀이 본문을 그려 [uri] 로 쓴다.
 *
 * @param jokja 화면에서 3배로 캡처한 족자 (GPU 비트맵일 수 있어 여기서 소프트웨어로 복사)
 * @param birthLine "남아 · 양력 2026년 8월 11일 12시 00분 · 부산" — 출생 전 작명이면 null
 */
fun writeCertificatePdf(
    context: Context,
    uri: Uri,
    jokja: Bitmap,
    eval: NameEvaluation,
    saju: SajuSummary?,
    stat: NameStat?,
    birthLine: String?,
    issuedDate: String,
) {
    val soft = if (jokja.config == Bitmap.Config.HARDWARE) {
        jokja.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        jokja
    }
    val fullHangul = eval.surname + eval.givenName
    val fullHanja = (eval.surnameHanja + eval.givenHanja).joinToString("") { it.char.toString() }

    val doc = PdfDocument()
    try {
        val w = CertWriter(doc, CertFonts(context), "감명서 — $fullHangul ($fullHanja)")

        coverPage(w, soft, eval, fullHangul, fullHanja, birthLine, issuedDate)

        w.newPage()
        summarySection(w, eval)
        saju?.let { sajuSection(w, it) }
        birthSignSection(w, saju)
        stat?.let { statSection(w, it) }
        suriSection(w, eval)
        baleumSection(w, eval)
        suriOhengSection(w, eval)
        eumyangSection(w, eval)
        jawonSection(w, eval, saju)
        bulyongSection(w, eval)
        letterSection(w, eval)
        closing(w, issuedDate)

        w.finish()
        context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            ?: error("출력 스트림을 열지 못했습니다")
    } finally {
        // close 가 던지면 원래 예외를 가린다 — 정리 실패는 삼킨다.
        runCatching { doc.close() }
    }
}

// ── 표지 ─────────────────────────────────────────────────────────────────────

private fun coverPage(
    w: CertWriter,
    jokja: Bitmap,
    eval: NameEvaluation,
    fullHangul: String,
    fullHanja: String,
    birthLine: String?,
    issuedDate: String,
) {
    val fonts = w.font()
    w.y = 84f
    w.textCentered("감  명  서", PAGE_W / 2f, w.y, 24f, INK, fonts.maruBold)
    w.y += 16f
    w.textCentered("鑑 名 書 · 작명하우스", PAGE_W / 2f, w.y, 8.5f, MUTED, fonts.hanja)
    w.y += 22f

    // 족자 — 표지의 주인공. 남는 폭을 종이 여백으로 남긴다.
    val maxW = 400f
    val maxH = 430f
    val fit = minOf(maxW / jokja.width, maxH / jokja.height)
    val iw = jokja.width * fit
    val ih = jokja.height * fit
    val left = (PAGE_W - iw) / 2f
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    w.canvas.drawBitmap(jokja, null, RectF(left, w.y, left + iw, w.y + ih), paint)
    w.y += ih + 26f

    // 기본 정보 — 증서의 신원 칸
    val rows = buildList {
        add("이름" to listOf(Run(fullHangul, INK, bold = true), Run("  $fullHanja", MUTED)))
        add("뜻" to listOf(Run(meaningLine(eval), INK)))
        birthLine?.let { add("사주 기준" to listOf(Run(it, INK))) }
            ?: add("사주 기준" to listOf(Run("출생 전 작명 — 사주 없이 성명학 축으로만 평가", MUTED)))
        add("종합 감명" to listOf(Run("${eval.score}점 · ${eval.grade}", GOLD, bold = true)))
        add("발급" to listOf(Run("$issuedDate · 작명하우스", INK)))
    }
    val boxTop = w.y
    val boxH = rows.size * 17.5f + 22f
    w.roundBox(MARGIN + 24f, boxTop, CONTENT_W - 48f, boxH)
    w.y = boxTop + 15f
    rows.forEach { (label, value) ->
        val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f; color = MUTED; typeface = fonts.bodyMedium
        }
        w.canvas.drawText(label, MARGIN + 40f, w.y + 8.5f, lp)
        w.drawRuns(value, MARGIN + 108f, w.y + 8.5f, 9.5f)
        w.y += 17.5f
    }
}

// ── 본문 마디들 ───────────────────────────────────────────────────────────────

private fun verdictRuns(verdict: AxisVerdict): List<Run> = listOf(
    Run("판정 ", MUTED),
    Run(verdict.label, verdictInk(verdict), bold = true),
)

private fun summarySection(w: CertWriter, eval: NameEvaluation) {
    val s = summarize(eval)
    w.sectionTitle("총평")
    w.paragraph(s.verdict, size = 9.5f)
    s.strengths.forEach { w.runsLine(listOf(Run("✓  ", GIL, bold = true), Run(it)), 9f, indent = 2f, spacingAfter = 1.5f) }
    s.cautions.forEach { w.runsLine(listOf(Run("!  ", HYUNG, bold = true), Run(it)), 9f, indent = 2f, spacingAfter = 1.5f) }
    s.suggestions.forEach { w.runsLine(listOf(Run("→  ", BOTONG, bold = true), Run(it)), 9f, indent = 2f, spacingAfter = 1.5f) }
    w.space(4f)
}

private fun sajuSection(w: CertWriter, saju: SajuSummary) {
    w.sectionTitle("사주", minBlock = 120f)

    // 4주 표 — [년·월·일·시]
    val labels = listOf("년주", "월주", "일주", "시주")
    val gap = 8f
    val cellW = (CONTENT_W - gap * 3) / 4f
    val cellH = 46f
    val top = w.y
    val fonts = w.font()
    saju.ganzis.forEachIndexed { i, ganzi ->
        val x = MARGIN + i * (cellW + gap)
        w.textCentered(labels[i], x + cellW / 2f, top + 8f, 7.5f, MUTED, fonts.bodyMedium)
        w.roundBox(x, top + 12f, cellW, cellH)
        val unknown = i == 3 && saju.unknownTime
        if (unknown) {
            w.textCentered("모름", x + cellW / 2f, top + 12f + 27f, 11f, MUTED, fonts.body)
        } else {
            w.textCentered(ganzi, x + cellW / 2f, top + 12f + 24f, 15f, INK, fonts.hanja)
            w.textCentered(ganziHangul(ganzi), x + cellW / 2f, top + 12f + 38f, 7.5f, MUTED, fonts.body)
        }
    }
    w.y = top + 12f + cellH + 10f

    val strength = when {
        saju.isNeutral -> "중화"
        saju.isStrong -> "신강"
        else -> "신약"
    }
    w.runsLine(
        listOf(
            Run("일간 ", MUTED),
            Run("${saju.dayStem}(${saju.dayElement.hanja})", INK, bold = true),
            Run("   $strength   ", INK),
            Run("용신 ", MUTED),
            Run(saju.yongsin.joinToString("·") { it.hanja }, INK, bold = true),
        ),
        9.5f,
    )
    w.runsLine(
        listOf(
            Run("이름으로 보완하면 좋은 오행  ", MUTED),
            Run(saju.targetElements.joinToString(" · ") { "${it.hanja}(${it.ko})" }, GOLD, bold = true),
        ),
        9.5f,
        spacingAfter = 8f,
    )

    ohengChart(w, saju.simpleCounts, emptyMap())

    if (saju.unknownTime) {
        w.paragraph("출생 시간 미상 — 시주를 제외하고 분석했습니다.", size = 7.5f, color = MUTED)
    }
    if (saju.input.useTrueSolarTime) {
        w.paragraph(
            "출생지 경도 기준 진태양시 보정을 적용해, 표준시 만세력과 시주가 다를 수 있습니다.",
            size = 7.5f, color = MUTED,
        )
    }
    w.space(2f)
}

/** 오행 분포 — 다섯 기둥 막대. 이름 자원오행은 옅은 칸으로 위에 얹는다. */
private fun ohengChart(w: CertWriter, sajuCounts: Map<Element, Int>, nameCounts: Map<Element, Int>) {
    val groupW = 52f
    val chartH = 46f
    val labelH = 22f
    w.ensure(chartH + labelH + 8f)
    val startX = MARGIN + (CONTENT_W - groupW * 5) / 2f
    val baseY = w.y + chartH
    val maxCount = Element.entries.maxOf { (sajuCounts[it] ?: 0) + (nameCounts[it] ?: 0) }.coerceAtLeast(1)
    val fonts = w.font()

    Element.entries.forEachIndexed { i, el ->
        val cx = startX + i * groupW + groupW / 2f
        val sajuN = sajuCounts[el] ?: 0
        val nameN = nameCounts[el] ?: 0
        val unit = chartH / maxCount
        val barW = 20f
        val color = elementColor(el)
        if (sajuN > 0) {
            w.fillRounded(RectF(cx - barW / 2f, baseY - sajuN * unit, cx + barW / 2f, baseY), color, 2.5f)
        }
        if (nameN > 0) {
            val bottom = baseY - sajuN * unit
            w.fillRounded(
                RectF(cx - barW / 2f, bottom - nameN * unit, cx + barW / 2f, bottom - 1f),
                (color and 0x00FFFFFF) or 0x59000000, 2.5f,
            )
        }
        val total = sajuN + nameN
        w.textCentered(if (total > 0) "$total" else "0", cx, baseY - total * unit - 3f, 7.5f,
            if (total > 0) INK else MUTED, fonts.bodyMedium)
        w.textCentered(el.hanja, cx, baseY + 11f, 9f, color, fonts.hanja)
        w.textCentered("(${el.ko})", cx, baseY + 20f, 6.5f, MUTED, fonts.body)
    }
    w.y = baseY + labelH + 4f
    if (nameCounts.isNotEmpty()) {
        w.paragraph("진한 칸이 사주, 옅은 칸이 이름 한자의 자원오행입니다.", size = 7.5f, color = MUTED)
    }
}

private fun birthSignSection(w: CertWriter, saju: SajuSummary?) {
    val ttii = saju?.ttii
    val star = saju?.starSign
    if (ttii == null && star == null) return
    w.sectionTitle("띠 · 별자리")
    ttii?.let {
        w.runsLine(listOf(Run("${it.name}  ", INK, bold = true), Run("${it.branchKo}(${it.branch})", MUTED)), 9.5f, spacingAfter = 1f)
        w.paragraph(it.traits, size = 8.5f, color = MUTED)
    }
    star?.let {
        w.runsLine(listOf(Run("${it.name}  ", INK, bold = true), Run(it.period, MUTED)), 9.5f, spacingAfter = 1f)
        w.paragraph(it.traits, size = 8.5f, color = MUTED)
    }
    w.paragraph("띠는 양력 1월 1일이 아니라 입춘을 해의 경계로 봅니다.", size = 7.5f, color = MUTED)
    w.space(2f)
}

private fun statSection(w: CertWriter, stat: NameStat) {
    w.sectionTitle("이름 통계 — 대법원 출생신고", minBlock = 70f)
    if (stat.ranks.isNotEmpty()) {
        val runs = mutableListOf<Run>(Run("최근 순위  ", MUTED))
        stat.ranks.take(6).forEachIndexed { i, (year, rank) ->
            if (i > 0) runs.add(Run("  ·  ", RULE))
            runs.add(Run("${year}년 ", MUTED))
            runs.add(Run("${rank}위", INK, bold = true))
        }
        w.runsLine(runs, 9f, spacingAfter = 6f)
    } else {
        w.paragraph("최근 상위권에 든 기록이 없는, 흔하지 않은 이름입니다.", size = 9f)
    }

    // 남녀 비율 — 한 줄 막대
    val barH = 13f
    w.ensure(barH + 22f)
    val malePct = stat.malePercent
    val top = w.y
    val maleW = CONTENT_W * malePct / 100f
    if (malePct > 0) w.fillRounded(RectF(MARGIN, top, MARGIN + maleW, top + barH), MALE, 6.5f)
    if (malePct < 100) w.fillRounded(RectF(MARGIN + maleW, top, MARGIN + CONTENT_W, top + barH), FEMALE, 6.5f)
    // 이음새를 직각으로 — 둥근 끝은 양 바깥만 남긴다.
    if (malePct in 1..99) {
        w.fillRounded(RectF(MARGIN + maleW - 6f, top, MARGIN + maleW, top + barH), MALE, 0f)
        w.fillRounded(RectF(MARGIN + maleW, top, MARGIN + maleW + 6f, top + barH), FEMALE, 0f)
    }
    val fonts = w.font()
    if (malePct >= 12) w.textCentered("남 $malePct%", MARGIN + maleW / 2f, top + 9.5f, 7f, ON_SEAL, fonts.bodyMedium)
    if (100 - malePct >= 12) {
        w.textCentered("여 ${100 - malePct}%", MARGIN + maleW + (CONTENT_W - maleW) / 2f, top + 9.5f, 7f, ON_SEAL, fonts.bodyMedium)
    }
    w.y = top + barH + 6f
    w.paragraph(
        "2008년 이후 출생신고 ${stat.total}건 기준 · 대법원 전자가족관계등록시스템 통계",
        size = 7.5f, color = MUTED,
    )
    w.space(2f)
}

private fun suriSection(w: CertWriter, eval: NameEvaluation) {
    w.sectionTitle("수리사격 — 원형이정", minBlock = 90f)
    w.paragraph(
        "성과 이름의 획수를 네 가지로 조합해(원격·형격·이격·정격) 초년·청년·중년·총운을 " +
            "보는 이론입니다. 각 격의 수를 81수리 길흉표에 대조해 판정합니다.",
        size = 8f, color = MUTED, spacingAfter = 8f,
    )
    suriRow(w, "원격 · 초년", eval.suri.won)
    suriRow(w, "형격 · 청년", eval.suri.hyeong)
    suriRow(w, "이격 · 중년", eval.suri.i)
    suriRow(w, "정격 · 총운", eval.suri.jeong)
    w.runsLine(verdictRuns(eval.suriVerdict), 9.5f, spacingAfter = 8f)
}

private fun suriRow(w: CertWriter, label: String, m: SuriMeaning) {
    w.ensure(34f)
    val fonts = w.font()
    val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = MUTED; typeface = fonts.bodyMedium }
    w.canvas.drawText(label, MARGIN, w.y + 9.5f, lp)
    val end = w.drawRuns(
        listOf(Run("${m.number}수 ", INK, bold = true), Run(m.title, INK, bold = true)),
        MARGIN + 78f, w.y + 9.5f, 9.5f,
    )
    w.drawRuns(
        listOf(Run("  ${m.grade.label}", if (m.grade.isGood) GIL else HYUNG, bold = true)),
        end, w.y + 9.5f, 9.5f,
    )
    w.y += 14f
    w.paragraph(m.description, size = 8f, color = MUTED, indent = 78f, spacingAfter = 5f)
}

/** 오행 배열 한 줄 — "김(木) →생 대(火) …" 처럼 관계 기호를 색으로 끼워 그린다. */
private fun arrangementRuns(
    items: List<Pair<String, Element?>>,
    relations: List<OhengRelation>,
): List<Run> {
    val runs = mutableListOf<Run>()
    items.forEachIndexed { i, (label, el) ->
        // 관계 기호가 없는 나열(자원오행)은 가운뎃점으로 글자를 띄운다.
        if (i > 0 && relations.isEmpty()) runs.add(Run("  ·  ", RULE))
        runs.add(Run(label, INK, bold = true))
        runs.add(Run("(${el?.hanja ?: "?"})", el?.let(::elementColor) ?: MUTED, bold = true))
        if (i < relations.size) {
            val r = relations[i]
            val (mark, color) = when (r) {
                OhengRelation.SANGSAENG -> "  →생  " to GIL
                OhengRelation.BIHWA -> "  =비  " to MUTED
                OhengRelation.SANGGEUK -> "  ×극  " to HYUNG
            }
            runs.add(Run(mark, color, bold = true))
        }
    }
    return runs
}

private fun baleumSection(w: CertWriter, eval: NameEvaluation) {
    w.sectionTitle("발음오행")
    val baleum = eval.baleum
    if (baleum == null) {
        w.paragraph("판정 불가(한글 이름 아님)", size = 9f)
        return
    }
    val full = eval.surname + eval.givenName
    val items = full.mapIndexed { i, ch -> ch.toString() to baleum.elements.getOrNull(i) }
    w.runsLine(arrangementRuns(items, baleum.relations), 10.5f, spacingAfter = 3f)
    w.runsLine(verdictRuns(eval.baleumVerdict), 9.5f, spacingAfter = 2f)
    w.paragraph(
        "이름 소리의 첫소리(자음)를 오행에 배속해, 이웃 글자끼리 살리는 관계(상생)인지 누르는 관계(상극)인지 봅니다.",
        size = 7.5f, color = MUTED,
    )
    w.space(2f)
}

private fun suriOhengSection(w: CertWriter, eval: NameEvaluation) {
    w.sectionTitle("수리오행")
    val strokes = (eval.surnameHanja + eval.givenHanja).map { it.wonhoek }
    val items = eval.suriOheng.elements.mapIndexed { i, el -> "${strokes.getOrNull(i) ?: ""}" to el }
    w.runsLine(arrangementRuns(items, eval.suriOheng.relations), 10.5f, spacingAfter = 3f)
    w.runsLine(verdictRuns(eval.suriOhengVerdict), 9.5f, spacingAfter = 2f)
    w.paragraph(
        "글자 획수의 끝자리를 오행으로 환산해 배열을 봅니다 (1·2 木, 3·4 火, 5·6 土, 7·8 金, 9·0 水).",
        size = 7.5f, color = MUTED,
    )
    w.space(2f)
}

private fun eumyangSection(w: CertWriter, eval: NameEvaluation) {
    w.sectionTitle("음양 배열")
    w.runsLine(
        listOf(
            Run("수리음양  ", MUTED),
            Run(eval.strokeEumyang.display, INK, bold = true),
            Run(if (eval.strokeEumyang.isBalanced) "  조화" else "  순음/순양",
                if (eval.strokeEumyang.isBalanced) GIL else HYUNG),
        ),
        9.5f, spacingAfter = 1.5f,
    )
    eval.soundEumyang?.let {
        w.runsLine(
            listOf(
                Run("발음음양  ", MUTED),
                Run(it.display, INK, bold = true),
                Run(if (it.isBalanced) "  조화" else "  편중", if (it.isBalanced) GIL else HYUNG),
            ),
            9.5f, spacingAfter = 1.5f,
        )
    }
    w.runsLine(verdictRuns(eval.eumyangVerdict), 9.5f, spacingAfter = 2f)
    w.paragraph(
        "획수의 홀짝(수리음양)과 모음의 양성·음성(발음음양)이 한쪽으로 쏠리지 않았는지 봅니다.",
        size = 7.5f, color = MUTED,
    )
    w.space(2f)
}

private fun jawonSection(w: CertWriter, eval: NameEvaluation, saju: SajuSummary?) {
    w.sectionTitle(if (saju != null) "자원오행 · 사주 보완" else "자원오행", minBlock = 80f)
    val items = eval.givenHanja.map { it.char.toString() to it.element }
    w.runsLine(arrangementRuns(items, emptyList()), 10.5f, spacingAfter = 5f)

    if (saju != null) {
        val nameCounts = eval.jawonElements.filterNotNull().groupingBy { it }.eachCount()
        ohengChart(w, saju.simpleCounts, nameCounts)
    }
    eval.sajuFit?.let { fit ->
        w.runsLine(
            listOf(
                Run("보완 대상 오행  ", MUTED),
                Run(fit.targets.joinToString(" · ") { "${it.hanja}(${it.ko})" }, INK),
            ),
            9f, spacingAfter = 1.5f,
        )
        if (fit.matched.isNotEmpty()) {
            w.runsLine(
                listOf(
                    Run("이름이 채워주는 오행  ", MUTED),
                    Run(fit.matched.joinToString(" · ") { "${it.hanja}(${it.ko})" }, GIL, bold = true),
                ),
                9f, spacingAfter = 1.5f,
            )
        }
        if (fit.gisinUsed.isNotEmpty()) {
            w.runsLine(
                listOf(
                    Run("주의 · 기신 오행 사용  ", MUTED),
                    Run(fit.gisinUsed.joinToString(" · ") { "${it.hanja}(${it.ko})" }, HYUNG, bold = true),
                ),
                9f, spacingAfter = 1.5f,
            )
        }
    }
    w.runsLine(verdictRuns(eval.jawonVerdict), 9.5f, spacingAfter = 2f)
    w.paragraph(
        "한자마다 부수와 뜻에서 오는 고유 오행(자원오행)이 있습니다. 사주에 부족하거나 필요한 " +
            "기운(용신)을 이름 한자가 채워 주는지를 봅니다 — 사주 기반 작명의 핵심 축입니다.",
        size = 7.5f, color = MUTED,
    )
    w.space(2f)
}

private fun bulyongSection(w: CertWriter, eval: NameEvaluation) {
    if (eval.bulyongWarnings.isEmpty()) return
    w.sectionTitle("불용한자 참고")
    eval.bulyongWarnings.forEach { (ch, info) ->
        w.runsLine(
            listOf(Run("$ch  ", HYUNG, bold = true), Run("[${info.category}] ${info.reason}", INK)),
            9f, spacingAfter = 1.5f,
        )
    }
    w.paragraph("불용한자는 전통 속설로, 학파에 따라 이견이 있습니다.", size = 7.5f, color = MUTED)
    w.space(2f)
}

private fun letterSection(w: CertWriter, eval: NameEvaluation) {
    w.sectionTitle("글자 풀이", minBlock = 60f)
    val fonts = w.font()
    (eval.surnameHanja + eval.givenHanja).forEachIndexed { i, h ->
        val role = if (i < eval.surnameHanja.size) "성" else "이름"
        w.ensure(34f)
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; color = INK; typeface = fonts.hanja }
        w.canvas.drawText(h.char.toString(), MARGIN + 2f, w.y + 20f, big)
        w.drawRuns(listOf(Run(h.meaning.ifEmpty { "-" }, INK, bold = true)), MARGIN + 38f, w.y + 9f, 9.5f)
        w.drawRuns(
            listOf(
                Run("$role · 원획 ${h.wonhoek} · 필획 ${h.pilhoek}", MUTED),
                h.element?.let { Run(" · 자원오행 ${it.hanja}(${it.ko})", elementColor(it)) } ?: Run("", MUTED),
            ),
            MARGIN + 38f, w.y + 22f, 8f,
        )
        w.y += 31f
    }
}

private fun closing(w: CertWriter, issuedDate: String) {
    w.ensure(72f)
    w.space(8f)
    w.divider(spacingAfter = 12f)
    val fonts = w.font()
    w.paragraph(
        "이 감명서는 작명하우스가 사주와 성명학 여덟 축(수리사격·발음오행·수리오행·음양·자원오행·" +
            "사주보완·불용한자·통계)으로 평가한 결과이며, 발급일 기준의 분석입니다.",
        size = 8f, color = MUTED, spacingAfter = 14f,
    )
    // 맺음 낙관 — 붉은 인장 하나로 끝을 맺는다.
    w.ensure(44f)
    val sealSize = 34f
    val x = PAGE_W - MARGIN - sealSize
    val top = w.y
    w.fillRounded(RectF(x, top, x + sealSize, top + sealSize), SEAL, 4f)
    w.textCentered("名", x + sealSize / 2f, top + sealSize / 2f + 6f, 17f, ON_SEAL, fonts.hanja)
    val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = INK; typeface = fonts.maruBold }
    w.canvas.drawText("작명하우스", x - 8f - lp.measureText("작명하우스"), top + sealSize / 2f + 3f, lp)
    val dp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = MUTED; typeface = fonts.body }
    w.canvas.drawText(issuedDate, x - 8f - dp.measureText(issuedDate), top + sealSize / 2f + 14f, dp)
    w.y = top + sealSize + 8f
}
