package com.el.sapiospend.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel")
}

/**
 * Writes a report to a file the system share sheet can hand to other apps.
 *
 * Files go to cacheDir rather than external storage: no runtime permission is needed,
 * and Android is free to reclaim the space once the file has been shared. A FileProvider
 * grants temporary read access, which is the only legal way to hand a file URI to
 * another app on modern Android.
 */
class ExportManager(private val context: Context) {

    suspend fun export(report: BudgetReport, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val file = File(directory, fileName(report, format))

        file.outputStream().use { stream ->
            when (format) {
                ExportFormat.PDF -> PdfReportWriter.write(report, stream)
                ExportFormat.EXCEL -> XlsxReportWriter.write(report, stream)
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri, format: ExportFormat, subject: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share $subject")
    }

    /** Deletes previously generated exports; safe to call on app start. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        File(context.cacheDir, EXPORT_DIR).listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun fileName(report: BudgetReport, format: ExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(report.generatedAt))
        // Anything that is not a letter, digit, dash or underscore becomes a dash, so an
        // event called "Tolu & Ada / Reception" cannot escape the export directory.
        val safeTitle = report.title
            .replace(Regex("[^A-Za-z0-9-_]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "report" }
        return "SapioSpend-$safeTitle-$stamp.${format.extension}"
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
