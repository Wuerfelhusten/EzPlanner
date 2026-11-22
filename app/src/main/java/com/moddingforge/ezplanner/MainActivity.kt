package com.moddingforge.ezplanner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.moddingforge.ezplanner.ui.EzApp
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.LocalUpdater
import com.moddingforge.ezplanner.ui.theme.EzTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as EzPlannerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            EzTheme {
                CompositionLocalProvider(
                    LocalRepository provides app.repository,
                    LocalUpdater provides app.updater,
                ) {
                    EzApp()
                }
            }
        }
        app.updater.checkOnStart()
        app.updater.checkWhatsNew()
    }

    override fun onResume() {
        super.onResume()
        // Also covers the app staying open over midnight: due orders are booked on return.
        app.bookDueStandingOrders()
    }
}
