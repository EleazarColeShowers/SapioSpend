package com.el.sapiospend.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import com.el.sapiospend.ui.component.PaywallSheet
import com.el.sapiospend.settings.SettingsRepository
import com.el.sapiospend.ui.component.PaywallTrigger
import com.el.sapiospend.ui.screen.AddEventScreen
import com.el.sapiospend.ui.screen.ExpenseFormScreen
import com.el.sapiospend.ui.screen.AnalyticsScreen
import com.el.sapiospend.ui.screen.BudgetPlanScreen
import com.el.sapiospend.ui.screen.EventDetailScreen
import com.el.sapiospend.ui.screen.HomeScreen
import com.el.sapiospend.ui.screen.SettingsScreen
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.ExportViewModel
import com.el.sapiospend.ui.viewmodel.UiMessage

// One ViewModel instance is passed to every screen so budget totals stay consistent
// while navigating without re-querying the database on each destination change.
@Composable
fun AppNavGraph(
    navController: NavHostController,
    eventViewModel: EventViewModel,
    exportViewModel: ExportViewModel,
    settingsRepository: SettingsRepository,
    /** Event a tapped notification wants opened, or null on an ordinary launch. */
    openEventId: String? = null,
    onEventOpened: () -> Unit = {},
    /**
     * Set when the app was launched by the widget's or a notification's "log an expense"
     * action, so the user lands on the form rather than on the screen it was tapped from.
     */
    quickAdd: Boolean = false,
    onQuickAddHandled: () -> Unit = {},
    notificationsAllowed: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val plan by eventViewModel.plan.collectAsState()
    val proUnlocked = PlanRules.proFeaturesUnlocked(plan)

    // The paywall is hoisted here because four different screens can trigger it, and a
    // sheet owned by any one of them would vanish the moment navigation moved on.
    var paywallTrigger by remember { mutableStateOf<PaywallTrigger?>(null) }

    // Handled here rather than in the activity because this is where the graph is: the
    // activity knows an event id, only the NavHost knows how to get to it. Cleared
    // immediately so the back button leads out of the detail screen instead of
    // re-navigating into it on the next recomposition.
    LaunchedEffect(openEventId) {
        openEventId?.let {
            navController.navigate(Routes.EventDetail.createRoute(it))
            onEventOpened()
        }
    }

    /**
     * Quick-add lands straight on the expense form for the event it was fired against —
     * or, when it names no event, the most recent one, which is the budget somebody
     * logging a spend on the way out of a shop is almost certainly logging against.
     *
     * With no events at all there is nothing to record against, so it falls through to
     * Home rather than opening a form bound to an event id that does not exist.
     */
    val events by eventViewModel.events.collectAsState()
    LaunchedEffect(quickAdd, events) {
        if (!quickAdd) return@LaunchedEffect
        val target = openEventId ?: events.firstOrNull()?.id
        if (events.isEmpty()) {
            onQuickAddHandled()
            return@LaunchedEffect
        }
        target?.let {
            navController.navigate(Routes.AddExpense.createRoute(it))
            onQuickAddHandled()
        }
    }

    val message by eventViewModel.message.collectAsState()
    LaunchedEffect(message) {
        when (val current = message) {
            UiMessage.EventLimitReached -> paywallTrigger = PaywallTrigger.EVENT_LIMIT
            is UiMessage.Error -> Toast.makeText(context, current.text, Toast.LENGTH_LONG).show()
            null -> Unit
        }
        if (message != null) eventViewModel.consumeMessage()
    }

    val shareIntent by exportViewModel.shareIntent.collectAsState()
    LaunchedEffect(shareIntent) {
        shareIntent?.let { intent ->
            context.startActivity(intent)
            exportViewModel.consumeShareIntent()
        }
    }

    val exportError by exportViewModel.error.collectAsState()
    LaunchedEffect(exportError) {
        exportError?.let { text ->
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            exportViewModel.consumeError()
        }
    }

    NavHost(navController = navController, startDestination = Routes.Home.route) {

        composable(Routes.Home.route) {
            HomeScreen(
                onAddEventClick = { navController.navigate(Routes.AddEvent.route) },
                onEventClick = { eventId -> navController.navigate(Routes.EventDetail.createRoute(eventId)) },
                onExpenseClick = { expenseId -> navController.navigate(Routes.EditExpense.createRoute(expenseId)) },
                onAnalyticsClick = {
                    if (proUnlocked) navController.navigate(Routes.Analytics.route)
                    else paywallTrigger = PaywallTrigger.ANALYTICS
                },
                onSettingsClick = { navController.navigate(Routes.Settings.route) },
                onRequirePro = { trigger -> paywallTrigger = trigger },
                eventViewModel = eventViewModel,
                exportViewModel = exportViewModel
            )
        }

        composable(Routes.AddEvent.route) {
            AddEventScreen(
                proUnlocked = proUnlocked,
                onBack = { navController.popBackStack() },
                onRequirePro = { paywallTrigger = PaywallTrigger.TEMPLATES },
                onSaveEvent = { input ->
                    eventViewModel.addEvent(
                        name = input.name,
                        budget = input.budget,
                        eventType = input.eventType,
                        template = input.template,
                        customLines = input.customLines,
                        startDate = input.startDate,
                        endDate = input.endDate,
                        guestCount = input.guestCount
                    )
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.Analytics.route) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
                onEventClick = { eventId -> navController.navigate(Routes.EventDetail.createRoute(eventId)) },
                eventViewModel = eventViewModel
            )
        }

        composable(Routes.Settings.route) {
            val currency by settingsRepository.currency.collectAsState()
            val notifications by settingsRepository.notifications.collectAsState()
            SettingsScreen(
                currency = currency,
                onCurrencyChange = settingsRepository::setCurrency,
                notifications = notifications,
                onNotificationsChange = settingsRepository::setNotifications,
                notificationsAllowed = notificationsAllowed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.AddExpense.createRoute(eventId)) },
                onEditExpense = { expenseId -> navController.navigate(Routes.EditExpense.createRoute(expenseId)) },
                onEditPlan = { navController.navigate(Routes.BudgetPlan.createRoute(eventId)) },
                onRequirePro = { trigger -> paywallTrigger = trigger },
                eventViewModel = eventViewModel,
                exportViewModel = exportViewModel
            )
        }

        composable(
            route = Routes.BudgetPlan.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            BudgetPlanScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                eventViewModel = eventViewModel
            )
        }

        composable(
            route = Routes.AddExpense.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            val budgetLines by eventViewModel.budgetLines.collectAsState()
            ExpenseFormScreen(
                eventId = eventId,
                budgetLines = budgetLines,
                onBack = { navController.popBackStack() },
                onSave = { result ->
                    eventViewModel.addExpense(
                        eventId = result.eventId,
                        title = result.title,
                        category = result.category,
                        amount = result.amount,
                        notes = result.notes,
                        date = result.date,
                        vendor = result.vendor,
                        amountPaid = result.amountPaid,
                        dueDate = result.dueDate,
                        receiptPath = result.receiptPath
                    )
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.EditExpense.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId") ?: return@composable
            val expenses by eventViewModel.allExpenses.collectAsState()
            val budgetLines by eventViewModel.budgetLines.collectAsState()
            // Deleting the expense pops back to the detail screen, but a stale back stack
            // entry can still recompose once on a row that has gone. Nothing to edit is
            // not an error worth a screen — it just returns.
            val expense = expenses.find { it.id == expenseId } ?: return@composable

            val events by eventViewModel.events.collectAsState()

            ExpenseFormScreen(
                eventId = expense.eventId,
                events = events,
                budgetLines = budgetLines,
                existing = expense,
                onBack = { navController.popBackStack() },
                onSave = { result ->
                    eventViewModel.updateExpense(
                        expense.copy(
                            eventId = result.eventId,
                            title = result.title,
                            category = result.category,
                            amount = result.amount,
                            notes = result.notes,
                            dateCreated = result.date,
                            vendor = result.vendor,
                            amountPaid = result.amountPaid,
                            dueDate = result.dueDate,
                            receiptPath = result.receiptPath
                        )
                    )
                    navController.popBackStack()
                }
            )
        }
    }

    paywallTrigger?.let { trigger ->
        PaywallSheet(
            trigger = trigger,
            onDismiss = { paywallTrigger = null },
            onUpgrade = {
                eventViewModel.applyPurchase(Plan.PRO)
                paywallTrigger = null
            }
        )
    }
}
