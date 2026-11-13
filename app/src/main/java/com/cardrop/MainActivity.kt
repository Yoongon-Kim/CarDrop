package com.cardrop

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.cardrop.core.AppContainer
import com.cardrop.core.CarDropApp
import com.cardrop.core.demo.DemoCar
import com.cardrop.core.demo.DemoCrew
import com.cardrop.core.demo.StartMode
import com.cardrop.core.demo.StartModeScreen
import com.cardrop.core.ui.theme.CarDropTheme

class CarDropApplication : Application() {
    /**
     * Every part of the app, made once per app start. Null until MainActivity knows the crew (the start
     * question, StartModeScreen); then it lives as long as the app, so a new activity keeps it.
     */
    var container: AppContainer? = null
        private set

    /** Makes [container] (once) with the crew and car saved in DemoCrew / DemoCar. */
    fun startContainer(): AppContainer = container ?: AppContainer(this).also { container = it }
}

class MainActivity : ComponentActivity() {

    private val app get() = application as CarDropApplication

    /** The in-app navigator needs GPS; ask once, then start it. */
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.container?.inAppNavigation?.onLocationPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val running = app.container
        val skip = intent.getBooleanExtra(StartMode.EXTRA_SKIP_QUESTION, false)
        when {
            // A new activity in the same app start (day/night theme, a second MainActivity on top).
            running != null -> show(running)
            StartMode.shouldAsk(BuildConfig.HAS_FIREBASE, skip) -> setContent {
                CarDropTheme {
                    StartModeScreen(
                        lastRealCrew = DemoCrew.current(this),
                        lastCar = DemoCar.current(this),
                        onPick = { realCrew, car ->
                            DemoCrew.pick(this, realCrew)
                            if (car != null) DemoCar.pick(this, car)
                            show(app.startContainer())
                        },
                    )
                }
            }
            else -> show(app.startContainer())
        }
    }

    private fun show(container: AppContainer) {
        if (container.inAppNavigation != null &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        setContent {
            CarDropTheme {
                CarDropApp(container)
            }
        }
    }
}
