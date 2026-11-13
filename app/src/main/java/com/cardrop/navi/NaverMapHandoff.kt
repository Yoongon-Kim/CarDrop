package com.cardrop.navi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.cardrop.core.model.NavRoute
import com.cardrop.core.model.Place
import com.cardrop.drive.TripDecisions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.URLEncoder
import java.util.Locale

/**
 * Owner: Member B.
 *
 * Opens Naver Map (from the Pleos app market) with turn-by-turn guidance:
 * `nmap://navigation?dlat=..&dlng=..&dname=..&v1lat=..&v1lng=..&v1name=..&appname=<applicationId>`
 * The start is the car's current location. Format from the official Naver Maps URL scheme guide:
 * https://guide.ncloud-docs.com/docs/application-maps-url-scheme-vpc.md
 *
 * Day-1 check (UNVERIFIED on Pleos): that the Pleos-market build of Naver Map uses the package
 * com.nhn.android.nmap and honours this deep link. Test on the emulator or a car before relying on it.
 */
class NaverMapHandoff(private val context: Context) : NavigationHandoff {
    override val decisions = TripDecisions()
    override fun clear() { _route.value = NavRoute() }

    private val _route = MutableStateFlow(NavRoute())
    override val route: StateFlow<NavRoute> = _route

    override val isAvailable: Boolean
        get() = context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    override fun setRoute(destination: Place, waypoint: Place?, reason: String?) {
        _route.value = NavRoute(destination = destination, waypoint = waypoint, lastAction = reason)
    }

    override fun launch(): Boolean {
        val current = _route.value
        val destination = current.destination ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildUrl(destination, current.waypoint, context.packageName)))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            _route.update { it.copy(lastAction = "네이버 지도로 안내를 시작했어요") }
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Naver Map is not installed", e)
            _route.update { it.copy(lastAction = "네이버 지도가 없어요 · Pleos 앱 마켓에서 설치해 주세요") }
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Navigation intent was rejected", e)
            _route.update { it.copy(lastAction = "네이버 지도를 열 수 없어요 · 앱 권한을 확인해 주세요") }
            false
        }
    }

    companion object {
        const val PACKAGE = "com.nhn.android.nmap"
        private const val TAG = "CarDropNaver"

        /** Pure function so it can be unit tested (see NaverMapHandoffTest). */
        fun buildUrl(destination: Place, waypoint: Place?, appName: String): String {
            val params = buildList {
                add("dlat" to coord(destination.lat))
                add("dlng" to coord(destination.lng))
                add("dname" to encode(destination.name))
                if (waypoint != null) {
                    add("v1lat" to coord(waypoint.lat))
                    add("v1lng" to coord(waypoint.lng))
                    add("v1name" to encode(waypoint.name))
                }
                add("appname" to appName)
            }
            return "nmap://navigation?" + params.joinToString("&") { (key, value) -> "$key=$value" }
        }

        private fun coord(value: Double): String = String.format(Locale.US, "%.6f", value)

        /** URL-encodes Korean names; spaces become %20, not '+'. */
        private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}
