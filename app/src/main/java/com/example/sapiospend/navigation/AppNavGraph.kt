package com.example.sapiospend.navigation

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
import com.example.sapiospend.billing.Plan
import com.example.sapiospend.ui.component.PaywallSheet
import com.example.sapiospend.ui.component.PaywallTrigger
import com.example.sapiospend.ui.screen.AddEventScreen
import com.example.sapiospend.ui.screen.AddExpenseScreen
import com.example.sapiospend.ui.screen.AnalyticsScreen
import com.example.sapiospend.ui.screen.EventDetailScreen
import com.example.sapiospend.ui.screen.HomeScreen
import com.example.sapiospend.ui.viewmodel.EventViewModel
import com.example.sapiospend.ui.viewmodel.ExportViewModel
import com.example.sapiospend.ui.viewmodel.UiMessage

// One ViewModel instance is passed to every screen so budget totals stay consistent
// while navigating without re-querying the database on each destination change.
@Composable
fun AppNavGraph(
    navController: NavHostController,
    eventViewModel: EventViewModel,
    exportViewModel: ExportViewModel
) {
    val context = LocalContext.current
    val plan by eventViewModel.plan.collectAsState()
    val isPro = plan == Plan.PRO

    // The paywall is hoisted here because four different screens can trigger it, and a
    // sheet owned by any one of them would vanish the moment navigation moved on.
    var paywallTrigger by remember { mutableStateOf<PaywallTrigger?>(null) }

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
                onAnalyticsClick = {
                    if (isPro) navController.navigate(Routes.Analytics.route)
                    else paywallTrigger = PaywallTrigger.ANALYTICS
                },
                onRequirePro = { trigger -> paywallTrigger = trigger },
                eventViewModel = eventViewModel,
                exportViewModel = exportViewModel
            )
        }

        composable(Routes.AddEvent.route) {
            AddEventScreen(
                isPro = isPro,
                onBack = { navController.popBackStack() },
                onRequirePro = { paywallTrigger = PaywallTrigger.TEMPLATES },
                onSaveEvent = { name, budget, eventType, template ->
                    eventViewModel.addEvent(name, budget, eventType, template)
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

        composable(
            route = Routes.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.AddExpense.createRoute(eventId)) },
                onRequirePro = { trigger -> paywallTrigger = trigger },
                eventViewModel = eventViewModel,
                exportViewModel = exportViewModel
            )
        }

        composable(
            route = Routes.AddExpense.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            val budgetLines by eventViewModel.budgetLines.collectAsState()
            AddExpenseScreen(
                plannedCategories = budgetLines.filter { it.eventId == eventId }.map { it.category },
                onBack = { navController.popBackStack() },
                onSaveExpense = { title, category, amount, notes ->
                    eventViewModel.addExpense(eventId, title, category, amount, notes)
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
