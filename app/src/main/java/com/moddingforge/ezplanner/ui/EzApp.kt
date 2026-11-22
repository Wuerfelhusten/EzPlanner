package com.moddingforge.ezplanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import com.moddingforge.ezplanner.ui.components.UpdateBanner
import com.moddingforge.ezplanner.ui.components.WhatsNewPopup
import com.moddingforge.ezplanner.update.Updater
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moddingforge.ezplanner.data.EzRepository
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.ui.screens.analytics.AnalyticsScreen
import com.moddingforge.ezplanner.ui.screens.booking.BookScreen
import com.moddingforge.ezplanner.ui.screens.booking.TransactionEditScreen
import com.moddingforge.ezplanner.ui.screens.history.HistoryScreen
import com.moddingforge.ezplanner.ui.screens.orders.OrderEditScreen
import com.moddingforge.ezplanner.ui.screens.orders.OrdersScreen
import com.moddingforge.ezplanner.ui.screens.overview.OverviewScreen
import com.moddingforge.ezplanner.ui.screens.pots.PotDetailScreen
import com.moddingforge.ezplanner.ui.screens.pots.PotEditScreen
import com.moddingforge.ezplanner.ui.screens.settings.SettingsScreen

val LocalRepository = staticCompositionLocalOf<EzRepository> { error("No repository") }
val LocalUpdater = staticCompositionLocalOf<Updater> { error("No updater") }

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Overview("overview", "Übersicht", Icons.Default.Home),
    Book("book", "Buchen", Icons.Default.AddCard),
    Analytics("analytics", "Auswertung", Icons.Default.PieChart),
    History("history", "Verlauf", Icons.AutoMirrored.Filled.ListAlt),
    Orders("orders", "Aufträge", Icons.Default.Repeat),
}

/** Navigation targets outside the bottom bar. */
object Routes {
    fun newTransaction(kind: TxKind, potId: Long? = null) = "tx?kind=${kind.name}&potId=${potId ?: 0}"
    fun editTransaction(id: Long) = "tx?id=$id"
    fun newOrder(potId: Long? = null) = if (potId != null) "order?potId=$potId" else "order"
    fun editOrder(id: Long) = "order?id=$id"
    fun pot(id: Long) = "pot/$id"
    fun newPot(kind: PotKind) = "potEdit?kind=${kind.name}"
    fun editPot(id: Long) = "potEdit?id=$id"
    const val SETTINGS = "settings"
}

@Composable
fun EzApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        // Each screen handles its own insets (top bar, keyboard); only the bottom bar is padded here.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
            UpdateBanner(LocalUpdater.current)
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            }
        },
    ) { padding ->
        WhatsNewPopup(LocalUpdater.current)
        NavHost(
            navController = nav,
            startDestination = Tab.Overview.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.Overview.route) { OverviewScreen(nav) }
            composable(Tab.Book.route) { BookScreen(nav) }
            composable(Tab.Analytics.route) { AnalyticsScreen() }
            composable(Tab.History.route) { HistoryScreen(nav) }
            composable(Tab.Orders.route) { OrdersScreen(nav) }

            composable(
                "tx?id={id}&kind={kind}&potId={potId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("kind") { type = NavType.StringType; defaultValue = TxKind.EXPENSE.name },
                    navArgument("potId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                val args = entry.arguments!!
                TransactionEditScreen(
                    nav = nav,
                    id = args.getLong("id"),
                    initialKind = TxKind.valueOf(args.getString("kind") ?: TxKind.EXPENSE.name),
                    initialPotId = args.getLong("potId").takeIf { it != 0L },
                )
            }
            composable(
                "order?id={id}&potId={potId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("potId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                val args = entry.arguments!!
                OrderEditScreen(nav, args.getLong("id"), args.getLong("potId").takeIf { it != 0L })
            }
            composable(
                "pot/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry -> PotDetailScreen(nav, entry.arguments!!.getLong("id")) }
            composable(
                "potEdit?id={id}&kind={kind}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("kind") { type = NavType.StringType; defaultValue = PotKind.SAVINGS.name },
                ),
            ) { entry ->
                val args = entry.arguments!!
                PotEditScreen(nav, args.getLong("id"), PotKind.valueOf(args.getString("kind") ?: PotKind.SAVINGS.name))
            }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
