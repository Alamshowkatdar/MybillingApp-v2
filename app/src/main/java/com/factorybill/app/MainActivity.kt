package com.factorybill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.factorybill.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FactoryApp
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val accent by app.settings.accent.collectAsStateWithLifecycle(0)
            FactoryTheme(theme, AccentPalette[accent]) { Root(app) }
        }
    }
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun Root(app: FactoryApp) {
    val nav = rememberNavController()
    val dests = listOf(
        Dest("home", "Home", Icons.Default.Home), Dest("invoice", "Invoice", Icons.Default.Receipt),
        Dest("scan", "Scan bill", Icons.Default.CameraAlt), Dest("ops", "Business", Icons.Default.Factory),
        Dest("more", "Settings", Icons.Default.MoreHoriz),
    )
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { NavigationBar { dests.forEach { d ->
            NavigationBarItem(current == d.route, {
                nav.navigate(d.route) { popUpTo("home"); launchSingleTop = true }
            }, { Icon(d.icon, d.label) }, label = { Text(d.label) }) } } },
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(viewModel(factory = viewModelFactory { initializer { HomeViewModel(app.repo) } })) { id ->
                    nav.navigate("invoice/$id")
                }
            }
            composable("invoice") { InvoiceScreen(viewModel(factory = viewModelFactory { initializer { InvoiceViewModel(app.repo) } })) }
            composable("invoice/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                if (id == null) InvoiceUnavailable()
                else InvoiceDetailsScreen(app.repo, id) { nav.popBackStack() }
            }
            composable("scan") { ScanScreen(app.repo) }
            composable("ops") { OperationsScreen(app.repo) }
            composable("more") { MoreScreen(app) }
        }
    }
}
