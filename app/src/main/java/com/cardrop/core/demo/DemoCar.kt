package com.cardrop.core.demo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.cardrop.BuildConfig
import com.cardrop.MainActivity
import com.cardrop.core.navigation.Screen

/**
 * Owner: lead. Which car this device drives in the real crew ([DemoCrew]), for the demo.
 *
 * The DEMO panel's "내 차" picks it and this device remembers it; without a pick it is `cardrop.myCar`
 * from local.properties. Picking restarts the app, so every part starts again as the new car (some
 * screens keep the car id they were made with). The crew is then created again (아빠 차) or joined
 * again with its code (the other cars).
 */
object DemoCar {
    /** The 4 cars in crew order, with the same keys as `cardrop.myCar`. */
    val KEYS = listOf("dad", "uncle", "minji", "aunt")

    /** The host in the design (board 01 "아빠 차 (나)"): the only car that creates the crew. */
    const val HOST = "dad"

    /** The car to drive: the DEMO panel's pick if it is one of [KEYS], else the build's default. */
    fun resolve(picked: String?, buildDefault: String): String = picked?.takeIf { it in KEYS } ?: buildDefault

    /**
     * The first screen. Board 01 creates a crew, so with the real crew only [HOST] starts there; the other
     * cars start on board 03 to join the host's crew with its code.
     */
    fun startScreen(useRealCrew: Boolean, car: String): Screen =
        if (useRealCrew && car != HOST) Screen.JoinCrew else Screen.CreateCrew

    /** The car this device drives now. */
    fun current(context: Context): String = resolve(demoPrefs(context).getString(PREF_KEY, null), BuildConfig.MY_CAR)

    /** Remembers [car] and restarts the app as that car. */
    fun pickAndRestart(context: Context, car: String) {
        require(car in KEYS) { "unknown car $car" }
        // commit(), not apply(): the process ends right below, before apply() would write.
        demoPrefs(context).edit().putString(PREF_KEY, car).commit()
        restartApp(context)
    }

    private const val PREF_KEY = "myCar"
}

/** What the DEMO panel picked on this device ([DemoCar], [DemoCrew]). */
internal fun demoPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences("cardrop_demo", Context.MODE_PRIVATE)

/** Starts the app again in a new process. A new process makes a new AppContainer, so nothing keeps the old pick. */
internal fun restartApp(context: Context) {
    context.startActivity(Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java)))
    Runtime.getRuntime().exit(0)
}
