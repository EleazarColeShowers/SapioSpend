package com.el.sapiospend.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the UI needs to know about an update that Play is handling for us. */
sealed interface UpdateStatus {
    /** Nothing to show: either no update exists, or Play is still deciding. */
    data object Idle : UpdateStatus

    /** A flexible update is downloading in the background; the app stays usable. */
    data object Downloading : UpdateStatus

    /** The new version is on the device and installs on the next restart. */
    data object ReadyToInstall : UpdateStatus
}

/**
 * Drives Google Play's in-app update flow so users move to new versions without ever
 * visiting the store listing.
 *
 * Two flows, chosen per update rather than per app:
 *  - *flexible* by default — Play downloads in the background and the user keeps working,
 *    then installs on their own schedule via [completeUpdate].
 *  - *immediate* for a release marked high priority or one the user has ignored for
 *    [STALENESS_THRESHOLD_DAYS], which blocks the app until the update is applied. That
 *    is the only lever for pushing out a build with a data-corrupting bug in it, so it is
 *    driven by the priority set at rollout time, not by anything hardcoded here.
 *
 * Attach one per Activity, constructed inside `onCreate` before the activity starts, as
 * the result launcher registration requires.
 */
class InAppUpdater(
    private val activity: ComponentActivity,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
) : DefaultLifecycleObserver {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    // One prompt per activity instance. Play itself throttles its consent dialog, but
    // without this a user who dismissed the sheet would be asked again on every return
    // from the share sheet or the export file picker.
    private var promptShown = false

    private val installListener = InstallStateUpdatedListener { state ->
        _status.value = when (state.installStatus()) {
            InstallStatus.DOWNLOADING, InstallStatus.PENDING -> UpdateStatus.Downloading
            InstallStatus.DOWNLOADED -> UpdateStatus.ReadyToInstall
            else -> UpdateStatus.Idle
        }
    }

    private val updateFlowLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // A declined or failed flow is not an error worth surfacing: the check runs again
        // next launch, and a nagging dialog is the fastest way to teach users to dismiss
        // it reflexively.
        if (result.resultCode != Activity.RESULT_OK) _status.value = UpdateStatus.Idle
    }

    init {
        manager.registerListener(installListener)
        activity.lifecycle.addObserver(this)
    }

    /**
     * Checked on every resume, not just at launch, so an update that finished downloading
     * while the app was backgrounded is offered as soon as the user comes back — and so a
     * blocking immediate update that was interrupted resumes instead of stranding the app.
     */
    override fun onResume(owner: LifecycleOwner) {
        activity.lifecycleScope.launch {
            // Throws when the app was not installed by Play — sideloaded debug builds and
            // emulators without Play Services. Swallowed, because there is no update
            // channel to offer in that case and it must not take the launch down with it.
            val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull() ?: return@launch
            when {
                info.installStatus() == InstallStatus.DOWNLOADED ->
                    _status.value = UpdateStatus.ReadyToInstall

                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    start(info, AppUpdateType.IMMEDIATE)

                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && !promptShown ->
                    start(info, chooseType(info))

                else -> Unit
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        manager.unregisterListener(installListener)
    }

    /**
     * Installs a downloaded update. The app is restarted by Play, so anything unsaved has
     * to be committed before this is called.
     */
    fun completeUpdate() {
        activity.lifecycleScope.launch { runCatching { manager.requestCompleteUpdate() } }
    }

    private fun start(info: AppUpdateInfo, type: Int) {
        if (!info.isUpdateTypeAllowed(type)) return
        promptShown = true
        runCatching {
            manager.startUpdateFlowForResult(
                info,
                updateFlowLauncher,
                AppUpdateOptions.newBuilder(type).build()
            )
        }
    }

    // updatePriority comes from the value set on the Play release; staleness is how long
    // the newer version has been available to this user.
    private fun chooseType(info: AppUpdateInfo): Int {
        val stale = (info.clientVersionStalenessDays() ?: 0) >= STALENESS_THRESHOLD_DAYS
        return if (info.updatePriority() >= IMMEDIATE_PRIORITY || stale) {
            AppUpdateType.IMMEDIATE
        } else {
            AppUpdateType.FLEXIBLE
        }
    }

    private companion object {
        const val IMMEDIATE_PRIORITY = 4
        const val STALENESS_THRESHOLD_DAYS = 30
    }
}
