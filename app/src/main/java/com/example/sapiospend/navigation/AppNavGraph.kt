package com.example.sapiospend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.sapiospend.ui.screen.AddEventScreen
import com.example.sapiospend.ui.screen.AddExpenseScreen
import com.example.sapiospend.ui.screen.EventDetailScreen
import com.example.sapiospend.ui.screen.HomeScreen
import com.example.sapiospend.ui.viewmodel.EventViewModel

// One ViewModel instance is passed to every screen so budget totals stay consistent
// while navigating without re-querying the database on each destination change.
@Composable
fun AppNavGraph(navController: NavHostController, eventViewModel: EventViewModel) {
    NavHost(navController = navController, startDestination = Routes.Home.route) {

        composable(Routes.Home.route) {
            HomeScreen(
                onAddEventClick = { navController.navigate(Routes.AddEvent.route) },
                onEventClick = { eventId -> navController.navigate(Routes.EventDetail.createRoute(eventId)) },
                eventViewModel = eventViewModel
            )
        }

        composable(Routes.AddEvent.route) {
            AddEventScreen(
                onBack = { navController.popBackStack() },
                onSaveEvent = { name, budget, eventType ->
                    eventViewModel.addEvent(name, budget, eventType)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.IntType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.AddExpense.createRoute(eventId)) },
                eventViewModel = eventViewModel
            )
        }

        composable(
            route = Routes.AddExpense.route,
            arguments = listOf(navArgument("eventId") { type = NavType.IntType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getInt("eventId") ?: return@composable
            AddExpenseScreen(
                onBack = { navController.popBackStack() },
                onSaveExpense = { title, category, amount ->
                    eventViewModel.addExpense(eventId, title, category, amount)
                    navController.popBackStack()
                }
            )
        }
    }
}