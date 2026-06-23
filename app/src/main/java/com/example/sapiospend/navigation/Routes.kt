package com.example.sapiospend.navigation

sealed class Routes (val route: String){
    data object Home: Routes("home")
    data object AddEvent: Routes("add_event")

}