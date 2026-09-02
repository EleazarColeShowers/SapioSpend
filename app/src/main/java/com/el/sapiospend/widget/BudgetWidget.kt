package com.el.sapiospend.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.el.sapiospend.MainActivity
import com.el.sapiospend.R
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.settings.SettingsRepository
import com.el.sapiospend.util.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A home-screen widget showing what is left across every budget, with one tap to log a
 * spend.
 *
 * The point is the tap. Recording an expense costs four taps and a launch from the home
 * screen, and the expenses that go unrecorded are precisely the small ones nobody is
 * willing to spend four taps on — which is how a budget quietly stops matching reality.
 *
 * RemoteViews rather than Glance: Glance would pull in another dependency for a layout
 * that is four text views, and this one has no interaction beyond a single click target.
 */
class BudgetWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // goAsync keeps the broadcast alive across the database read. It is legal here
        // because onUpdate is dispatched from onReceive and still inside its call.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = render(context)
                appWidgetIds.forEach { id -> manager.updateAppWidget(id, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_budget)

        val db = AppDatabase.getDatabase(context)
        val repository = EventRepository(
            db.eventDao(),
            db.expenseDao(),
            db.budgetLineDao(),
            db.contributionDao(),
            db.recurringExpenseDao()
        )
        val events = repository.events.first()
        val expenses = repository.allExpenses.first()

        // Read from storage rather than from ActiveCurrency: the widget is drawn in a
        // process that may never have opened the app — after a reboot, or an update — and
        // the global would still be sitting at its default.
        val currency = SettingsRepository.create(context).currency.value

        val budget = events.sumOf { it.budget }
        val spent = expenses.sumOf { it.amount }
        val owed = expenses.sumOf { it.outstanding }

        if (events.isEmpty()) {
            views.setTextViewText(R.id.widget_amount, context.getString(R.string.widget_empty))
            views.setTextViewText(R.id.widget_detail, "")
        } else {
            views.setTextViewText(R.id.widget_amount, (budget - spent).formatMoney(currency))
            views.setTextViewText(
                R.id.widget_detail,
                buildString {
                    append("${spent.formatMoney(currency)} of ${budget.formatMoney(currency)} spent")
                    if (owed > 0) append(" · ${owed.formatMoney(currency)} still to pay")
                }
            )
        }

        // The card opens the app; the button goes straight to the expense form. Two
        // targets rather than one, because a widget whose every pixel logs an expense is
        // a widget you cannot tap to check a figure.
        views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, quickAdd = false))
        views.setOnClickPendingIntent(R.id.widget_add, launchIntent(context, quickAdd = true))

        return views
    }

    private fun launchIntent(context: Context, quickAdd: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (quickAdd) putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        return PendingIntent.getActivity(
            context,
            if (quickAdd) REQUEST_QUICK_ADD else REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_OPEN = 5001
        private const val REQUEST_QUICK_ADD = 5002

        /**
         * Redraws every placed instance.
         *
         * Called when the app has changed the numbers — leaving a screen, finishing the
         * daily tick — rather than from a flow collector on every keystroke: a widget
         * update is a broadcast to the launcher, and one per typed digit would be
         * expensive for a surface nobody is looking at while they type.
         *
         * Cheap and safe when no widget is placed: the id array comes back empty and
         * nothing is broadcast.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, BudgetWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
