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
import com.cardrop.core.ui.theme.CarDropTheme

class CarDropApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class MainActivity : ComponentActivity() {

    private val container get() = (application as CarDropApplication).container

    /** The in-app navigator needs GPS; ask once, then start it. */
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) container.inAppNavigation?.onLocationPermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = container
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
