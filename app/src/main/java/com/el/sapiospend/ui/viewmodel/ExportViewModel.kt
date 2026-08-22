package com.el.sapiospend.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.el.sapiospend.export.BudgetReport
import com.el.sapiospend.export.ExportFormat
import com.el.sapiospend.export.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns report generation, separately from budget data.
 *
 * It lives in a ViewModel rather than a composable scope so a rotation mid-export does
 * not cancel the file write, and so a failure has somewhere to be reported from.
 */
class ExportViewModel(private val exportManager: ExportManager) : ViewModel() {

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _shareIntent = MutableStateFlow<Intent?>(null)
    val shareIntent: StateFlow<Intent?> = _shareIntent.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun export(report: BudgetReport, format: ExportFormat) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val uri = exportManager.export(report, format)
                _shareIntent.value = exportManager.shareIntent(uri, format, report.title)
            } catch (e: Exception) {
                // Storage can be full, or the report can be malformed. Either way the
                // user gets told rather than watching a spinner stop for no reason.
                _error.value = e.message ?: "Export failed"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun consumeShareIntent() {
        _shareIntent.value = null
    }

    fun consumeError() {
        _error.value = null
    }

    companion object {
        fun factory(exportManager: ExportManager): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportViewModel(exportManager) }
        }
    }
}
