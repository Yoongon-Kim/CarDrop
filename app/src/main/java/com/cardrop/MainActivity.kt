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
import com.cardrop.safety.AaosSafetyTelemetry

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

    /** Navigation GPS and optional public vehicle speed are requested together. */
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            app.container?.inAppNavigation?.onLocationPermissionGranted()
            app.container?.safety?.onAppVisible(true)
        }
        if (grants[AaosSafetyTelemetry.PERMISSION] == true) app.container?.safety?.onSpeedPermissionGranted()
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
        container.safety.onAppVisible(true)
        val missingPermissions = buildList {
            if (BuildConfig.USE_REAL_VEHICLE && checkSelfPermission(AaosSafetyTelemetry.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                add(AaosSafetyTelemetry.PERMISSION)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        setContent {
            CarDropTheme {
                CarDropApp(container)
            }
        }
        if (missingPermissions.isNotEmpty()) askLocation.launch(missingPermissions.toTypedArray())
    }

    override fun onStart() { super.onStart(); app.container?.safety?.onAppVisible(true) }
    override fun onStop() { app.container?.safety?.onAppVisible(false); super.onStop() }
    override fun onResume() { super.onResume(); app.container?.safety?.onSpeedPermissionGranted() }
}
