package com.cardrop.drive

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Owner: lead. The code of the crew whose trip this device follows, in a small file on the device, for
 * tools/drive_gps.py. Every real crew has its own random code, so the script asks the emulator it drives
 * (`adb shell run-as com.cardrop --user 10 cat files/crew_code.txt`) and waits for that trip's 출발.
 * No file = no real crew (the bot crew, or before creating / joining one).
 */
object TripCodeFile {
    /** The same name is in tools/drive_gps.py. */
    private const val NAME = "crew_code.txt"

    fun save(context: Context, code: String?) {
        runCatching {
            val file = File(context.filesDir, NAME)
            if (code == null) file.delete() else file.writeText(code)
        }.onFailure { Log.w("CarDropTrip", "Could not save the crew code for drive_gps.py: $it") }
    }
}
