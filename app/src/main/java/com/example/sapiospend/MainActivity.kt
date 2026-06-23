package com.example.sapiospend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.sapiospend.data.local.AppDatabase
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.navigation.AppNavGraph
import com.example.sapiospend.ui.viewmodel.EventViewModel

class MainActivity : ComponentActivity() {

    private val eventViewModel: EventViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = EventRepository(db.eventDao())
        EventViewModel.factory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                AppNavGraph(
                    navController = navController,
                    eventViewModel = eventViewModel
                )
            }
        }
    }
}