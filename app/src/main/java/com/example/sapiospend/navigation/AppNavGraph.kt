package com.example.sapiospend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.sapiospend.ui.screen.AddEventScreen
import com.example.sapiospend.ui.screen.HomeScreen
import com.example.sapiospend.ui.viewmodel.EventViewModel

@Composable
fun AppNavGraph(navController: NavHostController, eventViewModel: EventViewModel) {

    NavHost(
        navController = navController,
        startDestination = Routes.Home.route
    ) {

        composable(Routes.Home.route) {
            HomeScreen(
                onAddEventClick = {
                    navController.navigate(Routes.AddEvent.route)
                },
                eventViewModel = eventViewModel,


            )
        }

        composable(Routes.AddEvent.route) {
            AddEventScreen(
                onBack = { navController.popBackStack() },
                onSaveEvent = { event ->

                    eventViewModel.addEvent(
                        name = event.name,
                        budget = event.budget
                    )

                    navController.popBackStack()
                }

            )
        }

    }
}