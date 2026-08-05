package com.example.sapiospend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.sapiospend.billing.LocalEntitlements
import com.example.sapiospend.data.local.AppDatabase
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.export.ExportManager
import com.example.sapiospend.navigation.AppNavGraph
import com.example.sapiospend.ui.viewmodel.EventViewModel
import com.example.sapiospend.ui.viewmodel.ExportViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        lifecycleScope.launch { ExportManager(applicationContext).clearCache() }

        setContent {
            val navController = rememberNavController()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                AppNavGraph(
                    navController = navController,
                    eventViewModel = eventViewModel,
                    exportViewModel = exportViewModel
                )
            }
        }
    }
}
