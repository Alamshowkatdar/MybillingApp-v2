package com.factorybill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.factorybill.app.ui.AccentPalette
import com.factorybill.app.ui.CompanySettingsScreen
import com.factorybill.app.ui.CustomersScreen
import com.factorybill.app.ui.EmployeeDetailScreen
import com.factorybill.app.ui.ExpensesScreen
import com.factorybill.app.ui.FactoryTheme
import com.factorybill.app.ui.HomeScreen
import com.factorybill.app.ui.HomeViewModel
import com.factorybill.app.ui.InventoryScreen
import com.factorybill.app.ui.InvoiceCreateScreen
import com.factorybill.app.ui.InvoiceDetailScreen
import com.factorybill.app.ui.InvoiceFormViewModel
import com.factorybill.app.ui.InvoiceListScreen
import com.factorybill.app.ui.LocalRepository
import com.factorybill.app.ui.MoreScreen
import com.factorybill.app.ui.PayrollScreen
import com.factorybill.app.ui.PurchaseCreateScreen
import com.factorybill.app.ui.PurchaseDetailScreen
import com.factorybill.app.ui.PurchaseFormViewModel
import com.factorybill.app.ui.PurchaseListScreen
import com.factorybill.app.ui.ScanFlowScreen
import com.factorybill.app.ui.SuppliersScreen
import com.factorybill.app.ui.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FactoryApp
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle(ThemeMode.LIGHT)
            val accentIndex by app.settings.accent.collectAsStateWithLifecycle(0)
            FactoryTheme(
                mode = theme,
                accent = AccentPalette.getOrElse(accentIndex) { AccentPalette.first() },
            ) {
                CompositionLocalProvider(LocalRepository provides app.repo) {
                    MybillingRoot(app)
                }
            }
        }
    }
}

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun MybillingRoot(app: FactoryApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val destinations = remember {
        listOf(
            TopDestination("home", "Home", Icons.Default.Home),
            TopDestination("invoices", "Invoices", Icons.AutoMirrored.Filled.ReceiptLong),
            TopDestination("purchases", "Purchases", Icons.Default.ShoppingCart),
            TopDestination("payroll", "Payroll", Icons.Default.Groups),
            TopDestination("more", "More", Icons.Default.MoreHoriz),
        )
    }
    val topRoutes = remember(destinations) { destinations.map { it.route }.toSet() }

    val homeVm: HomeViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { HomeViewModel(app.repo) } } },
    )
    val invoiceVm: InvoiceFormViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { InvoiceFormViewModel(app.repo) } } },
    )
    val purchaseVm: PurchaseFormViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { PurchaseFormViewModel(app.repo) } } },
    )

    val navigate: (String) -> Unit = { route ->
        if (route in topRoutes) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            nav.navigate(route) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in topRoutes) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    vm = homeVm,
                    onInvoiceSelect = { id -> nav.navigate("invoice/view/$id") },
                    onPurchaseSelect = { id -> nav.navigate("purchase/view/$id") },
                    onNavigate = navigate,
                )
            }
            composable("invoices") {
                InvoiceListScreen(
                    onOpen = { id -> nav.navigate("invoice/view/$id") },
                    onCreate = { nav.navigate("invoice/new") },
                )
            }
            composable("invoice/new") {
                InvoiceCreateScreen(
                    vm = invoiceVm,
                    onBack = { nav.popBackStack() },
                    onIssued = { id ->
                        nav.navigate("invoice/view/$id") {
                            popUpTo("invoice/new") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "invoice/edit/{invoiceId}",
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType }),
            ) { entry ->
                InvoiceCreateScreen(
                    vm = invoiceVm,
                    invoiceId = entry.arguments?.getLong("invoiceId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onIssued = { nav.popBackStack() },
                )
            }
            composable(
                route = "invoice/view/{invoiceId}",
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType }),
            ) { entry ->
                InvoiceDetailScreen(
                    repo = app.repo,
                    invoiceId = entry.arguments?.getLong("invoiceId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate("invoice/edit/$id") },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable("purchases") {
                PurchaseListScreen(
                    onOpen = { id -> nav.navigate("purchase/view/$id") },
                    onCreate = { nav.navigate("purchase/new") },
                )
            }
            composable("purchase/new") {
                PurchaseCreateScreen(
                    vm = purchaseVm,
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.navigate("purchase/view/$id") {
                            popUpTo("purchase/new") { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = "purchase/edit/{purchaseId}",
                arguments = listOf(navArgument("purchaseId") { type = NavType.LongType }),
            ) { entry ->
                PurchaseCreateScreen(
                    vm = purchaseVm,
                    purchaseId = entry.arguments?.getLong("purchaseId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable(
                route = "purchase/view/{purchaseId}",
                arguments = listOf(navArgument("purchaseId") { type = NavType.LongType }),
            ) { entry ->
                PurchaseDetailScreen(
                    repo = app.repo,
                    purchaseId = entry.arguments?.getLong("purchaseId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate("purchase/edit/$id") },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable("payroll") {
                PayrollScreen(onOpenEmployee = { id -> nav.navigate("employee/$id") })
            }
            composable(
                route = "employee/{employeeId}",
                arguments = listOf(navArgument("employeeId") { type = NavType.LongType }),
            ) { entry ->
                EmployeeDetailScreen(
                    repo = app.repo,
                    employeeId = entry.arguments?.getLong("employeeId") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("more") {
                MoreScreen(settings = app.settings, onNavigate = navigate)
            }
            composable("settings/company") {
                CompanySettingsScreen(settings = app.settings, onBack = { nav.popBackStack() })
            }
            composable("inventory") { InventoryScreen(onBack = { nav.popBackStack() }) }
            composable("expenses") { ExpensesScreen(onBack = { nav.popBackStack() }) }
            composable("customers") { CustomersScreen(onBack = { nav.popBackStack() }) }
            composable("suppliers") { SuppliersScreen(onBack = { nav.popBackStack() }) }
            composable("scan") {
                ScanFlowScreen(
                    repo = app.repo,
                    onOpenInvoice = { id ->
                        nav.navigate("invoice/view/$id") { popUpTo("scan") { inclusive = true } }
                    },
                    onOpenPurchase = { id ->
                        nav.navigate("purchase/view/$id") { popUpTo("scan") { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
