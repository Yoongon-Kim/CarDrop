package com.cardrop.drive

import android.content.Context
import android.util.Log
import com.cardrop.core.model.Gear
import java.io.File

/**
 * Owner: lead (added by Member A for the 시연). This car's gear in a small file on the device, for
 * tools/drive_gps.py: while it says P the script holds the car still (0 km/h, same place), and drives on after D.
 * So the DEMO panel's P stops the demo car, like a real car stops before parking. The script reads it with
 * `adb shell run-as com.cardrop --user 10 cat files/gear.txt`, like the crew code ([TripCodeFile]).
 */
object GearFile {
    /** The same name is in tools/drive_gps.py. */
    private const val NAME = "gear.txt"

    fun save(context: Context, gear: Gear) {
        runCatching { File(context.filesDir, NAME).writeText(gear.name) }
            .onFailure { Log.w("CarDropTrip", "Could not save the gear for drive_gps.py: $it") }
    }
}
