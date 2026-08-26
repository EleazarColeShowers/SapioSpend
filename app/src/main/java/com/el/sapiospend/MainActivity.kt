package com.el.sapiospend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.el.sapiospend.billing.LocalEntitlements
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.export.ExportManager
import com.el.sapiospend.navigation.AppNavGraph
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.settings.SettingsRepository
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.ExportViewModel
import com.el.sapiospend.update.InAppUpdater
import com.el.sapiospend.update.UpdateStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val eventViewModel: EventViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = EventRepository(db.eventDao(), db.expenseDao(), db.budgetLineDao())
        EventViewModel.factory(repository, LocalEntitlements.create(applicationContext))
    }

    private val exportViewModel: ExportViewModel by viewModels {
        ExportViewModel.factory(ExportManager(applicationContext))
    }

    /**
     * Seeded into [ActiveCurrency] the moment it is built rather than collected into it,
     * so the very first frame already formats in the user's currency. A collector alone
     * would render one frame of naira before catching up, which on a cold start is a
     * visible flicker of the wrong symbol.
     */
    private val settingsRepository: SettingsRepository by lazy {
        SettingsRepository.create(applicationContext)
            .also { ActiveCurrency.value = it.currency.value }
    }

    // Constructed here rather than lazily: registering the update flow's result launcher
    // is only legal before the activity starts.
    private lateinit var inAppUpdater: InAppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inAppUpdater = InAppUpdater(this)

        lifecycleScope.launch { ExportManager(applicationContext).clearCache() }

        // Keeps the process-wide formatting currency in step with the stored setting for
        // the rest of the session; the initial value is already in place by construction.
        lifecycleScope.launch {
            settingsRepository.currency.collect { ActiveCurrency.value = it }
        }

        setContent {
            val navController = rememberNavController()
            val updateStatus by inAppUpdater.status.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            val restartMessage = stringResource(R.string.update_ready_message)
            val restartAction = stringResource(R.string.update_ready_action)
            LaunchedEffect(updateStatus) {
                if (updateStatus == UpdateStatus.ReadyToInstall) {
                    // Indefinite because installing restarts the app: the user has to be
                    // the one who decides they are at a safe point to lose the screen.
                    val result = snackbarHostState.showSnackbar(
                        message = restartMessage,
                        actionLabel = restartAction,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) inAppUpdater.completeUpdate()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                AppNavGraph(
                    navController = navController,
                    eventViewModel = eventViewModel,
                    exportViewModel = exportViewModel,
                    settingsRepository = settingsRepository
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }
}
