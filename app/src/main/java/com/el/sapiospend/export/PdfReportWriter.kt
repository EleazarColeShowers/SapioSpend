package com.el.sapiospend.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatMoney
import com.el.sapiospend.util.formatPeriod
import java.io.OutputStream

/**
 * Draws a [BudgetReport] as an A4 PDF using the platform's PdfDocument — no third-party
 * PDF library, which keeps the APK small and avoids the licensing question around
 * iText-style dependencies in a commercial app.
 *
 * The layout is deliberately plain: this is a document a planner forwards to a client,
 * so legibility beats decoration.
 */
object PdfReportWriter {

    // A4 at 72dpi, the unit PdfDocument works in.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM_LIMIT = PAGE_HEIGHT - MARGIN

    private val titlePaint = paint(20f, bold = true)
    private val headingPaint = paint(13f, bold = true)
    private val labelPaint = paint(9f, color = Color.parseColor("#6B7280"))
    private val bodyPaint = paint(10f)
    private val bodyBoldPaint = paint(10f, bold = true)
    private val dangerPaint = paint(10f, color = Color.parseColor("#DC2626"))
    private val rulePaint = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 0.8f }

    // "1 expenses · 1 days tracked" on a document a planner forwards to a paying client
    // reads as sloppy, and this is the only place it appears.
    private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.parseColor("#111111")) =
        Paint().apply {
            this.color = color
            textSize = size
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    fun write(report: BudgetReport, out: OutputStream) {
        val document = PdfDocument()
        val cursor = Cursor(document)

        cursor.newPage()
        cursor.text("SapioSpend", titlePaint)
        cursor.text(report.title, headingPaint, gapBefore = 6f)
        cursor.text("Generated ${report.generatedAt.formatDate()}", labelPaint, gapBefore = 4f)
        cursor.rule(gapBefore = 12f)

        report.portfolio?.let { portfolio ->
            cursor.text("Portfolio Summary", headingPaint, gapBefore = 16f)
            cursor.keyValue("Total budget", portfolio.totalBudget.formatMoney())
            cursor.keyValue("Total spent", portfolio.totalSpent.formatMoney())
            cursor.keyValue("Remaining", portfolio.totalRemaining.formatMoney())
            cursor.keyValue("Events", "${portfolio.eventCount} (${portfolio.overBudgetCount} over budget)")
        }

        report.sections.forEach { section ->
            val a = section.analytics

            cursor.rule(gapBefore = 16f)
            cursor.text(a.eventName, headingPaint, gapBefore = 12f)
            cursor.text(
                "${a.eventType} · ${plural(a.expenseCount, "expense")} · ${plural(a.daysTracked, "day")} tracked",
                labelPaint,
                gapBefore = 4f
            )

            cursor.keyValue("Budget", a.budget.formatMoney(), gapBefore = 10f)
            cursor.keyValue("Planned", a.totalPlanned.formatMoney())
            cursor.keyValue("Spent", a.totalSpent.formatMoney())
            cursor.keyValue(
                "Remaining",
                a.remaining.formatMoney(),
                valuePaint = if (a.isOverBudget) dangerPaint else bodyBoldPaint
            )
            cursor.keyValue("Daily burn rate", a.dailyBurnRate.formatMoney())

            // Period figures only for a dated budget — see EventAnalytics.hasPeriod.
            formatPeriod(a.periodStart, a.periodEnd)?.let { cursor.keyValue("Period", it) }
            a.daysRemaining?.let { cursor.keyValue("Days remaining", "$it") }
            a.safeDailySpend?.let { cursor.keyValue("Safe daily spend", maxOf(it, 0.0).formatMoney()) }
            a.projectedTotalSpend?.let {
                cursor.keyValue(
                    "Projected at this pace",
                    it.formatMoney(),
                    valuePaint = if (a.projectedOverspend != null) dangerPaint else bodyBoldPaint
                )
            }

            if (a.categories.isNotEmpty()) {
                cursor.text("Planned vs Actual", bodyBoldPaint, gapBefore = 14f)
                cursor.tableRow(listOf("Category", "Planned", "Actual", "Variance"), labelPaint, gapBefore = 8f)
                cursor.rule(gapBefore = 3f)
                a.categories.forEach { category ->
                    cursor.tableRow(
                        listOf(
                            category.category,
                            category.planned.formatMoney(),
                            category.actual.formatMoney(),
                            category.variance.formatMoney()
                        ),
                        if (category.isOverPlan) dangerPaint else bodyPaint,
                        gapBefore = 6f
                    )
                }
            }

            if (section.expenses.isNotEmpty()) {
                cursor.text("Expenses", bodyBoldPaint, gapBefore = 16f)
                cursor.tableRow(listOf("Date", "Item", "Category", "Amount"), labelPaint, gapBefore = 8f)
                cursor.rule(gapBefore = 3f)
                section.expenses.forEach { expense ->
                    cursor.tableRow(
                        listOf(
                            expense.dateCreated.formatDate(),
                            expense.title,
                            expense.category,
                            expense.amount.formatMoney()
                        ),
                        bodyPaint,
                        gapBefore = 6f
                    )
                }
            }
        }

        cursor.finish()
        document.writeTo(out)
        document.close()
    }

    /**
     * Tracks the drawing position and starts a new page whenever the next line would run
     * past the bottom margin. Without this, a wedding with fifty expenses silently loses
     * everything below the first page.
     */
    private class Cursor(private val document: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = MARGIN
        private var pageNumber = 0

        fun newPage() {
            finish()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info).also { canvas = it.canvas }
            y = MARGIN
        }

        fun finish() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        private fun ensureSpace(needed: Float) {
            if (y + needed > BOTTOM_LIMIT) newPage()
        }

        fun text(value: String, paint: Paint, gapBefore: Float = 0f) {
            y += gapBefore
            ensureSpace(paint.textSize + 4f)
            y += paint.textSize
            canvas?.drawText(value, MARGIN, y, paint)
        }

        fun keyValue(label: String, value: String, gapBefore: Float = 6f, valuePaint: Paint = bodyBoldPaint) {
            y += gapBefore
            ensureSpace(bodyPaint.textSize + 4f)
            y += bodyPaint.textSize
            canvas?.drawText(label, MARGIN, y, labelPaint)
            canvas?.drawText(value, MARGIN + 140f, y, valuePaint)
        }

        fun tableRow(cells: List<String>, paint: Paint, gapBefore: Float = 6f) {
            y += gapBefore
            ensureSpace(paint.textSize + 4f)
            y += paint.textSize
            val columns = floatArrayOf(MARGIN, MARGIN + 200f, MARGIN + 320f, MARGIN + 430f)
            cells.forEachIndexed { index, cell ->
                if (index < columns.size) {
                    val maxWidth = if (index + 1 < columns.size) columns[index + 1] - columns[index] - 8f else 100f
                    canvas?.drawText(truncate(cell, paint, maxWidth), columns[index], y, paint)
                }
            }
        }

        fun rule(gapBefore: Float = 8f) {
            y += gapBefore
            ensureSpace(2f)
            canvas?.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
        }

        // Long expense titles would otherwise overlap the next column.
        private fun truncate(value: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(value) <= maxWidth) return value
            var end = value.length
            while (end > 1 && paint.measureText(value.take(end) + "…") > maxWidth) end--
            return value.take(end) + "…"
        }
    }
}
