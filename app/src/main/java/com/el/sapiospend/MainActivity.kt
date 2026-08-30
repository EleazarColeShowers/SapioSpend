package com.el.sapiospend

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.el.sapiospend.notify.AndroidBudgetAlertPublisher
import com.el.sapiospend.notify.NotificationChannels
import com.el.sapiospend.notify.NotificationScheduler
import com.el.sapiospend.notify.Notifier
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
        EventViewModel.factory(
            repository,
            LocalEntitlements.create(applicationContext),
            AndroidBudgetAlertPublisher(applicationContext)
        )
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

    /**
     * The event a notification was tapped on, waiting for the nav graph to exist.
     *
     * Compose state rather than a plain field so setting it from [onNewIntent] — which
     * arrives long after composition — actually moves the screen.
     */
    private var pendingEventId by mutableStateOf<String?>(null)

    /**
     * Whether a notification would currently be delivered, for Settings to explain itself
     * with. Kept as state here rather than read inside the screen because it changes
     * outside composition — in the system dialog, and in the settings app — and a value
     * read once at composition would leave the explanation stale.
     */
    private var notificationsAllowed by mutableStateOf(true)

    /**
     * Denying is the user's answer and gets no follow-up dialog — that pattern is exactly
     * what gets an app's notifications blocked for good. The result only refreshes what
     * Settings shows and puts the alarm in place once permission exists.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshNotificationState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inAppUpdater = InAppUpdater(this)

        // Channels must exist before the first notification is posted, and creating one
        // that already exists is a no-op, so every launch is the simplest correct place.
        NotificationChannels.ensure(applicationContext)
        pendingEventId = intent?.getStringExtra(EXTRA_EVENT_ID)

        lifecycleScope.launch { ExportManager(applicationContext).clearCache() }

        // Keeps the process-wide formatting currency in step with the stored setting for
        // the rest of the session; the initial value is already in place by construction.
        lifecycleScope.launch {
            settingsRepository.currency.collect { ActiveCurrency.value = it }
        }

        // Every change to the notification preferences moves the alarm — a new hour, or
        // no alarm at all once the last scheduled notification is switched off.
        lifecycleScope.launch {
            settingsRepository.notifications.collect {
                NotificationScheduler(applicationContext).sync(it)
            }
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
                    settingsRepository = settingsRepository,
                    openEventId = pendingEventId,
                    onEventOpened = { pendingEventId = null },
                    notificationsAllowed = notificationsAllowed,
                    onRequestNotificationPermission = ::requestNotificationPermission
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

    /**
     * singleTop means a notification tapped while the app is already open is delivered
     * here instead of rebuilding the activity, so the new event id has to be picked up
     * explicitly — otherwise the tap would just bring the old screen forward.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_EVENT_ID)?.let { pendingEventId = it }
    }

    /**
     * Notification permission and the per-channel switches live in system settings, where
     * the user can change them behind the app's back. Re-syncing on every resume is what
     * turns "they granted it from the settings app" into an alarm that actually exists.
     */
    override fun onResume() {
        super.onResume()
        refreshNotificationState()
    }

    private fun refreshNotificationState() {
        notificationsAllowed = Notifier(applicationContext).canPost()
        NotificationScheduler(applicationContext).sync(settingsRepository.notifications.value)
    }

    /**
     * Asked for in context — when the user switches a notification on — rather than at
     * launch. A permission dialog on first run, before the app has shown what it would
     * notify about, is the one most likely to be denied permanently.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** Set by [com.el.sapiospend.notify.Notifier] so a tap lands on the right event. */
        const val EXTRA_EVENT_ID = "com.el.sapiospend.extra.EVENT_ID"
    }
}
