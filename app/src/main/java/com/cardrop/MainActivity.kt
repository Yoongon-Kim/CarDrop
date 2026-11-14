package com.cardrop

import android.Manifest
import android.app.Application
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.cardrop.core.AppContainer
import com.cardrop.core.CarDropApp
import com.cardrop.core.demo.CarNames
import com.cardrop.core.demo.DemoCar
import com.cardrop.core.demo.DemoCrew
import com.cardrop.core.demo.StartMode
import com.cardrop.core.demo.StartModeScreen
import com.cardrop.core.ui.theme.CarDropTheme
import com.cardrop.navi.PleosFusedLocationSource
import com.cardrop.safety.AaosSafetyTelemetry

class CarDropApplication : Application() {
    /**
     * Every part of the app, made once per app start. Null until MainActivity knows the crew (the start
     * question, StartModeScreen); then it lives as long as the app, so a new activity keeps it.
     */
    var container: AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        CarNames.load(this) // the cars' names typed on this device, before any screen or crew uses them
    }

    /** Makes [container] (once) with the crew and car saved in DemoCrew / DemoCar. */
    fun startContainer(): AppContainer = container ?: AppContainer(this).also { container = it }
}

class MainActivity : ComponentActivity() {

    private val app get() = application as CarDropApplication

    /** Request navigation GPS, crew vehicle location and optional public vehicle speed together. */
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            app.container?.inAppNavigation?.onLocationPermissionGranted()
            app.container?.safety?.onAppVisible(true)
        }
        if (grants[AaosSafetyTelemetry.PERMISSION] == true) app.container?.safety?.onSpeedPermissionGranted()
        if (grants[PleosFusedLocationSource.PERMISSION] == true) {
            app.container?.onFusedLocationPermissionGranted()
        }
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
            if (container.supportsFusedLocation &&
                checkSelfPermission(PleosFusedLocationSource.PERMISSION) != PackageManager.PERMISSION_GRANTED
            ) add(PleosFusedLocationSource.PERMISSION)
        }
        setContent {
            CarDropTheme {
                CarDropApp(container)
            }
        }
        if (PleosFusedLocationSource.PERMISSION in missingPermissions) {
            // Some Pleos images do not label the custom permission in the system dialog.
            AlertDialog.Builder(this)
                .setTitle("차량 위치 사용")
                .setMessage("크루 차량의 위치 도식에 내 위치를 표시하기 위해 차량 위치 접근 권한을 요청해요. GPS로 계속 사용할 수도 있어요.")
                .setPositiveButton("계속") { _, _ -> askLocation.launch(missingPermissions.toTypedArray()) }
                .setNegativeButton("GPS로 사용") { _, _ -> requestGpsOnly(missingPermissions) }
                .setOnCancelListener { requestGpsOnly(missingPermissions) }
                .show()
        } else if (missingPermissions.isNotEmpty()) {
            askLocation.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requestGpsOnly(permissions: List<String>) {
        val gpsPermissions = permissions.filter { it != PleosFusedLocationSource.PERMISSION }
        if (gpsPermissions.isNotEmpty()) askLocation.launch(gpsPermissions.toTypedArray())
    }

    override fun onStart() { super.onStart(); app.container?.safety?.onAppVisible(true) }
    override fun onStop() { app.container?.safety?.onAppVisible(false); super.onStop() }
    override fun onResume() { super.onResume(); app.container?.safety?.onSpeedPermissionGranted() }
}
